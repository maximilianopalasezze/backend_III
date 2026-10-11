"""Verificacion de solo lectura: auditoria, outbox, proyecciones y Kafka en EC2.

Requiere las operaciones de prueba S9 y el retiro confirmado por el script de
canales. Verifica la existencia de la DLQ; no inyecta un evento fallido en ella.
Ejecutar: python3 scripts/comprobar-kafka-aws-s9.py
"""
import json
import re
import socket
import subprocess
from decimal import Decimal
from pathlib import Path
from verificacion_cloud_s9 import docker, ejecutar, sql, verificar

ESPERADOS = {
    "s9_aws_dep_001": ("DEPOSITO", Decimal("10000"), 1),
    "s9_aws_pago_001": ("PAGO", Decimal("1000"), 1),
    "s9_aws_trans_001": ("TRANSFERENCIA", Decimal("2000"), 2),
    "s9_aws_mov_pago_001": ("PAGO", Decimal("500"), 1)
}

def lag(grupo, texto):
    filas = [linea.split() for linea in texto.splitlines()
             if linea.split() and linea.split()[0] == grupo]
    verificar(bool(filas), "No hay offsets para " + grupo)
    total, vacias = 0, 0
    for f in filas:
        verificar(len(f) >= 6, "Formato de offsets inesperado")
        if f[5] == "-":
            verificar(f[3] == "-" and f[4] == "0", "Lag desconocido en particion con mensajes")
            vacias += 1
        else:
            valor = int(f[5])
            verificar(valor >= 0, "Lag invalido")
            total += valor
    return total, vacias

def kafka(script, argumentos):
    return docker("backend-kafka", ["/opt/kafka/bin/" + script,
                  "--bootstrap-server", "localhost:9092"] + argumentos, kafka=True)

def prueba():
    print("EC2:", socket.gethostname(), "| consultas de solo lectura", flush=True)
    saldos = {int(cuenta): Decimal(saldo) for cuenta, saldo in sql(
        "SELECT cuenta_id,saldo FROM cuentas WHERE cuenta_id IN (900201,900202) ORDER BY cuenta_id")}
    verificar(saldos == {900201: Decimal("5500"), 900202: Decimal("2000")}, "Saldos SQL inesperados")
    print("SALDOS MYSQL:", saldos[900201], "(900201) |", saldos[900202], "(900202)", flush=True)

    ids = ",".join("'" + valor + "'" for valor in ESPERADOS)
    filas = sql("SELECT p.solicitud_id,p.tipo,p.monto,p.estado,COALESCE(o.publicado,0),"
        "COALESCE(o.topic,'FALTA'),(SELECT COUNT(*) FROM movimientos_pagos m "
        "WHERE m.referencia=p.referencia),p.referencia FROM pagos_operaciones p "
        "LEFT JOIN pagos_outbox o ON o.referencia=p.referencia WHERE p.solicitud_id IN ("
        + ids + ") ORDER BY p.solicitud_id")
    verificar(len(filas) == 4 and {f[0] for f in filas} == set(ESPERADOS), "Faltan operaciones S9")
    print("\nAUDITORIA Y OUTBOX: solicitud | tipo | monto | estado | publicado | topic | movimientos | referencia", flush=True)
    for f in filas:
        tipo, monto, movimientos = ESPERADOS[f[0]]
        verificar(f[1] == tipo and Decimal(f[2]) == monto and f[3] == "APROBADA"
                  and f[4] == "1" and f[5] == "pagos.procesados"
                  and int(f[6]) == movimientos, "Auditoria, outbox o proyeccion pendiente en " + f[0])
        print(" | ".join(f), flush=True)
    print("OK: 4 operaciones, 4 eventos publicados y 5 movimientos", flush=True)

    proyeccion = sql("SELECT p.solicitud_id,m.cuenta_id,m.tipo_movimiento,m.monto "
        "FROM movimientos_pagos m JOIN pagos_operaciones p ON p.referencia=m.referencia "
        "WHERE p.solicitud_id IN (" + ids + ") ORDER BY p.solicitud_id,m.cuenta_id")
    verificar(len(proyeccion) == 5, "Numero inesperado de movimientos")
    print("\nPROYECCION CONSUMIDA: solicitud | cuenta | tipo | monto", flush=True)
    for f in proyeccion:
        print(" | ".join(f), flush=True)
    transferencias = {(int(f[1]), f[2], Decimal(f[3])) for f in proyeccion if f[0] == "s9_aws_trans_001"}
    verificar(transferencias == {(900201, "TRANSFERENCIA_SALIDA", Decimal("2000")),
                                 (900202, "TRANSFERENCIA_ENTRADA", Decimal("2000"))},
              "Proyeccion de transferencia inesperada")

    topicos = kafka("kafka-topics.sh", ["--list"])
    verificar({"pagos.procesados", "operaciones.procesadas", "operaciones.fallidas"}
              .issubset(set(topicos.splitlines())), "Falta un topico requerido")
    print("\nTOPICOS (incluye DLQ operaciones.fallidas):\n" + topicos, flush=True)
    for grupo in ("ms-movimientos-pagos-group", "ms-movimientos-group"):
        estado = kafka("kafka-consumer-groups.sh", ["--describe", "--group", grupo, "--state"])
        verificar(bool(re.search(r"\bstable\b", estado, re.IGNORECASE)), "Grupo no estable: " + grupo)
        offsets = kafka("kafka-consumer-groups.sh", ["--describe", "--group", grupo])
        atraso, vacias = lag(grupo, offsets)
        verificar(atraso == 0, "Hay mensajes pendientes en " + grupo)
        print("\n" + estado + "\n" + offsets, flush=True)
        print("OK:", grupo, "sin mensajes pendientes; particiones vacias sin offset:", vacias, flush=True)

    marca = json.loads(Path("/home/ec2-user/backend_III/.local/retiro_s9_aws_001.json").read_text())
    verificar(marca.get("control") == "COMPLETADO", "Retiro no confirmado")
    referencia = marca["respuesta"]["referencia"]
    registro = subprocess.run(["sudo", "docker", "logs", "--tail", "2000", "backend-ms-movimientos"],
        check=True, capture_output=True, text=True, timeout=30)
    recibidos = [linea for linea in (registro.stdout + "\n" + registro.stderr).splitlines()
                 if "Evento Kafka recibido" in linea and referencia in linea]
    verificar(bool(recibidos), "No aparece el evento Kafka del retiro confirmado")
    print("\nEVENTO DEL RETIRO RECIBIDO:\n" + recibidos[-1], flush=True)
    print("KAFKA OK: OUTBOX PUBLICADO, 5 MOVIMIENTOS Y CONSUMIDORES SIN ATRASO", flush=True)

if __name__ == "__main__":
    ejecutar(prueba)
