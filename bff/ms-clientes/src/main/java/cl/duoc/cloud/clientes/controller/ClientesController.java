package cl.duoc.cloud.clientes.controller;

import cl.duoc.cloud.clientes.model.*;
import cl.duoc.cloud.clientes.service.ClientesService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clientes")
public class ClientesController {
    private final ClientesService service;

    public ClientesController(ClientesService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ClienteResponse crear(@Valid @RequestBody CrearClienteRequest req) { return service.crear(req); }

    @GetMapping("/{id}")
    public ClienteResponse consultar(@PathVariable Long id) { return service.consultar(id); }

    @GetMapping("/cuentas/{cuentaId}")
    public ClienteResponse porCuenta(@PathVariable Long cuentaId) { return service.consultarPorCuenta(cuentaId); }

    @PutMapping("/{id}")
    public ClienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarClienteRequest req) {
        return service.actualizar(id, req);
    }

    @PostMapping("/{id}/cuentas")
    public ClienteResponse vincular(@PathVariable Long id, @Valid @RequestBody VincularCuentaRequest req) {
        return service.vincular(id, req.cuentaId());
    }
}
