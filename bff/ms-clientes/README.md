# MS-CLIENTES — datos personales y perfiles de Semana 9

Gestiona nombre, correo, teléfono, dirección y perfil comercial del cliente, junto con su vínculo con cuentas existentes. Perfiles admitidos: `ESTANDAR`, `PREFERENTE` y `EMPRESA`. El perfil comercial no concede roles de seguridad ni scopes OAuth2.

Un cliente puede tener varias cuentas; cada cuenta tiene un único titular. La creación del cliente y el primer vínculo se guardan en una misma transacción. Un ID duplicado o una cuenta que ya tiene titular devuelve 409 y revierte la creación completa. Las cuentas cerradas no admiten nuevos vínculos; las cuentas anteriores sin fila en `cuentas_estado` se consideran activas. No se modifican saldos.

## Migración

En `bank_xyz_semana5_db`, ejecutar primero [03-gestion-cuentas.sql](../sql/03-gestion-cuentas.sql) y después [04-clientes.sql](../sql/04-clientes.sql). La segunda migración crea `clientes` y `clientes_cuentas`, con claves foráneas e índice de búsqueda por cliente. Ambas son repetibles y conservan los datos existentes. Los perfiles se crean mediante la API; la migración no inventa clientes para el legado.

Compose monta el archivo 04 para una base nueva. En un volumen MySQL existente, aplicar la migración manualmente antes de iniciar MS-CLIENTES actualizado, conservando ese volumen.

## Contratos internos

Base local: `http://localhost:8094`. Todas las rutas `/api/**` requieren Basic Auth y el rol `SERVICE`; sin credenciales responden 401 y el usuario VIEWER recibe 403.

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/clientes` | 201, cliente con versión 0 y primer vínculo |
| GET | `/api/clientes/900101` | 200, datos, perfil, versión y lista de cuentas |
| GET | `/api/clientes/cuentas/101` | 200, cliente titular de esa cuenta |
| PUT | `/api/clientes/900101` | 200, datos y perfil actualizados, versión incrementada |
| POST | `/api/clientes/900101/cuentas` | 200, vínculo con una cuenta activa; repetir el mismo vínculo es idempotente |

Crear un cliente de prueba para la cuenta 101 activa, conservada durante las pruebas previas:

```json
{
  "clienteId": 900101,
  "cuentaId": 101,
  "nombre": "Jane Smith",
  "email": "jane.s9@example.com",
  "telefono": "+56911111111",
  "direccion": "Santiago",
  "perfil": "ESTANDAR"
}
```

Actualizar datos y perfil:

```json
{
  "nombre": "Jane Smith",
  "email": "jane.actualizada@example.com",
  "telefono": "+56922222222",
  "direccion": "Maipu, Santiago",
  "perfil": "PREFERENTE",
  "version": 0
}
```

La API valida IDs positivos, campos obligatorios, correo, tamaños y perfil. `version` debe ser un entero no negativo. En cada actualización enviar la versión obtenida al consultar el cliente. Si otro proceso ya lo actualizó, devuelve 409 y conserva el cambio anterior; consultar nuevamente antes de decidir una nueva actualización. Esto evita sobrescribir información usando una lectura antigua.

Para vincular otra cuenta, enviar `{"cuentaId":102}`. La cuenta debe existir, estar activa y no tener otro titular. Cliente o cuenta inexistentes producen 404; los conflictos de titular, cuenta cerrada, ID o versión producen 409. No existe borrado de clientes ni transferencia automática de titularidad.

## Compilación y ejecución local

Desde `bff`, compilar el reactor completo:

```powershell
.\mvnw.cmd -B clean verify
```

Con DB_URL, DB_USER, DB_PASSWORD y JAVA_HOME ya definidos, iniciar en la misma terminal después de detener el servicio anterior con Ctrl+C:

```powershell
$env:SPRING_DATASOURCE_URL = $env:DB_URL
$env:SPRING_DATASOURCE_USERNAME = $env:DB_USER
$env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD
& "$env:JAVA_HOME\bin\java.exe" -jar .\ms-clientes\target\ms-clientes-0.0.1-SNAPSHOT.jar --server.port=8094 --spring.config.import=optional:file:./config-repo/ms-clientes.properties --spring.cloud.config.enabled=false --eureka.client.enabled=false
```

Este arranque usa el archivo de configuración local y permite probar el contrato interno en Postman. Mantener Batch detenido durante las pruebas de negocio. Usar las credenciales de servicio del entorno y capturar la petición y respuesta sin mostrar contraseñas.

En Docker, MS-CLIENTES usa Config Server, se registra en Eureka con un ID de instancia propio y dispone de healthcheck. Su puerto se mantiene en la red interna de Compose.

## Verificación

`GestionClientesTests` utiliza repositorio y servicio reales con transacciones H2 en modo MySQL y ejecuta los mismos scripts SQL 03 y 04. Comprueba persistencia, conservación del saldo, creación revertida ante conflictos, titular único, varias cuentas, vínculo repetido, cuentas cerradas, inexistencias y dos actualizaciones concurrentes sobre la misma versión. `SeguridadClientesTests` comprueba 401, 403, creación 201, validación de correo y perfil, y propagación del conflicto 409.

Verificación de desarrollo con Java 17 y Maven: `verify` para MS-CLIENTES y su padre terminó correctamente. Se aprobaron 15 pruebas, con cero fallas, errores u omisiones: 8 de gestión con base de datos y 7 de seguridad/validación. El JAR ejecutable inició correctamente en el puerto 8094 importando la configuración local, con Config Server y Eureka desactivados. Esa comprobación de arranque no consultó MySQL. La estructura YAML de Compose también se validó.

La validación local con MySQL/Postman, las rutas en los BFF con OAuth2 y Resilience4j, y la ejecución en AWS se completarán en las siguientes etapas. MS-CLIENTES no publica eventos bancarios; los eventos de retiros existentes se gestionan en MS-OPERACIONES/Kafka.
