package cl.duoc.cloud.pagos.repo;

import cl.duoc.cloud.pagos.config.ApiErrorHandler.*;
import cl.duoc.cloud.pagos.model.OperacionPagoResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class PagosRepository {
    private final JdbcTemplate jdbc;
    public PagosRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<OperacionPagoResponse> operacion(String solicitudId) {
        return jdbc.query("SELECT * FROM pagos_operaciones WHERE solicitud_id=?", mapper(), solicitudId)
                .stream().findFirst();
    }

    public BigDecimal bloquearCuenta(Long cuentaId) {
        return jdbc.query("SELECT saldo FROM cuentas WHERE cuenta_id=? FOR UPDATE",
                (rs, i) -> rs.getBigDecimal("saldo"), cuentaId).stream().findFirst()
                .orElseThrow(() -> new OperacionNoEncontrada("No existe la cuenta " + cuentaId));
    }

    public void exigirActiva(Long cuentaId) {
        if (jdbc.query("SELECT estado FROM cuentas_estado WHERE cuenta_id=? FOR UPDATE",
                (rs, i) -> rs.getString("estado"), cuentaId).stream().anyMatch("CERRADA"::equals)) {
            throw new OperacionConflicto("La cuenta está cerrada");
        }
    }

    public void actualizarSaldo(Long cuentaId, BigDecimal saldo) {
        if (jdbc.update("UPDATE cuentas SET saldo=? WHERE cuenta_id=?", saldo, cuentaId) != 1) {
            throw new IllegalStateException("La cuenta bloqueada ya no está disponible");
        }
    }

    public void registrar(OperacionPagoResponse op) {
        jdbc.update("INSERT INTO pagos_operaciones(solicitud_id,referencia,tipo,cuenta_id,cuenta_destino_id,monto,beneficiario,concepto,saldo_anterior,saldo_posterior,saldo_destino_anterior,saldo_destino_posterior,estado) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                op.solicitudId(), op.referencia(), op.tipo(), op.cuentaId(), op.cuentaDestinoId(),
                op.monto(), op.beneficiario(), op.concepto(), op.saldoAnterior(), op.saldoPosterior(),
                op.saldoDestinoAnterior(), op.saldoDestinoPosterior(), op.estado());
    }

    private RowMapper<OperacionPagoResponse> mapper() {
        return (rs, i) -> new OperacionPagoResponse(rs.getString("solicitud_id"), rs.getString("referencia"),
                rs.getString("tipo"), rs.getLong("cuenta_id"), nullableLong(rs, "cuenta_destino_id"),
                rs.getBigDecimal("monto"), rs.getString("beneficiario"), rs.getString("concepto"),
                rs.getBigDecimal("saldo_anterior"), rs.getBigDecimal("saldo_posterior"),
                rs.getBigDecimal("saldo_destino_anterior"), rs.getBigDecimal("saldo_destino_posterior"),
                rs.getString("estado"), rs.getTimestamp("fecha").toLocalDateTime());
    }

    private Long nullableLong(ResultSet rs, String columna) throws SQLException {
        long value = rs.getLong(columna);
        return rs.wasNull() ? null : value;
    }
}
