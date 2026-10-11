# Banco XYZ — Video de 5–7 minutos

Objetivo: **6:20 minutos**, pantalla y webcam visible. Entregar MP4 junto con PDF y los tres Markdown. Los tiempos incluyen explicación y pausas para señalar resultados.

Antes de grabar abrir el README, diagrama, Eureka y capturas 94–100. Presentar pruebas guardadas: no repetir retiros ni detener servicios. Ocultar pestañas de tokens/credenciales.

## 0:00–0:40 — Objetivo

Mostrar README y arquitectura.

«Hola, soy Maximiliano. Presento la evaluación final de Backend III para Banco XYZ. El objetivo fue integrar los procesos del legado con una arquitectura de servicios que atiende Web, Móvil y Cajero, con seguridad, mensajería, resiliencia y despliegue AWS. Implementé tres Jobs para transacciones diarias, intereses mensuales y estados anuales, junto con servicios de cuentas, clientes y pagos. Mostraré los resultados comprobados y los límites del laboratorio.»

## 0:40–1:25 — Arquitectura

Mostrar diagrama, Compose y Eureka; señalar IDs distintos de captura 94.

«Cada canal tiene un BFF propio. Los BFF validan OAuth2 y reciben peticiones HTTPS. Config Server centraliza la configuración y Eureka descubre los servicios. Las llamadas usan el balanceador de Spring Cloud y Resilience4j. MySQL conserva saldos y operaciones; Kafka distribuye los eventos. Compose coordina el arranque y healthchecks. En EC2 dejé dos réplicas de cuentas, clientes y pagos. Las consultas mostraron dos IDs distintos por servicio, demostrando balanceo con tráfico efectivo.»

## 1:25–2:30 — Batch y comparación

Mostrar tabla Batch y captura 100; señalar totales y BUILD SUCCESS.

«Usé tres CSV oficiales de mil registros. El Job diario escribió 480 transacciones, rechazó 520 e identificó 88 anomalías. Intereses produjo 50 cálculos; estados consolidó 684 movimientos en 20 estados. Comparé uno y tres hilos y también las ejecuciones recuperadas: los campos de negocio revisados quedaron iguales. Con cien mil transacciones, el tiempo pasó de aproximadamente 272 a 163 segundos: reducción del 40,01 por ciento en ese ensayo, no un promedio. La referencia fueron datos oficiales y reglas implementadas; no ejecuté el COBOL original. El cierre Maven aprobó 14 pruebas y empaquetó el JAR.»

Señalar el período mensual configurado aunque el archivo oficial conserva nombre trimestral. Dar tiempo para leer la tabla.

## 2:30–3:20 — Canales y autorización

Mostrar capturas 92/95/96, sin tokens completos.

«Web gestiona cuentas y clientes y permite depósitos, pagos y transferencias. Móvil expone consultas reducidas y pagos. Cajero conserva saldo y retiros. En AWS confirmé la actualización del cliente y sus dos cuentas, un pago móvil de 500 y retiro de 1000. TLS estuvo verificado. Sin token obtuve 401; con scope insuficiente o cuenta ajena, 403. Móvil rechazó el token Web por canal y audiencia. Los saldos finales fueron 5500 y 2000.»

## 3:20–4:15 — Integridad y Kafka

Mostrar capturas 96/97A/97B; señalar referencia, cantidades y offsets.

«MS-PAGOS recibe una clave estable para evitar cargos duplicados. Repetir la transferencia devuelve el mismo comprobante; cambiar el monto con esa clave produce 409. Los saldos no cambiaron. Saldos, recibo y evento pendiente se confirman en una transacción. El outbox se publica en Kafka y el consumidor evita duplicar movimientos. MySQL mostró cuatro operaciones, cuatro eventos y cinco movimientos porque una transferencia genera salida y entrada. Kafka mostró grupos estables y ausencia de mensajes pendientes en las particiones verificadas. También confirmé la recepción del retiro.»

La presencia DLQ está comprobada; no presentar como ejecutada una nueva prueba de fallo DLQ.

## 4:15–5:20 — Resiliencia y desafíos

Mostrar capturas 98–99/100, señalar 503 → healthy → 200 y Retry.

«Detuve ambas réplicas de cuentas. El BFF respondió 503 con un mensaje controlado. Al restaurarlas, volvieron consultas 200 y se conservaron saldos. En Batch provoqué una falla después de dos commits: la misma instancia retomó desde el checkpoint y terminó los registros restantes. Otro desafío fue clasificar errores JDBC. Cubrí fallos transitorios, recuperables y de conexión: cada caso se recuperó con un reintento y resultados idénticos. El SQL inválido no se reintenta. Los errores JDBC fueron simulados; no desconecté físicamente MySQL. El retiro legado tampoco se reintenta automáticamente porque no tiene clave idempotente.»

## 5:20–6:20 — Mejoras y entrega

Mostrar arquitectura y archivos de entrega.

«La solución integra procesos Batch, canales y servicios bancarios con evidencias de seguridad, balanceo, mensajería e integridad. El laboratorio depende de una sola EC2 y de MySQL, Kafka y autorización únicos. Propongo distribuirlos en varias zonas y usar servicios administrados. También incorporaría métricas de estados del Circuit Breaker y Authorization Code con PKCE para usuarios finales. Queda por validar Batch junto con operaciones en línea y comparar con el COBOL original si se dispone de él. Entrego código, instrucciones, guía AWS, informe con diagramas y esta presentación. Gracias.»

Mostrar los cinco archivos cuando PDF/MP4 estén incorporados. Antes de entregar revisar duración entre 5–7 minutos, webcam, audio y reproducción completa. Si la lectura termina antes, usar pausas para explicar los resultados de las capturas; no acelerar el discurso.
