# Contratos de los BFF — Semana 9

Los BFF usan HTTPS, OAuth2 Resource Server y nombres lógicos de Eureka para invocar MS-CUENTAS, MS-CLIENTES y MS-PAGOS. No acceden a MySQL. Los contratos de consulta ampliada de Web, consulta reducida de móvil y saldo/retiro de cajero se conservan.

## Permisos y cuentas

El token debe tener firma, issuer, audience y canal válidos, además del scope de la ruta. Las rutas de cuenta comprueban que el ID esté autorizado en el token. Sin token se responde 401; con scope insuficiente o una cuenta ajena, 403. HTTP se rechaza incluso si el cliente envía `X-Forwarded-Proto: https`.

El Authorization Server asocia cada cliente de demostración con una cuenta principal mediante `OAUTH_WEB_CUENTA_ID`, `OAUTH_MOVIL_CUENTA_ID` y `OAUTH_CAJERO_CUENTA_ID`; el valor predeterminado sigue siendo 102. Las listas opcionales `OAUTH_WEB_CUENTAS`, `OAUTH_MOVIL_CUENTAS` y `OAUTH_CAJERO_CUENTAS` admiten IDs adicionales separados por comas. Se validan al iniciar y se incluyen en la claim firmada `cuentas`. La cuenta principal siempre forma parte de la lista. El cliente no puede elegir estas claims al pedir un token.

Esta asociación es una configuración del cliente OAuth2 del laboratorio con `client_credentials`. En una aplicación con usuarios finales, las cuentas se asociarían a la identidad autenticada y se obtendrían desde datos de autorización del servidor.

| Canal | Scopes disponibles |
| --- | --- |
| Web | `web:lectura`, `web:resumen`, `web:gestion`, `web:pagos` |
| Móvil | `movil:lectura`, `movil:pagos` |
| Cajero | `cajero:lectura`, `cajero:retiro` |

Solicitar solo lectura produce un token sin permisos de escritura. Los clientes móvil y cajero no pueden solicitar scopes Web.

## Gestión Web

Base: `https://localhost:8081/api/bff/web`.

| Método | Ruta relativa | Scope | Comportamiento |
| --- | --- | --- | --- |
| POST | `/cuentas` | `web:gestion` | Apertura 201; cuentaId del JSON debe estar autorizado |
| PUT | `/cuentas/{cuentaId}` | `web:gestion` | Mantiene tipo de producto; no acepta escritura de saldo |
| POST | `/cuentas/{cuentaId}/cierre` | `web:gestion` | Cierre lógico; MS-CUENTAS exige saldo cero |
| GET | `/cuentas/{cuentaId}/cliente` | `web:lectura` | Consulta del perfil vinculado a esa cuenta |
| POST | `/cuentas/{cuentaId}/cliente` | `web:gestion` | Alta 201; la cuenta se toma de la ruta |
| PUT | `/cuentas/{cuentaId}/cliente` | `web:gestion` | Actualiza el perfil con control de versión |
| POST | `/cuentas/{cuentaId}/cliente/cuentas` | `web:gestion` | Vincula una cuenta adicional; ambas deben estar autorizadas |

La apertura recibe `cuentaId`, `nombre`, `edad` y `tipoCuenta`. El mantenimiento recibe solo `tipoCuenta`. El alta de cliente recibe `clienteId`, `nombre`, `email`, `telefono`, `direccion` y `perfil`; no necesita un campo cuentaId porque lo obtiene de la ruta. La actualización recibe esos datos personales y `version`, sin clienteId. El BFF resuelve el cliente a partir de la cuenta autorizada, evitando que una petición elija un cliente ajeno. El vínculo recibe `cuentaId`.

## Operaciones Web y móvil

Bases: `https://localhost:8081/api/bff/web` y `https://localhost:8082/api/bff/movil`.

| Método | Ruta relativa | Scope |
| --- | --- | --- |
| POST | `/cuentas/{cuentaId}/depositos` | `web:pagos` o `movil:pagos` |
| POST | `/cuentas/{cuentaId}/pagos` | `web:pagos` o `movil:pagos` |
| POST | `/cuentas/{cuentaId}/transferencias` | `web:pagos` o `movil:pagos` |
| GET | `/cuentas/{cuentaId}/solicitudes/{solicitudId}` | `web:lectura` o `movil:lectura` |

Los tres POST reciben los contratos de [MS-PAGOS](ms-pagos/README.md), incluyendo una `solicitudId` estable. Devuelven 201 y un comprobante con referencia, tipo, monto, concepto, beneficiario, cuenta origen/destino, saldo posterior del origen, estado y fecha. El BFF omite los saldos anterior y posterior del destinatario.

Reenviar la misma clave con los mismos datos devuelve el recibo original. Cambiar los datos usando esa clave produce 409. Una transferencia exige autorización del origen; el destinatario puede ser otra cuenta activa del banco.

Cajero conserva exclusivamente sus consultas de saldo y retiros. No expone gestión de clientes, depósitos, pagos ni transferencias.

## Resiliencia

Las nuevas llamadas usan Circuit Breaker y fallback con los nombres `cuentasService`, `clientesService` y `pagosService`. Las consultas, el cierre idempotente y las operaciones de MS-PAGOS tienen Retry. Cada reintento de pago conserva el mismo objeto de solicitud y la misma clave; MS-PAGOS evita repetir su efecto bancario.

Los errores 400, 404 y 409 conservan su estado y mensaje, y están excluidos de Retry y del conteo de fallos del circuito. Los fallos de infraestructura producen un 503 controlado con el nombre del servicio. El cliente HTTP tiene límites de conexión de 2 segundos y lectura de 5 segundos.

Las altas y las actualizaciones con versión no se reintentan automáticamente. El contrato legado de retiro tampoco tiene clave de solicitud; conserva Circuit Breaker y fallback, pero se retiró su Retry automático para evitar duplicar un cargo tras perder una respuesta.

## Compilación y validación

Desde `bff`, con Java 17:

```powershell
.\mvnw.cmd -B verify
```

Maven `verify` terminó con BUILD SUCCESS en los módulos bff-compartido, auth-server, bff-web, bff-movil y bff-cajero: 50 pruebas, cero fallas, errores u omisiones. Las pruebas comprueban scopes, propiedad de cuentas, HTTPS, validación de JSON/montos, propagación de 409 y fallback 503, emisión OAuth2 con firma real, claims de canal/audience/issuer y caducidad. La integración HTTP simulada comprueba un reintento real de Resilience4j con el mismo cuerpo, apertura del circuito ante fallos, exclusión de conflictos y selección del cliente desde la cuenta. El BOM de Resilience4j fija todos sus componentes en 2.3.0 para evitar versiones incompatibles durante la ejecución.

También se ejecutaron los JAR empaquetados del Authorization Server y los tres BFF. Con un certificado de prueba y verificación TLS activa, se comprobaron transferencia Web 201, pago móvil 201, saldo cajero 200, rechazo 401 sin token y 403 por scope insuficiente o cuenta ajena. Los tokens fueron emitidos y firmados por el servidor real; los servicios HTTP del backend estaban simulados. Se verificaron la conservación de solicitudId, la omisión de saldos del destinatario y las cabeceras de instancia seleccionada. Esta ejecución no realizó operaciones en una base de datos.

La prueba real con los tres BFF, MySQL, Eureka, Kafka y réplicas en AWS sigue pendiente. Las pruebas automatizadas no sustituyen esa evidencia.

## Preparación de Docker y réplicas

Compose etiqueta las aplicaciones `banco-xyz/*` como `semana9`. MS-CUENTAS, MS-CLIENTES y MS-PAGOS no fijan nombre de contenedor ni publican un puerto de host; se registran con identidad distinta y dirección IP de cada réplica. Los BFF invocan sus nombres lógicos con `RestTemplate @LoadBalanced`.

Antes de actualizar una base existente, aplicar las migraciones 03, 04 y 05. Construir los JAR y disponer de ellos en los contextos Docker de EC2. Desde `infra`:

```bash
docker compose --env-file ../.env.docker.local up -d --build --scale ms-cuentas=2 --scale ms-clientes=2 --scale ms-pagos=2
docker compose --env-file ../.env.docker.local ps
```

Ese comando prepara dos instancias de cada servicio clave. La evidencia debe mostrar contenedores saludables, registros distintos en Eureka y tráfico efectivo desde los BFF. La disponibilidad de MySQL, Kafka y el Authorization Server sigue dependiendo de sus instancias únicas en este laboratorio.

Para observar el destino elegido por el balanceador, los BFF añaden las cabeceras `X-Instancia-Cuentas`, `X-Instancia-Clientes` y `X-Instancia-Pagos` cuando llaman al servicio correspondiente. El log registra `Balanceo <servicio> -> <instancia> [<resultado>]`. No se añaden IDs de infraestructura al JSON bancario ni se registran tokens/cuerpos. Varias consultas exitosas deben mostrar IDs distintos; el registro de Eureka por sí solo no demuestra tráfico balanceado.
