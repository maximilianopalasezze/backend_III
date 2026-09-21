# Banco XYZ - Spring Cloud Semana 6

Proyecto Maven multi-módulo. Ejecutar desde esta carpeta:

```powershell
.\mvnw.cmd clean package
```

Módulos: `config-server`, `discovery-server`, `ms-cuentas`, `ms-movimientos`, `ms-operaciones`, `bff-compartido`, `bff-web`, `bff-movil` y `bff-cajero`.

La configuración de ejecución está en `config-repo`. Los BFF consumen los microservicios mediante `RestTemplate` con `@LoadBalanced`; no acceden directamente a MySQL. Ver el `README.md` de la raíz para preparación, ejecución y evidencias.
