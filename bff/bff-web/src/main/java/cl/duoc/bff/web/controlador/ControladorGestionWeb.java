package cl.duoc.bff.web.controlador;

import cl.duoc.bff.compartido.integracion.ClienteGestionBanco;
import cl.duoc.bff.compartido.modelo.*;
import cl.duoc.bff.compartido.modelo.SolicitudesBanco.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bff/web")
public class ControladorGestionWeb {
    private final ClienteGestionBanco banco;
    public ControladorGestionWeb(ClienteGestionBanco banco) { this.banco = banco; }

    @PostMapping("/cuentas") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accesoCuenta.permitir(authentication, #req.cuentaId())")
    public CuentaBancaria abrir(@Valid @RequestBody AperturaCuenta req) { return banco.abrirCuenta(req); }

    @PutMapping("/cuentas/{cuentaId}")
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public CuentaBancaria mantener(@PathVariable Long cuentaId, @Valid @RequestBody MantenimientoCuenta req) {
        return banco.mantenerCuenta(cuentaId, req);
    }

    @PostMapping("/cuentas/{cuentaId}/cierre")
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public CuentaBancaria cerrar(@PathVariable Long cuentaId) { return banco.cerrarCuenta(cuentaId); }

    @GetMapping("/cuentas/{cuentaId}/cliente")
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public PerfilCliente cliente(@PathVariable Long cuentaId) { return banco.consultarCliente(cuentaId); }

    @PostMapping("/cuentas/{cuentaId}/cliente") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public PerfilCliente crearCliente(@PathVariable Long cuentaId, @Valid @RequestBody AltaCliente req) {
        return banco.crearCliente(cuentaId, req);
    }

    @PutMapping("/cuentas/{cuentaId}/cliente")
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public PerfilCliente actualizarCliente(@PathVariable Long cuentaId, @Valid @RequestBody ActualizarCliente req) {
        return banco.actualizarCliente(cuentaId, req);
    }

    @PostMapping("/cuentas/{cuentaId}/cliente/cuentas")
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId) and @accesoCuenta.permitir(authentication, #req.cuentaId())")
    public PerfilCliente vincular(@PathVariable Long cuentaId, @Valid @RequestBody VincularCuenta req) {
        return banco.vincularCuenta(cuentaId, req);
    }

    @PostMapping("/cuentas/{cuentaId}/depositos") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public ComprobantePago depositar(@PathVariable Long cuentaId, @Valid @RequestBody Deposito req) {
        return ComprobantePago.desde(banco.depositar(cuentaId, req));
    }

    @PostMapping("/cuentas/{cuentaId}/pagos") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public ComprobantePago pagar(@PathVariable Long cuentaId, @Valid @RequestBody Pago req) {
        return ComprobantePago.desde(banco.pagar(cuentaId, req));
    }

    @PostMapping("/cuentas/{cuentaId}/transferencias") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public ComprobantePago transferir(@PathVariable Long cuentaId, @Valid @RequestBody Transferencia req) {
        return ComprobantePago.desde(banco.transferir(cuentaId, req));
    }

    @GetMapping("/cuentas/{cuentaId}/solicitudes/{solicitudId}")
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public ComprobantePago solicitud(@PathVariable Long cuentaId, @PathVariable String solicitudId) {
        return ComprobantePago.desde(banco.consultarPago(cuentaId, solicitudId));
    }
}
