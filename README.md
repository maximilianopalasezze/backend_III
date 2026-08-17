# Banco XYZ - Migración de Procesos Batch

Proyecto desarrollado con Spring Batch para modernizar tres procesos legacy del Banco XYZ: procesamiento de transacciones diarias, cálculo de intereses mensuales y generación de estados de cuenta anuales.

## Autor

- **Nombre:** Maximiliano Palasezze
- **Tecnología principal:** Java 17 y Spring Batch
- **Base de datos:** MySQL

## Objetivo

Migrar los procesos batch del sistema legacy del Banco XYZ hacia una solución moderna que permita:

- Leer información desde archivos CSV.
- Validar y transformar datos incorrectos.
- Detectar anomalías y registros duplicados.
- Omitir registros inválidos sin detener completamente el Job.
- Reintentar errores transitorios de base de datos.
- Persistir los resultados procesados en MySQL.
- Generar resúmenes útiles para auditoría.

## Fuente de datos

Los archivos utilizados corresponden al repositorio proporcionado para la actividad:

[Repositorio bank_legacy_data](https://github.com/KariVillagran/bank_legacy_data)

Los datos se encuentran organizados en:

```text
src/main/resources/data
├── semana_1
│   ├── transacciones.csv
│   ├── intereses.csv
│   └── cuentas_anuales.csv
├── semana_2
│   ├── transacciones.csv
│   ├── intereses.csv
│   └── cuentas_anuales.csv
└── semana_3
    ├── transacciones.csv
    ├── intereses.csv
    └── cuentas_anuales.csv
```

Cada semana representa un nivel distinto de complejidad:

- `semana_1`: datos mayoritariamente válidos.
- `semana_2`: datos con errores controlados.
- `semana_3`: archivos de 1.000 registros con errores, anomalías y duplicados.

## Propuesta técnica

La solución utiliza el procesamiento orientado a chunks de Spring Batch.

Cada Job implementa el siguiente flujo:

```text
Archivo CSV
    ↓
ItemReader
    ↓
ItemProcessor
    ↓
ItemWriter
    ↓
MySQL
```

Los registros se procesan en bloques de 100 elementos para reducir la cantidad de operaciones individuales contra la base de datos y mejorar el rendimiento.

La solución incluye:

- Un Job independiente para cada proceso bancario.
- Lectores `FlatFileItemReader`.
- Procesadores `ItemProcessor`.
- Escritores JDBC.
- Procesamiento por chunks.
- Políticas de `skip`.
- Políticas de `retry`.
- Listeners para registrar rechazos.
- Listeners para generar resúmenes.
- Persistencia de auditoría.
- Variables de entorno para las credenciales.

## Jobs implementados

| Job | Step | Descripción |
|---|---|---|
| `jobTransaccionesDiarias` | `stepProcesarTransacciones` | Procesa transacciones, detecta anomalías y genera resúmenes diarios. |
| `jobInteresesMensuales` | `stepProcesarIntereses` | Calcula intereses, actualiza saldos y registra los cálculos realizados. |
| `jobEstadosCuentaAnuales` | `stepProcesarMovimientosAnuales` | Procesa movimientos anuales y genera un estado consolidado por cuenta. |

## 1. Reporte de transacciones diarias

El Job `jobTransaccionesDiarias` lee el archivo `transacciones.csv`.

### Validaciones

- Identificador obligatorio, numérico y positivo.
- Fecha obligatoria y válida.
- Monto obligatorio y numérico.
- Tipo de transacción permitido: débito o crédito.
- Normalización de mayúsculas, espacios y acentos.
- Prevención de duplicados mediante restricciones de base de datos.

### Detección de anomalías

Los montos negativos o iguales a cero se conservan como transacciones procesadas, pero quedan marcados como anomalías para permitir su posterior revisión.

### Salidas

- `transacciones_procesadas`
- `resumen_transacciones_diarias`
- `registros_rechazados`

## 2. Cálculo de intereses mensuales

El Job `jobInteresesMensuales` lee el archivo `intereses.csv`.

### Validaciones

- Identificador de cuenta obligatorio y positivo.
- Nombre del titular obligatorio.
- Rechazo del nombre `Unknown`.
- Saldo obligatorio, numérico y no negativo.
- Edad válida entre 18 y 99 años.
- Tipo de cuenta permitido.
- Detección de cuentas duplicadas.
- Normalización de espacios, mayúsculas y acentos.

### Tasas configuradas

| Tipo de cuenta | Tasa mensual |
|---|---:|
| Ahorro | 0,5 % |
| Préstamo | 1,5 % |
| Hipoteca | 0 % |

Las tasas se encuentran configuradas en `application.properties`.

La tasa de hipoteca se mantiene en cero porque el requerimiento principal solicita aplicar intereses sobre cuentas de ahorro y préstamos. El tipo hipoteca se conserva como una cuenta válida para no perder información del sistema legacy.

### Fórmula

```text
interés = saldo inicial × tasa de interés
saldo final = saldo inicial + interés
```

Los resultados se redondean a dos decimales.

### Salidas

- `cuentas`
- `intereses_calculados`
- `registros_rechazados`

## 3. Estados de cuenta anuales

El Job `jobEstadosCuentaAnuales` lee el archivo `cuentas_anuales.csv`.

### Validaciones y transformaciones

- Identificador de cuenta obligatorio y positivo.
- Fecha válida y perteneciente al año procesado.
- Descripción obligatoria.
- Monto obligatorio, numérico y diferente de cero.
- Tipo de movimiento permitido:
    - `deposito`
    - `retiro`
    - `compra`
    - `pago`
- Normalización de `depósito` como `deposito`.
- Detección de movimientos duplicados.
- Corrección de formatos de fecha.
- Normalización del signo de los montos.

### Normalización de montos

- Los depósitos se almacenan con signo positivo.
- Los retiros, compras y pagos se almacenan con signo negativo.

### Formatos de fecha admitidos

```text
yyyy-MM-dd
yyyy/MM/dd
dd-MM-yyyy
dd/MM/yyyy
```

Después de procesarlos, todos quedan representados como fechas estándar de MySQL.

### Salidas

- `movimientos_anuales_procesados`
- `estados_cuenta_anuales`
- `registros_rechazados`

Cada estado anual contiene:

- Cantidad de movimientos.
- Total de depósitos.
- Total de retiros.
- Total de compras.
- Total de pagos.
- Saldo neto anual.

## Manejo de errores

Los Steps se configuraron como tolerantes a fallos.

### Política de omisión

```text
skipLimit = 2000
```

Los errores de validación no detienen el procesamiento completo. La fila se omite y se guarda en `registros_rechazados`.

Cada rechazo incluye:

- Nombre del Job.
- Nombre del Step.
- Archivo de origen.
- Número de línea.
- Contenido original.
- Motivo del rechazo.
- Fecha del rechazo.

### Política de reintento

```text
retryLimit = 3
```

Los errores transitorios de acceso a MySQL se reintentan hasta tres veces antes de considerar que la operación falló.

## Tecnologías utilizadas

- Java 17
- Spring Boot 4.1.0
- Spring Batch
- Spring JDBC
- Maven
- MySQL 8
- IntelliJ IDEA
- MySQL Workbench
- Git
- GitHub

## Estructura del proyecto

```text
bank-batch
├── src
│   ├── main
│   │   ├── java
│   │   │   └── cl.duoc.bank_batch
│   │   │       ├── configuration
│   │   │       │   ├── ConfiguracionJobTransacciones.java
│   │   │       │   ├── ConfiguracionJobIntereses.java
│   │   │       │   └── ConfiguracionJobEstadosAnuales.java
│   │   │       ├── excepcion
│   │   │       │   └── ValidacionDatoException.java
│   │   │       ├── listener
│   │   │       ├── modelo
│   │   │       ├── procesador
│   │   │       ├── utilidad
│   │   │       │   └── ConversorFecha.java
│   │   │       └── BankBatchApplication.java
│   │   └── resources
│   │       ├── data
│   │       ├── application.properties
│   │       └── schema.sql
│   └── test
├── .gitignore
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```

## Tablas de negocio

El archivo `schema.sql` crea las siguientes tablas:

| Tabla | Propósito |
|---|---|
| `cuentas` | Mantiene el saldo actualizado de cada cuenta. |
| `transacciones_procesadas` | Guarda las transacciones diarias válidas. |
| `resumen_transacciones_diarias` | Guarda el resumen diario de transacciones. |
| `intereses_calculados` | Registra el cálculo mensual de intereses. |
| `movimientos_anuales_procesados` | Guarda los movimientos anuales normalizados. |
| `estados_cuenta_anuales` | Guarda el informe anual consolidado por cuenta. |
| `registros_rechazados` | Mantiene la auditoría de datos inválidos. |

Spring Batch también crea sus tablas internas `BATCH_*` para almacenar el estado y el historial de cada ejecución.

## Requisitos para ejecutar

- Java 17.
- MySQL 8.
- MySQL Workbench.
- IntelliJ IDEA o una terminal.
- Puerto MySQL `3306` disponible.

No es obligatorio instalar Maven globalmente porque el proyecto incluye Maven Wrapper.

## Configuración de MySQL

Crear la base de datos:

```sql
CREATE DATABASE IF NOT EXISTS bank_batch_db
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
```

Opcionalmente, crear un usuario exclusivo:

```sql
CREATE USER IF NOT EXISTS 'bank_batch_user'@'localhost'
IDENTIFIED BY 'TU_CONTRASENA';

GRANT ALL PRIVILEGES ON bank_batch_db.*
TO 'bank_batch_user'@'localhost';

FLUSH PRIVILEGES;
```

No se deben guardar contraseñas reales dentro del repositorio.

## Variables de entorno

| Variable | Descripción | Obligatoria |
|---|---|---|
| `DB_PASSWORD` | Contraseña del usuario MySQL. | Sí |
| `DB_USER` | Usuario MySQL. Por defecto `bank_batch_user`. | No |
| `DB_URL` | URL de conexión MySQL. | No |
| `BATCH_JOB_NAME` | Job que se ejecutará. | No |
| `BATCH_ARCHIVO_TRANSACCIONES` | CSV de transacciones. | No |
| `BATCH_ARCHIVO_INTERESES` | CSV de intereses. | No |
| `BATCH_ARCHIVO_ESTADOS` | CSV de movimientos anuales. | No |

## Ejecución desde IntelliJ IDEA

1. Abrir el proyecto en IntelliJ.
2. Esperar que Maven descargue las dependencias.
3. Abrir `Run → Edit Configurations`.
4. Seleccionar `BankBatchApplication`.
5. Configurar las variables de entorno.

Ejemplo para ejecutar transacciones:

```text
DB_USER=bank_batch_user;DB_PASSWORD=TU_CONTRASENA;BATCH_JOB_NAME=jobTransaccionesDiarias
```

En `Program arguments`, agregar un identificador único:

```text
run.id=1001
```

Para ejecutar otro Job, cambiar solamente `BATCH_JOB_NAME`.

### Nombres disponibles

```text
jobTransaccionesDiarias
jobInteresesMensuales
jobEstadosCuentaAnuales
```

Cada nueva ejecución debe utilizar un valor diferente para `run.id`.

## Ejecución desde PowerShell

Configurar las variables:

```powershell
$env:DB_USER="bank_batch_user"
$env:DB_PASSWORD="TU_CONTRASENA"
```

### Transacciones diarias

```powershell
$env:BATCH_JOB_NAME="jobTransaccionesDiarias"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=run.id=1001"
```

### Intereses mensuales

```powershell
$env:BATCH_JOB_NAME="jobInteresesMensuales"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=run.id=1002"
```

### Estados de cuenta anuales

```powershell
$env:BATCH_JOB_NAME="jobEstadosCuentaAnuales"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=run.id=1003"
```

## Selección de los archivos CSV

Por defecto, la aplicación utiliza los archivos de `semana_3`.

Para procesar otra semana desde IntelliJ, se puede cambiar la variable correspondiente.

Ejemplo:

```text
BATCH_ARCHIVO_INTERESES=data/semana_2/intereses.csv
```

Desde PowerShell:

```powershell
$env:BATCH_ARCHIVO_ESTADOS="data/semana_2/cuentas_anuales.csv"
```

## Compilación

Desde PowerShell, en la carpeta del proyecto:

```powershell
.\mvnw.cmd clean package -DskipTests
```

El archivo compilado se generará dentro de:

```text
target/
```

La carpeta `target` no se incluye en GitHub.

## Resultados obtenidos con semana_3

### Transacciones diarias

| Resultado | Cantidad |
|---|---:|
| Registros leídos | 1.000 |
| Registros procesados | 491 |
| Registros rechazados | 509 |
| Anomalías detectadas | 90 |
| Estado final | COMPLETED |

Distribución de rechazos:

| Motivo | Cantidad |
|---|---:|
| Tipo de transacción no permitido | 294 |
| Monto vacío | 160 |
| Fecha inexistente | 55 |

### Intereses mensuales

| Resultado | Cantidad |
|---|---:|
| Registros leídos | 1.000 |
| Registros procesados | 50 |
| Registros rechazados | 950 |
| Intereses calculados | 2.850,00 |
| Saldos finales | 420.850,00 |
| Estado final | COMPLETED |

Distribución de rechazos:

| Motivo | Cantidad |
|---|---:|
| Cuenta duplicada | 255 |
| Saldo vacío | 206 |
| Tipo no permitido | 163 |
| Edad vacía | 144 |
| Edad fuera de rango | 132 |
| Nombre desconocido | 50 |

### Estados de cuenta anuales

| Resultado | Cantidad |
|---|---:|
| Registros leídos | 1.000 |
| Registros procesados | 723 |
| Registros rechazados | 277 |
| Estados generados | 20 |
| Depósitos | 424.100,00 |
| Retiros | 329.100,00 |
| Compras | 379.800,00 |
| Pagos | 46.400,00 |
| Saldo neto anual | -331.200,00 |
| Estado final | COMPLETED |

Distribución de rechazos:

| Motivo | Cantidad |
|---|---:|
| Descripción vacía | 217 |
| Monto vacío | 48 |
| Monto igual a cero | 12 |

## Integridad e idempotencia

La solución utiliza restricciones únicas y limpieza controlada de resultados anteriores para evitar duplicaciones cuando se vuelve a procesar el mismo archivo.

Los Jobs pueden ejecutarse nuevamente utilizando un nuevo parámetro `run.id`.

Las tablas internas de Spring Batch permiten consultar:

- Job ejecutado.
- Parámetros utilizados.
- Estado final.
- Hora de inicio.
- Hora de término.
- Cantidad de lecturas.
- Cantidad de escrituras.
- Cantidad de omisiones.

## Seguridad

- La contraseña de MySQL se obtiene desde `DB_PASSWORD`.
- La contraseña no está escrita en el código.
- Los archivos locales de IntelliJ están excluidos mediante `.gitignore`.
- Los logs y archivos compilados no se suben al repositorio.
- No se incluyen credenciales reales en el `README.md`.

## Evidencias

Las evidencias de ejecución se entregan en un documento separado e incluyen:

- Ejecución en consola de los tres Jobs.
- Resultados generados en MySQL.
- Registros rechazados.
- Procesamiento de los archivos de 1.000 filas.
- Estados finales `COMPLETED`.
- Códigos de salida `0`.

## Conclusión

La solución moderniza los tres procesos legacy solicitados utilizando Spring Batch y MySQL.

La implementación organiza cada proceso como un Job independiente, aplica transformaciones y validaciones mediante `ItemProcessor`, utiliza procesamiento por chunks, registra los errores sin detener completamente las ejecuciones y persiste los resultados necesarios para auditoría.

Los resultados obtenidos demuestran que el sistema puede procesar archivos con datos válidos, errores controlados y volúmenes de 1.000 registros manteniendo la consistencia de la información.