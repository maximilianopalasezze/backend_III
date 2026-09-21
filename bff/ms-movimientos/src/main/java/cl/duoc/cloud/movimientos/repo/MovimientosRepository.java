package cl.duoc.cloud.movimientos.repo;
import cl.duoc.cloud.movimientos.model.MovimientoResponse; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Repository; import java.util.List;
@Repository public class MovimientosRepository { private final JdbcTemplate jdbc; public MovimientosRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<MovimientoResponse> ultimos(Long id,int limite){return jdbc.query("SELECT fecha,tipo_movimiento,monto,descripcion,archivo_origen FROM movimientos_anuales_procesados WHERE cuenta_id=? ORDER BY fecha DESC,id DESC LIMIT ?",
 (rs,i)->new MovimientoResponse(rs.getDate("fecha").toLocalDate(),rs.getString("tipo_movimiento"),rs.getBigDecimal("monto"),rs.getString("descripcion"),rs.getString("archivo_origen")),id,limite);}
}
