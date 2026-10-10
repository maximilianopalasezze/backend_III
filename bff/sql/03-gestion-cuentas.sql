-- Semana 9: estado de cuenta sin modificar los datos ni el esquema del legado.
-- Ejecutar en bank_xyz_semana5_db antes de iniciar la versión de semana 9.
CREATE TABLE IF NOT EXISTS cuentas_estado (
    cuenta_id BIGINT NOT NULL PRIMARY KEY,
    estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVA',
    fecha_apertura TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_cierre TIMESTAMP NULL,
    CONSTRAINT fk_estado_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuentas(cuenta_id),
    CONSTRAINT chk_estado_cuenta CHECK (estado IN ('ACTIVA', 'CERRADA'))
);

-- Las cuentas anteriores, sin fila en esta tabla, se consultan como ACTIVA.
-- No borra cuentas, resultados Batch ni movimientos; es repetible.
