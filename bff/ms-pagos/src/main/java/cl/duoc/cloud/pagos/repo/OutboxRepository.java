package cl.duoc.cloud.pagos.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class OutboxRepository {
    private final JdbcTemplate jdbc;
    public OutboxRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Pendiente(String referencia, String topic, String payload) {}

    public void guardar(String referencia, String solicitudId, String topic, String payload) {
        jdbc.update("INSERT INTO pagos_outbox(referencia,solicitud_id,topic,payload) VALUES(?,?,?,?)",
                referencia, solicitudId, topic, payload);
    }

    public List<String> pendientes() {
        return jdbc.query("SELECT referencia FROM pagos_outbox WHERE publicado=FALSE ORDER BY fecha_creacion,referencia LIMIT 10",
                (rs, i) -> rs.getString("referencia"));
    }

    public Optional<Pendiente> bloquearPendiente(String referencia) {
        return jdbc.query("SELECT referencia,topic,payload FROM pagos_outbox WHERE referencia=? AND publicado=FALSE FOR UPDATE",
                (rs, i) -> new Pendiente(rs.getString("referencia"), rs.getString("topic"), rs.getString("payload")), referencia)
                .stream().findFirst();
    }

    public void confirmar(String referencia) {
        jdbc.update("UPDATE pagos_outbox SET publicado=TRUE,intentos=intentos+1,ultimo_error=NULL,fecha_publicacion=CURRENT_TIMESTAMP WHERE referencia=?", referencia);
    }

    public void fallar(String referencia, String error) {
        jdbc.update("UPDATE pagos_outbox SET intentos=intentos+1,ultimo_error=? WHERE referencia=?", error, referencia);
    }
}
