package cl.duoc.cloud.clientes.service;

import cl.duoc.cloud.clientes.config.ApiErrorHandler.*;
import cl.duoc.cloud.clientes.model.*;
import cl.duoc.cloud.clientes.repo.ClientesRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientesService {
    private final ClientesRepository repo;

    public ClientesService(ClientesRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public ClienteResponse consultar(Long id) { return respuesta(id); }

    @Transactional(readOnly = true)
    public ClienteResponse consultarPorCuenta(Long cuentaId) {
        Long id = repo.clientePorCuenta(cuentaId).orElseThrow(() ->
                new ClienteNoEncontrado("No existe un cliente asociado a la cuenta " + cuentaId));
        return respuesta(id);
    }

    @Transactional
    public ClienteResponse crear(CrearClienteRequest req) {
        bloquearCuentaActiva(req.cuentaId());
        try {
            repo.crear(req);
            repo.vincular(req.clienteId(), req.cuentaId());
        } catch (DuplicateKeyException ex) {
            throw new ClienteConflicto("El cliente ya existe o la cuenta ya tiene un titular");
        }
        return respuesta(req.clienteId());
    }

    @Transactional
    public ClienteResponse actualizar(Long id, ActualizarClienteRequest req) {
        if (repo.cliente(id).isEmpty()) throw new ClienteNoEncontrado("No existe el cliente " + id);
        if (repo.actualizar(id, req) == 0) {
            throw new ClienteConflicto("El perfil fue modificado; consulta la versión actual antes de actualizar");
        }
        return respuesta(id);
    }

    @Transactional
    public ClienteResponse vincular(Long clienteId, Long cuentaId) {
        bloquearCuentaActiva(cuentaId);
        if (!repo.bloquearCliente(clienteId)) throw new ClienteNoEncontrado("No existe el cliente " + clienteId);
        var titular = repo.titularActual(cuentaId);
        if (titular.isPresent() && !titular.get().equals(clienteId)) {
            throw new ClienteConflicto("La cuenta ya tiene otro titular");
        }
        if (titular.isEmpty()) repo.vincular(clienteId, cuentaId);
        return respuesta(clienteId);
    }

    private void bloquearCuentaActiva(Long id) {
        if (!repo.bloquearCuenta(id)) throw new ClienteNoEncontrado("No existe la cuenta " + id);
        if (repo.cuentaCerrada(id)) throw new ClienteConflicto("No se puede vincular una cuenta cerrada");
    }

    private ClienteResponse respuesta(Long id) {
        var c = repo.cliente(id).orElseThrow(() -> new ClienteNoEncontrado("No existe el cliente " + id));
        return new ClienteResponse(c.clienteId(), c.nombre(), c.email(), c.telefono(), c.direccion(),
                c.perfil(), c.version(), c.fechaActualizacion(), repo.cuentas(id));
    }
}
