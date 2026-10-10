# MS-PAGOS — depósitos, pagos y transferencias de Semana 9

Procesa tres operaciones bancarias: un depósito acredita la cuenta indicada; un pago debita esa cuenta y registra beneficiario y concepto; una transferencia debita el origen y acredita el destino. Los montos son positivos, admiten dos decimales y respetan el rango de `DECIMAL(15,2)`. Los saldos no pueden quedar negativos ni exceder ese rango. Las cuentas anteriores sin fila en `cuentas_estado` se consideran activas.

Las cuentas se bloquean en orden creciente de ID, compartiendo el bloqueo de la cuenta con cierre y retiros. Las dos partes de una transferencia, el recibo y el evento pendiente se confirman en una única transacción. Una cuenta inexistente produce 404; una cuenta cerrada, saldo insuficiente o desborde de saldo producen 409. Transferir hacia la misma cuenta produce 400. Un error al guardar cualquier parte revierte la operación completa.

## Solicitudes repetidas

Cada petición contiene `solicitudId`, una clave de 8 a 80 letras minúsculas, números, guiones o guiones bajos. Reenviar la misma clave con los mismos datos devuelve el mismo recibo, referencia y fecha, sin volver a alterar saldos ni crear eventos. La clave es global al servicio; utilizarla con otra cuenta, tipo, destino, monto, beneficiario o concepto produce 409. Cada operación nueva requiere una clave nueva.

El recibo conserva los saldos de esa operación; después de otras operaciones esos valores pueden diferir del saldo actual. Repetir una operación ya confirmada devuelve ese recibo incluso si la cuenta posteriormente se cerró, sin reabrirla ni mover dinero.

## Migración y contratos

En `bank_xyz_semana5_db`, aplicar [03-gestion-cuentas.sql](../sql/03-gestion-cuentas.sql) y [05-pagos.sql](../sql/05-pagos.sql). Para la integración completa, aplicar también la migración 04 de clientes. El script 05 crea `pagos_operaciones`, `pagos_outbox` y `movimientos_pagos`. Es repetible, conserva los datos y no crea cuentas de prueba. Compose lo monta para una base nueva; en un volumen existente debe ejecutarse manualmente antes de actualizar los servicios.

Base interna local: `http://localhost:8095`. Todas las rutas `/api/**` requieren Basic Auth con rol `SERVICE`. Sin credenciales responden 401 y VIEWER recibe 403. Los BFF aplicarán además OAuth2, permisos de canal y acceso a la cuenta antes de invocar estos contratos.

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/pagos/cuentas/{cuentaId}/depositos` | 201, recibo de depósito |
| POST | `/api/pagos/cuentas/{cuentaId}/pagos` | 201, recibo de pago |
| POST | `/api/pagos/cuentas/{cuentaId}/transferencias` | 201, recibo con saldos de origen y destino |
| GET | `/api/pagos/cuentas/{cuentaId}/solicitudes/{solicitudId}` | 200, recibo de esa cuenta; 404 si no corresponde a la cuenta indicada |

Los POST válidos, incluidas las repeticiones idénticas, responden 201. `estado: APROBADA` indica que la operación bancaria se confirmó en MySQL. El envío a Kafka se realiza posteriormente mediante el registro pendiente.

Ejemplos para dos cuentas activas de prueba, 900201 y 900202, abiertas previamente mediante MS-CUENTAS con saldo cero:

Depósito de 10000 en 900201:

```json
{
  "solicitudId": "s9-deposito-900201-01",
  "monto": 10000,
  "concepto": "Carga para pruebas Semana 9"
}
```

Pago de 1000 desde 900201:

```json
{
  "solicitudId": "s9-pago-900201-01",
  "monto": 1000,
  "beneficiario": "Servicio de prueba",
  "concepto": "Pago Semana 9"
}
```

Transferencia de 2000 desde 900201 hacia 900202:

```json
{
  "solicitudId": "s9-transferencia-900201-01",
  "cuentaDestinoId": 900202,
  "monto": 2000,
  "concepto": "Transferencia Semana 9"
}
```

Si se ejecutan una vez en ese orden, los saldos esperados son 7000 en 900201 y 2000 en 900202. Repetir solicitudes no cambia esos saldos. Son resultados esperados para la prueba local; su validación en PC está pendiente.

## Eventos y recuperación de publicación

El evento JSON completo se guarda en `pagos_outbox` dentro de la transacción bancaria. El publicador procesa hasta diez eventos por ciclo, bloqueando cada fila para coordinar varias instancias. Solo marca `publicado=true` cuando Kafka confirma el envío. Un fallo conserva el evento pendiente y aumenta `intentos`; el ciclo siguiente vuelve a intentar. Si un envío llegó a Kafka pero su confirmación o el guardado local fallaron, puede repetirse la entrega.

MS-MOVIMIENTOS consume `pagos.procesados` con el grupo `ms-movimientos-pagos-group`, registra depósitos y pagos, y guarda la salida y entrada de una transferencia en una transacción. La clave `(referencia, cuenta_id)` evita duplicados en reentregas. Una referencia con otros datos se rechaza y conserva el movimiento anterior. El consumidor no vuelve a modificar los saldos maestros. La consulta de movimientos combina esta proyección con los movimientos anuales del legado.

El consumidor comparte el manejo de errores existente: dos reintentos separados por dos segundos y envío a `operaciones.fallidas` cuando se agotan. Los eventos enviados a esa DLQ requieren revisión y reentrega tras resolver la causa. La publicación y el consumo con un broker real se comprobarán durante la integración.

## Compilación y arranque local

Desde `bff`, compilar todo el reactor:

```powershell
.\mvnw.cmd -B clean verify
```

Con JAVA_HOME, DB_URL, DB_USER y DB_PASSWORD definidos, ejecutar en la misma terminal después de detener el servicio anterior:

```powershell
$env:SPRING_DATASOURCE_URL = $env:DB_URL
$env:SPRING_DATASOURCE_USERNAME = $env:DB_USER
$env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD
& "$env:JAVA_HOME\bin\java.exe" -jar .\ms-pagos\target\ms-pagos-0.0.1-SNAPSHOT.jar --server.port=8095 --spring.config.import=optional:file:./config-repo/ms-pagos.properties --spring.cloud.config.enabled=false --eureka.client.enabled=false --app.outbox.enabled=false
```

Este primer arranque desactiva el publicador mientras se prueban los contratos de MySQL sin broker. Los eventos se guardan como pendientes. Para la prueba de mensajería, iniciar Kafka y MS-MOVIMIENTOS actualizado y arrancar MS-PAGOS con `--app.outbox.enabled=true`. Compose configura Kafka, Config Server y Eureka, y mantiene el puerto de pagos en la red interna, sin nombre fijo de contenedor ni puerto de host que impida crear réplicas.

## Verificación de desarrollo

Con Java 17, Maven `verify` para MS-PAGOS y MS-MOVIMIENTOS terminó correctamente: 31 pruebas, cero fallas, errores u omisiones. MS-PAGOS aprobó 15 pruebas de gestión con repositorios reales, transacciones H2 en modo MySQL y los scripts 03/05, y 7 de seguridad y validación con MockMVC. MS-MOVIMIENTOS aprobó 6 pruebas nuevas de proyección y consulta y sus 3 pruebas de seguridad existentes.

Se verificaron persistencia de saldos, recibo y JSON, repetición de los tres tipos, clave reutilizada con otros datos, repetición tras cierre, cuenta cerrada/inexistente, saldo insuficiente, precisión y límites, reversión de ambos saldos ante fallo de outbox, cargos concurrentes, solicitudes concurrentes idénticas y transferencias opuestas. La prueba de publicación utiliza KafkaTemplate simulado: falla, conserva el evento y después confirma la publicación sin repetir el cargo. Las pruebas de consumidor invocan el componente real con transacciones H2; comprueban reentrega, conflicto de referencia, reversión de una transferencia incompleta y consulta conjunta con el legado.

Ambos JAR se empaquetaron. MS-PAGOS inició en 8095 usando la configuración local y `/actuator/info` respondió 200; MS-MOVIMIENTOS inició en 8092 con las dos fábricas de consumidores configuradas. Esas comprobaciones de arranque no consultaron MySQL ni validaron envío o consumo con un broker real.

Quedan pendientes las pruebas de PC con MySQL/Postman, los contratos de los BFF con OAuth2 y Resilience4j, la entrega Kafka real y la ejecución con varias instancias en AWS.
