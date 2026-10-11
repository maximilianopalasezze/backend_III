"""Prueba en EC2 del pago movil y retiro cajero sobre la cuenta de laboratorio 900201.

Requiere saldo inicial 7000 y certificados publicos en /home/ec2-user/banco_xyz_s9_ca.pem.
Pago: 500 con solicitudId fijo; retiro: 1000. Saldo final esperado: 5500.
La marca .local/retiro_s9_aws_001.json impide reenviar automaticamente un retiro
confirmado o pendiente. No eliminarla para repetir la prueba.
Las credenciales se leen del contenedor auth-server y no se muestran.
Ejecutar: python3 scripts/comprobar-canales-aws-s9.py
"""
import base64, json, os, socket, ssl, subprocess, sys
from decimal import Decimal
from pathlib import Path
import urllib.error, urllib.parse, urllib.request

def verificar(condicion, mensaje):
    if not condicion:
        raise RuntimeError(mensaje)

def obtener_token(canal, scopes):
    secreto = subprocess.run(
        ["sudo", "docker", "exec", "backend-auth-server", "printenv",
         "OAUTH_" + canal.upper() + "_SECRET"],
        check=True, capture_output=True, text=True).stdout.strip()
    verificar(bool(secreto), "Falta secreto OAuth para " + canal)
    basic = base64.b64encode(("bff-" + canal + ":" + secreto).encode()).decode()
    datos = urllib.parse.urlencode({"grant_type": "client_credentials", "scope": scopes}).encode()
    req = urllib.request.Request("http://127.0.0.1:9000/oauth2/token", data=datos,
        headers={"Authorization": "Basic " + basic,
                 "Content-Type": "application/x-www-form-urlencoded"})
    with http.open(req, timeout=20) as r:
        return json.load(r)["access_token"]

def api(puerto, ruta, token, cuerpo=None, esperado=200):
    datos = None if cuerpo is None else json.dumps(cuerpo).encode()
    req = urllib.request.Request("https://localhost:" + str(puerto) + ruta, data=datos,
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
    with http.open(req, timeout=20) as r:
        verificar(r.status == esperado, "HTTP inesperado en " + ruta)
        respuesta = json.load(r)
        print(req.get_method(), req.full_url, "-> HTTP", r.status, flush=True)
    return respuesta

try:
    tls = ssl.create_default_context(cafile="/home/ec2-user/banco_xyz_s9_ca.pem")
    http = urllib.request.build_opener(
        urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=tls))
    movil = obtener_token("movil", "movil:lectura movil:pagos")
    cajero = obtener_token("cajero", "cajero:lectura cajero:retiro")
    print("EC2:", socket.gethostname(), "| HTTPS validado | OAuth por canal", flush=True)

    base_movil = "/api/bff/movil/cuentas/900201"
    base_cajero = "/api/bff/cajero/cuentas/900201"
    inicio = api(8082, base_movil, movil)
    verificar(inicio.get("canal") == "MOVIL", "Respuesta incorrecta de Movil")
    print("MOVIL INICIAL:", json.dumps(inicio), flush=True)
    pago = api(8082, base_movil + "/pagos", movil, {
        "solicitudId": "s9_aws_mov_pago_001", "monto": 500,
        "beneficiario": "Servicio movil de prueba",
        "concepto": "Pago movil Semana 9 AWS"}, esperado=201)
    verificar(pago.get("estado") == "APROBADA" and bool(pago.get("referencia"))
              and Decimal(str(pago.get("monto"))) == Decimal("500"), "Pago no confirmado")
    print("PAGO MOVIL (solicitud idempotente):", json.dumps(pago), flush=True)

    saldo = api(8083, base_cajero + "/saldo", cajero)
    verificar(saldo.get("canal") == "CAJERO", "Respuesta incorrecta de Cajero")
    print("SALDO CAJERO:", json.dumps(saldo), flush=True)

    marca = Path("/home/ec2-user/backend_III/.local/retiro_s9_aws_001.json")
    marca.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if marca.exists():
        guardado = json.loads(marca.read_text())
        verificar(guardado.get("control") == "COMPLETADO",
                  "Retiro pendiente de confirmar. No se reenvia automaticamente")
        retiro = guardado["respuesta"]
        print("RETIRO YA CONFIRMADO; comprobante guardado:", json.dumps(retiro), flush=True)
    else:
        verificar(saldo.get("retiroDisponible") is True, "Retiro no disponible")
        with marca.open("x") as f:
            json.dump({"control": "PENDIENTE", "cuentaId": 900201, "monto": 1000}, f)
            f.flush()
            os.fsync(f.fileno())
        retiro = api(8083, base_cajero + "/retiros", cajero, {"monto": 1000})
        verificar(retiro.get("canal") == "CAJERO" and retiro.get("estado") == "APROBADA"
                  and bool(retiro.get("referencia"))
                  and Decimal(str(retiro.get("monto"))) == Decimal("1000")
                  and Decimal(str(retiro["saldoAnterior"]))
                      - Decimal(str(retiro["saldoDisponible"])) == Decimal("1000"),
                  "Comprobante de retiro inesperado. Revisar antes de continuar")
        temporal = marca.with_suffix(".tmp")
        temporal.write_text(json.dumps({"control": "COMPLETADO", "respuesta": retiro}))
        temporal.replace(marca)
        print("RETIRO CAJERO:", json.dumps(retiro), flush=True)

    final_movil = api(8082, base_movil, movil)
    final_cajero = api(8083, base_cajero + "/saldo", cajero)
    saldo_movil = Decimal(str(final_movil["saldoDisponible"]))
    saldo_cajero = Decimal(str(final_cajero["saldoDisponible"]))
    verificar(saldo_movil == saldo_cajero == Decimal("5500"), "Saldo final distinto del esperado")
    print("SALDOS FINALES: MOVIL =", saldo_movil, "| CAJERO =", saldo_cajero, flush=True)
    print("CANALES OK: PAGO MOVIL Y RETIRO CAJERO APROBADOS", flush=True)
except urllib.error.HTTPError as e:
    print("DETENIDO: HTTP", e.code, "en", e.geturl(), file=sys.stderr)
    sys.exit(1)
except RuntimeError as e:
    print("DETENIDO:", e, file=sys.stderr)
    sys.exit(1)
except Exception as e:
    print("DETENIDO:", type(e).__name__, "(no se muestran credenciales)", file=sys.stderr)
    sys.exit(1)
