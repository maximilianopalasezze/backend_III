USE bank_batch_semana4_db;

-- 1. Cuenta oficial recomendada para las pruebas de Postman.
SELECT cuenta_id, nombre, saldo, edad, tipo_cuenta, fecha_actualizacion
FROM cuentas
WHERE cuenta_id = 106;

-- 2. Demuestra que cada Job leyó y clasificó los 1.000 registros oficiales.
-- La ruta queda visible en la evidencia y se excluyen pruebas de otros datasets.
WITH conteos_oficiales AS (
    SELECT
        'Transacciones diarias' AS proceso,
        'data/semana_3/transacciones.csv' AS archivo_oficial,
        (SELECT COUNT(*)
         FROM transacciones_procesadas
         WHERE archivo_origen = 'data/semana_3/transacciones.csv') AS procesados,
        (SELECT COUNT(*)
         FROM registros_rechazados
         WHERE nombre_job = 'jobTransaccionesDiarias'
           AND archivo_origen = 'data/semana_3/transacciones.csv') AS rechazados

    UNION ALL

    SELECT
        'Intereses mensuales',
        'data/semana_3/intereses.csv',
        (SELECT COUNT(*)
         FROM intereses_calculados
         WHERE archivo_origen = 'data/semana_3/intereses.csv'),
        (SELECT COUNT(*)
         FROM registros_rechazados
         WHERE nombre_job = 'jobInteresesMensuales'
           AND archivo_origen = 'data/semana_3/intereses.csv')

    UNION ALL

    SELECT
        'Estados de cuenta anuales',
        'data/semana_3/cuentas_anuales.csv',
        (SELECT COUNT(*)
         FROM movimientos_anuales_procesados
         WHERE archivo_origen = 'data/semana_3/cuentas_anuales.csv'),
        (SELECT COUNT(*)
         FROM registros_rechazados
         WHERE nombre_job = 'jobEstadosCuentaAnuales'
           AND archivo_origen = 'data/semana_3/cuentas_anuales.csv')
)
SELECT
    proceso,
    archivo_oficial,
    procesados,
    rechazados,
    procesados + rechazados AS total_leidos,
    IF(procesados + rechazados = 1000, 'DATOS OFICIALES COMPLETOS', 'REVISAR')
        AS verificacion
FROM conteos_oficiales;

-- 3. Auditoría de retiros realizados únicamente por el BFF Cajero.
SELECT referencia,
       cuenta_id,
       tipo_operacion,
       monto,
       saldo_anterior,
       saldo_posterior,
       estado,
       fecha_operacion
FROM operaciones_cajero
ORDER BY fecha_operacion DESC;
