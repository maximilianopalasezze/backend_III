# Banco XYZ - Desarrollo Backend III - Semana 9

Base de trabajo de la **Evaluación Final Transversal de Semana 9**, continuando la solución de semana 8: OAuth2, tres BFF, Spring Cloud, Resilience4j, Kafka y Docker Compose.

## Estado de la integración

Se creó esta rama desde `semana8-oauth-docker` (commit `dbd941fa3ce2a5bc306e3f984e43941edfd0627f`) y se recuperó Spring Batch de `main` (commit `354050a1ed5380a5f579596788b27c947cf488f9`) en la carpeta independiente [batch](batch/README.md). Se conserva **bank_xyz_semana5_db**.

Los CSV requeridos por la evaluación se incorporaron desde [fin_legacy_data](https://github.com/KariVillagran/fin_legacy_data) en `batch/src/main/resources/data/semana_9`. Los tres Jobs usan estos archivos por defecto y requieren activación explícita mediante `BATCH_JOB_ENABLED=true`.

Esta rama está en desarrollo; aún no es la entrega final. La compilación Maven de Batch y sus 10 pruebas seleccionadas se verificaron en PC. Con los tres CSV oficiales se comprobaron resultados equivalentes entre 1 y 3 hilos, rendimiento con 100.000 transacciones y recuperación automática ante una falla controlada, conservando todos los campos de negocio comparados. El detalle está en [batch/README.md](batch/README.md).

Semana 9 incorpora apertura, mantenimiento del tipo de producto y cierre en MS-CUENTAS. El cierre conserva la cuenta y su historial, exige saldo cero y bloquea nuevos retiros. Aplicar [la migración repetible](bff/sql/03-gestion-cuentas.sql) antes de iniciar esta versión. La gestión se verificó localmente con Postman y MySQL: apertura 201, mantenimiento y cierre 200, duplicado y modificación de cuenta cerrada 409, persistencia de fechas y rechazo de cierre con saldo sin alterar la cuenta. MS-OPERACIONES también rechazó el retiro desde una cuenta cerrada con 409; MySQL conservó saldo, estado y fecha de cierre, con cero operaciones registradas. Los pasos y resultados están en [gestión de cuentas](bff/ms-cuentas/README.md). Queda pendiente la integración de gestión mediante OAuth2 en BFF Web.

Se incorpora [MS-CLIENTES](bff/ms-clientes/README.md), con alta y mantenimiento de datos personales y perfil comercial, consulta por cliente o cuenta, titular único por cuenta y varias cuentas por cliente. Usa transacciones y control de versión para rechazar actualizaciones antiguas. Aplicar [04-clientes.sql](bff/sql/04-clientes.sql) después de la migración 03. Se incluyen configuración centralizada, Dockerfile y servicio Compose; su validación local con MySQL, la exposición mediante los BFF y el despliegue AWS siguen pendientes.

El Job de intereses actualiza saldos del legado desde un archivo histórico. Mantener Batch desactivado durante estas pruebas de operaciones bancarias; la coordinación de actualizaciones Batch y operaciones en línea todavía requiere una validación final.

### Trabajo pendiente según la evaluación

| Criterio | Base disponible | Validación o cambio pendiente |
| --- | --- | --- |
| 5 procesos críticos (10 puntos) | Batch, microservicios, BFF, seguridad distribuida y Kafka | Explicar los cinco procesos en la propuesta y el informe |
| Arquitectura y 3 requisitos de negocio (15 puntos) | Arquitectura de semana 8 | Justificar eficiencia batch, contratos por canal y continuidad/seguridad operativa |
| 3 procesos batch (15 puntos) | Tres Jobs, entradas oficiales, comparación 1/3 hilos, 100.000 filas y recuperación automática verificados en MySQL | Comparación formal con resultados legacy y evidencia de reintentos transitorios de base de datos |
| 3 BFF (15 puntos) | Web, móvil y cajero | Revalidar respuestas y seguridad con la solución final |
| 3 servicios clave (15 puntos) | Gestión de cuentas validada localmente con MySQL, MS-CLIENTES implementado, movimientos y retiros heredados | Validar clientes en MySQL; implementar pagos/transferencias/depósitos; integrar nuevos contratos en BFF Web con OAuth2, resiliencia y mensajería |
| Docker y escala horizontal en AWS (10 puntos) | Compose y despliegue previo | Incorporar componentes nuevos y demostrar varias instancias de los tres servicios exigidos y balanceo |
| Documentación (10 puntos) | README y diagrama de semana 8 | Preparar `readme.md`, PDF con plantilla oficial, `instrucciones.md`, `despliegue.md` y MP4 de 5–7 minutos con webcam |

Las métricas de Actuator para estados OPEN y HALF_OPEN se consideran una mejora útil para demostrar resiliencia. Authorization Code con PKCE es una propuesta futura; la retroalimentación valida el flujo `client_credentials` utilizado en semana 8.

## Referencia de la solución de Semana 8

Las siguientes secciones describen los componentes y la ejecución heredados. El diagrama y las etiquetas de imágenes de semana 8 se conservan como referencia hasta actualizar la implementación final.

## Arquitectura general

```text
                            POSTMAN / CLIENTES
                                   |
                                   | OAuth2 client_credentials
                                   v
                         AUTH SERVER :9000
                         Emisión de JWT Bearer
                                   |
              +--------------------+--------------------+
              |                    |                    |
              v                    v                    v
        BFF WEB :8081       BFF MÓVIL :8082      BFF CAJERO :8083
        HTTPS + OAuth2      HTTPS + OAuth2        HTTPS + OAuth2
              |                    |                    |
              +--------------------+--------------------+
                                   |
                         Eureka + Config Server
                         :8761        :8888
                                   |
              +--------------------+--------------------+
              |                    |                    |
              v                    v                    v
       MS-CUENTAS :8091   MS-MOVIMIENTOS :8092  MS-OPERACIONES :8093
              |                    |                    |
              +--------------------+--------------------+
                                   |
                                MySQL :3306

                         MS-OPERACIONES
                               |
                               v
                        Apache Kafka :9092
                               |
             +-----------------+-----------------+
             |                 |                 |
             v                 v                 v
   movimientos.creados  operaciones.procesadas  operaciones.fallidas
                                              (DLQ)

                         Kafka UI :8090
```

Toda la solución se ejecuta dentro de una instancia AWS EC2 mediante **Docker Compose**.

### Diagrama visual

El diagrama de arquitectura de la entrega se encuentra en:

[Ver diagrama de arquitectura Semana 8](docs/semana8/arquitectura-semana8.svg)

## Componentes principales

| Componente | Responsabilidad |
| --- | --- |
| `auth-server` | Authorization Server OAuth2, puerto 9000 |
| `config-server` | Configuración centralizada, puerto 8888 |
| `discovery-server` | Service Discovery con Eureka, puerto 8761 |
| `ms-cuentas` | Información de cuentas y resumen bancario, puerto 8091 |
| `ms-movimientos` | Consulta de movimientos y consumo de eventos Kafka, puerto 8092 |
| `ms-operaciones` | Retiros y publicación de eventos, puerto 8093 |
| `bff-web` | Contrato Web, HTTPS 8081 |
| `bff-movil` | Contrato Móvil, HTTPS 8082 |
| `bff-cajero` | Saldo y retiros de Cajero, HTTPS 8083 |
| `mysql` | Persistencia de datos |
| `kafka` | Mensajería asíncrona |
| `kafka-ui` | Inspección visual de topics y consumidores |

## OAuth2 y seguridad

Se implementó un Authorization Server real con Spring Authorization Server.

Flujo utilizado:

```text
client_credentials
```

Clientes registrados:

- `bff-web`
- `bff-movil`
- `bff-cajero`

Scopes configurados:

```text
web:lectura
web:resumen
movil:lectura
cajero:lectura
cajero:retiro
```

Los access tokens son JWT firmados con RSA y contienen información adicional de seguridad:

- `aud`
- `canal`
- `cuentaId`
- `scope`
- `iss`

Los BFF funcionan como OAuth2 Resource Server y validan el token mediante el JWK Set del Authorization Server.

Comportamientos validados:

- Sin token o token inválido: **401 Unauthorized**.
- Token válido sin scope requerido: **403 Forbidden**.
- Token y scope correctos: **200 OK**.

Los tres BFF mantienen HTTPS con certificados PKCS12 generados localmente.

## Contratos por canal

### Web

El BFF Web entrega información ampliada de la cuenta, resumen e historial de movimientos.

### Móvil

El BFF Móvil entrega una respuesta reducida y limita la cantidad de movimientos consultados.

### Cajero

El BFF Cajero permite consultar saldo y realizar retiros. La cuenta se entrega enmascarada y el monto de retiro debe ser múltiplo de 1000 CLP.

## Spring Cloud Config y Eureka

El Config Server centraliza parámetros de los servicios y BFF.

Eureka permite el descubrimiento dinámico de:

- AUTH-SERVER
- BANK-BFF-WEB
- BANK-BFF-MOVIL
- BANK-BFF-CAJERO
- MS-CUENTAS
- MS-MOVIMIENTOS
- MS-OPERACIONES

La comunicación BFF -> Backend utiliza nombres lógicos mediante `RestTemplate @LoadBalanced`, evitando dependencias directas de IP o puerto.

## Resilience4j

Las llamadas entre BFF y microservicios están protegidas con:

- Circuit Breaker.
- Retry.
- Fallbacks controlados.

Servicios configurados:

- `cuentasService`
- `movimientosService`
- `operacionesService`

Ejemplos de comportamiento:

- Si `MS-MOVIMIENTOS` falla, el BFF puede degradar la respuesta a una lista vacía.
- Si `MS-CUENTAS` no está disponible, se devuelve un error controlado 503.
- Las excepciones de negocio se excluyen del conteo de fallos de infraestructura.

## Kafka y procesamiento asíncrono

Apache Kafka se ejecuta en el puerto 9092.

Topics utilizados:

```text
movimientos.creados
operaciones.procesadas
operaciones.fallidas
```

Flujo principal:

```text
BFF CAJERO
   |
   v
MS-OPERACIONES
   |
   v
operaciones.procesadas
   |
   v
MS-MOVIMIENTOS
```

`MS-OPERACIONES` publica eventos de retiro procesado.

`MS-MOVIMIENTOS` consume los eventos mediante el grupo:

```text
ms-movimientos-group
```

Para demostrar tolerancia a fallos se configuraron reintentos automáticos y Dead Letter Queue:

- 2 reintentos.
- Espera de 2 segundos entre intentos.
- Evento no procesable enviado a `operaciones.fallidas`.

Kafka UI permite visualizar topics, mensajes, consumer groups y lag.

## Docker y Docker Compose

Cada aplicación Java posee su propio Dockerfile.

Imágenes construidas:

- `banco-xyz/config-server:semana8`
- `banco-xyz/discovery-server:semana8`
- `banco-xyz/auth-server:semana8`
- `banco-xyz/ms-cuentas:semana8`
- `banco-xyz/ms-movimientos:semana8`
- `banco-xyz/ms-operaciones:semana8`
- `banco-xyz/bff-web:semana8`
- `banco-xyz/bff-movil:semana8`
- `banco-xyz/bff-cajero:semana8`

El archivo principal de orquestación es:

```text
infra/docker-compose.yml
```

Incluye:

- Health checks.
- Dependencias condicionadas por estado saludable.
- Variables de entorno.
- Volúmenes persistentes.
- Inicialización automática de topics Kafka.
- Persistencia MySQL.
- Montaje de certificados TLS.
- Arranque coordinado de todos los servicios.

## Variables de entorno

El repositorio no contiene credenciales reales.

Usar como plantilla:

```text
env.docker.example
```

y crear localmente:

```text
.env.docker.local
```

Variables requeridas:

```text
MYSQL_ROOT_PASSWORD
DB_PASSWORD
BACKEND_BASIC_PASSWORD
BFF_TLS_PASSWORD
OAUTH_WEB_SECRET
OAUTH_MOVIL_SECRET
OAUTH_CAJERO_SECRET
```

Los archivos sensibles, certificados y secretos locales están excluidos mediante `.gitignore`.

## Certificados TLS

Los certificados de los BFF no se versionan.

Para generarlos:

```bash
./infra/generar-tls.sh
```

Se generan:

```text
bff/tls/web.p12
bff/tls/movil.p12
bff/tls/cajero.p12
```

## Base de datos

La base utilizada es:

```text
bank_xyz_semana5_db
```

El dump reproducible se encuentra en:

```text
infra/bank_xyz_semana5_db.sql
```

Los BFF no acceden directamente a MySQL. El acceso JDBC queda encapsulado en los microservicios Backend.

## Compilación

Requisito:

```text
Java 17
```

Desde la carpeta `bff`:

```bash
./mvnw clean package -DskipTests
```

La compilación debe finalizar con:

```text
BUILD SUCCESS
```

## Ejecución con Docker Compose

Desde `infra`:

```bash
docker compose --env-file ../.env.docker.local up -d
```

Verificación:

```bash
docker compose --env-file ../.env.docker.local ps -a
```

Los servicios principales deben quedar en estado `healthy`.

`kafka-init` debe finalizar correctamente con:

```text
Exited (0)
```

## Pruebas principales

### Obtener token OAuth2

```text
POST http://localhost:9000/oauth2/token
```

Ejemplo Web:

```text
grant_type=client_credentials
scope=web:lectura web:resumen
```

### Consulta protegida Web

```text
GET https://localhost:8081/api/bff/web/cuentas/102
```

Resultado esperado:

```text
200 OK
```

### Retiro Cajero

```text
POST https://localhost:8083/api/bff/cajero/cuentas/102/retiros
```

Body:

```json
{
  "monto": 1000
}
```

Resultado esperado:

```text
200 OK
estado: APROBADA
```

Luego el evento puede verificarse en:

```text
operaciones.procesadas
```

## Evidencias de la entrega

Las pruebas realizadas demuestran:

- Compilación Maven exitosa.
- Authorization Server OAuth2 operativo.
- Respuestas 200, 401 y 403.
- HTTPS en los tres BFF.
- Servicios registrados en Eureka.
- Configuración centralizada.
- Comunicación por Service Discovery.
- Circuit Breaker, Retry y fallback.
- Dockerización de todos los módulos.
- Orquestación mediante Docker Compose.
- MySQL persistente.
- Kafka operativo.
- Publicación y consumo de eventos.
- Consumer group con lag controlado.
- Reintentos y DLQ.
- Retiro real Cajero -> Kafka.
- Rama final publicada en GitHub.

## Rama de trabajo actual

```text
semana9-evaluacion-final
```

La base validada de Semana 8 permanece en `semana8-oauth-docker`. Esta rama integra el trabajo de Semana 9 y sus pendientes se describen al inicio del documento.
