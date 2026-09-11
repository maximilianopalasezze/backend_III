# Validación técnica - Banco XYZ Semana 5
Este documento resume las verificaciones realizadas sobre la implementación Backend for Frontend (BFF) de Banco XYZ.
La solución está compuesta por tres BFF ejecutables de forma independiente para Web, Móvil y Cajero, además de un módulo compartido para seguridad y acceso a datos.
## 1. Validación de estructura
La solución se organiza en los siguientes módulos Maven:
- `bff-web`: backend específico para navegadores.
- `bff-movil`: backend optimizado para dispositivos móviles.
- `bff-cajero`: backend orientado a consultas de saldo y retiros.
- `bff-compartido`: componentes reutilizables de seguridad, autenticación y acceso a datos.
Cada canal posee sus propios controladores, servicios y DTO. Los BFF Web, Móvil y Cajero se generan como JAR independientes.
## 2. APIs verificadas
### Autenticación
- `POST /api/auth/token`
### Web
- `GET /api/bff/web/cuentas/{cuentaId}`
- `GET /api/bff/web/resumen`
### Móvil
- `GET /api/bff/movil/cuentas/{cuentaId}`
- `GET /api/bff/movil/cuentas/{cuentaId}/movimientos`
### Cajero
- `GET /api/bff/cajero/cuentas/{cuentaId}/saldo`
- `POST /api/bff/cajero/cuentas/{cuentaId}/retiros`
Las APIs acceden a los datos utilizados por Banco XYZ y entregan contratos distintos según las necesidades de cada canal.
## 3. Personalización por canal
La implementación adapta las respuestas de acuerdo con cada frontend:
- Web entrega información completa y hasta 20 movimientos.
- Móvil entrega información esencial y limita la respuesta a un máximo de 10 movimientos.
- Cajero expone únicamente información y operaciones necesarias para consultas de saldo y retiros.
Esta separación evita entregar información innecesaria a clientes que requieren contratos más pequeños.
## 4. Seguridad
Los tres BFF utilizan HTTPS.
La solución implementa:
- TLS 1.2/1.3.
- Certificados locales para los tres canales.
- Autenticación mediante JWT.
- Claves JWT diferenciadas por canal.
- Tokens con vencimiento.
- Autorización mediante scopes específicos.
- Validación de cuenta autorizada.
- BCrypt para validación de contraseñas.
- Respuestas 401 para solicitudes sin autenticación válida.
- Respuestas 403 para operaciones autenticadas sin permisos suficientes.
Los tokens de un canal no permiten acceder libremente a los recursos de otro canal.
Para Cajero se agregan validaciones específicas para operaciones críticas, entre ellas saldo disponible, autorización de retiro y montos múltiplos de 1000.
## 5. Pruebas automatizadas
Se ejecutó desde la copia limpia del proyecto:
`mvn clean test`
Resultado:
- Tests ejecutados: 17
- Failures: 0
- Errors: 0
- Skipped: 0
Resultado de módulos:
- `bff-compartido`: SUCCESS
- `bff-web`: SUCCESS
- `bff-movil`: SUCCESS
- `bff-cajero`: SUCCESS
Resultado final:
`BUILD SUCCESS`
Las pruebas cubren comportamiento de servicios y controles de seguridad de los tres canales.
## 6. Validación HTTPS y Postman
Los BFF fueron ejecutados mediante HTTPS y probados desde Postman con verificación SSL habilitada.
La colección utilizada se encuentra en:
`bff/postman/Banco_XYZ_Semana5_HTTPS_JWT.postman_collection.json`
La preparación local genera los certificados y credenciales necesarios sin almacenar secretos reales dentro del repositorio.
Los elementos privados se mantienen bajo `.local`, carpeta excluida mediante `.gitignore`.
## 7. Optimización y mediciones
Se ejecutaron 20 muestras válidas por canal después de descartar tres solicitudes de calentamiento.
Resultados:
| Canal | Muestras | p50 | p95 | Bytes transferidos promedio | JSON promedio |
| --- | ---: | ---: | ---: | ---: | ---: |
| Web | 20 | 238,316 ms | 254,67 ms | 631 | 3407 |
| Móvil | 20 | 237,159 ms | 247,06 ms | 144 | 144 |
| Cajero | 20 | 238,819 ms | 246,898 ms | 113 | 113 |
Las mediciones muestran que Web entrega el contrato más completo, mientras Móvil y Cajero reducen significativamente la cantidad de información transferida.
Los tres canales mantienen tiempos de respuesta similares dentro del entorno local de prueba.
## 8. Evidencias para la entrega
Las evidencias finales deben mostrar:
1. Ejecución de `mvn clean test` con 17 pruebas y `BUILD SUCCESS`.
2. Ejecución independiente de Web, Móvil y Cajero mediante HTTPS.
3. Emisión de JWT.
4. Acceso autorizado a Web.
5. Respuesta optimizada de Móvil.
6. Consulta de saldo en Cajero.
7. Retiro autorizado en Cajero y saldo actualizado.
8. Rechazo 401 ante autenticación inválida o ausente.
9. Rechazo 403 ante permisos insuficientes.
10. Mediciones comparativas de Web, Móvil y Cajero.
## 9. Estado final
La solución cumple con la implementación del patrón Backend for Frontend para los tres canales solicitados, diferenciando contratos, permisos y operaciones.
La versión preparada para entrega incluye código fuente, README, colección Postman, scripts de ejecución y documentación técnica. Se excluyen archivos generados, credenciales, certificados privados, logs y configuraciones locales.
