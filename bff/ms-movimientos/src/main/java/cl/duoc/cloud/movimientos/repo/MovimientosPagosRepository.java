package cl.duoc.cloud.movimientos.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Repository
public class MovimientosPagosRepository {
    private final JdbcTemplate jdbc;
    public MovimientosPagosRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void guardar(String referencia, Long cuentaId, LocalDateTime fecha,
                        String tipo, BigDecimal monto, String descripcion) {
        fecha = fecha.truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("INSERT INTO movimientos_pagos(referencia,cuenta_id,fecha,tipo_movimiento,monto,descripcion) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE referencia=referencia",
                referencia, cuentaId, Timestamp.valueOf(fecha), tipo, monto, descripcion);
        var actual = jdbc.queryForMap("SELECT fecha,tipo_movimiento,monto,descripcion FROM movimientos_pagos WHERE referencia=? AND cuenta_id=? FOR UPDATE", referencia, cuentaId);
        if (!tipo.equals(actual.get("tipo_movimiento")) || monto.compareTo((BigDecimal) actual.get("monto")) != 0
                || !descripcion.equals(actual.get("descripcion")) || !fecha.equals(((Timestamp) actual.get("fecha")).toLocalDateTime())) {
            throw new IllegalArgumentException("La referencia del movimiento ya tiene otros datos");
        }
    }
}
