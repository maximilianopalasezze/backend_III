# BFF Banco XYZ - Semana 5
Este proyecto implementa tres Backend for Frontend independientes para los canales Web, Móvil y Cajero, junto con el módulo compartido `bff-compartido`.
Cada canal posee sus propios controladores, servicios y DTO, y se genera como un JAR independiente:
- `bff-web`: información completa para navegadores.
- `bff-movil`: respuestas reducidas y optimizadas para dispositivos móviles.
- `bff-cajero`: consulta de saldo y operaciones de retiro.
- `bff-compartido`: seguridad, autenticación, autorización y acceso común a datos.
La comunicación con los BFF utiliza HTTPS y autenticación mediante tokens JWT con permisos específicos por canal.
## Compilación
En Windows:
`.\mvnw.cmd clean package`
En Linux:
`./mvnw clean package`
El proyecto utiliza Java 17 y Spring Boot 4.1.0.
El README ubicado en la raíz del repositorio contiene la arquitectura completa, las APIs implementadas, preparación de certificados, configuración de Postman, ejecución de los tres canales y resultados de las pruebas y mediciones.
