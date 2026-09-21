# Banco XYZ - Desarrollo Backend III - Semana 6

Implementación de la actividad **"Implementando microservicios y seguridad en la nube con Spring Cloud"**. La solución continúa el trabajo de Semana 5 y corrige la observación docente principal: los BFF ya **no acceden directamente a MySQL**. Ahora consumen servicios Backend reales mediante HTTP, nombres lógicos de Eureka y un `RestTemplate` balanceado.

## Arquitectura

```text
                         CONFIG SERVER :8888
                         bff/config-repo
                                |
                                v
                         EUREKA SERVER :8761
                                |
        +-----------------------+-----------------------+
        |                       |                       |
  MS-CUENTAS :8091      MS-MOVIMIENTOS :8092    MS-OPERACIONES :8093
        |                       |                       |
        +-----------------------+-----------------------+
                                ^
                 HTTP + @LoadBalanced RestTemplate
                       + Resilience4j
                                |
          +---------------------+---------------------+
          |                     |                     |
    BFF WEB :8081        BFF MOVIL :8082       BFF CAJERO :8083
       HTTPS/JWT             HTTPS/JWT              HTTPS/JWT
```

### Componentes

| Módulo | Responsabilidad |
| --- | --- |
| `config-server` | Configuración centralizada con Spring Cloud Config, puerto 8888 |
| `config-repo` | Configuración externa de microservicios y BFF |
| `discovery-server` | Service Discovery con Netflix Eureka, puerto 8761 |
| `ms-cuentas` | Cuenta, interés, estado anual y resumen operacional, puerto 8091 |
| `ms-movimientos` | Movimientos bancarios, puerto 8092 |
| `ms-operaciones` | Retiros transaccionales y auditoría, puerto 8093 |
| `bff-web` | Contrato completo Web, HTTPS 8081 |
| `bff-movil` | Contrato reducido Móvil, HTTPS 8082 |
| `bff-cajero` | Saldo y retiro Cajero, HTTPS 8083 |
| `bff-compartido` | JWT, DTO comunes, cliente HTTP balanceado y Resilience4j |

## Corrección aplicada respecto a Semana 5

Antes:

```text
BFF -> JdbcTemplate -> MySQL
```

Semana 6:

```text
BFF -> RestTemplate @LoadBalanced -> Eureka -> Microservicio Backend -> JdbcTemplate -> MySQL
```

El acceso JDBC queda dentro de `ms-cuentas`, `ms-movimientos` y `ms-operaciones`. Los BFF no contienen datasource ni driver MySQL.

## Spring Cloud Config

`config-server` usa perfil `native` y lee `bff/config-repo`. Las aplicaciones cliente conservan localmente solo su nombre y el import del Config Server:

```properties
spring.application.name=ms-cuentas
spring.config.import=optional:configserver:http://localhost:8888
```

La configuración central contiene puertos, Eureka, datasource, seguridad interna, SSL/JWT de los BFF y parámetros de Resilience4j.

Prueba:

```text
http://localhost:8888/ms-cuentas/default
```

## Service Discovery

Eureka se ejecuta en:

```text
http://localhost:8761
```

Los tres servicios Backend y los tres BFF se registran automáticamente. La comunicación no usa URLs fijas con puertos; el cliente compartido llama por nombre:

```java
http://MS-CUENTAS/...
http://MS-MOVIMIENTOS/...
http://MS-OPERACIONES/...
```

## Tolerancia a fallos

`ClienteServiciosBanco` protege las llamadas salientes con Resilience4j:

- `cuentasService`: Circuit Breaker + Retry.
- `movimientosService`: Circuit Breaker + Retry + fallback a lista vacía.
- `operacionesService`: Circuit Breaker + Retry + error controlado 503 si el servicio no está disponible.

La configuración del circuito está centralizada en `config-repo/application.properties`.

Prueba visual recomendada: detener `ms-movimientos` y consultar nuevamente la cuenta Web. El BFF debe mantener respuesta `200` y entregar `movimientosRecientes: []`, demostrando degradación controlada en vez de fallo en cascada.

## Autenticación y autorización

### Entre BFF y microservicios Backend

Los tres microservicios usan **Spring Security + HTTP Basic + roles**, alineado con el ejemplo de Semana 6:

- `ROLE_SERVICE`: puede acceder a `/api/**`.
- `ROLE_VIEWER`: usuario válido, pero no autorizado para `/api/**`; permite demostrar `403`.
- Sin credenciales: `401`.

Las credenciales fuente se generan localmente mediante
`scripts/preparar-semana6.ps1` y se almacenan protegidas mediante
Windows en `.local`. El mismo script genera un entorno local de Postman
con las variables necesarias para las pruebas. Toda la carpeta `.local`
está excluida de Git y no forma parte de la entrega.

### Cliente -> BFF

Se conserva la seguridad funcional de Semana 5: HTTPS + JWT, scopes por canal y autorización por cuenta.

## Base de datos

Se reutiliza la base preparada en las semanas anteriores:

```text
bank_xyz_semana5_db
```

Los BFF no se conectan a ella. Solo los microservicios Backend usan JDBC.

## Compilar

Requisito: Java 17.

Desde la raíz `backend_III`:

```powershell
.\bff\mvnw.cmd -f .\bff\pom.xml clean package
```

El wrapper está fijado a Maven 3.9.11. Antes de continuar debe aparecer `BUILD SUCCESS`.

## Preparación local

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preparar-semana6.ps1
```

El script conserva/genera los certificados y secretos BFF, genera las credenciales HTTP Basic internas y crea:

```text
.local\Semana6_Cloud_Local.postman_environment.json
```

## Iniciar toda la Semana 6

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar-semana6.ps1
```

Se solicita una vez la contraseña MySQL y luego se abren ventanas separadas para:

1. Config Server.
2. Eureka.
3. MS Cuentas.
4. MS Movimientos.
5. MS Operaciones.
6. BFF Web.
7. BFF Móvil.
8. BFF Cajero.

## Postman

Importar:

```text
bff/postman/Banco_XYZ_Semana6_Cloud.postman_collection.json
.local/Semana6_Cloud_Local.postman_environment.json
```

Para HTTPS de los BFF conservar la configuración de certificados usada en Semana 5.

La colección contiene evidencias para Config Server, Eureka, `401`, `403`, `200`, BFF y tolerancia a fallos.

## Evidencias para la entrega

Revisar `GUIA_CAPTURAS_SEMANA6.md`. Las evidencias principales son:

- `BUILD SUCCESS`.
- Config Server respondiendo configuración externa.
- Eureka con al menos `MS-CUENTAS`, `MS-MOVIMIENTOS` y `MS-OPERACIONES` en `UP`.
- `401` sin autenticación.
- `403` con usuario `VIEWER`.
- `200` con usuario `SERVICE`.
- BFF consumiendo servicios Backend por Eureka.
- Resilience4j funcionando con `ms-movimientos` detenido.
- Tres BFF funcionando con sus contratos diferenciados.

## Entrega

Código, README, Postman y evidencias deben quedar en una misma carpeta antes de comprimir. No incluir `.local`, contraseñas, certificados privados, `target` ni logs.
