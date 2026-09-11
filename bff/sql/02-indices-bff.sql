-- Ejecutar una sola vez en la base que ya contiene los datos oficiales.
USE bank_xyz_semana5_db;

-- No elimina ni cambia cuentas, saldos ni movimientos.
-- Ejecutar individualmente si alguno de estos indices ya existe.
CREATE INDEX idx_bff_movimientos_recientes
    ON movimientos_anuales_procesados (cuenta_id, fecha DESC, id DESC);
CREATE INDEX idx_bff_interes_reciente
    ON intereses_calculados (cuenta_id, fecha_calculo DESC, id DESC);
CREATE INDEX idx_bff_estado_reciente
    ON estados_cuenta_anuales (cuenta_id, anio DESC, fecha_generacion DESC, id DESC);
