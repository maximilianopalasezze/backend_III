# Migración de procesos batch del Banco XYZ

Proyecto académico de Desarrollo Backend III que moderniza tres procesos legacy del Banco XYZ mediante Java 17, Spring Boot 4.1, Spring Batch 6 y MySQL 8.

## Objetivo

El sistema lee archivos CSV, valida y transforma los registros con `ItemProcessor`, persiste la información válida en MySQL y registra los datos rechazados sin interrumpir innecesariamente el procesamiento.

La versión de la Semana 2 incorpora procesamiento escalable con tres hilos, chunks de cinco registros, políticas de omisión y reintento, reinicio desde checkpoints y logs de rendimiento.

## Jobs implementados

| Job | Archivo de entrada | Resultado principal |
|---|---|---|
| `jobTransaccionesDiarias` | `transacciones.csv` | Detecta anomalías y genera un resumen diario. |
| `jobInteresesMensuales` | `intereses.csv` | Calcula intereses y actualiza saldos de cuentas. |
| `jobEstadosCuentaAnuales` | `cuentas_anuales.csv` | Consolida movimientos y genera estados anuales por cuenta. |

## Requisitos de la Semana 2

| Requisito | Implementación |
|---|---|
| Chunks de tamaño 5 | `batch.escalamiento.chunk=5`. Cada commit confirma como máximo cinco elementos. |
| Tres hilos paralelos | `ThreadPoolTaskExecutor` fijo con `batch.escalamiento.hilos=3`. |
| Lectura segura en paralelo | Cada `FlatFileItemReader` se envuelve en `SynchronizedItemStreamReader`. |
| Procesadores thread-safe | Los conjuntos de duplicados usan `ConcurrentHashMap.newKeySet()`. |
| Omisiones | `PoliticaOmisionDatosInvalidos` omite únicamente errores de validación, parseo o integridad hasta el límite configurado. |
| Reintentos | Los errores transitorios de acceso a datos se reintentan tres veces, esperando 250 ms entre intentos. |
| Reinicio | Spring Batch conserva el `ExecutionContext`; al reiniciar una instancia fallida no se eliminan los datos ya confirmados. |
| Re-ejecución controlada | `startLimit=3`, `allowStartIfComplete=false` y `RunIdIncrementer` en los tres Jobs. |
| Optimización | Pool Hikari de seis conexiones y opciones de MySQL para escrituras batch y caché de sentencias preparadas. |
| Logs | Se registra hilo, duración, velocidad, lecturas, escrituras, omisiones, commits, rollbacks y estado final. |

## Estructura principal

```text
src/main/java/cl/duoc/bank_batch
├── configuration   Configuración de Jobs, Steps, hilos y políticas
├── excepcion       Excepciones de validación
├── listener        Rechazos, reintentos, rendimiento y resúmenes
├── modelo          Modelos de entrada y salida
├── politica        Política personalizada de omisión
├── procesador      Validaciones y transformaciones
├── servicio        Control de reinicios de JobInstance
└── utilidad        Conversión de fechas

src/main/resources
├── data             CSV de las semanas 1, 2 y 3
├── application.properties
└── schema.sql
```

## Configuración de MySQL

Crear la base de datos y el usuario desde MySQL Workbench:

```sql
CREATE DATABASE IF NOT EXISTS bank_batch_db
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'bank_batch_user'@'localhost'
IDENTIFIED BY 'CAMBIAR_ESTA_PASSWORD';

GRANT ALL PRIVILEGES ON bank_batch_db.*
TO 'bank_batch_user'@'localhost';

FLUSH PRIVILEGES;
```

La contraseña no se guarda en Git. En IntelliJ se deben configurar estas variables de entorno en **Run > Edit Configurations**:

```text
DB_USER=bank_batch_user;DB_PASSWORD=TU_PASSWORD
```

## Selección del Job y archivo

Agregar también las variables correspondientes al Job que se desea ejecutar.

### Transacciones diarias

```text
BATCH_JOB_NAME=jobTransaccionesDiarias;BATCH_ARCHIVO_TRANSACCIONES=data/semana_3/transacciones.csv
```

### Intereses mensuales

```text
BATCH_JOB_NAME=jobInteresesMensuales;BATCH_ARCHIVO_INTERESES=data/semana_3/intereses.csv
```

### Estados de cuenta anuales

```text
BATCH_JOB_NAME=jobEstadosCuentaAnuales;BATCH_ARCHIVO_ESTADOS=data/semana_3/cuentas_anuales.csv
```

Usar un `run.id` nuevo como argumento de programa para realizar una ejecución completa nueva:

```text
run.id=101
```

## Compilación y ejecución

Desde PowerShell, dentro de la carpeta que contiene `pom.xml`:

```powershell
./mvnw.cmd clean package
./mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="run.id=101"
```

También puede ejecutarse `BankBatchApplication` directamente desde IntelliJ después de configurar las variables de entorno y los argumentos.

## Reinicio y re-ejecución

- Para reiniciar una ejecución que terminó en `FAILED`, se deben conservar exactamente el mismo Job y los mismos parámetros, incluido `run.id`. Spring Batch retomará el Step desde el último checkpoint confirmado.
- Para ejecutar nuevamente todo el archivo desde el principio, se debe usar un `run.id` diferente.
- `allowStartIfComplete=false` evita repetir un Step ya completado dentro de la misma instancia.
- `startLimit=3` limita a tres los inicios del Step para una misma instancia.
- La limpieza de las tablas propias se realiza solamente al comenzar una instancia nueva. En un reinicio se conservan los datos confirmados.

## Tolerancia a fallos

- Los errores de calidad de datos se omiten y se almacenan en `registros_rechazados`.
- La política no omite excepciones técnicas desconocidas; estas detienen el Step para no ocultar fallos graves.
- Los errores transitorios de base de datos se reintentan hasta tres veces.
- Entre reintentos existe una pausa de 250 ms para evitar saturar MySQL.
- Las escrituras usan transacciones por chunk, por lo que un fallo revierte únicamente el bloque actual.

## Parámetros de rendimiento

Los valores exigidos por la actividad están en `application.properties`:

```properties
batch.escalamiento.hilos=3
batch.escalamiento.chunk=5
batch.escalamiento.capacidad-cola=50
batch.tolerancia.limite-omisiones=2000
batch.tolerancia.max-reintentos=3
batch.tolerancia.pausa-reintento-ms=250
```

El pool Hikari tiene seis conexiones: tres para los trabajadores y conexiones adicionales para metadatos, listeners y consultas de resumen. El número máximo de hilos permanece fijo en tres para cumplir la pauta y evitar consumo de recursos sin control.

## Logs y evidencia de rendimiento

Los mensajes aparecen en la consola y también en `logs/bank-batch.log`. El archivo rota al alcanzar 10 MB y conserva cinco históricos.

Para la evidencia de la Semana 2 conviene capturar:

1. Las propiedades `batch.escalamiento.hilos=3` y `batch.escalamiento.chunk=5`.
2. La consola mostrando `batch-worker-1`, `batch-worker-2` y `batch-worker-3`.
3. El bloque `RENDIMIENTO DEL STEP` con duración, velocidad, commits, rollbacks y omisiones.
4. Los mensajes de registros rechazados y la consulta a `registros_rechazados`.
5. El resumen final y el estado `COMPLETED` de cada Job.
6. Las tablas de resultados en MySQL.

Consultas útiles:

```sql
USE bank_batch_db;

SELECT JOB_NAME, STATUS, START_TIME, END_TIME
FROM BATCH_JOB_INSTANCE i
JOIN BATCH_JOB_EXECUTION e
  ON e.JOB_INSTANCE_ID = i.JOB_INSTANCE_ID
ORDER BY e.JOB_EXECUTION_ID DESC;

SELECT STEP_NAME, STATUS, READ_COUNT, WRITE_COUNT,
       COMMIT_COUNT, ROLLBACK_COUNT,
       READ_SKIP_COUNT, PROCESS_SKIP_COUNT, WRITE_SKIP_COUNT
FROM BATCH_STEP_EXECUTION
ORDER BY STEP_EXECUTION_ID DESC;

SELECT nombre_job, motivo_rechazo, COUNT(*) AS cantidad
FROM registros_rechazados
GROUP BY nombre_job, motivo_rechazo
ORDER BY cantidad DESC;
```

## Entrega

La entrega debe incluir el código fuente, este `README.md` y las evidencias de ejecución. Todo debe quedar en una misma carpeta comprimida con la nomenclatura indicada por el profesor, por ejemplo `Exp1_S2_Grupo4.zip`.
