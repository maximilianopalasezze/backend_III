package cl.duoc.bff.movil.controlador;

import cl.duoc.bff.compartido.integracion.ClienteGestionBanco;
import cl.duoc.bff.compartido.modelo.ComprobantePago;
import cl.duoc.bff.compartido.modelo.SolicitudesBanco.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bff/movil/cuentas/{cuentaId}")
@PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
public class ControladorPagosMovil {
    private final ClienteGestionBanco banco;
    public ControladorPagosMovil(ClienteGestionBanco banco) { this.banco = banco; }

    @PostMapping("/depositos") @ResponseStatus(HttpStatus.CREATED)
    public ComprobantePago depositar(@PathVariable Long cuentaId, @Valid @RequestBody Deposito req) {
        return ComprobantePago.desde(banco.depositar(cuentaId, req));
    }

    @PostMapping("/pagos") @ResponseStatus(HttpStatus.CREATED)
    public ComprobantePago pagar(@PathVariable Long cuentaId, @Valid @RequestBody Pago req) {
        return ComprobantePago.desde(banco.pagar(cuentaId, req));
    }

    @PostMapping("/transferencias") @ResponseStatus(HttpStatus.CREATED)
    public ComprobantePago transferir(@PathVariable Long cuentaId, @Valid @RequestBody Transferencia req) {
        return ComprobantePago.desde(banco.transferir(cuentaId, req));
    }

    @GetMapping("/solicitudes/{solicitudId}")
    public ComprobantePago solicitud(@PathVariable Long cuentaId, @PathVariable String solicitudId) {
        return ComprobantePago.desde(banco.consultarPago(cuentaId, solicitudId));
    }
}
