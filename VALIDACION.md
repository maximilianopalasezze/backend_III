# Validación - Backend III Semana 6

## Checklist de pauta

| Criterio | Implementación | Evidencia sugerida |
| --- | --- | --- |
| Servidor centralizado funcional | `config-server` en 8888 + `config-repo` externo | `GET /ms-cuentas/default` |
| Integración con microservicio | Todos los Backend/BFF consumen Config Server | Consola de inicio + endpoint Config |
| Service Discovery | `discovery-server` Eureka en 8761 | Dashboard Eureka |
| Tres microservicios registrados | `MS-CUENTAS`, `MS-MOVIMIENTOS`, `MS-OPERACIONES` | Tres instancias `UP` |
| Integración real por HTTP | BFF -> `RestTemplate @LoadBalanced` -> nombres Eureka | Código `ClienteServiciosBanco` + respuesta Postman |
| Tolerancia a fallos | Resilience4j Circuit Breaker, Retry y fallbacks | Detener MS-MOVIMIENTOS y repetir Web |
| Autenticación | HTTP Basic en tres microservicios + JWT en BFF | 401 sin credenciales |
| Autorización | `ROLE_SERVICE` / `ROLE_VIEWER` | 403 con VIEWER y 200 con SERVICE |
| Continuidad Semana 5 | Web/Móvil/Cajero mantienen contratos propios | Respuestas distintas por canal |
| Corrección feedback docente | BFF no contiene JDBC/datasource; acceso DB en Backend | Revisión de POM y código |

## Validación previa a capturas

1. Ejecutar `clean package` y confirmar `BUILD SUCCESS`.
2. Ejecutar `scripts/preparar-semana6.ps1`.
3. Ejecutar `scripts/iniciar-semana6.ps1`.
4. Esperar hasta que Eureka muestre los servicios `UP`.
5. Importar colección y entorno de Semana 6 en Postman.
6. Ejecutar las carpetas de Infraestructura y Seguridad.
7. Ejecutar Web, Móvil y Cajero.
8. Para resiliencia, detener únicamente `ms-movimientos`, ejecutar el request de tolerancia y luego volver a iniciar ese servicio.
9. Volver a iniciar `ms-movimientos` y verificar su registro nuevamente en Eureka.
10. Repetir la consulta Web y comprobar HTTP 200 con la respuesta completa,
    demostrando recuperación después del fallo.

## Comportamiento esperado

- `http://localhost:8888/ms-cuentas/default`: HTTP 200 con propiedades centralizadas.
- `http://localhost:8761`: dashboard Eureka.
- `GET http://localhost:8091/api/cuentas/106` sin Basic: 401.
- El mismo endpoint con usuario VIEWER: 403.
- El mismo endpoint con usuario SERVICE: 200.
- Web con MS-MOVIMIENTOS operativo: movimientos presentes según datos.
- Web con MS-MOVIMIENTOS detenido: HTTP 200 y `movimientosRecientes: []` por fallback.
- Si MS-CUENTAS está caído: el BFF responde 503 controlado, evitando error no tratado.
- Después de reiniciar `MS-MOVIMIENTOS`, Eureka vuelve a mostrar la instancia
  activa y el BFF Web recupera su respuesta normal.