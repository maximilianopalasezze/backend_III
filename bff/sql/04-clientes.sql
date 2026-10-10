-- Semana 9: datos personales y perfil comercial, separados del saldo de cuentas.
-- Ejecutar después de 03-gestion-cuentas.sql, en bank_xyz_semana5_db.
CREATE TABLE IF NOT EXISTS clientes (
    cliente_id BIGINT NOT NULL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL,
    telefono VARCHAR(25) NOT NULL,
    direccion VARCHAR(255) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_cliente_perfil CHECK (perfil IN ('ESTANDAR', 'PREFERENTE', 'EMPRESA')),
    CONSTRAINT chk_cliente_version CHECK (version >= 0)
);

-- Un cliente puede tener varias cuentas; una cuenta tiene un único cliente titular.
CREATE TABLE IF NOT EXISTS clientes_cuentas (
    cuenta_id BIGINT NOT NULL PRIMARY KEY,
    cliente_id BIGINT NOT NULL,
    fecha_vinculacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cliente_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuentas(cuenta_id),
    CONSTRAINT fk_cuenta_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(cliente_id),
    INDEX idx_clientes_cuentas_cliente (cliente_id)
);

-- Migración repetible. No crea perfiles ficticios ni altera cuentas, saldos o Batch.
