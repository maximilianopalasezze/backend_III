# Banco XYZ — Microservicios y BFF de la evaluación final

Proyecto Maven con doce módulos: once servicios ejecutables y la biblioteca `bff-compartido`. Desde esta carpeta, con Java 17:

```powershell
.\mvnw.cmd -B clean verify
```

| Módulos | Función |
| --- | --- |
| `auth-server`, `config-server`, `discovery-server` | OAuth2, configuración y descubrimiento |
| `ms-cuentas`, `ms-clientes`, `ms-pagos` | Gestión de cuentas, clientes y operaciones de pagos |
| `ms-operaciones`, `ms-movimientos` | Retiros, mensajería y consulta/proyección de movimientos |
| `bff-web`, `bff-movil`, `bff-cajero` | HTTPS y contratos protegidos por canal |
| `bff-compartido` | Seguridad, clientes HTTP balanceados y resiliencia compartidos |

La configuración está en `config-repo` y Compose en `../infra/docker-compose.yml`. Los BFF invocan los nombres lógicos de los microservicios mediante `RestTemplate @LoadBalanced` y no acceden directamente a MySQL.

Consultar [ejecución y pruebas](../instrucciones.md), [despliegue AWS](../despliegue.md), [contratos BFF](README-semana9.md) y [arquitectura](../docs/arquitectura-s9.md). Spring Batch permanece como módulo independiente en `../batch`.
