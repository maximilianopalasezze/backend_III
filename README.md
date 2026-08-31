Banco XYZ: optimización y resiliencia de procesos batch

Proyecto académico de Desarrollo Backend III orientado a modernizar tres procesos legacy del Banco XYZ mediante Java 17, Spring Boot 4.1.0, Spring Batch y MySQL 8.

La solución procesa archivos CSV oficiales, valida y transforma los registros con ItemProcessor, persiste los resultados válidos, registra los rechazos y mantiene métricas de rendimiento. La versión de la Semana 3 incorpora procesamiento multihilo configurable, comparación de diferentes tamaños de chunk, políticas personalizadas de omisión y reintento, control de reinicios y evidencia de reejecución sin duplicados.

Objetivo

Diseñar una arquitectura Spring Batch capaz de ejecutar los Jobs requeridos de forma estructurada, escalable y tolerante a fallos. Además de obtener la salida esperada en MySQL, se busca comparar configuraciones de procesamiento para seleccionar una alternativa que mejore el tiempo de ejecución sin afectar la integridad de los datos.

Tecnologías utilizadas

Java 17.

Spring Boot 4.1.0.

Spring Batch.

Spring JDBC.

MySQL 8.

Maven Wrapper.

IntelliJ IDEA.

MySQL Workbench.

Arquitectura general

Cada proceso se implementa como un Job compuesto por un Step orientado a chunks:

Archivo CSV oficial
        |
SynchronizedItemStreamReader
        |
ItemProcessor: validación y transformación
        |
JdbcBatchItemWriter
        |
MySQL

Los Steps comparten un ThreadPoolTaskExecutor, una política personalizada de omisión, una política de reintento para fallos transitorios y listeners para rechazos, reintentos, uso de hilos, métricas y resúmenes finales.

Jobs y Steps implementados

Job

Step

Archivo oficial

Resultado principal

jobTransaccionesDiarias

stepProcesarTransacciones

data/semana_3/transacciones.csv

Valida transacciones, detecta anomalías y persiste los registros aceptados.

jobInteresesMensuales

stepProcesarIntereses

data/semana_3/intereses.csv

Calcula intereses mensuales y saldos finales.

jobEstadosCuentaAnuales

stepProcesarMovimientosAnuales

data/semana_3/cuentas_anuales.csv

Procesa movimientos y genera estados de cuenta anuales mediante una agregación posterior al procesamiento paralelo.

Archivos oficiales utilizados

Los tres archivos de entrada corresponden a los datos oficiales de la Semana 3 publicados en el repositorio bank_legacy_data. Cada archivo contiene 1.000 registros, además de su encabezado.

Archivo

Registros leídos

data/semana_3/transacciones.csv

1.000

data/semana_3/intereses.csv

1.000

data/semana_3/cuentas_anuales.csv

1.000

Los archivos se encuentran incluidos en src/main/resources/data/semana_3 y no fueron modificados para realizar las ejecuciones documentadas.

Lectura y procesamiento concurrente

Los lectores FlatFileItemReader están envueltos en SynchronizedItemStreamReader, lo que protege el avance del archivo cuando varios trabajadores solicitan elementos de manera concurrente.

El escalamiento se realiza con un ThreadPoolTaskExecutor configurable. El pool utiliza el mismo valor para corePoolSize y maxPoolSize, posee una cola limitada y emplea el prefijo batch-worker-, permitiendo verificar en los logs qué trabajadores participaron.

Los conjuntos empleados para detectar duplicados se construyen con ConcurrentHashMap.newKeySet(), evitando condiciones de carrera entre los hilos. En el proceso anual, la consolidación por cuenta se ejecuta una vez finalizado correctamente el Step paralelo, de modo que no se actualizan acumuladores compartidos durante el procesamiento concurrente.

Transformaciones y validaciones

Los ItemProcessor validan los campos antes de construir los objetos que serán persistidos. Entre las reglas aplicadas se encuentran:

Identificadores obligatorios y con formato válido.

Fechas obligatorias y convertibles al formato esperado.

Montos presentes y numéricos.

Tipos de transacción, cuenta o movimiento permitidos.

Edad obligatoria y dentro del rango establecido.

Nombre del titular obligatorio.

Saldo presente y no negativo.

Descripción obligatoria en los movimientos anuales.

Detección de registros duplicados cuando corresponde.

Los registros que no cumplen estas reglas se omiten mediante la política configurada y se almacenan en registros_rechazados, incluyendo Job, Step, archivo, línea, contenido original, motivo y fecha del rechazo.

Tolerancia a fallos

La solución diferencia los errores de calidad de datos de los errores técnicos:

PoliticaOmisionDatosInvalidos permite omitir hasta 2.000 errores conocidos de validación, parseo o integridad.

Las excepciones técnicas desconocidas no se omiten y detienen el Step para evitar ocultar fallos graves.

TransientDataAccessException se reintenta hasta tres veces.

Entre reintentos se aplica una pausa de 250 ms para no saturar MySQL.

Las escrituras se confirman por chunk; si ocurre un error, se revierte únicamente la transacción del bloque actual.

ListenerReintentosBatch registra cada intento para facilitar el diagnóstico.

Reinicio y reejecución

Los Steps se configuran con startLimit=3 y allowStartIfComplete=false. Los Jobs utilizan RunIdIncrementer y Spring Batch conserva el ExecutionContext y los metadatos de ejecución.

Si una instancia termina en FAILED, puede reiniciarse conservando los mismos parámetros para continuar desde el último checkpoint confirmado.

Si se necesita procesar nuevamente el archivo completo, se utiliza un run.id nuevo.

La preparación de las tablas propias se realiza solamente al comenzar una instancia nueva; durante un reinicio se conservan los registros confirmados.

Las restricciones únicas y las escrituras idempotentes evitan que la salida quede duplicada.

Como prueba, el Job anual fue ejecutado nuevamente con un run.id diferente. Después de la reejecución se mantuvieron 723 movimientos únicos y 20 estados de cuenta únicos, ambos verificados en MySQL como SIN DUPLICADOS.

Configuración de escalamiento

Los parámetros quedan externalizados en application.properties y pueden modificarse mediante variables de entorno:

batch.escalamiento.hilos=${BATCH_HILOS:3}
batch.escalamiento.chunk=${BATCH_CHUNK:50}
batch.escalamiento.capacidad-cola=${BATCH_CAPACIDAD_COLA:50}
batch.rendimiento.id-prueba=${BATCH_ID_PRUEBA:configuracion-optima-h3-c50}

batch.tolerancia.limite-omisiones=2000
batch.tolerancia.max-reintentos=3
batch.tolerancia.pausa-reintento-ms=250
batch.reinicio.max-ejecuciones-step=3

El pool Hikari mantiene un máximo de seis conexiones: tres destinadas al trabajo paralelo y conexiones adicionales para metadatos, listeners y consultas de resumen.

Comparación de rendimiento

Las pruebas se realizaron sobre el archivo oficial data/semana_3/transacciones.csv. Las métricas se almacenaron en metricas_rendimiento_batch, registrando identificador de prueba, hilos, chunk, duración, velocidad, lecturas, escrituras, omisiones, commits, rollbacks y estado final.

Configuración

Hilos

Chunk

Ejecuciones

Duración promedio

Velocidad promedio

transacciones_h1_c5

1

5

2

3.827,50 ms

261,53 registros/s

transacciones_h3_c5

3

5

1

3.117,00 ms

320,82 registros/s

transacciones_h3_c25

3

25

1

1.826,00 ms

547,52 registros/s

transacciones_h3_c50

3

50

1

1.625,00 ms

615,15 registros/s

La configuración H3/C50 fue seleccionada como la alternativa óptima observada. Frente al promedio de H1/C5, redujo la duración aproximadamente un 57,5 % y aumentó la velocidad promedio alrededor de un 135,2 %. También disminuyó la cantidad de commits de 200 a 20, sin cambiar las 1.000 lecturas, las 491 escrituras, las 509 omisiones ni el estado COMPLETED.

Los tiempos dependen del equipo, la carga del sistema y el estado de MySQL. Por ese motivo, la conclusión se basa en las ejecuciones registradas en el entorno de prueba y no se presenta como una medida universal.

Resultados con los archivos oficiales

Las ejecuciones finales utilizaron tres hilos y chunks de 50 registros.

Job

Leídos

Escritos

Rechazados

Commits

Rollbacks

Estado

Transacciones diarias

1.000

491

509

20

0

COMPLETED

Intereses mensuales

1.000

50

950

20

0

COMPLETED

Estados de cuenta anuales

1.000

723

277

20

0

COMPLETED

Resultados adicionales:

Transacciones: 90 anomalías detectadas.

Intereses: total de intereses calculados de 2.850,00 y total de saldos finales de 420.850,00.

Estados anuales: 20 estados generados, depósitos por 424.100,00, retiros por 329.100,00, compras por 379.800,00, pagos por 46.400,00 y saldo neto anual de -331.200,00.

Las cantidades aceptadas y rechazadas suman 1.000 registros en cada Job, demostrando que todos los datos de entrada fueron leídos y clasificados.

Estructura principal

src/main/java/cl/duoc/bank_batch
├── configuration   Jobs, Steps, hilos y políticas compartidas
├── excepcion       Excepciones de validación
├── listener        Rechazos, reintentos, métricas y resúmenes
├── modelo          Modelos de entrada y salida
├── politica        Política personalizada de omisión
├── procesador      Validaciones y transformaciones
├── servicio        Control de reinicios de JobInstance
└── utilidad        Conversión y validación de fechas

src/main/resources
├── data
│   └── semana_3    Archivos CSV oficiales utilizados
├── application.properties
└── schema.sql

Configuración de MySQL

Crear la base de datos y el usuario desde MySQL Workbench:

CREATE DATABASE IF NOT EXISTS bank_batch_db
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'bank_batch_user'@'localhost'
IDENTIFIED BY 'CAMBIAR_ESTA_PASSWORD';

GRANT ALL PRIVILEGES ON bank_batch_db.*
TO 'bank_batch_user'@'localhost';

FLUSH PRIVILEGES;

La contraseña no se almacena en Git. Debe proporcionarse mediante la variable DB_PASSWORD.

Compilación

Desde PowerShell, dentro de la carpeta que contiene pom.xml:

.\mvnw.cmd clean package -DskipTests

La compilación genera el archivo ejecutable dentro de target.

Ejecución desde IntelliJ

En Run > Edit Configurations, seleccionar BankBatchApplication, incorporar las variables correspondientes y utilizar un run.id nuevo como argumento del programa.

Transacciones diarias

DB_USER=bank_batch_user;DB_PASSWORD=TU_PASSWORD;BATCH_JOB_NAME=jobTransaccionesDiarias;BATCH_ARCHIVO_TRANSACCIONES=data/semana_3/transacciones.csv;BATCH_HILOS=3;BATCH_CHUNK=50;BATCH_CAPACIDAD_COLA=50;BATCH_ID_PRUEBA=transacciones_h3_c50

Intereses mensuales

DB_USER=bank_batch_user;DB_PASSWORD=TU_PASSWORD;BATCH_JOB_NAME=jobInteresesMensuales;BATCH_ARCHIVO_INTERESES=data/semana_3/intereses.csv;BATCH_HILOS=3;BATCH_CHUNK=50;BATCH_CAPACIDAD_COLA=50;BATCH_ID_PRUEBA=intereses_h3_c50

Estados de cuenta anuales

DB_USER=bank_batch_user;DB_PASSWORD=TU_PASSWORD;BATCH_JOB_NAME=jobEstadosCuentaAnuales;BATCH_ARCHIVO_ESTADOS=data/semana_3/cuentas_anuales.csv;BATCH_HILOS=3;BATCH_CHUNK=50;BATCH_CAPACIDAD_COLA=50;BATCH_ID_PRUEBA=estados_h3_c50

Argumento de programa de ejemplo:

run.id=401

El valor debe cambiarse para iniciar una ejecución completa nueva. Para reiniciar una instancia fallida se conservan exactamente los mismos parámetros.

Consultas de verificación

Comparación de configuraciones

SELECT
    id_prueba,
    cantidad_hilos,
    tamano_chunk,
    COUNT(*) AS ejecuciones,
    ROUND(AVG(duracion_ms), 2) AS duracion_promedio_ms,
    ROUND(AVG(registros_por_segundo), 2) AS velocidad_promedio
FROM metricas_rendimiento_batch
WHERE nombre_job = 'jobTransaccionesDiarias'
  AND estado = 'COMPLETED'
GROUP BY id_prueba, cantidad_hilos, tamano_chunk
ORDER BY duracion_promedio_ms ASC;

Última ejecución de cada Job

WITH ultimas_ejecuciones AS (
    SELECT
        ji.JOB_NAME,
        se.STEP_NAME,
        se.READ_COUNT,
        se.WRITE_COUNT,
        se.READ_SKIP_COUNT + se.PROCESS_SKIP_COUNT
            + se.WRITE_SKIP_COUNT AS OMISIONES,
        se.COMMIT_COUNT,
        se.ROLLBACK_COUNT,
        se.STATUS,
        ROW_NUMBER() OVER (
            PARTITION BY ji.JOB_NAME
            ORDER BY se.END_TIME DESC
        ) AS numero
    FROM BATCH_JOB_INSTANCE ji
    INNER JOIN BATCH_JOB_EXECUTION je
        ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
    INNER JOIN BATCH_STEP_EXECUTION se
        ON je.JOB_EXECUTION_ID = se.JOB_EXECUTION_ID
)
SELECT
    JOB_NAME,
    STEP_NAME,
    READ_COUNT,
    WRITE_COUNT,
    OMISIONES,
    COMMIT_COUNT,
    ROLLBACK_COUNT,
    STATUS
FROM ultimas_ejecuciones
WHERE numero = 1
ORDER BY JOB_NAME;

Verificación de reejecución sin duplicados

SELECT
    'Movimientos anuales' AS resultado,
    COUNT(*) AS total_registros,
    COUNT(DISTINCT cuenta_id, fecha, tipo_movimiento,
          monto, archivo_origen) AS registros_unicos
FROM movimientos_anuales_procesados
WHERE archivo_origen = 'data/semana_3/cuentas_anuales.csv'

UNION ALL

SELECT
    'Estados de cuenta',
    COUNT(*),
    COUNT(DISTINCT cuenta_id, anio, archivo_origen)
FROM estados_cuenta_anuales
WHERE archivo_origen = 'data/semana_3/cuentas_anuales.csv';

Logs y métricas

Los mensajes se muestran en la consola y se guardan en logs/bank-batch.log. El archivo rota al alcanzar 10 MB y conserva cinco históricos.

Los listeners registran:

Nombre del Job y del Step.

Identificador de prueba.

Hilos y tamaño de chunk.

Duración y registros por segundo.

Lecturas, escrituras y omisiones.

Commits y rollbacks.

Hilos que participaron.

Reintentos y rechazos.

Estado final.

Evidencias generadas

La documentación de la Semana 3 incluye evidencias de:

Compilación exitosa del proyecto.

Ejecución de los tres Jobs con los archivos oficiales.

Comparación de configuraciones H1/C5, H3/C5, H3/C25 y H3/C50.

Persistencia y clasificación de los registros aceptados y rechazados.

Metadatos COMPLETED, commits y ausencia de rollbacks.

Configuración externalizada de escalamiento y tolerancia a fallos.

Pool real de hilos y políticas personalizadas.

Reejecución controlada sin duplicación de movimientos ni estados de cuenta.

Repositorio

Código fuente del proyecto:

https://github.com/maximilianopalasezze/backend_III.git

Repositorio de los archivos oficiales:

https://github.com/KariVillagran/bank_legacy_data.git

Entrega

La entrega debe incluir en una misma carpeta comprimida:

Código fuente completo.

README.md actualizado.

Informe con las evidencias de ejecución.

La carpeta comprimida debe respetar la nomenclatura indicada en la plataforma de la asignatura.