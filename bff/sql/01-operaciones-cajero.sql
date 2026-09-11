-- Operaciones críticas originadas exclusivamente desde el BFF de cajeros.
CREATE TABLE IF NOT EXISTS operaciones_cajero (
                                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                    referencia VARCHAR(36) NOT NULL,
    cuenta_id BIGINT NOT NULL,
    tipo_operacion VARCHAR(20) NOT NULL,
    monto DECIMAL(15,2) NOT NULL,
    saldo_anterior DECIMAL(15,2) NOT NULL,
    saldo_posterior DECIMAL(15,2) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    fecha_operacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_operacion_cajero_referencia UNIQUE (referencia),
    CONSTRAINT fk_operacion_cajero_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuentas(cuenta_id),

    INDEX idx_operaciones_cajero_cuenta
        (cuenta_id, fecha_operacion)
    );

