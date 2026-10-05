#!/bin/bash
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TLS="$ROOT/bff/tls"
PASSWORD="${BFF_TLS_PASSWORD:-changeit}"

mkdir -p "$TLS"

for nombre in web movil cajero
do
  rm -f "$TLS/$nombre.p12"

  keytool -genkeypair \
    -alias bff-tls \
    -keyalg RSA \
    -keysize 2048 \
    -storetype PKCS12 \
    -keystore "$TLS/$nombre.p12" \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -dname "CN=localhost, OU=Backend III, O=Banco XYZ, L=Santiago, ST=RM, C=CL" \
    -ext "SAN=dns:localhost,ip:127.0.0.1" \
    -validity 3650 \
    -noprompt

  echo "Generado: bff/tls/$nombre.p12"
done
