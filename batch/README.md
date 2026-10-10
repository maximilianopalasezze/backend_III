# Banco XYZ — Spring Batch integrado para Semana 9

Componente Maven independiente recuperado de `main`, commit `354050a1ed5380a5f579596788b27c947cf488f9`. Conserva Java 17, Spring Boot 4.1.0 y el código de los tres procesos batch. Se compila desde esta carpeta; no forma parte del reactor Maven de `../bff`.

## Integración y datos de entrada

La conexión predeterminada utiliza **bank_xyz_semana5_db**, la misma base de la solución de semana 8. Se conservan `DB_URL`, `DB_USER` y `DB_PASSWORD` para configurar el entorno. El usuario predeterminado es `bank_batch_user`, igual al definido en Docker Compose.

Los archivos de esta evaluación provienen de [fin_legacy_data](https://github.com/KariVillagran/fin_legacy_data), directorio `data/semana_3`, y se incluyen con sus nombres originales en `src/main/resources/data/semana_9`. Cada archivo tiene 1.000 registros más encabezado. Su contenido es distinto al de los archivos recuperados de `main`; por ello, los resultados históricos de semana 3 no son evidencia de esta evaluación.

| Job | Archivo predeterminado | Función |
| --- | --- | --- |
| `jobTransaccionesDiarias` | `data/semana_9/movimientos_financieros_diarios.csv` | Validación, anomalías y resumen diario |
| `jobInteresesMensuales` | `data/semana_9/intereses_trimestrales.csv` | Cálculo mensual según las tasas configuradas |
| `jobEstadosCuentaAnuales` | `data/semana_9/estados_financieros_anuales.csv` | Procesamiento y consolidación anual |

El nombre oficial `intereses_trimestrales.csv` se conserva aunque el caso exige un cálculo **mensual**. El CSV contiene cuentas, nombres, saldos, edades y tipos; las tasas y el período del cálculo se configuran en `application.properties`. Estos supuestos deberán justificarse en el informe final.

Los datos de semanas 1–3 recuperados de `main` se mantienen para comparación. Las variables `BATCH_ARCHIVO_TRANSACCIONES`, `BATCH_ARCHIVO_INTERESES` y `BATCH_ARCHIVO_ESTADOS` permiten elegirlos.

## Compilación

Desde PowerShell, en la raíz del repositorio:

```powershell
cd batch
.\mvnw.cmd -B -DskipTests package
```

Se genera `target/bank-batch-0.0.1-SNAPSHOT.jar`. Este comando compila y empaqueta; no verifica los tests. El test de contexto recuperado necesita acceso a MySQL y las variables de conexión.

En Linux:

```bash
cd batch
bash mvnw -B -DskipTests package
```

## Ejecución deliberada de un Job

Los Jobs están desactivados por defecto con `BATCH_JOB_ENABLED=false`. Esto evita ejecutar una carga al iniciar el componente sin haber seleccionado la prueba. El arranque todavía puede inicializar tablas mediante `spring.sql.init` y `spring.batch.jdbc`.

Con MySQL disponible en `localhost:3306`, base `bank_xyz_semana5_db`, configurar en PowerShell:

```powershell
$env:DB_USER = "bank_batch_user"
$claveMySQL = Read-Host "Contrasena de MySQL" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $claveMySQL).Password
$env:BATCH_JOB_ENABLED = "true"
$env:BATCH_JOB_NAME = "jobTransaccionesDiarias"
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\bank-batch-0.0.1-SNAPSHOT.jar
```

Para los otros procesos, seleccionar el Job:

```powershell
$env:BATCH_JOB_NAME = "jobInteresesMensuales"
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\bank-batch-0.0.1-SNAPSHOT.jar

$env:BATCH_JOB_NAME = "jobEstadosCuentaAnuales"
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\bank-batch-0.0.1-SNAPSHOT.jar
```

El lanzador predeterminado de Spring Boot 4.1 / Spring Batch 6 usa el `RunIdIncrementer` de estos Jobs para crear una instancia nueva. Las ejecuciones locales mostraron que ignora un `run.id` adicional pasado por la linea de comandos. Por ello, repetir este comando **no demuestra reinicio** de una instancia fallida. El modo recuperable descrito más abajo utiliza los parámetros originales de un lote y evita el incrementador.

**Efectos sobre la base compartida:** el Job de intereses inserta o actualiza `cuentas`, incluyendo el saldo, a partir del CSV. Al comenzar una instancia nueva, los listeners limpian resultados y rechazos correspondientes al archivo/período procesado; durante un reinicio conservan los datos confirmados. Antes de las pruebas integradas, guardar una copia de los datos de prueba y evitar retiros simultáneos sobre las cuentas que se recargan.

No se ejecutó ningún Job sobre la base de EC2 durante esta integración.

## Funcionalidad recuperada

- Tres Jobs con lectores CSV, procesadores y escritores JDBC.
- Lectores sincronizados y pool configurable de trabajadores.
- Políticas de omisión, reintentos de fallos transitorios y límites de ejecución.
- Listeners de rechazos, resúmenes, hilos y métricas.
- Tablas de resultados y restricciones únicas.

## Estado de verificación

Paso 1: integración del componente y configuración de entradas. Se verificaron la estructura, los tres Jobs, los encabezados y cantidades de registros, y la coincidencia de los esquemas de tablas de negocio con el SQL de semana 8.

La compilacion inicial y los tres Jobs se verificaron en el PC con MySQL. Se ejecutaron con 1 y 3 hilos, chunk 50, y todos terminaron en `COMPLETED`. Las consultas por transaccion/cuenta confirmaron cero faltantes y cero diferencias en los datos comparados (excluidos IDs internos y fechas de procesamiento).

| Job | Leidos | Escritos | Rechazados | Step 1 hilo (ms) | Step 3 hilos (ms) |
| --- | ---: | ---: | ---: | ---: | ---: |
| Transacciones | 1000 | 480 | 520 | 2899 | 1921 |
| Intereses | 1000 | 50 | 950 | 5154 | 2799 |
| Estados anuales | 1000 | 684 | 316 | 2040 | 1216 |

Intereses: periodo `2024-01`, total de intereses `2525.00`, saldos finales `413525.00`. Estados anuales: 20 cuentas, anio 2024, depositos `354400.00`, retiros `268700.00`, compras `345500.00`, pagos `67800.00`, saldo neto de movimientos `-327600.00`. Los 950 rechazos de intereses incluyen 270 cuentas duplicadas y 680 errores de validacion; no representan 950 cuentas distintas.

Estos tiempos corresponden a **una medición por configuración**, no a promedios. La prueba de mayor volumen se documenta a continuación. Quedan pendientes la evidencia de recuperación automática en MySQL, la consistencia con BFF concurrentes y la equivalencia con reglas legacy documentadas. La igualdad entre hilos no sustituye la comparación con el sistema legacy.

## Prueba reproducible de mayor volumen

Los tres lectores aceptan la ruta habitual dentro del classpath o una URI `file:` de un CSV externo. El generador de transacciones conserva fecha, monto y tipo de cada fila oficial, incluidos sus errores; transforma solo el ID: `ID original + 1000 * indice de copia`. No agrega un CSV grande al repositorio.

Primero compilar y ejecutar los tests de recursos (sin levantar el contexto ni ejecutar Jobs):

```powershell
.\mvnw.cmd -B -Dtest=RecursoEntradaBatchTests package
```

Generar 100.000 filas desde la carpeta `batch`:

```powershell
.\scripts\generar-volumen-transacciones.ps1 -Repeticiones 100
$rutaVolumen = (Resolve-Path '.\.local\datos-prueba\transacciones_100000.csv').Path
$env:BATCH_ARCHIVO_TRANSACCIONES = [System.Uri]::new($rutaVolumen).AbsoluteUri
$env:BATCH_JOB_NAME = 'jobTransaccionesDiarias'
$env:BATCH_JOB_ENABLED = 'true'
$env:BATCH_HILOS = '3'
$env:BATCH_CHUNK = '50'
$env:BATCH_LIMITE_OMISIONES = '60000'
$env:BATCH_ID_PRUEBA = 's9_transacciones_100k_h3_c50'
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\bank-batch-0.0.1-SNAPSHOT.jar
```

El limite de omisiones se eleva explicitamente para este ensayo: al repetir el archivo 100 veces se esperan 48.000 transacciones aceptadas, 52.000 rechazos y 8.800 anomalias entre las aceptadas. El manifiesto JSON junto al CSV registra la transformacion y los SHA-256 de entrada y salida. `.local/` ya esta excluido de Git. El archivo/perfil de esta prueba es diferente del oficial, por lo que la limpieza de resultados queda limitada a su URI. No usar operaciones simultaneas sobre el mismo archivo durante la prueba.

Comparar 1 y 3 hilos requiere conservar el mismo CSV, cambiar solo `BATCH_HILOS` e `BATCH_ID_PRUEBA` y verificar los datos persistidos. Las advertencias de las 52.000 filas rechazadas forman parte del tiempo medido; mantener la misma configuracion de logging al comparar.

Para volver a las pruebas oficiales:

```powershell
$env:BATCH_ARCHIVO_TRANSACCIONES = 'data/semana_9/movimientos_financieros_diarios.csv'
Remove-Item Env:BATCH_LIMITE_OMISIONES -ErrorAction SilentlyContinue
```

La compilación de esta ampliación y los tres tests de recursos se verificaron en PC. El generador produjo 100.000 registros (100.001 líneas con encabezado). Ambas ejecuciones terminaron en `COMPLETED`, con 100.000 lecturas, 48.000 escrituras, 52.000 rechazos, 8.800 anomalías y cero rollbacks. La comparación de las 48.000 transacciones persistidas confirmó cero faltantes y cero diferencias en los campos de negocio revisados.

| Hilos | Chunk | Duración del Step (ms) | Registros/segundo |
| ---: | ---: | ---: | ---: |
| 3 | 50 | 163125 | 613.02 |
| 1 | 50 | 271941 | 367.73 |

En este ensayo, el tiempo disminuyó un 40,01 %. Es una ejecución por configuración, con los mismos datos y logging; no es un promedio ni garantiza esa mejora para otras cargas.

## Recuperación automática de un lote

`BATCH_REINICIO_AUTOMATICO=true` activa un lanzador específico para cualquiera de los tres Jobs. Usar `BATCH_JOB_ENABLED=false` para evitar lanzar también una instancia mediante el runner predeterminado. `BATCH_LOTE` identifica la instancia; se conserva el mismo identificador durante sus intentos. El archivo, su SHA-256, el chunk, los hilos y los parámetros de negocio se comprueban antes de retomar un lote existente. Un lote ya `COMPLETED` no se reprocesa.

El servicio usa `JobOperator.run` con parámetros estables porque, en Spring Batch 6, `start` ignora parámetros explícitos si el Job tiene `RunIdIncrementer`. Reinicia ejecuciones `FAILED` o `STOPPED` y respeta el máximo de tres ejecuciones de la misma instancia. No modifica estados `STARTED` ni presupone que otro proceso haya terminado.

`BATCH_FALLAR_DESPUES_COMMITS=N` provoca una falla técnica **una sola vez en el primer intento**, antes de leer el chunk siguiente a N commits. Con `0` está desactivada. La prueba termina el Step con `FAILED`; el lanzador reinicia la misma instancia y el lector continúa desde su checkpoint. Esto simula una interrupción del procesamiento, no mata la JVM. Los procesadores de intereses y movimientos anuales también guardan y restauran sus conjuntos de datos procesados en el `ExecutionContext`, para conservar la detección de duplicados y limpiar ese estado al comenzar una instancia nueva.

Compilar y probar sin conectarse a la base bancaria:

```powershell
.\mvnw.cmd -B "-Dtest=RecursoEntradaBatchTests,RecuperacionBatchTests,ServicioControlReinicioTests" package
```

La verificación de desarrollo compiló 35 fuentes Java con Java 17 y aprobó 10 pruebas usando Spring Batch 6.0.4. La compilación Maven y la evidencia de recuperación en el PC con MySQL se verifican como siguiente paso.

`RecuperacionBatchTests` usa una base H2 en memoria separada del banco, las entradas oficiales y los tres Jobs reales. Compara resultados y rechazos con una ejecución completa, verifica `FAILED → COMPLETED` en la misma instancia, 100 lecturas antes de la falla más 900 tras el reinicio, el límite de intentos y la protección contra cambios de archivo. El test de control de reinicio comprueba IDs numéricos mayores que 127 para evitar confundir objetos `Long` diferentes con ejecuciones diferentes.

Prueba local con MySQL, desde `batch`, manteniendo las credenciales de conexión:

```powershell
$env:BATCH_JOB_ENABLED = 'false'
$env:BATCH_REINICIO_AUTOMATICO = 'true'
$env:BATCH_JOB_NAME = 'jobTransaccionesDiarias'
$env:BATCH_ARCHIVO_TRANSACCIONES = 'data/semana_9/movimientos_financieros_diarios.csv'
$env:BATCH_HILOS = '1'
$env:BATCH_CHUNK = '50'
$env:BATCH_LOTE = 's9-recuperacion-transacciones-01'
$env:BATCH_ID_PRUEBA = 's9_recuperacion_transacciones'
$env:BATCH_FALLAR_DESPUES_COMMITS = '2'
Remove-Item Env:BATCH_LIMITE_OMISIONES -ErrorAction SilentlyContinue
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\bank-batch-0.0.1-SNAPSHOT.jar
```

Esperar el resumen final `COMPLETED`, con 480 transacciones persistidas, 520 rechazos y 88 anomalías. Los logs muestran la ejecución fallida, el reinicio automático y el ID de la instancia compartida. En metadatos se esperan dos ejecuciones del mismo lote: la primera `FAILED` con 100 lecturas y la segunda `COMPLETED` con 900. Un nuevo ensayo independiente requiere un nuevo `BATCH_LOTE`.

Para intereses y estados, seleccionar `jobInteresesMensuales` o `jobEstadosCuentaAnuales` y un identificador de lote distinto; mantener las entradas oficiales. El resultado final esperado sigue siendo 50 cálculos/950 rechazos o 684 movimientos/316 rechazos/20 estados, respectivamente.

Al terminar la prueba:

```powershell
$env:BATCH_REINICIO_AUTOMATICO = 'false'
$env:BATCH_FALLAR_DESPUES_COMMITS = '0'
$env:BATCH_JOB_ENABLED = 'false'
```

La evidencia de interrupción y recuperación en MySQL todavía debe recogerse antes de dar por finalizada esta etapa.
