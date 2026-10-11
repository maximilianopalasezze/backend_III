"""Pruebas S9 de OAuth y reenvio idempotente de la transferencia ya existente.

Requiere fixtures: transferencia s9_aws_trans_001 por 2000; saldos actuales
900201=5500 y 900202=2000 tras la prueba de canales. No genera un solicitudId nuevo.
Ejecutar en EC2: python3 scripts/comprobar-seguridad-aws-s9.py
"""
import socket
from decimal import Decimal
from verificacion_cloud_s9 import BffCloud, ejecutar, verificar

def prueba():
    bff = BffCloud()
    web = bff.token("web", "web:lectura web:resumen web:gestion web:pagos")
    limitado = bff.token("web", "web:lectura")
    print("EC2:", socket.gethostname(), "| HTTPS con certificado validado", flush=True)
    print("\nSEGURIDAD OAUTH", flush=True)
    bff.api(8081, "/api/bff/web/cuentas/900201", esperado=401)
    print("OK: recurso protegido rechaza ausencia de token", flush=True)
    bff.api(8081, "/api/bff/web/resumen", limitado, esperado=403)
    print("OK: token valido con scope insuficiente es rechazado", flush=True)
    bff.api(8081, "/api/bff/web/cuentas/900999", web, esperado=403)
    print("OK: cuenta ajena al listado autorizado es rechazada", flush=True)
    bff.api(8082, "/api/bff/movil/cuentas/900201", web, esperado=401)
    print("OK: token WEB no es valido para el canal MOVIL", flush=True)

    print("\nIDEMPOTENCIA DE TRANSFERENCIA", flush=True)
    ruta = "/api/bff/web/cuentas/900201"
    original = bff.api(8081, ruta + "/solicitudes/s9_aws_trans_001", web)
    verificar(original.get("referencia") == "5f8a4808-5401-42bf-9dde-213cd95ed489"
              and original.get("solicitudId") == "s9_aws_trans_001"
              and original.get("cuentaId") == 900201
              and original.get("cuentaDestinoId") == 900202
              and Decimal(str(original.get("monto"))) == Decimal("2000")
              and original.get("concepto") == "Transferencia prueba Semana 9 AWS",
              "La transferencia original no coincide; no se reenvia")
    antes = (bff.saldo_web(900201, web), bff.saldo_web(900202, web))
    verificar(antes == (Decimal("5500"), Decimal("2000")), "Saldos iniciales inesperados")
    cuerpo = {"solicitudId": "s9_aws_trans_001", "cuentaDestinoId": 900202,
              "monto": 2000, "concepto": "Transferencia prueba Semana 9 AWS"}
    repetida = bff.api(8081, ruta + "/transferencias", web, cuerpo, esperado=201)
    verificar(repetida == original, "El reenvio no devolvio el mismo comprobante")
    print("MISMO COMPROBANTE: referencia =", repetida["referencia"],
          "| saldoPosterior historico =", repetida["saldoPosterior"], flush=True)
    cambiado = dict(cuerpo, monto=3000)
    bff.api(8081, ruta + "/transferencias", web, cambiado, esperado=409)
    despues = (bff.saldo_web(900201, web), bff.saldo_web(900202, web))
    verificar(despues == antes, "Los saldos cambiaron durante los reenvios")
    print("SALDOS SIN CAMBIOS: 900201 =", despues[0], "| 900202 =", despues[1], flush=True)
    print("SEGURIDAD E IDEMPOTENCIA OK", flush=True)

if __name__ == "__main__":
    ejecutar(prueba)
