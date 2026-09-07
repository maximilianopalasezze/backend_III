# Checklist de evaluación — Semana 4 BFF

Este checklist relaciona cada criterio de la pauta con una implementación verificable y con la evidencia que debe incluirse en la entrega.

## 1. Demuestra una comprensión clara del patrón BFF

**Estado del código y la documentación: cumplido.**

- El README define BFF, explica el problema que resuelve y documenta ventajas y compensaciones.
- La solución no expone una respuesta genérica: cada canal posee un contrato HTTP propio.
- Los resultados de Spring Batch se reutilizan como fuente de datos validada; el BFF se ocupa de adaptar la experiencia de consumo.

**Evidencia recomendada:** diagrama del README y una explicación breve de por qué Web, Móvil y Cajero no necesitan los mismos datos.

## 2. Implementa una de las estrategias de BFF

**Estado del código: cumplido; falta obtener evidencia de ejecución local.**

- Estrategia elegida: un backend desplegable por canal mediante perfiles de Spring.
- `application-web.properties`: BFF Web, puerto 8081.
- `application-movil.properties`: BFF Móvil, puerto 8082.
- `application-cajero.properties`: BFF Cajero, puerto 8083.
- `@Profile` activa solamente controladores y servicios del canal ejecutado.
- Cada BFF usa una API key y un rol diferentes.

**Evidencia obligatoria:** las tres aplicaciones iniciadas en terminales separadas, mostrando perfil, puerto y ruta del canal.

## 3. Personaliza la información según las necesidades de cada frontend

**Estado del código y Postman: cumplido; faltan capturas de las respuestas.**

| Frontend | Personalización implementada | Prueba principal |
|---|---|---|
| Web | Respuesta completa, información anidada, interés, estado anual, movimientos y resumen | `GET /api/bff/web/cuentas/106` |
| Móvil | Respuesta liviana y endpoint de movimientos con límite máximo | `GET /api/bff/movil/cuentas/106` |
| Cajero | Cuenta enmascarada, consulta de saldo y retiro transaccional auditado | `GET .../saldo` y `POST .../retiros` |

La colección Postman comprueba que Móvil y Cajero no exponen campos reservados para la respuesta completa Web.

**Evidencia obligatoria:** una captura legible de la respuesta de cada canal para la misma cuenta oficial `106`.

## 4. Organiza el código según la estrategia BFF elegida

**Estado del código: cumplido.**

- Paquetes independientes `bff.web`, `bff.movil` y `bff.cajero`.
- Cada canal separa `controlador`, `servicio` y `dto`.
- Infraestructura transversal limitada a `bff.compartido`.
- Los componentes Spring Batch permanecen separados de la capa BFF.
- Perfiles, puertos y credenciales se externalizan en archivos de configuración.

**Evidencia recomendada:** árbol de paquetes abierto en IntelliJ y archivos de configuración de los tres perfiles.

## Control especial de datos oficiales

Aunque no aparece como uno de los cuatro enunciados breves de la pauta, es una indicación expresa del profesor y debe tratarse como obligatoria:

- Los CSV del repositorio coinciden byte por byte con el ZIP oficial.
- `scripts/verificar-datos-oficiales.ps1` valida cantidad y SHA-256.
- `DatosOficialesTests` repite automáticamente esa validación durante `mvnw clean test`.
- Los tres Jobs deben ejecutarse en una base nueva llamada `bank_batch_semana4_db`.
- Antes de ejecutarlos se fijan explícitamente las tres variables `BATCH_ARCHIVO_*` con rutas `data/semana_3/...`.
- `sql/evidencia-bff.sql` muestra la ruta oficial, procesados, rechazados y total leído.
- Las capturas deben mostrar simultáneamente la consulta o log y la ruta `data/semana_3/...`.

## Condición para declarar la entrega terminada

No se debe subir la versión final hasta completar en el equipo local:

1. `mvnw clean test` con `BUILD SUCCESS`.
2. Los tres Jobs con estado `COMPLETED` y rutas oficiales.
3. Los tres BFF ejecutándose con sus perfiles.
4. Toda la colección Postman en verde.
5. Consulta SQL con `DATOS OFICIALES COMPLETOS` para los tres procesos.
6. Auditoría del retiro visible en `operaciones_cajero`.
