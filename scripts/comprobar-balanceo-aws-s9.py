"""Prueba de consultas y balanceo en EC2 con TLS validado. Requiere fixtures S9 creados."""
import base64, json, socket, ssl, subprocess, sys
import urllib.error, urllib.parse, urllib.request

try:
    secreto = subprocess.run(
        ["sudo", "docker", "exec", "backend-auth-server", "printenv", "OAUTH_WEB_SECRET"],
        check=True, capture_output=True, text=True).stdout.strip()
    if not secreto:
        raise RuntimeError("OAUTH_WEB_SECRET no esta configurado")
    tls = ssl.create_default_context(cafile="/home/ec2-user/banco_xyz_s9_ca.pem")
    http = urllib.request.build_opener(
        urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=tls))
    basic = base64.b64encode(("bff-web:" + secreto).encode()).decode()
    datos = urllib.parse.urlencode({"grant_type": "client_credentials",
        "scope": "web:lectura web:resumen web:gestion web:pagos"}).encode()
    auth = urllib.request.Request("http://127.0.0.1:9000/oauth2/token", data=datos,
        headers={"Authorization": "Basic " + basic,
                 "Content-Type": "application/x-www-form-urlencoded"})
    with http.open(auth, timeout=20) as r:
        token = json.load(r)["access_token"]
    print("EC2:", socket.gethostname(), "| HTTPS con certificado verificado", flush=True)
    rutas = [("Cuentas", "/cuentas/900201"),
             ("Clientes", "/cuentas/900201/cliente"),
             ("Pagos", "/cuentas/900201/solicitudes/s9_aws_trans_001")]
    for servicio, ruta in rutas:
        instancias = set()
        print("\nMS-" + servicio.upper(), flush=True)
        for intento in range(1, 5):
            req = urllib.request.Request("https://localhost:8081/api/bff/web" + ruta,
                headers={"Authorization": "Bearer " + token})
            with http.open(req, timeout=20) as r:
                estado = r.status
                instancia = r.headers.get("X-Instancia-" + servicio)
                cuerpo = json.load(r)
            if estado != 200 or not instancia:
                raise RuntimeError("Falta HTTP 200 o cabecera de instancia en " + servicio)
            instancias.add(instancia)
            print("  Consulta", intento, "| HTTP", estado, "|", instancia, flush=True)
            if servicio == "Clientes" and intento == 1:
                cuentas = cuerpo.get("cuentas", [])
                print("  Cliente:", cuerpo.get("clienteId"), "| perfil:", cuerpo.get("perfil"),
                      "| version:", cuerpo.get("version"), "| cuentas:", cuentas, flush=True)
                if (cuerpo.get("clienteId") != 900501 or cuerpo.get("perfil") != "PREFERENTE"
                        or not {900201, 900202}.issubset(set(cuentas))):
                    raise RuntimeError("El cliente o sus cuentas no coinciden con la prueba")
        if len(instancias) < 2:
            raise RuntimeError("Solo se observo una instancia de " + servicio)
        print("  OK:", len(instancias), "instancias distintas", flush=True)
    print("\nBALANCEO OK EN CUENTAS, CLIENTES Y PAGOS", flush=True)
except urllib.error.HTTPError as e:
    print("DETENIDO: HTTP", e.code, "en", e.geturl(), file=sys.stderr)
    sys.exit(1)
except RuntimeError as e:
    print("DETENIDO:", e, file=sys.stderr)
    sys.exit(1)
except Exception as e:
    print("DETENIDO:", type(e).__name__, "(no se muestran credenciales)", file=sys.stderr)
    sys.exit(1)
