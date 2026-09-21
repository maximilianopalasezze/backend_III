package cl.duoc.cloud.operaciones.repo;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Repository; import java.math.BigDecimal; import java.util.*;
@Repository public class OperacionesRepository { private final JdbcTemplate jdbc; public OperacionesRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Optional<BigDecimal> saldoParaActualizar(Long id){return jdbc.query("SELECT saldo FROM cuentas WHERE cuenta_id=? FOR UPDATE",(rs,i)->rs.getBigDecimal("saldo"),id).stream().findFirst();}
 public void actualizarSaldo(Long id,BigDecimal saldo){jdbc.update("UPDATE cuentas SET saldo=? WHERE cuenta_id=?",saldo,id);}
 public void registrar(String ref,Long id,BigDecimal monto,BigDecimal antes,BigDecimal despues){jdbc.update("INSERT INTO operaciones_cajero(referencia,cuenta_id,tipo_operacion,monto,saldo_anterior,saldo_posterior,estado) VALUES(?,?,'RETIRO',?,?,?,'APROBADA')",ref,id,monto,antes,despues);}
}
