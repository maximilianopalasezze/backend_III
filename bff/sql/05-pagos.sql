-- Semana 9. Ejecutar en bank_xyz_semana5_db después de 03-gestion-cuentas.sql.
-- Las operaciones y su evento pendiente se guardan junto con los saldos.
CREATE TABLE IF NOT EXISTS pagos_operaciones (
    solicitud_id VARCHAR(80) NOT NULL PRIMARY KEY,
    referencia CHAR(36) NOT NULL UNIQUE,
    tipo VARCHAR(20) NOT NULL,
    cuenta_id BIGINT NOT NULL,
    cuenta_destino_id BIGINT NULL,
    monto DECIMAL(15,2) NOT NULL,
    beneficiario VARCHAR(150) NOT NULL,
    concepto VARCHAR(200) NOT NULL,
    saldo_anterior DECIMAL(15,2) NOT NULL,
    saldo_posterior DECIMAL(15,2) NOT NULL,
    saldo_destino_anterior DECIMAL(15,2) NULL,
    saldo_destino_posterior DECIMAL(15,2) NULL,
    estado VARCHAR(15) NOT NULL,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pago_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuentas(cuenta_id),
    CONSTRAINT fk_pago_destino FOREIGN KEY (cuenta_destino_id) REFERENCES cuentas(cuenta_id),
    CONSTRAINT chk_pago_tipo CHECK (tipo IN ('DEPOSITO','PAGO','TRANSFERENCIA')),
    CONSTRAINT chk_pago_monto CHECK (monto > 0),
    CONSTRAINT chk_pago_saldos CHECK (saldo_anterior >= 0 AND saldo_posterior >= 0),
    CONSTRAINT chk_pago_estado CHECK (estado = 'APROBADA'),
    INDEX idx_pagos_cuenta_fecha (cuenta_id, fecha)
);

CREATE TABLE IF NOT EXISTS pagos_outbox (
    referencia CHAR(36) NOT NULL PRIMARY KEY,
    solicitud_id VARCHAR(80) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    publicado BOOLEAN NOT NULL DEFAULT FALSE,
    intentos INT NOT NULL DEFAULT 0,
    ultimo_error VARCHAR(300) NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_publicacion TIMESTAMP NULL,
    CONSTRAINT fk_outbox_pago FOREIGN KEY (solicitud_id) REFERENCES pagos_operaciones(solicitud_id),
    INDEX idx_outbox_pendientes (publicado, fecha_creacion)
);

-- Proyección asíncrona de MS-MOVIMIENTOS. Un mismo evento no duplica movimientos.
CREATE TABLE IF NOT EXISTS movimientos_pagos (
    referencia CHAR(36) NOT NULL,
    cuenta_id BIGINT NOT NULL,
    fecha TIMESTAMP NOT NULL,
    tipo_movimiento VARCHAR(30) NOT NULL,
    monto DECIMAL(15,2) NOT NULL,
    descripcion VARCHAR(200) NOT NULL,
    PRIMARY KEY (referencia, cuenta_id),
    CONSTRAINT fk_movimiento_pago_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuentas(cuenta_id),
    CONSTRAINT chk_movimiento_pago_monto CHECK (monto > 0),
    INDEX idx_movimientos_pago_cuenta_fecha (cuenta_id, fecha)
);

-- Migración repetible: no reinicia tablas ni crea cuentas o transacciones ficticias.
