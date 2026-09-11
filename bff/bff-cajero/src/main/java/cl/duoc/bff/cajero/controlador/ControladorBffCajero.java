package cl.duoc.bff.cajero.controlador;

import cl.duoc.bff.cajero.dto.RetiroCajeroResponse;
import cl.duoc.bff.cajero.dto.SaldoCajeroResponse;
import cl.duoc.bff.cajero.dto.SolicitudRetiro;
import cl.duoc.bff.cajero.servicio.ServicioBffCajero;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bff/cajero")
public class ControladorBffCajero {

    private final ServicioBffCajero servicio;

    public ControladorBffCajero(ServicioBffCajero servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/cuentas/{cuentaId}/saldo")
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public SaldoCajeroResponse consultarSaldo(@PathVariable Long cuentaId) {
        return servicio.consultarSaldo(cuentaId);
    }

    @PostMapping("/cuentas/{cuentaId}/retiros")
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public RetiroCajeroResponse retirar(
            @PathVariable Long cuentaId,
            @Valid @RequestBody SolicitudRetiro solicitud) {

        return servicio.retirar(cuentaId, solicitud);
    }
}
