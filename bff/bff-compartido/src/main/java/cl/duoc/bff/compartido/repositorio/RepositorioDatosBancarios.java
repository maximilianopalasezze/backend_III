package cl.duoc.bff.compartido.repositorio;

import cl.duoc.bff.compartido.modelo.CuentaBancaria;
import cl.duoc.bff.compartido.modelo.EstadoAnualCuenta;
import cl.duoc.bff.compartido.modelo.InteresCuenta;
import cl.duoc.bff.compartido.modelo.MovimientoCuenta;
import cl.duoc.bff.compartido.modelo.ResumenOperacional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioDatosBancarios {

    private final JdbcTemplate jdbcTemplate;

    public RepositorioDatosBancarios(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CuentaBancaria> buscarCuenta(Long cuentaId) {
        return buscarCuenta(cuentaId, false);
    }

    public Optional<CuentaBancaria> buscarCuentaParaActualizar(Long cuentaId) {
        return buscarCuenta(cuentaId, true);
    }

    public Optional<InteresCuenta> buscarUltimoInteres(Long cuentaId) {
        List<InteresCuenta> resultados = jdbcTemplate.query("""
                SELECT periodo, saldo_inicial, tasa_interes,
                       interes_calculado, saldo_final, archivo_origen
                FROM intereses_calculados
                WHERE cuenta_id = ?
                ORDER BY fecha_calculo DESC, id DESC
                LIMIT 1
                """,
                (rs, fila) -> new InteresCuenta(
                        rs.getString("periodo"),
                        rs.getBigDecimal("saldo_inicial"),
                        rs.getBigDecimal("tasa_interes"),
                        rs.getBigDecimal("interes_calculado"),
                        rs.getBigDecimal("saldo_final"),
                        rs.getString("archivo_origen")
                ),
                cuentaId
        );

        return resultados.stream().findFirst();
    }

    public Optional<EstadoAnualCuenta> buscarUltimoEstadoAnual(Long cuentaId) {
        List<EstadoAnualCuenta> resultados = jdbcTemplate.query("""
                SELECT anio, cantidad_movimientos, total_depositos,
                       total_retiros, total_compras, total_pagos,
                       saldo_anual, archivo_origen
                FROM estados_cuenta_anuales
                WHERE cuenta_id = ?
                ORDER BY anio DESC, fecha_generacion DESC, id DESC
                LIMIT 1
                """,
                (rs, fila) -> new EstadoAnualCuenta(
                        rs.getInt("anio"),
                        rs.getInt("cantidad_movimientos"),
                        rs.getBigDecimal("total_depositos"),
                        rs.getBigDecimal("total_retiros"),
                        rs.getBigDecimal("total_compras"),
                        rs.getBigDecimal("total_pagos"),
                        rs.getBigDecimal("saldo_anual"),
                        rs.getString("archivo_origen")
                ),
                cuentaId
        );

        return resultados.stream().findFirst();
    }

    public List<MovimientoCuenta> buscarUltimosMovimientos(
            Long cuentaId,
            int limite) {

        return jdbcTemplate.query("""
                SELECT fecha, tipo_movimiento, monto,
                       descripcion, archivo_origen
                FROM movimientos_anuales_procesados
                WHERE cuenta_id = ?
                ORDER BY fecha DESC, id DESC
                LIMIT ?
                """,
                (rs, fila) -> new MovimientoCuenta(
                        rs.getDate("fecha").toLocalDate(),
                        rs.getString("tipo_movimiento"),
                        rs.getBigDecimal("monto"),
                        rs.getString("descripcion"),
                        rs.getString("archivo_origen")
                ),
                cuentaId,
                limite
        );
    }

    public record MovimientoEsencial(java.time.LocalDate fecha, String tipo, BigDecimal monto) {}

    public List<MovimientoEsencial> buscarMovimientosEsenciales(Long cuentaId, int limite) {
        return jdbcTemplate.query("""
                SELECT fecha, tipo_movimiento, monto
                FROM movimientos_anuales_procesados WHERE cuenta_id = ?
                ORDER BY fecha DESC, id DESC LIMIT ?
                """, (rs, fila) -> new MovimientoEsencial(rs.getDate("fecha").toLocalDate(),
                        rs.getString("tipo_movimiento"), rs.getBigDecimal("monto")), cuentaId, limite);
    }

    public ResumenOperacional obtenerResumenOperacional() {
        return jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM cuentas) AS cuentas,
                    (SELECT COUNT(*) FROM transacciones_procesadas) AS transacciones,
                    (SELECT COUNT(*) FROM movimientos_anuales_procesados) AS movimientos,
                    (SELECT COUNT(*) FROM registros_rechazados) AS rechazos,
                    COALESCE((SELECT SUM(saldo) FROM cuentas), 0) AS saldo_total
                """,
                (rs, fila) -> new ResumenOperacional(
                        rs.getLong("cuentas"),
                        rs.getLong("transacciones"),
                        rs.getLong("movimientos"),
                        rs.getLong("rechazos"),
                        rs.getBigDecimal("saldo_total")
                )
        );
    }

    public void actualizarSaldo(Long cuentaId, BigDecimal nuevoSaldo) {
        jdbcTemplate.update(
                "UPDATE cuentas SET saldo = ? WHERE cuenta_id = ?",
                nuevoSaldo,
                cuentaId
        );
    }

    public void registrarRetiro(
            String referencia,
            Long cuentaId,
            BigDecimal monto,
            BigDecimal saldoAnterior,
            BigDecimal saldoPosterior) {

        jdbcTemplate.update("""
                INSERT INTO operaciones_cajero (
                    referencia, cuenta_id, tipo_operacion, monto,
                    saldo_anterior, saldo_posterior, estado
                )
                VALUES (?, ?, 'RETIRO', ?, ?, ?, 'APROBADA')
                """,
                referencia,
                cuentaId,
                monto,
                saldoAnterior,
                saldoPosterior
        );
    }

    private Optional<CuentaBancaria> buscarCuenta(Long cuentaId, boolean bloquear) {
        String sql = """
                SELECT cuenta_id, nombre, saldo, edad,
                       tipo_cuenta, fecha_actualizacion
                FROM cuentas
                WHERE cuenta_id = ?
                """ + (bloquear ? " FOR UPDATE" : "");

        List<CuentaBancaria> resultados = jdbcTemplate.query(
                sql,
                (rs, fila) -> new CuentaBancaria(
                        rs.getLong("cuenta_id"),
                        rs.getString("nombre"),
                        rs.getBigDecimal("saldo"),
                        rs.getInt("edad"),
                        rs.getString("tipo_cuenta"),
                        rs.getTimestamp("fecha_actualizacion").toLocalDateTime()
                ),
                cuentaId
        );

        return resultados.stream().findFirst();
    }
}
