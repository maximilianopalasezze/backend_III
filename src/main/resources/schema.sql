-- =====================================================
-- TABLAS PROPIAS DEL SISTEMA BANCARIO
-- =====================================================

CREATE TABLE IF NOT EXISTS cuentas (
                                       cuenta_id BIGINT PRIMARY KEY,
                                       nombre VARCHAR(150) NOT NULL,
    saldo DECIMAL(15,2) NOT NULL,
    edad INT NOT NULL,
    tipo_cuenta VARCHAR(30) NOT NULL,
    fecha_actualizacion TIMESTAMP NOT NULL
    DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS transacciones_procesadas (
                                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                        transaccion_id BIGINT NOT NULL,
                                                        fecha DATE NOT NULL,
                                                        monto DECIMAL(15,2) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    es_anomalia BOOLEAN NOT NULL DEFAULT FALSE,
    detalle_anomalia VARCHAR(255),
    archivo_origen VARCHAR(150) NOT NULL,
    fecha_procesamiento TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_transaccion_archivo
    UNIQUE (transaccion_id, archivo_origen)
    );

CREATE TABLE IF NOT EXISTS resumen_transacciones_diarias (
                                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                             fecha DATE NOT NULL,
                                                             cantidad_transacciones INT NOT NULL,
                                                             cantidad_debitos INT NOT NULL,
                                                             cantidad_creditos INT NOT NULL,
                                                             monto_total_debitos DECIMAL(15,2) NOT NULL,
    monto_total_creditos DECIMAL(15,2) NOT NULL,
    cantidad_anomalias INT NOT NULL,
    archivo_origen VARCHAR(150) NOT NULL,
    fecha_generacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_resumen_fecha_archivo
    UNIQUE (fecha, archivo_origen)
    );

CREATE TABLE IF NOT EXISTS intereses_calculados (
                                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                    cuenta_id BIGINT NOT NULL,
                                                    periodo CHAR(7) NOT NULL,
    saldo_inicial DECIMAL(15,2) NOT NULL,
    tasa_interes DECIMAL(8,6) NOT NULL,
    interes_calculado DECIMAL(15,2) NOT NULL,
    saldo_final DECIMAL(15,2) NOT NULL,
    archivo_origen VARCHAR(150) NOT NULL,
    fecha_calculo TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_interes_cuenta_periodo_archivo
    UNIQUE (cuenta_id, periodo, archivo_origen)
    );

CREATE TABLE IF NOT EXISTS movimientos_anuales_procesados (
                                                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                              cuenta_id BIGINT NOT NULL,
                                                              fecha DATE NOT NULL,
                                                              tipo_movimiento VARCHAR(30) NOT NULL,
    monto DECIMAL(15,2) NOT NULL,
    descripcion VARCHAR(255) NOT NULL,
    archivo_origen VARCHAR(150) NOT NULL,
    fecha_procesamiento TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_movimiento_anual
    UNIQUE (
               cuenta_id,
               fecha,
               tipo_movimiento,
               monto,
               archivo_origen
           )
    );

CREATE TABLE IF NOT EXISTS estados_cuenta_anuales (
                                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                      cuenta_id BIGINT NOT NULL,
                                                      anio SMALLINT NOT NULL,
                                                      cantidad_movimientos INT NOT NULL,
                                                      total_depositos DECIMAL(15,2) NOT NULL,
    total_retiros DECIMAL(15,2) NOT NULL,
    total_compras DECIMAL(15,2) NOT NULL,
    total_pagos DECIMAL(15,2) NOT NULL,
    saldo_anual DECIMAL(15,2) NOT NULL,
    archivo_origen VARCHAR(150) NOT NULL,
    fecha_generacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_estado_cuenta_anual
    UNIQUE (cuenta_id, anio, archivo_origen)
    );

CREATE TABLE IF NOT EXISTS registros_rechazados (
                                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                    nombre_job VARCHAR(100) NOT NULL,
    nombre_step VARCHAR(100) NOT NULL,
    archivo_origen VARCHAR(150) NOT NULL,
    numero_linea BIGINT,
    contenido_original TEXT NOT NULL,
    motivo_rechazo VARCHAR(500) NOT NULL,
    fecha_rechazo TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );