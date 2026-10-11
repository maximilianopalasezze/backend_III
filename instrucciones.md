# Banco XYZ — Ejecución y pruebas

Checkout: [semana9-evaluacion-final](https://github.com/maximilianopalasezze/backend_III/tree/semana9-evaluacion-final). Los comandos parten de la raíz del checkout, en el PC o en EC2 según se indica.

## 1. Preparar y compilar

Se requieren Java 17 y Git. Los wrappers Maven están incluidos. Docker Engine, Docker Compose y Python 3 se requieren para la integración; Postman permite revisar los contratos.

```powershell
git clone --branch semana9-evaluacion-final https://github.com/maximilianopalasezze/backend_III.git
cd backend_III
java -version
Push-Location .\bff
.\mvnw.cmd -B -DskipTests package
Pop-Location
Push-Location .\batch
.\mvnw.cmd -B -DskipTests package
Pop-Location
```

Si ya existe el checkout, usar `git pull --ff-only` en esa rama. Se generan once JAR ejecutables en `bff/<modulo>/target` y el JAR Batch en `batch/target`. `bff-compartido` es una dependencia. `-DskipTests` empaqueta sin validar tests.

## 2. Verificar Batch y BFF

Tests Batch con H2 aislada, sin modificar el banco:

```powershell
Push-Location .\batch
.\mvnw.cmd -B "-Dtest=ReintentosBatchTests,RecuperacionBatchTests,ServicioControlReinicioTests,RecursoEntradaBatchTests" package | Tee-Object -FilePath .\target\cierre-batch-s9.log
Select-String -Path .\target\cierre-batch-s9.log -Pattern "REINTENTO BATCH OK|ERROR PERMANENTE OK|Tests run:|BUILD SUCCESS"
Pop-Location
```

Resultado observado: 14 pruebas sin fallas, errores u omisiones. Cada caso JDBC registró un reintento y COMPLETED con 1000 lecturas, 480 escrituras, 520 rechazos y datos idénticos. SQL inválido produce FAILED, cero Retry/escrituras de forma deliberada; la prueba es exitosa.

Verificaciones seleccionadas de autorización y BFF:

```powershell
Push-Location .\bff
.\mvnw.cmd -B -pl bff-compartido,auth-server,bff-web,bff-movil,bff-cajero -am verify
Pop-Location
```

La verificación de desarrollo aprobó 50 pruebas de esos módulos. Las 31 de pagos/movimientos se documentan en el [README de pagos](https://github.com/maximilianopalasezze/backend_III/blob/semana9-evaluacion-final/bff/ms-pagos/README.md). Los tests no sustituyen la ejecución integrada EC2.

## 3. Ejecutar los tres Jobs en MySQL de prueba

El arranque inicializa tablas. El Job de intereses actualiza saldos desde el CSV histórico; se mantiene desactivado en EC2 durante las operaciones bancarias. Desde `batch`, usando una base de prueba:

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/bank_xyz_semana5_db?useSSL=false&serverTimezone=America/Santiago&allowPublicKeyRetrieval=true'
$env:DB_USER = 'bank_batch_user'
$claveBatch = Read-Host 'Contraseña MySQL' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $claveBatch).Password
$env:BATCH_JOB_ENABLED = 'true'
$env:BATCH_REINICIO_AUTOMATICO = 'false'
$env:BATCH_HILOS = '1'
$env:BATCH_CHUNK = '50'
$env:BATCH_JOB_NAME = 'jobTransaccionesDiarias'
java -jar .\target\bank-batch-0.0.1-SNAPSHOT.jar
```

Para los otros Jobs, cambiar `BATCH_JOB_NAME` y ejecutar el mismo JAR. Los CSV oficiales están incluidos y son las entradas predeterminadas.

| Job | Leídos / escritos / rechazados | Resultado adicional |
| --- | --- | --- |
| `jobTransaccionesDiarias` | 1000 / 480 / 520 | 88 anomalías |
| `jobInteresesMensuales` | 1000 / 50 / 950 | Intereses 2525,00; saldos 413525,00; período 2024-01 |
| `jobEstadosCuentaAnuales` | 1000 / 684 / 316 | 20 estados de 2024 |

El archivo conserva el nombre oficial `intereses_trimestrales.csv`; la solución aplica tasas mensuales configuradas: ahorro 0,005, préstamo 0,015 e hipoteca 0.

Para volumen y comparación uno/tres hilos, seguir [Batch — mayor volumen](https://github.com/maximilianopalasezze/backend_III/blob/semana9-evaluacion-final/batch/README.md#prueba-reproducible-de-mayor-volumen). Se generan 100000 filas con IDs diferentes, manteniendo logging y datos iguales entre mediciones.

## 4. Recuperar un lote

Con la conexión MySQL configurada, desde `batch`:

```powershell
$env:BATCH_JOB_ENABLED = 'false'
$env:BATCH_REINICIO_AUTOMATICO = 'true'
$env:BATCH_JOB_NAME = 'jobTransaccionesDiarias'
$env:BATCH_LOTE = 's9-recuperacion-demostracion-02'
$env:BATCH_HILOS = '1'
$env:BATCH_CHUNK = '50'
$env:BATCH_FALLAR_DESPUES_COMMITS = '2'
java -jar .\target\bank-batch-0.0.1-SNAPSHOT.jar
```

El intento inicial falla después de 100 lecturas/dos commits; la misma instancia retoma 900 registros y termina igual al lote completo. La JVM sigue activa. Un lote COMPLETED no se reprocesa; otro ensayo requiere otro `BATCH_LOTE`. Se comprueban hash y parámetros antes del reinicio.

Al finalizar:

```powershell
$env:BATCH_JOB_ENABLED = 'false'
$env:BATCH_REINICIO_AUTOMATICO = 'false'
$env:BATCH_FALLAR_DESPUES_COMMITS = '0'
```

## 5. Iniciar el stack integrado

Preparar secretos, migraciones, certificados y JAR según [despliegue.md](despliegue.md). Desde la raíz en EC2:

```bash
sudo docker compose --env-file .env.docker.local -f infra/docker-compose.yml up -d --build --scale ms-cuentas=2 --scale ms-clientes=2 --scale ms-pagos=2
sudo docker compose --env-file .env.docker.local -f infra/docker-compose.yml ps
```

Compose inicia MySQL, Kafka, tópicos, Kafka UI, Config Server, Eureka, autorización, cinco microservicios y tres BFF. Los healthchecks ordenan las dependencias. El inicializador de tópicos termina al completar su tarea. Para el PC, abrir el túnel SSH de la guía AWS.

## 6. OAuth2, TLS y contratos

Importar `banco_xyz_s9_ca.pem` como CA en Postman con verificación SSL activada. Pedir un token en `POST http://localhost:9000/oauth2/token`: Basic Auth del cliente, cuerpo `x-www-form-urlencoded` con `grant_type=client_credentials` y los scopes requeridos. Usar Bearer en el BFF.

| Canal / cliente | Base HTTPS | Scopes |
| --- | --- | --- |
| Web / `bff-web` | `https://localhost:8081/api/bff/web` | `web:lectura web:resumen web:gestion web:pagos` |
| Móvil / `bff-movil` | `https://localhost:8082/api/bff/movil` | `movil:lectura movil:pagos` |
| Cajero / `bff-cajero` | `https://localhost:8083/api/bff/cajero` | `cajero:lectura cajero:retiro` |

El servidor fija canal/audience/cuentas. En este laboratorio los clientes tienen cuenta principal 900201; Web también autoriza 900202/900203. Los [contratos BFF](https://github.com/maximilianopalasezze/backend_III/blob/semana9-evaluacion-final/bff/README-semana9.md) detallan rutas y JSON.

Los pagos requieren `solicitudId`: una operación nueva usa clave nueva; una repetición conserva clave/cuerpo y devuelve el recibo histórico. Cambiar el cuerpo con esa clave produce 409. El retiro legado no tiene clave idempotente; la prueba ya retiró 1000 y conserva un control local. Conservar ese control al repetir las comprobaciones; una respuesta incierta del retiro requiere revisar el saldo antes de reenviarlo.

### Preparar el escenario en una base de prueba nueva

Los helpers de integración usan las cuentas 900201/900202, el cliente 900501 y claves conocidas. Para reproducir ese escenario desde cero, configurar en `.env.docker.local` la cuenta principal de los tres clientes OAuth2 como 900201 y agregar 900202/900203 a `OAUTH_WEB_CUENTAS` antes de iniciar el Authorization Server. Usar un token Web con scopes de lectura, gestión y pagos.

1. Abrir 900201 y 900202 con `POST https://localhost:8081/api/bff/web/cuentas`. Para cada una enviar `{"cuentaId":900201,"nombre":"Cuenta de prueba S9","edad":30,"tipoCuenta":"corriente"}`, cambiando el ID en la segunda. Ambas comienzan con saldo cero.
2. Crear el cliente con `POST /cuentas/900201/cliente` y `{"clienteId":900501,"nombre":"Cliente Prueba S9 AWS","email":"cliente.s9@example.test","telefono":"+56900000001","direccion":"Direccion de prueba S9","perfil":"ESTANDAR"}`. Vincular la segunda cuenta con `POST /cuentas/900201/cliente/cuentas` y `{"cuentaId":900202}`.
3. Actualizar mediante `PUT /cuentas/900201/cliente`, enviando los mismos datos personales, `"perfil":"PREFERENTE"` y `"version":0`. La respuesta debe tener versión 1.
4. Ejecutar una vez las tres operaciones Web de la tabla. Las rutas relativas usan la base `https://localhost:8081/api/bff/web` y scope `web:pagos`.

| Ruta POST | Cuerpo JSON |
| --- | --- |
| `/cuentas/900201/depositos` | `{"solicitudId":"s9_aws_dep_001","monto":10000,"concepto":"Deposito prueba Semana 9 AWS"}` |
| `/cuentas/900201/pagos` | `{"solicitudId":"s9_aws_pago_001","monto":1000,"beneficiario":"Servicio de prueba","concepto":"Pago prueba Semana 9 AWS"}` |
| `/cuentas/900201/transferencias` | `{"solicitudId":"s9_aws_trans_001","cuentaDestinoId":900202,"monto":2000,"concepto":"Transferencia prueba Semana 9 AWS"}` |

La respuesta de cada operación es 201. Los saldos quedan en 7000/2000. Completar los dos canales restantes desde la raíz en EC2:

```bash
python3 scripts/comprobar-canales-aws-s9.py
```

Este helper realiza el pago móvil de 500 y el retiro cajero de 1000, dejando saldos 5500/2000. El pago conserva una clave idempotente y el retiro registra un control local. En una instalación existente con ese escenario completado, conservar las claves, los cuerpos y el control de retiro; no volver a abrir ni cargar las cuentas como si fueran nuevas.

## 7. Verificación integrada

Desde la raíz del repositorio en EC2:

```bash
python3 scripts/comprobar-balanceo-aws-s9.py
python3 scripts/comprobar-seguridad-aws-s9.py
python3 scripts/comprobar-kafka-aws-s9.py
```

Los helpers consultan balanceo, autorización, reenvío de la transferencia existente, saldos, outbox y consumo. Usan los datos de esta demostración; no inicializan un banco genérico. El helper de canales creó el pago/retiro original y protege su repetición.

La prueba de resiliencia se ejecuta con:

```bash
python3 scripts/comprobar-resiliencia-aws-s9.py
```

Detiene ambas réplicas de cuentas, programa un rescate con systemd y las restaura antes de exigir consultas 200 y saldos conservados. Esta comprobación interrumpe temporalmente el servicio y se utiliza en el entorno de prueba. En la validación final produjo tres respuestas 503 controladas, restauró ambas réplicas en healthy y recuperó consultas 200. Los saldos finales observados fueron 5500/2000.

Los contratos de apertura, mantenimiento, cierre, clientes y pagos y sus ejemplos JSON se documentan en los README de [cuentas](bff/ms-cuentas/README.md), [clientes](bff/ms-clientes/README.md), [pagos](bff/ms-pagos/README.md) y [BFF](bff/README-semana9.md).
