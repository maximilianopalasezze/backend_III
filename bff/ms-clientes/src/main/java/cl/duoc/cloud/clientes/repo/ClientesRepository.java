package cl.duoc.cloud.clientes.repo;

import cl.duoc.cloud.clientes.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class ClientesRepository {
    private final JdbcTemplate jdbc;

    public ClientesRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<ClienteResponse> cliente(Long id) {
        return jdbc.query("SELECT cliente_id,nombre,email,telefono,direccion,perfil,version,fecha_actualizacion FROM clientes WHERE cliente_id=?",
                (rs, fila) -> new ClienteResponse(rs.getLong("cliente_id"), rs.getString("nombre"),
                        rs.getString("email"), rs.getString("telefono"), rs.getString("direccion"),
                        rs.getString("perfil"), rs.getInt("version"),
                        rs.getTimestamp("fecha_actualizacion").toLocalDateTime(), List.of()), id)
                .stream().findFirst();
    }

    public List<Long> cuentas(Long id) {
        return jdbc.query("SELECT cuenta_id FROM clientes_cuentas WHERE cliente_id=? ORDER BY cuenta_id",
                (rs, fila) -> rs.getLong("cuenta_id"), id);
    }

    public Optional<Long> clientePorCuenta(Long id) {
        return jdbc.query("SELECT cliente_id FROM clientes_cuentas WHERE cuenta_id=?",
                (rs, fila) -> rs.getLong("cliente_id"), id).stream().findFirst();
    }

    public boolean bloquearCuenta(Long id) {
        return !jdbc.query("SELECT cuenta_id FROM cuentas WHERE cuenta_id=? FOR UPDATE",
                (rs, fila) -> rs.getLong("cuenta_id"), id).isEmpty();
    }

    public boolean cuentaCerrada(Long id) {
        return jdbc.query("SELECT estado FROM cuentas_estado WHERE cuenta_id=? FOR UPDATE",
                (rs, fila) -> rs.getString("estado"), id).stream().anyMatch("CERRADA"::equals);
    }

    public boolean bloquearCliente(Long id) {
        return !jdbc.query("SELECT cliente_id FROM clientes WHERE cliente_id=? FOR UPDATE",
                (rs, fila) -> rs.getLong("cliente_id"), id).isEmpty();
    }

    public Optional<Long> titularActual(Long cuentaId) {
        return jdbc.query("SELECT cliente_id FROM clientes_cuentas WHERE cuenta_id=? FOR UPDATE",
                (rs, fila) -> rs.getLong("cliente_id"), cuentaId).stream().findFirst();
    }

    public void crear(CrearClienteRequest req) {
        jdbc.update("INSERT INTO clientes(cliente_id,nombre,email,telefono,direccion,perfil) VALUES(?,?,?,?,?,?)",
                req.clienteId(), req.nombre().trim(), req.email().trim().toLowerCase(Locale.ROOT),
                req.telefono().trim(), req.direccion().trim(), req.perfil());
    }

    public void vincular(Long clienteId, Long cuentaId) {
        jdbc.update("INSERT INTO clientes_cuentas(cuenta_id,cliente_id) VALUES(?,?)", cuentaId, clienteId);
    }

    public int actualizar(Long id, ActualizarClienteRequest req) {
        return jdbc.update("UPDATE clientes SET nombre=?,email=?,telefono=?,direccion=?,perfil=?,version=version+1,fecha_actualizacion=CURRENT_TIMESTAMP WHERE cliente_id=? AND version=?",
                req.nombre().trim(), req.email().trim().toLowerCase(Locale.ROOT), req.telefono().trim(),
                req.direccion().trim(), req.perfil(), id, req.version());
    }
}
