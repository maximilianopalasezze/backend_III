# Banco XYZ — Resultados e índice de evidencias

Pruebas en PC y EC2 del 10–11 de octubre de 2026. Las capturas se incorporan al PDF, con títulos que identifiquen resultado y entorno. No se publican credenciales.

## Batch en PC

| Evidencia | Resultado |
| --- | --- |
| CSV oficiales; uno y tres hilos | 1000 registros por CSV; cero faltantes/diferencias en campos comparados |
| Transacciones | 480 escritas, 520 rechazadas y 88 anomalías |
| Intereses | 50 escritos, 950 rechazados; intereses 2525,00, saldos 413525,00, período 2024-01 |
| Estados anuales | 684 movimientos, 316 rechazos y 20 estados de 2024 |
| Volumen de 100000 filas | 48000 transacciones, 52000 rechazos y 8800 anomalías; resultados iguales entre hilos |
| Rendimiento | Step de 271941 ms con un hilo y 163125 ms con tres; reducción 40,01 % en un ensayo por configuración |
| Recuperación MySQL de tres Jobs | 100 lecturas antes de FAILED; 900 tras reiniciar; misma instancia, resultados iguales al lote completo |
| Captura 100: `Screenshot_134(2).png` | BUILD SUCCESS, 14 tests, cero fallas/errores/omisiones; JAR empaquetado |
| Captura 100: JDBC | Tres errores traducidos, un Retry por caso, COMPLETED, 1000/480/520 y resultados idénticos |
| Captura 100: SQL inválido | FAILED esperado, cero Retry y escrituras; test aprobado |

Captura 100: `RecuperacionBatchTests` (5), `ReintentosBatchTests` (4), `ServicioControlReinicioTests` (2), `RecursoEntradaBatchTests` (3). Log PC: `batch/target/cierre-batch-s9.log`. Build finalizado el 11 de octubre a las 03:34:52 hora local, duración 12,647 segundos.

JDBC usa excepciones simuladas, traducción real de `JdbcTemplate` y un Job real con H2 aislada. La recuperación MySQL usa una falla controlada del Step con JVM activa. No se provocó una caída física de MySQL. Las comparaciones utilizan ejecuciones completas de esta implementación; no se ejecutó COBOL original.

## Integración en EC2

| Captura | Archivo | Resultado comprobado |
| --- | --- | --- |
| 80 | `Screenshot_108(5).png` | Eureka: dos instancias UP de cuentas/clientes/pagos |
| 81–86 | `Screenshot_110(4).png`, `Screenshot_114(4).png` a `Screenshot_118(3).png` | Aperturas 201, consulta HTTPS, depósito 10000, pago 1000 y transferencia 2000 |
| 88 | `Screenshot_120(3).png` | Otra cantidad con la misma clave: 409 |
| 89A–B | `Screenshot_121(2).png`, `Screenshot_122(2).png` | Saldos 7000/2000 antes de móvil/cajero |
| 90 / 92 | `Screenshot_123(3).png`, `Screenshot_126(2).png` | Cliente creado 201, actualizado PREFERENTE/version 1 con 200 |
| 94 | `Screenshot_128(2).png` | Cuatro consultas por servicio, dos IDs diferentes, todos 200; cliente con cuentas 900201/900202 |
| 95 | `Screenshot_129(2).png` | Pago móvil 500, retiro cajero 1000; saldo final 5500 |
| 96 | `Screenshot_131(2).png` | 401 sin token/canal incorrecto, 403 por scope/cuenta; repetición y conflicto sin nuevos cargos |
| 97A | `Screenshot_131(2).png` | Cuatro operaciones aprobadas, cuatro eventos publicados y cinco movimientos |
| 97B | `Screenshot_130(2).png` | Grupos Kafka Stable; lag cero en particiones activas; referencia del retiro recibida |
| 98–99 | `Screenshot_132(1).png` | Ambas réplicas detenidas, tres 503; dos réplicas restauradas healthy, consultas 200 y saldos iguales |

El arranque también mostró 17 contenedores en ejecución, 16 healthy y Kafka UI Up. Seleccionar la captura completa de ese resultado conservada por el estudiante.

TLS se acredita con Postman y las pruebas Python de CA/hostname; la captura 81 por sí sola no lo demuestra. Usar 92 y 94 para perfil/vínculos completos: 91 y 93 son respuestas parciales o una consulta con cuerpo de actualización. No incorporar `Screenshot_109(5).png` ni `Screenshot_112(4).png`, que contienen un token/secreto visible.

## Integridad y mensajería

La transferencia `s9_aws_trans_001` repetida conserva referencia `5f8a4808-5401-42bf-9dde-213cd95ed489`; cambiar monto produce 409. Su saldo histórico es 7000; después de otras operaciones el saldo actual es 5500. La cuenta 900202 conserva 2000.

Cuatro operaciones MS-PAGOS producen cuatro eventos y cinco movimientos, porque la transferencia tiene salida/entrada. El retiro de 1000 se verificó por referencia `73fbf8cd-b799-4ef6-8889-f95de7fa21b3` en el log legado; no se cuenta como otra fila de proyección de pagos.

Kafka mostró grupos Stable y lag cero en particiones con mensajes; las vacías sin offset confirmado tenían log-end cero. Se comprobó el tópico DLQ, sin inyectar una nueva falla.

## Correspondencia con la pauta

| Requisito | Evidencia |
| --- | --- |
| Jobs, validaciones y reportes | CSV oficiales y resultados Batch |
| Volumen, integridad, recuperación y Retry | Uno/tres hilos, 100000 filas, checkpoints MySQL, captura 100 |
| BFF y seguridad | Contratos por canal; capturas 82/95/96 |
| Cuentas, clientes y pagos | Contratos y migraciones; capturas 81–86/90/92/94 |
| Configuración, descubrimiento y balanceo | Config Server, Compose, Eureka y capturas 80/94 |
| Mensajería y resiliencia | Outbox/Kafka y capturas 97/98–99 |
| Entrega escrita y exposición | Markdown preparados; incorporar PDF con plantilla AVA y MP4 con webcam |

Esta tabla no asigna puntaje. Las mejoras propuestas se distinguen de pruebas ejecutadas: OPEN/HALF_OPEN, PKCE, disponibilidad fuera de una sola EC2, comparación COBOL y coordinación Batch/operaciones simultáneas.
