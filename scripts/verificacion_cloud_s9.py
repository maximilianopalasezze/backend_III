"""Utilidades para las pruebas de laboratorio S9 en EC2; no imprimen secretos."""
import base64
import json
import ssl
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from decimal import Decimal

def verificar(condicion, mensaje):
    if not condicion:
        raise RuntimeError(mensaje)

class BffCloud:
    def __init__(self):
        tls = ssl.create_default_context(cafile="/home/ec2-user/banco_xyz_s9_ca.pem")
        self.http = urllib.request.build_opener(
            urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=tls))

    def token(self, canal, scopes):
        secreto = subprocess.run(
            ["sudo", "docker", "exec", "backend-auth-server", "printenv",
             "OAUTH_" + canal.upper() + "_SECRET"],
            check=True, capture_output=True, text=True, timeout=20).stdout.strip()
        verificar(bool(secreto), "Falta secreto OAuth para " + canal)
        basic = base64.b64encode(("bff-" + canal + ":" + secreto).encode()).decode()
        datos = urllib.parse.urlencode({"grant_type": "client_credentials", "scope": scopes}).encode()
        req = urllib.request.Request("http://127.0.0.1:9000/oauth2/token", data=datos,
            headers={"Authorization": "Basic " + basic,
                     "Content-Type": "application/x-www-form-urlencoded"})
        with self.http.open(req, timeout=20) as r:
            return json.load(r)["access_token"]

    def api(self, puerto, ruta, token=None, cuerpo=None, esperado=200):
        headers = {"Content-Type": "application/json"}
        if token is not None:
            headers["Authorization"] = "Bearer " + token
        datos = None if cuerpo is None else json.dumps(cuerpo).encode()
        req = urllib.request.Request("https://localhost:" + str(puerto) + ruta,
                                     data=datos, headers=headers)
        try:
            with self.http.open(req, timeout=20) as r:
                estado, texto = r.status, r.read().decode("utf-8")
        except urllib.error.HTTPError as e:
            estado, texto = e.code, e.read().decode("utf-8")
        verificar(estado == esperado,
                  req.get_method() + " " + ruta + ": HTTP " + str(estado)
                  + "; se esperaba " + str(esperado))
        print(req.get_method(), req.full_url, "-> HTTP", estado, flush=True)
        return json.loads(texto) if texto.strip() else None

    def saldo_web(self, cuenta, token):
        respuesta = self.api(8081, "/api/bff/web/cuentas/" + str(cuenta), token)
        return Decimal(str(respuesta["producto"]["saldoDisponible"]))

def docker(contenedor, argumentos, kafka=False):
    comando = ["sudo", "docker", "exec"]
    if kafka:
        comando += ["-e", "KAFKA_HEAP_OPTS=-Xms32m -Xmx128m"]
    comando += [contenedor] + argumentos
    return subprocess.run(comando, check=True, capture_output=True,
                          text=True, timeout=45).stdout.strip()

def sql(consulta):
    texto = docker("backend-mysql", ["sh", "-c",
        'set -eu; export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --raw --skip-column-names bank_xyz_semana5_db -e "$1"',
        "verificacion-s9", consulta])
    return [linea.split("\t") for linea in texto.splitlines() if linea.strip()]

def ejecutar(prueba):
    try:
        prueba()
    except urllib.error.HTTPError as e:
        print("DETENIDO: HTTP", e.code, "en", e.geturl(), file=sys.stderr)
        sys.exit(1)
    except RuntimeError as e:
        print("DETENIDO:", e, file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print("DETENIDO:", type(e).__name__, "(no se muestran credenciales)", file=sys.stderr)
        sys.exit(1)
