# Banco XYZ Semana 5 con HTTPS y JWT

Esta implementación incorpora tres BFF ejecutables independientes, HTTPS con certificados propios, tokens JWT temporales y permisos específicos por canal. Los BFF acceden a los datos oficiales utilizados en la actividad mediante la base `bank_xyz_semana5_db`.

`VALIDACION.md` documenta las comprobaciones funcionales, de seguridad y rendimiento realizadas sobre la solución.

## Estrategia y organización

| Proyecto o módulo | Responsabilidad |
| --- | --- |
| `bff/bff-web` | Cuenta completa, hasta 20 movimientos y resumen; HTTPS 8081 |
| `bff/bff-movil` | Cuenta esencial y hasta 10 movimientos; HTTPS 8082 |
| `bff/bff-cajero` | Saldo enmascarado y retiros; HTTPS 8083 |
| `bff/bff-compartido` | Biblioteca de seguridad y acceso a datos incluida en cada JAR |
| `scripts` | Certificados locales, arranque y medición de respuestas |

Cada BFF contiene sus propios controladores, servicios y DTO y se despliega como un JAR independiente. La biblioteca común evita duplicar infraestructura de seguridad y acceso a datos; las tablas MySQL compartidas constituyen la fuente de datos de los tres canales.

El proyecto principal de la actividad es `bff/pom.xml`, que coordina los cuatro módulos Maven de la solución BFF.

## APIs implementadas
Las APIs se encuentran separadas por canal y acceden a los datos utilizados por la solución Banco XYZ. Cada BFF expone únicamente las operaciones necesarias para su tipo de cliente.
| Canal | Método | Endpoint | Función |
| --- | --- | --- | --- |
| Autenticación | POST | `/api/auth/token` | Autentica al usuario y genera un token JWT |
| Web | GET | `/api/bff/web/cuentas/{cuentaId}` | Obtiene información completa de una cuenta |
| Web | GET | `/api/bff/web/resumen` | Obtiene el resumen general utilizado por el canal Web |
| Móvil | GET | `/api/bff/movil/cuentas/{cuentaId}` | Obtiene información esencial de una cuenta |
| Móvil | GET | `/api/bff/movil/cuentas/{cuentaId}/movimientos` | Obtiene movimientos reducidos para el canal Móvil |
| Cajero | GET | `/api/bff/cajero/cuentas/{cuentaId}/saldo` | Consulta el saldo de la cuenta |
| Cajero | POST | `/api/bff/cajero/cuentas/{cuentaId}/retiros` | Ejecuta un retiro con validaciones de seguridad y saldo |

## Seguridad implementada

- HTTPS obligatorio con TLS 1.2 o 1.3, certificado RSA de 3072 bits por canal y HSTS. La aplicación se enlaza a `127.0.0.1` para la demostración local.
- No se abre un conector HTTP adicional ni se confía en cabeceras `X-Forwarded-Proto` proporcionadas por clientes.
- Tokens JWT HS256 con claves aleatorias de 256 bits distintas para cada canal. Se verifican firma, emisor, audiencia, canal, vencimiento e inicio de validez.
- Tokens de cinco minutos; los permisos y la cuenta se asignan en el servidor. El cliente no puede elegir scopes al autenticarse.
- Verificación de propiedad de la cuenta 106 y permisos diferenciados para leer, consultar el resumen y retirar.
- Comparación de contraseñas con BCrypt. Las contraseñas iniciales y claves de firma se generan localmente y no se distribuyen en el código.
- Las APIs protegidas requieren `Authorization: Bearer <token>`.
- El retiro mantiene transacción, bloqueo `FOR UPDATE`, comprobación de saldo, múltiplos de 1000 y auditoría.

Es una demostración académica local: sus usuarios están configurados por canal y vinculados a la cuenta 106. La emisión local de JWT no constituye un servidor OAuth2 completo ni un sistema de identidad bancario de producción. Un despliegue real debe usar identidad administrada, usuarios y cuentas reales, y políticas de rotación y revocación.

| Usuario | Scopes |
| --- | --- |
| `web-demo` | `web:lectura web:resumen` |
| `web-consulta` | `web:lectura` |
| `movil-demo` y `movil-consulta` | `movil:lectura` |
| `cajero-demo` | `cajero:lectura cajero:retiro` |
| `cajero-consulta` | `cajero:lectura` |

Un token válido que solicita una ruta no autorizada en su propio servidor recibe 403. Un token de otro servidor recibe 401 porque tiene otra firma, audiencia y emisor. Consultar una cuenta distinta de la autorizada produce 403.

## 1 Compilar

Desde la raíz del proyecto, donde se encuentran `bff` y `scripts`, abrir PowerShell y ejecutar:

```powershell
.\bff\mvnw.cmd -f .\bff\pom.xml clean package
```

Se conserva Java 17 y Spring Boot 4.1.0. Esperar `BUILD SUCCESS` antes de continuar. Las pruebas usan H2 y no modifican MySQL.

## 2 Generar certificados y credenciales

Con Java configurado en `JAVA_HOME`, ejecutar:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preparar-local.ps1
```

El script utiliza `keytool`, incluido en Java. Crea `.local` con tres almacenes PKCS12, certificados públicos, configuración SecureString/CLIXML cifrada por Windows y un entorno Postman. No instala certificados en Windows ni modifica la configuración del sistema.

Los certificados tienen vigencia de 365 días, uso de servidor TLS y SAN para `localhost` y `127.0.0.1`. Son autofirmados para desarrollo; un despliegue real requiere certificados emitidos por una CA apropiada. Una preparación existente se conserva para no cambiar claves inesperadamente.

La configuración CLIXML se usa con el mismo usuario de Windows que la generó. El entorno Postman contiene las contraseñas de demostración en texto legible para su importación. No compartir `.local`, subirla a Git ni incluirla en la entrega; está excluida en `.gitignore`.

## 3 Conservar los datos e incorporar índices

La base ya debe incluir `operaciones_cajero`. No repetir cargas ni reiniciar saldos. Abrir en Workbench, mediante **File → Open SQL Script**, `bff/sql/02-indices-bff.sql` y ejecutarlo una sola vez. Los índices no cambian los datos. Si alguno existe, conservarlo y ejecutar solo los restantes.

Si la tabla de operaciones de cajero aún no existe, ejecutar `bff/sql/01-operaciones-cajero.sql` antes de iniciar el BFF Cajero. Sobre la base utilizada para las evidencias esta estructura ya se encuentra preparada.

## 4 Iniciar los canales

Abrir tres terminales en la carpeta de este README. Ejecutar un comando en cada una:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar-bff.ps1 -Canal web
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar-bff.ps1 -Canal movil
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar-bff.ps1 -Canal cajero
```

Cada terminal solicita la contraseña MySQL de `bank_batch_user`. Las claves BFF se cargan desde la configuración generada. Para otro usuario MySQL añadir `-DbUsuario nombre`; para otra conexión usar `-DbUrl`. El script usa `JAVA_HOME`, sin depender de que `java` esté en PATH. Mantener abiertas las terminales; deben indicar puertos HTTPS.

HTTPS protege el tramo cliente/BFF. La conexión MySQL predeterminada sigue siendo local y sin TLS (`useSSL=false`). No se afirma cifrado de ese tramo; para MySQL remoto se debe configurar validación TLS y sus certificados.

## 5 Configurar Postman

1. Importar `bff/postman/Banco_XYZ_Semana5_HTTPS_JWT.postman_collection.json`.
2. Importar `.local/Semana5_Local.postman_environment.json` y seleccionar ese entorno.
3. En **Settings → Certificates**, agregar `.local/certificados-locales.pem` como certificado CA personalizado. Reúne los tres certificados públicos locales que se desean confiar.
4. Mantener **SSL certificate verification** habilitado. No omitir la validación ni utilizar `curl -k` para la evidencia.
5. En BFF Web, ejecutar **Obtener token completo**. Postman guarda el token automáticamente. Repetir los inicios de sesión de Móvil y Cajero al ejecutar sus carpetas.

`POST https://localhost:PUERTO/api/auth/token` recibe `usuario` y `password` en JSON, y devuelve `access_token`, `token_type`, `expires_in` y `scope`. La colección usa las variables generadas; no hace falta copiar contraseñas manualmente. Obtener otro token cuando venza después de cinco minutos. Ocultar el token completo y las contraseñas en las capturas finales.

## Evidencias de ejecución

| Evidencia | Qué debe mostrar |
| --- | --- |
| Compilación | Nuevas pruebas y BUILD SUCCESS |
| Arranque | Tres aplicaciones independientes en HTTPS |
| Certificados | Emisor, vigencia, SAN y validación SSL activa |
| Token | Emisión, vencimiento y scopes, ocultando su valor completo |
| Personalización | Cuenta 106 en Web y Móvil con respuestas distintas por HTTPS |
| Autenticación | 401 sin token, con token alterado, vencido o de otro canal |
| Autorización | 403 al retirar con usuario consulta, pedir otra cuenta o ruta ajena |
| Cajero | Retiro aprobado, saldo actualizado y auditoría |
| Optimización | Mediciones reales con metodología indicada |

Ejecutar el retiro aprobado una sola vez por demostración. Cada POST aprobado descuenta nuevamente 2000. La prueba compara saldo disponible con saldo anterior menos monto, sin presuponer un saldo inicial si ya hubo retiros. Consultar auditoría mediante `bff/sql/evidencia-bff.sql`.

## Optimización y mediciones

Móvil consulta solo fecha, tipo y monto para sus movimientos. Se añaden índices para las consultas recientes y compresión JSON desde 1024 bytes. Con los tres BFF abiertos:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\medir-bff.ps1
```

El script usa `curl.exe` con `--cacert` y compresión aceptada. Descarta tres solicitudes de calentamiento y toma 20 muestras por canal. Guarda CSV sin tokens en `mediciones`: estado HTTP, tiempo total, bytes transferidos y JSON descomprimido; calcula p50 y p95 por rango más cercano.

Los bytes transferidos corresponden al cuerpo, no a cabeceras. Cada muestra inicia un proceso curl; el tiempo incluye conexión y TLS, no es solamente SQL. Compara contratos distintos sobre la misma cuenta en un entorno local. No es una prueba de carga concurrente ni mide CPU o memoria. No atribuir una mejora a índices sin comparar planes o medir antes y después.

### Resultados obtenidos
Las mediciones se realizaron con 20 solicitudes válidas por canal, descartando previamente tres solicitudes de calentamiento.
| Canal | Muestras | p50 | p95 | Bytes transferidos promedio | JSON promedio |
| --- | ---: | ---: | ---: | ---: | ---: |
| Web | 20 | 238,316 ms | 254,67 ms | 631 | 3407 |
| Móvil | 20 | 237,159 ms | 247,06 ms | 144 | 144 |
| Cajero | 20 | 238,819 ms | 246,898 ms | 113 | 113 |
Los resultados muestran que los contratos de Móvil y Cajero reducen considerablemente el volumen de información respecto del canal Web. Web entrega el contrato más completo, mientras que Móvil y Cajero reciben únicamente los datos requeridos para sus operaciones, manteniendo tiempos de respuesta similares en el entorno local.
### Pruebas automatizadas
La solución fue recompilada desde cero mediante `mvn clean test`.
Resultado final:
- 17 pruebas ejecutadas.
- 0 fallos.
- 0 errores.
- 0 pruebas omitidas.
- `bff-compartido`: SUCCESS.
- `bff-web`: SUCCESS.
- `bff-movil`: SUCCESS.
- `bff-cajero`: SUCCESS.
- `BUILD SUCCESS`.

## Entrega final

La entrega reúne el código fuente de los tres BFF, README, colección Postman, scripts de ejecución, enlace GitHub y evidencias de funcionamiento. Se excluyen `.local`, `target`, logs, certificados privados y credenciales. El archivo para AVA utiliza la nomenclatura `Exp2_S5_Maximiliano_Palasezze.zip`.

Referencias técnicas: [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Spring Boot y servidores web](https://docs.spring.io/spring-boot/how-to/webserver.html).



