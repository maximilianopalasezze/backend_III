# Banco XYZ — Backend III, Semana 9

Evaluación Final Transversal: tres procesos Spring Batch, servicios bancarios, BFF por canal, OAuth2, HTTPS, Spring Cloud, Resilience4j, Kafka y Docker Compose. Integra los componentes del banco y conserva su base de datos `bank_xyz_semana5_db`.

**Código:** [rama semana9-evaluacion-final](https://github.com/maximilianopalasezze/backend_III/tree/semana9-evaluacion-final).

## Documentación

- [instrucciones.md](instrucciones.md): compilación, ejecución y pruebas de los componentes.
- [despliegue.md](despliegue.md): preparación, certificados, migraciones y despliegue en AWS.
- [Arquitectura, casos de uso y componentes](docs/arquitectura-s9.md).
- [Procesamiento Spring Batch](batch/README.md).
- [Contratos y seguridad de los BFF](bff/README-semana9.md).

El informe técnico PDF con la plantilla oficial y el video MP4 de 5–7 minutos con webcam se adjuntan en la entrega de la actividad, junto con `readme.md`, `instrucciones.md` y `despliegue.md`.

## Componentes

| Componente | Función |
| --- | --- |
| Batch | Transacciones diarias, intereses mensuales y estados anuales; validación, checkpoints y Retry |
| MS-CUENTAS | Consulta, apertura, mantenimiento del producto y cierre lógico con saldo cero |
| MS-CLIENTES | Datos personales, perfil, vínculos de cuentas y control de versión |
| MS-PAGOS | Depósitos, pagos y transferencias; saldos, recibo y outbox en una transacción |
| MS-OPERACIONES / MS-MOVIMIENTOS | Retiro legado, eventos y proyección de pagos |
| BFF Web / Móvil / Cajero | Contratos por canal, permisos por cuenta, HTTPS y resiliencia |
| Authorization Server / Config Server / Eureka | Tokens, configuración centralizada y descubrimiento |
| MySQL / Kafka / Kafka UI | Persistencia, mensajería y consulta del broker |

Todos los componentes usan Java 17. Batch es un módulo independiente con Spring Boot 4.1.0 / Spring Batch 6. El reactor `bff` usa Spring Boot 3.5.10, Spring Cloud 2025.0.1 y Resilience4j 2.3.0. `bff-compartido` es una dependencia, no un servidor.

## Resultados comprobados

Los tres CSV de [fin_legacy_data](https://github.com/KariVillagran/fin_legacy_data) contienen 1000 registros cada uno. Los Jobs produjeron 480 transacciones/520 rechazos; 50 intereses/950 rechazos; 684 movimientos anuales/316 rechazos/20 estados. Las comparaciones entre uno y tres hilos y después de reiniciar conservaron los campos de negocio revisados. Con 100000 transacciones, tres hilos redujeron el tiempo un 40,01 % en una medición por configuración.

El cierre de Batch en PowerShell del 11 de octubre de 2026 aprobó **14 pruebas, cero fallas, errores u omisiones**, y empaquetó el JAR. Tres fallos JDBC simulados se recuperaron con un reintento cada uno y datos idénticos. El SQL permanente terminó en FAILED sin Retry, como exige la prueba. [Detalle de Batch](batch/README.md).

En EC2 se observaron 17 contenedores en ejecución: 16 healthy y Kafka UI Up. Cuentas, clientes y pagos tuvieron dos réplicas; consultas reales mostraron dos IDs distintos por servicio. Los BFF usaron TLS verificado y clientes OAuth separados. Se comprobaron 401 sin token/canal incorrecto y 403 por scope insuficiente/cuenta ajena.

Web realizó apertura, operaciones y gestión de clientes; móvil pagó 500 y cajero retiró 1000. La transferencia repetida conservó su comprobante y saldos; otro monto con la misma clave produjo 409. Los saldos finales fueron 5500/2000. MySQL confirmó cuatro operaciones de pagos, cuatro eventos publicados y cinco movimientos. Kafka confirmó el consumo. Detener ambas réplicas de cuentas produjo tres 503 controlados; restaurarlas recuperó consultas 200 sin cambios de saldo.

## Alcance y ejecución

Batch se ejecutó en PC con MySQL y sus tests usan H2 aislada; no se ejecutó sobre EC2. El Retry JDBC simula excepciones y la recuperación MySQL simula una falla del Step; no se provocó una caída física de MySQL. La comparación usa ejecuciones completas de esta solución y datos oficiales, sin ejecutar el COBOL original. La concurrencia Batch/operaciones en línea queda como mejora.

El laboratorio tiene una sola EC2, MySQL, Kafka y Authorization Server. Las réplicas demuestran balanceo y recuperación dentro del host. Se verificó la existencia de la DLQ sin inyectar una nueva falla. La prueba AWS acredita fallback y recuperación, sin observar OPEN/HALF_OPEN mediante Actuator.

Consultar las [instrucciones](instrucciones.md). Los secretos se configuran en `.env.docker.local`, fuera de Git. Batch permanece desactivado durante la demostración bancaria integrada: el Job de intereses carga saldos históricos.
