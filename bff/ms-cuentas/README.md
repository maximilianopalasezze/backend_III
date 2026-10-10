# MS-CUENTAS — gestión de cuentas de Semana 9

El servicio permite abrir una cuenta con saldo cero, mantener su tipo y cerrarla conservando datos e historial. El saldo se modifica únicamente mediante procesos de negocio; estos contratos no permiten asignarlo manualmente. Tipos admitidos: `ahorro`, `corriente`, `prestamo`, `hipoteca`. La apertura exige un ID positivo, nombre no vacío de hasta 150 caracteres y edad de 18 a 120.

## Base de datos

Ejecutar [../sql/03-gestion-cuentas.sql](../sql/03-gestion-cuentas.sql) en `bank_xyz_semana5_db` antes de iniciar los servicios actualizados. Crea `cuentas_estado` con clave foránea hacia `cuentas` sin borrar datos. Es repetible. Las cuentas anteriores, sin una fila de estado, se consideran `ACTIVA`.

Compose monta esta migración para una base nueva. Un volumen MySQL ya existente no vuelve a ejecutar los scripts de inicialización: aplicar esta misma migración manualmente antes de actualizar sus servicios. No eliminar el volumen para migrar.

## Contratos

| Método | Ruta | Solicitud | Resultado |
| --- | --- | --- | --- |
| POST | `/api/cuentas` | `{"cuentaId":900001,"nombre":"Prueba Semana 9","edad":30,"tipoCuenta":"ahorro"}` | 201, saldo cero, estado ACTIVA |
| GET | `/api/cuentas/900001` | Sin cuerpo | 200, cuenta y estado |
| PUT | `/api/cuentas/900001` | `{"tipoCuenta":"corriente"}` | 200, tipo actualizado, mismo saldo |
| POST | `/api/cuentas/900001/cierre` | Sin cuerpo | 200, estado CERRADA si saldo cero |

ID duplicado, mantenimiento de una cuenta cerrada y cierre con saldo distinto de cero devuelven 409; cuenta inexistente devuelve 404; datos inválidos devuelven 400. Repetir el cierre conserva la fecha original. No existe borrado ni reapertura implícita.

Las rutas internas mantienen la autenticación Basic del servicio y el rol `SERVICE` heredados. Sin credenciales responden 401 y el usuario VIEWER recibe 403. La exposición de gestión en BFF Web con permisos OAuth2 se incorporará en la integración siguiente; estas rutas no deben publicarse como una API administrativa abierta.

## Compilación y prueba local

Desde `bff`:

```powershell
.\mvnw.cmd -B clean verify
```

Con `DB_URL`, `DB_USER` y `DB_PASSWORD` ya definidos, iniciar el servicio en una terminal local:

```powershell
$env:SPRING_DATASOURCE_URL = $env:DB_URL
$env:SPRING_DATASOURCE_USERNAME = $env:DB_USER
$env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD
& "$env:JAVA_HOME\bin\java.exe" -jar .\ms-cuentas\target\ms-cuentas-0.0.1-SNAPSHOT.jar --server.port=8091 --spring.cloud.config.enabled=false --eureka.client.enabled=false
```

Este modo permite probar directamente en `http://localhost:8091`, sin Config Server ni Eureka. Como los parámetros de conexión y puerto están en Config Server, la prueba aislada copia las variables DB a las propiedades de entorno que Spring reconoce directamente e indica el puerto explícitamente. Para la integración final se usan Config Server, Eureka y los BFF con HTTPS/OAuth2. Mantener Batch detenido durante la prueba: su Job de intereses escribe saldos históricos.

En Postman configurar Basic Auth con el usuario y contraseña de servicio definidos por `backend.security.usuario` y `backend.security.password` (o sus valores del entorno utilizado). No incluir contraseñas ni tokens en las capturas. Usar una cuenta nueva de prueba, por ejemplo 900001, en lugar de cerrar la cuenta 102 de los canales OAuth2.

## Consistencia y verificación

Cierre, mantenimiento y retiro bloquean primero la misma fila de `cuentas` con `SELECT ... FOR UPDATE` dentro de una transacción. El estado se consulta después mediante una lectura bloqueante para evitar un estado antiguo bajo REPEATABLE READ. Una operación concurrente termina antes de decidir el cierre; una cuenta ya cerrada rechaza retiros con 409 sin cambiar saldo ni registrar operación. El BFF Web incluye el estado en `producto.estado`.

`GestionCuentasTests` comprueba persistencia, saldo, duplicados, restricciones del cierre, compatibilidad con cuentas previas, errores por inexistencia y bloqueo concurrente. `EstadoCuentaRetiroTests`, en MS-OPERACIONES, usa el repositorio y servicio reales para comprobar retiros de cuentas cerradas, cuentas previas y saldo insuficiente. Las pruebas de seguridad ejercitan además apertura y cierre protegidos y validación de solicitudes. H2 se utiliza como base aislada de pruebas; los resultados MySQL observados se detallan a continuación.

Verificación de desarrollo con Java 17 y Maven: `verify` terminó correctamente para MS-CUENTAS, MS-OPERACIONES, los tres BFF y el módulo compartido. Se aprobaron 27 pruebas, con cero fallas y errores. En PC, el reactor completo también terminó con `BUILD SUCCESS` y todos los módulos en `SUCCESS`. La tabla de estado se creó en MySQL y se probaron los contratos internos con Basic Auth.

## Resultados locales con MySQL y Postman

Observaciones de la ejecución del 10 de octubre de 2026, con Batch detenido:

| Caso | Cuenta | Resultado observado |
| --- | --- | --- |
| Apertura | 900001 | 201, saldo 0.00, tipo ahorro y estado ACTIVA |
| Mantenimiento | 900001 | 200, tipo corriente, saldo 0.00 y estado ACTIVA |
| ID duplicado | 900001 | 409: Ya existe la cuenta 900001 |
| Cierre con saldo cero | 900001 | 200, tipo corriente, saldo 0.00 y estado CERRADA |
| Mantenimiento posterior al cierre | 900001 | 409: No se puede modificar una cuenta cerrada |
| Consulta SQL de cuenta y estado | 900001 | La fila se conserva, tipo corriente, saldo 0.00, estado CERRADA; apertura 2026-10-10 17:44:32 y cierre 2026-10-10 17:57:24 |
| Cierre con saldo positivo | 101 | 409: La cuenta debe tener saldo cero antes del cierre |
| Consulta posterior al cierre rechazado | 101 | 200, Jane Smith, saldo 5025.00, tipo ahorro y estado ACTIVA |

Estas pruebas corresponden al servicio interno en localhost:8091. La cuenta 900001 ya quedó cerrada; para repetir toda la secuencia utilizar otro ID que no exista y conservar los datos de las pruebas anteriores.

## Prueba pendiente de retiro desde una cuenta cerrada

Después de completar las consultas de MS-CUENTAS, detenerlo con Ctrl+C y utilizar la misma terminal en `bff`, conservando las variables DB y JAVA_HOME. Iniciar MS-OPERACIONES importando su archivo local de configuración; así también se configura el nombre del tópico Kafka requerido por el publicador:

```powershell
$env:SPRING_DATASOURCE_URL = $env:DB_URL
$env:SPRING_DATASOURCE_USERNAME = $env:DB_USER
$env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD
& "$env:JAVA_HOME\bin\java.exe" -jar .\ms-operaciones\target\ms-operaciones-0.0.1-SNAPSHOT.jar --server.port=8093 --spring.config.import=optional:file:./config-repo/ms-operaciones.properties --spring.cloud.config.enabled=false --eureka.client.enabled=false
```

Enviar POST a `http://localhost:8093/api/operaciones/cuentas/900001/retiros`, con Basic Auth de servicio y cuerpo `{"monto":1000}`. Se espera 409 con el mensaje `La cuenta está cerrada`. Comprobar después en MySQL que el saldo continúa en cero, el estado sigue CERRADA y no existe una operación para esa cuenta en `operaciones_cajero`.

El rechazo sucede antes de modificar saldos, registrar operaciones y emitir eventos. Las operaciones aprobadas y su publicación/consumo Kafka requieren el broker y forman parte de la validación de integración. La captura de esta prueba MySQL, la gestión mediante BFF Web/OAuth2 y la ejecución de los nuevos contratos en AWS siguen pendientes.
