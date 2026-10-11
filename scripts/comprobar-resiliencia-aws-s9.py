"""Caida y recuperacion de las dos replicas de MS-CUENTAS en el laboratorio.

Solo consulta saldos. Programa un rescate independiente de SSH antes de parar
las replicas y las restaura en finally. No consulta estados de Actuator.
Ejecutar en EC2: python3 scripts/comprobar-resiliencia-aws-s9.py
"""
import fcntl
import os
import re
import signal
import socket
import subprocess
import time
import uuid
from decimal import Decimal
from pathlib import Path
from verificacion_cloud_s9 import BffCloud, ejecutar, verificar

SENALES = (signal.SIGINT, signal.SIGTERM, signal.SIGHUP)

def comando(argumentos, timeout=25, comprobar=True):
    resultado = subprocess.run(["sudo"] + argumentos, capture_output=True,
                               text=True, timeout=timeout)
    verificar(not comprobar or resultado.returncode == 0,
              "Fallo al ejecutar " + argumentos[0] + "; no se detienen mas servicios")
    return resultado.stdout.strip()

def estados(ids):
    formato = '{{.Name}} {{.State.Running}} {{if .State.Health}}{{.State.Health.Status}}{{else}}sin-healthcheck{{end}}'
    return [linea.split() for linea in
            comando(["docker", "inspect", "--format", formato] + ids).splitlines()]

def saludables(ids):
    filas = estados(ids)
    return len(filas) == len(ids) and all(f[1:] == ["true", "healthy"] for f in filas)

def interrumpido(numero, frame):
    raise RuntimeError("Prueba interrumpida; se restauran las replicas")

def restaurar(ids, unidad):
    for senal in SENALES:
        signal.signal(senal, signal.SIG_IGN)
    print("\nRESTAURANDO LAS DOS REPLICAS DE MS-CUENTAS", flush=True)
    try:
        comando(["docker", "start"] + ids, timeout=45)
        limite = time.monotonic() + 150
        while not saludables(ids):
            verificar(time.monotonic() < limite,
                      "Las replicas aun no estan healthy; el rescate queda programado")
            time.sleep(3)
        for fila in estados(ids):
            print("RECUPERADO:", " ".join(fila), flush=True)
        comando(["systemctl", "stop", unidad + ".timer"], comprobar=False)
    finally:
        for senal in SENALES:
            signal.signal(senal, interrumpido)

def probar_caida():
    verificar(os.path.isfile("/usr/bin/docker"), "No existe /usr/bin/docker para el rescate")
    ids = comando(["docker", "ps", "-q",
        "--filter", "label=com.docker.compose.project=infra",
        "--filter", "label=com.docker.compose.service=ms-cuentas"]).splitlines()
    verificar(len(ids) == 2 and all(re.fullmatch(r"[0-9a-f]{12,64}", x) for x in ids),
              "Se requieren exactamente dos replicas de MS-CUENTAS")
    verificar(saludables(ids), "Las dos replicas deben comenzar healthy")
    bff = BffCloud()
    web = bff.token("web", "web:lectura")
    antes = (bff.saldo_web(900201, web), bff.saldo_web(900202, web))
    verificar(antes == (Decimal("5500"), Decimal("2000")), "Saldos iniciales inesperados")
    print("EC2:", socket.gethostname(), "| HTTPS con certificado validado", flush=True)
    print("BASELINE: HTTP 200 | saldos 900201=5500, 900202=2000", flush=True)

    unidad = "banco-s9-rescate-" + uuid.uuid4().hex[:12]
    comando(["systemd-run", "--unit=" + unidad, "--on-active=120s",
             "--timer-property=AccuracySec=1s", "/usr/bin/docker", "start"] + ids)
    tocadas = False
    try:
        comando(["systemctl", "is-active", "--quiet", unidad + ".timer"])
        print("RESCATE PROGRAMADO: reinicio en 120 s si se pierde SSH", flush=True)
        tocadas = True  # Antes de parar: un fallo parcial tambien exige restauracion.
        comando(["docker", "stop", "--time", "10"] + ids, timeout=45)
        filas = estados(ids)
        verificar(len(filas) == 2 and all(f[1] == "false" for f in filas),
                  "Las dos replicas no quedaron detenidas")
        print("DOS REPLICAS DETENIDAS", flush=True)
        for intento in range(1, 4):
            respuesta = bff.api(8081, "/api/bff/web/cuentas/900201", web, esperado=503)
            verificar(respuesta.get("estado") == 503
                      and respuesta.get("mensaje") == "MS-CUENTAS no disponible temporalmente",
                      "No se obtuvo el fallback controlado de MS-CUENTAS")
            print("FALLBACK", intento, ":", respuesta["mensaje"], flush=True)
    finally:
        if tocadas:
            restaurar(ids, unidad)
        else:
            comando(["systemctl", "stop", unidad + ".timer"], comprobar=False)

    web = bff.token("web", "web:lectura")
    limite = time.monotonic() + 150
    exitos = 0
    while exitos < 2:
        try:
            saldo = bff.saldo_web(900201, web)
        except RuntimeError as error:
            if "HTTP 503;" not in str(error):
                raise
            exitos = 0
            verificar(time.monotonic() < limite, "El BFF sigue devolviendo 503 tras restaurar")
            print("Esperando actualizacion de Eureka y recuperacion del BFF...", flush=True)
            time.sleep(3)
            continue
        verificar(saldo == antes[0], "El saldo cambio durante las consultas")
        exitos += 1
        print("CONSULTA RECUPERADA", exitos, ": HTTP 200 | saldo =", saldo, flush=True)
    despues = (bff.saldo_web(900201, web), bff.saldo_web(900202, web))
    verificar(despues == antes and saludables(ids), "La recuperacion final no coincide")
    print("SALDOS SIN CAMBIOS: 900201 =", despues[0], "| 900202 =", despues[1], flush=True)
    print("RESILIENCIA OK: 200 -> 503 CONTROLADO -> 200; DOS REPLICAS HEALTHY", flush=True)

def prueba():
    carpeta = Path(__file__).resolve().parent.parent / ".local"
    carpeta.mkdir(exist_ok=True)
    with (carpeta / "resiliencia-s9.lock").open("a") as bloqueo:
        try:
            fcntl.flock(bloqueo, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError("Ya hay otra prueba de resiliencia en ejecucion")
        for senal in SENALES:
            signal.signal(senal, interrumpido)
        probar_caida()

if __name__ == "__main__":
    ejecutar(prueba)
