# Banco XYZ — Arquitectura, casos de uso y componentes

## Arquitectura

Cada BFF valida OAuth2, autoriza canal/cuenta y usa llamadas HTTP balanceadas con resiliencia. El balanceador está dentro del cliente del BFF; no hay un servicio Gateway adicional. Config Server y Eureka suministran configuración y descubrimiento.

```mermaid
flowchart TD
    canal["Web, Móvil y Cajero"] -->|HTTPS y Bearer| bff["Tres BFF: autorización y resiliencia"]
    auth["Authorization Server"] -->|Token firmado| canal
    auth -->|Firma e issuer| bff
    config["Config Server"] -->|Configuración| bff
    eureka["Eureka"] -->|Descubrimiento| bff
    bff --> cuentas["MS-CUENTAS: dos réplicas"]
    bff --> clientes["MS-CLIENTES: dos réplicas"]
    bff --> pagos["MS-PAGOS: dos réplicas"]
    bff --> legado["MS-OPERACIONES y MS-MOVIMIENTOS"]
    cuentas --> db["MySQL"]
    clientes --> db
    pagos --> db
    legado --> db
    pagos -->|Outbox| kafka["Kafka"]
    legado -->|Publicación y consumo| kafka
```

Batch se ejecutó en PC con MySQL; no integra el arranque Compose ni se ejecutó sobre EC2 durante las pruebas bancarias.

```mermaid
flowchart TD
    csv["Tres CSV oficiales"] --> jobs["Jobs: diario, intereses y estados"]
    jobs --> step["Lectura, validación y escritura por chunks"]
    step --> db["Resultados, rechazos y reportes"]
    step --> checkpoint["Metadatos y checkpoints"]
    checkpoint -->|Reinicio de la misma instancia| jobs
    step -->|Fallo JDBC recuperable| retry["Retry limitado"]
    retry --> step
```

## Casos de uso

```mermaid
flowchart TD
    web["Canal Web"] --> gestion["Abrir, mantener y cerrar cuenta"]
    web --> perfil["Crear, consultar y actualizar cliente"]
    web --> pagos["Depositar, pagar y transferir"]
    web --> consulta["Consultar cuenta y resumen"]
    movil["Canal Móvil"] --> pagos
    movil --> ligera["Consultar cuenta y movimientos reducidos"]
    cajero["Canal Cajero"] --> saldo["Consultar saldo"]
    cajero --> retiro["Retirar monto válido"]
```

| Caso | Regla |
| --- | --- |
| Gestión de cuentas | Scope Web y cuenta autorizada; cierre con saldo cero conserva historial |
| Gestión de cliente | Se resuelve el titular desde la cuenta autorizada; actualización controla versión |
| Pagos y transferencias | Monto válido, cuentas activas y clave estable; cambio de saldos atómico |
| Repetición | Misma clave/cuerpo devuelve recibo; clave con cuerpo distinto produce 409 |
| Móvil | Datos reducidos y scopes propios |
| Retiro cajero | Mínimo/múltiplo de 1000; sin Retry automático del POST legado |

## Componentes de pagos y mensajería

```mermaid
flowchart TD
    controlador["Controlador de pagos"] --> servicio["Servicio transaccional"]
    servicio --> cuentas["Cuentas: bloqueos ordenados"]
    servicio --> recibo["Recibos: solicitudId único"]
    servicio --> outbox["Outbox: evento pendiente"]
    outbox --> publicador["Publicador: bloqueo de fila"]
    publicador -->|Envío confirmado| kafka["pagos.procesados"]
    kafka --> consumer["Consumer de MS-MOVIMIENTOS"]
    consumer --> sql["Proyección: referencia y cuenta únicas"]
    consumer -->|Reintentos agotados| dlq["operaciones.fallidas"]
```

Saldos, recibo y outbox se confirman en una transacción. La publicación puede repetirse si se pierde la confirmación; la proyección deduplica por referencia/cuenta y no modifica nuevamente los saldos. Una transferencia genera salida y entrada. El consumidor legado de retiros registra su recepción en logs; no se le atribuyen filas de la proyección SQL de pagos.

Las consultas fallidas producen fallback 503 y recuperan acceso cuando vuelve el servicio. Los errores de negocio 400/404/409 quedan excluidos de Retry y del conteo del circuito. EC2 demostró fallback y recuperación, sin observar OPEN/HALF_OPEN mediante Actuator. MySQL, Kafka y autorización tienen una instancia en este laboratorio.
