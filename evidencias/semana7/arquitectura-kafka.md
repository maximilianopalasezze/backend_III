# Diagrama Kafka - Semana 7

```text
BFF Cajero
    |
    | HTTPS + JWT
    v
ms-operaciones
    |
    +----> MySQL
    |       |
    |       +--> COMMIT
    |
    +----> Evento AFTER_COMMIT
             |
             v
     operaciones.procesadas
          [3 particiones]
             |
             v
       ms-movimientos
             |
       consumer group
    ms-movimientos-group
             |
       +-----+------+
       |            |
       | OK         | ERROR
       |            |
       v            v
  procesado     reintento 1
                    |
                    v
               reintento 2
                    |
                    v
          operaciones.fallidas
                (DLQ)
