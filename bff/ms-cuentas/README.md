# MS-CUENTAS — gestión de cuentas de Semana 9

El servicio permite abrir una cuenta con saldo cero, mantener su tipo y cerrarla conservando datos e historial. El saldo se modifica únicamente mediante procesos de negocio; estos contratos no permiten asignarlo manualmente. Tipos admitidos: `ahorro`, `corriente`, `prestamo`, `hipoteca`. La apertura exige un ID positivo, nombre no vacío de hasta 150 caracteres y edad de 18 a 120.

## Base de datos

Ejecutar [../sql/03-gestion-cuentas.sql](../sql/03-gestion-cuentas.sql) en `bank_xyz_semana5_db` antes de iniciar los servicios actualizados. Crea `cuentas_estado` con clave foránea hacia `cuentas` sin borrar datos. Es repetible. Las cuentas anteriores, sin una fila de estado, se consideran `ACTIVA`.

Compose monta esta migración para una base nueva. Un volumen MySQL ya existente no vuelve a ejecutar los scripts de inicialización: aplicar esta misma migración manualmente antes de actualizar sus servicios. No eliminar el volumen para migrar.

## Contratos

| Método | Ruta | Solicitud | Resultado |
| --- | --- | --- | --- |
| POST | `/api/cuentas` | `{"cuentaId":900001,"nombre":"Prueba Semana 9","edad":30,"tipoCuenta":"ahorro"}` | 201, saldo cero, estado ACTIVA |
| GET | `/api/cuentas/900001` | Sin cuerpo | 200, cuenta y estado |
| PUT | `/api/cuentas/900001` | `{"tipoCuenta":"corriente"}` | 200, tipo actualizado, mismo saldo |
| POST | `/api/cuentas/900001/cierre` | Sin cuerpo | 200, estado CERRADA si saldo cero |

ID duplicado, mantenimiento de una cuenta cerrada y cierre con saldo distinto de cero devuelven 409; cuenta inexistente devuelve 404; datos inválidos devuelven 400. Repetir el cierre conserva la fecha original. No existe borrado ni reapertura implícita.

Las rutas internas mantienen la autenticación Basic del servicio y el rol `SERVICE` heredados. Sin credenciales responden 401 y el usuario VIEWER recibe 403. La exposición de gestión en BFF Web con permisos OAuth2 se incorporará en la integración siguiente; estas rutas no deben publicarse como una API administrativa abierta.

## Compilación y prueba local

Desde `bff`:

```powershell
.\mvnw.cmd -B clean verify
```

Con `DB_URL`, `DB_USER` y `DB_PASSWORD` ya definidos, iniciar el servicio en una terminal local:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar .\ms-cuentas\target\ms-cuentas-0.0.1-SNAPSHOT.jar --spring.cloud.config.enabled=false --eureka.client.enabled=false
```

Este modo permite probar directamente en `http://localhost:8091`, sin Config Server ni Eureka. Para la integración final se usan Config Server, Eureka y los BFF con HTTPS/OAuth2. Mantener Batch detenido durante la prueba: su Job de intereses escribe saldos históricos.

En Postman configurar Basic Auth con el usuario y contraseña de servicio definidos por `backend.security.usuario` y `backend.security.password` (o sus valores del entorno utilizado). No incluir contraseñas ni tokens en las capturas. Usar una cuenta nueva de prueba, por ejemplo 900001, en lugar de cerrar la cuenta 102 de los canales OAuth2.

## Consistencia y verificación

Cierre, mantenimiento y retiro bloquean primero la misma fila de `cuentas` con `SELECT ... FOR UPDATE` dentro de una transacción. El estado se consulta después mediante una lectura bloqueante para evitar un estado antiguo bajo REPEATABLE READ. Una operación concurrente termina antes de decidir el cierre; una cuenta ya cerrada rechaza retiros con 409 sin cambiar saldo ni registrar operación. El BFF Web incluye el estado en `producto.estado`.

`GestionCuentasTests` comprueba persistencia, saldo, duplicados, restricciones del cierre, compatibilidad con cuentas previas, errores por inexistencia y bloqueo concurrente. `EstadoCuentaRetiroTests`, en MS-OPERACIONES, usa el repositorio y servicio reales para comprobar retiros de cuentas cerradas, cuentas previas y saldo insuficiente. Las pruebas de seguridad ejercitan además apertura y cierre protegidos y validación de solicitudes. H2 se utiliza como base aislada de pruebas; la evidencia MySQL se recoge durante la validación local.

Verificación de desarrollo con Java 17 y Maven: `verify` terminó correctamente para MS-CUENTAS, MS-OPERACIONES, los tres BFF y el módulo compartido. Se aprobaron 27 pruebas, con cero fallas y errores. La compilación completa en el PC y la validación contra MySQL son los siguientes pasos.

Capturas previstas: compilación y pruebas; tabla `cuentas_estado`; apertura 201; mantenimiento 200; ID duplicado 409; cierre 200; modificación de cuenta cerrada 409; consultas SQL que conservan la cuenta. El rechazo de retiros se validará al iniciar también MS-OPERACIONES y comprobar su integración.
