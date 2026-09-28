# Backend III - Semana 7 - Kafka en AWS

## Arquitectura

La solución utiliza Amazon Linux 2023 en AWS Academy Learner Lab.

Componentes principales:

- Config Server
- Eureka Discovery Server
- ms-cuentas
- ms-movimientos
- ms-operaciones
- BFF Web
- BFF Móvil
- BFF Cajero
- MySQL 8
- Apache Kafka 3.9
- Kafbat UI

## Flujo asíncrono

1. BFF Cajero solicita una operación de retiro.
2. ms-operaciones procesa la operación y confirma la transacción en MySQL.
3. Después del COMMIT se publica un evento en Kafka.
4. El tópico principal es `operaciones.procesadas`.
5. ms-movimientos consume el evento mediante `ms-movimientos-group`.
6. Si el procesamiento falla, se realizan reintentos.
7. Después de agotar los reintentos, el evento se publica en `operaciones.fallidas`.

## Tópicos Kafka

- `movimientos.creados`
- `operaciones.procesadas`
- `operaciones.fallidas`

Cada tópico fue configurado con 3 particiones para demostrar escalabilidad mediante particionamiento y grupos de consumidores.

## Tolerancia a fallos

Se configuró `DefaultErrorHandler` con:

- 2 reintentos
- espera de 2 segundos
- Dead Letter Topic: `operaciones.fallidas`

La prueba controlada `TEST-DLQ-003` demostró:

- recepción del evento
- reintento 1
- reintento 2
- envío final a `operaciones.fallidas`

## Escalabilidad

El tópico `operaciones.procesadas` utiliza 3 particiones.

El grupo:

`ms-movimientos-group`

consume las particiones mediante Kafka Consumer Groups, permitiendo escalar horizontalmente agregando más instancias consumidoras.

## Kafbat UI

La interfaz se publica en el puerto 8090 de la instancia AWS y permite visualizar:

- broker Kafka
- topics
- particiones
- consumer groups
- consumer lag
- estado de réplicas

## Evidencias principales

- Kafka ejecutándose en Docker.
- Creación y descripción de tópicos.
- Productor y consumidor por consola.
- Retiro real publicado después del COMMIT.
- Consumo asíncrono por ms-movimientos.
- Reintentos y DLQ.
- Consumer group `ms-movimientos-group`.
- Visualización de tópicos y particiones en Kafbat UI.
