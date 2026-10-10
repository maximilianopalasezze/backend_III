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
$env:DB_PASSWORD = "TU_PASSWORD_LOCAL"
$env:BATCH_JOB_ENABLED = "true"
$env:BATCH_JOB_NAME = "jobTransaccionesDiarias"
java -jar .\target\bank-batch-0.0.1-SNAPSHOT.jar run.id=9001
```

Para los otros procesos, seleccionar el Job y un identificador nuevo:

```powershell
$env:BATCH_JOB_NAME = "jobInteresesMensuales"
java -jar .\target\bank-batch-0.0.1-SNAPSHOT.jar run.id=9002

$env:BATCH_JOB_NAME = "jobEstadosCuentaAnuales"
java -jar .\target\bank-batch-0.0.1-SNAPSHOT.jar run.id=9003
```

Para una instancia nueva, elegir un `run.id` que aún no exista. Para reiniciar una ejecución fallida, conservar exactamente sus parámetros; la verificación de reinicios y recuperación automática pertenece a la siguiente etapa.

**Efectos sobre la base compartida:** el Job de intereses inserta o actualiza `cuentas`, incluyendo el saldo, a partir del CSV. Al comenzar una instancia nueva, los listeners limpian resultados y rechazos correspondientes al archivo/período procesado; durante un reinicio intentan conservar los datos confirmados. Antes de las pruebas integradas, guardar una copia de los datos de prueba y evitar retiros simultáneos sobre las cuentas que se recargan.

No se ejecutó ningún Job sobre la base de EC2 durante esta integración.

## Funcionalidad recuperada

- Tres Jobs con lectores CSV, procesadores y escritores JDBC.
- Lectores sincronizados y pool configurable de trabajadores.
- Políticas de omisión, reintentos de fallos transitorios y límites de ejecución.
- Listeners de rechazos, resúmenes, hilos y métricas.
- Tablas de resultados y restricciones únicas.

## Estado de verificación

Paso 1: integración del componente y configuración de entradas. Se verificaron la estructura, los tres Jobs, los encabezados y cantidades de registros, y la coincidencia de los esquemas de tablas de negocio con el SQL de semana 8.

La compilación se intentó, pero la descarga del POM padre desde Maven Central falló por resolución de red. No se certifica compilación ni ejecución en este entorno. Deben verificarse en PC/EC2 los tres Jobs, resultados con los CSV nuevos, metadatos Batch en MySQL, consistencia al convivir con los BFF, reinicios y equivalencia de resultados con reglas legacy documentadas.
