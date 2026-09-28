#!/bin/bash

set -e

BASE_DIR="/home/ec2-user/backend_III"
BFF_DIR="$BASE_DIR/bff"
LOG_DIR="$BASE_DIR/evidencias/semana7/logs"

mkdir -p "$LOG_DIR"

export DB_URL="jdbc:mysql://localhost:3306/bank_xyz_semana5_db?useSSL=false&serverTimezone=America/Santiago&allowPublicKeyRetrieval=true"
export DB_USER="bank_batch_user"
export DB_PASSWORD="${DB_PASSWORD:?Define DB_PASSWORD}"
export BFF_JWT_SECRET="${BFF_JWT_SECRET:-Cambiar-Este-Secreto-JWT}"
export BFF_LOGIN_PASSWORD="${BFF_LOGIN_PASSWORD:-Cambiar-Password-Login}"
export BFF_READ_PASSWORD="${BFF_READ_PASSWORD:-Cambiar-Password-Consulta}"
export BFF_TLS_PASSWORD="${BFF_TLS_PASSWORD:-changeit}"

echo "========================================"
echo " Banco XYZ - Inicio Semana 7 en AWS"
echo "========================================"

echo "[1/8] Iniciando Config Server..."
cd "$BFF_DIR"
nohup java -jar config-server/target/config-server-0.0.1-SNAPSHOT.jar \
  > "$LOG_DIR/config-server.log" 2>&1 &

sleep 8

echo "[2/8] Iniciando Eureka..."
nohup java -jar discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar \
  > "$LOG_DIR/discovery-server.log" 2>&1 &

sleep 8

echo "[3/8] Iniciando ms-cuentas..."
nohup java -jar ms-cuentas/target/ms-cuentas-0.0.1-SNAPSHOT.jar \
  > "$LOG_DIR/ms-cuentas.log" 2>&1 &

sleep 5

echo "[4/8] Iniciando ms-movimientos..."
nohup java -jar ms-movimientos/target/ms-movimientos-0.0.1-SNAPSHOT.jar \
  > "$LOG_DIR/ms-movimientos.log" 2>&1 &

sleep 5

echo "[5/8] Iniciando ms-operaciones..."
nohup java -jar ms-operaciones/target/ms-operaciones-0.0.1-SNAPSHOT.jar \
  > "$LOG_DIR/ms-operaciones.log" 2>&1 &

sleep 5

echo "[6/8] Iniciando BFF Web..."
BFF_TLS_STORE="$BFF_DIR/tls/web.p12" \
nohup java -jar bff-web/target/bff-web-0.0.1-SNAPSHOT.jar \
  > "$LOG_DIR/bff-web.log" 2>&1 &

sleep 3

echo "[7/8] Iniciando BFF Movil..."
BFF_TLS_STORE="$BFF_DIR/tls/movil.p12" \
nohup java -jar bff-movil/target/bff-movil-0.0.1-SNAPSHOT.jar \
  > "$LOG_DIR/bff-movil.log" 2>&1 &

sleep 3

echo "[8/8] Iniciando BFF Cajero..."
BFF_TLS_STORE="$BFF_DIR/tls/cajero.p12" \
nohup java -jar bff-cajero/target/bff-cajero-0.0.1-SNAPSHOT.jar \
  > "$LOG_DIR/bff-cajero.log" 2>&1 &

echo
echo "Servicios lanzados."
echo "Logs disponibles en:"
echo "$LOG_DIR"
