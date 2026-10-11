# Banco XYZ — Despliegue AWS

Guía reproducible para [Semana 9](https://github.com/maximilianopalasezze/backend_III/tree/semana9-evaluacion-final). Los identificadores, direcciones y rutas privadas de la instancia se completan en el entorno local; no se publican aquí.

## 1. Requisitos y acceso

Usar una EC2 Linux con Git, Docker Engine, complemento Docker Compose y Python 3. La demostración utilizó Amazon Linux 2023. Verificar estado Running, asociación de la IP elástica y una regla SSH cuyo origen sea la conexión del operador. Los servicios se consultan mediante túnel SSH.

Definir en PowerShell los datos de la propia instancia:

```powershell
$ipEc2S9 = 'IP_ELASTICA_DE_TU_INSTANCIA'
$claveEc2S9 = 'RUTA_LOCAL_DE_TU_CLAVE_PEM'
$rutaEc2S9 = 'RUTA_DEL_REPOSITORIO_EN_EC2'
ssh -i $claveEc2S9 "ec2-user@$ipEc2S9"
```

La clave permanece en el PC. En EC2 verificar los requisitos:

```bash
git --version
sudo systemctl is-active docker
sudo docker version
sudo docker compose version
python3 --version
```

La instancia de la demostración ya tenía estas herramientas instaladas. En una máquina nueva instalarlas antes de continuar.

## 2. Código y configuración

Desde el directorio elegido en EC2:

```bash
git clone --branch semana9-evaluacion-final https://github.com/maximilianopalasezze/backend_III.git
cd backend_III
cp env.docker.example .env.docker.local
chmod 600 .env.docker.local
nano .env.docker.local
```

Completar las contraseñas MySQL, Basic Auth, TLS y secretos OAuth con los valores del entorno privado. Configurar las cuentas de demostración mediante `OAUTH_*_CUENTA_ID` y las listas `OAUTH_*_CUENTAS`. No publicar `.env.docker.local`.

En una instancia existente conservar configuración y certificados; actualizar con `git pull --ff-only` en esta rama. Los cambios de documentación y tests Batch no requieren reconstruir servicios que ya están healthy.

## 3. JAR y TLS

Compilar el reactor `bff` en PC según `instrucciones.md`. Desde la raíz del checkout, con las variables de conexión definidas, transferir los once JAR:

```powershell
$modulosS9 = @('config-server','discovery-server','auth-server','ms-cuentas','ms-movimientos','ms-operaciones','ms-clientes','ms-pagos','bff-web','bff-movil','bff-cajero')
foreach ($moduloS9 in $modulosS9) {
    ssh -i $claveEc2S9 "ec2-user@$ipEc2S9" "mkdir -p $rutaEc2S9/bff/$moduloS9/target"
    scp -i $claveEc2S9 ".\bff\$moduloS9\target\$moduloS9-0.0.1-SNAPSHOT.jar" "ec2-user@${ipEc2S9}:${rutaEc2S9}/bff/$moduloS9/target/"
}
```

Los BFF usan keystores PKCS12 montados desde `infra/certs`. Conservar los certificados existentes y su contraseña. El script `infra/generar-tls.sh` prepara certificados de laboratorio para una instalación nueva; ejecutarlo sobre los existentes los reemplaza.

Exportar únicamente los certificados públicos de confianza en el bundle `banco_xyz_s9_ca.pem` e importarlo como CA de Postman con SSL activo. Las pruebas Python verifican CA y hostname. No transferir ni publicar las claves privadas. El bundle debe estar en la ubicación que esperan los helpers de verificación, definida en su código.

## 4. MySQL y migraciones

Una base nueva usa el SQL inicial y las migraciones montadas por Compose. En un volumen existente no se repiten automáticamente: respaldar y aplicar 03, 04, 05 en orden. Desde la raíz, con MySQL iniciado:

```bash
respaldoS9="../bank_xyz_antes_s9_$(date +%Y%m%d_%H%M%S).sql"
sudo docker exec backend-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers bank_xyz_semana5_db' > "$respaldoS9"
chmod 600 "$respaldoS9"
test -s "$respaldoS9"
sudo docker exec -i backend-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot bank_xyz_semana5_db' < bff/sql/03-gestion-cuentas.sql
sudo docker exec -i backend-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot bank_xyz_semana5_db' < bff/sql/04-clientes.sql
sudo docker exec -i backend-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot bank_xyz_semana5_db' < bff/sql/05-pagos.sql
```

Revisar el éxito de cada comando antes del siguiente. Guardar el respaldo fuera del repositorio y conservar el legado. Las migraciones agregan estado de cuentas, clientes/vínculos y pagos/outbox/proyección. No usar `down -v` para actualizar.

## 5. Construcción y arranque

Desde la raíz en EC2:

```bash
sudo docker compose --env-file .env.docker.local -f infra/docker-compose.yml build
sudo docker compose --env-file .env.docker.local -f infra/docker-compose.yml up -d --scale ms-cuentas=2 --scale ms-clientes=2 --scale ms-pagos=2
sudo docker compose --env-file .env.docker.local -f infra/docker-compose.yml ps
sudo docker images --filter 'reference=banco-xyz/*:semana9'
```

Healthchecks y dependencias coordinan el arranque. Kafka KRaft crea cuatro tópicos con tres particiones y factor de réplica uno. Cuentas, clientes y pagos no fijan nombre de contenedor ni puerto de host, permitiendo réplicas con IDs distintos. Las aplicaciones usan `-Xms64m -Xmx192m`.

Resultado observado: 17 contenedores en ejecución, 16 healthy y Kafka UI Up sin healthcheck. `kafka-init` termina al completar su tarea. Cerrar SSH no detiene los contenedores. Tras reiniciar el host, comprobar con `up -d` y los mismos argumentos `--scale`.

## 6. Túnel desde Windows

En otra ventana PowerShell definir las mismas variables de conexión y mantener activo:

```powershell
ssh -i $claveEc2S9 -o ExitOnForwardFailure=yes -N -L 8761:127.0.0.1:8761 -L 8090:127.0.0.1:8090 -L 9000:127.0.0.1:9000 -L 8081:127.0.0.1:8081 -L 8082:127.0.0.1:8082 -L 8083:127.0.0.1:8083 "ec2-user@$ipEc2S9"
```

Puede permanecer sin texto mientras mantiene el túnel.

| Servicio | Acceso local |
| --- | --- |
| Eureka / Kafka UI | `http://localhost:8761` / `http://localhost:8090` |
| Authorization Server | `http://localhost:9000` |
| BFF Web / Móvil / Cajero | `https://localhost:8081` / `8082` / `8083` |

El issuer del laboratorio es `http://localhost:9000`. MySQL y servicios de negocio reciben las llamadas internas en la red de Compose.

## 7. Validación y límites

Eureka y tráfico real demostraron dos réplicas por servicio crítico. Se verificaron canales, OAuth, idempotencia, outbox/Kafka y recuperación de cuentas sin cambios de saldo. Consultar el [índice de capturas](https://github.com/maximilianopalasezze/backend_III/blob/semana9-evaluacion-final/docs/evidencias-s9.md).

La disponibilidad del laboratorio depende de una sola EC2 y MySQL/Kafka/autorización únicos. Se propone varias zonas, servicios administrados, métricas del Circuit Breaker y PKCE para usuarios finales. Batch se verificó en PC; no cargó CSV históricos sobre EC2.
