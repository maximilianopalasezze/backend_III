package cl.duoc.bff.movil.controlador;

import cl.duoc.bff.movil.dto.InicioMovilResponse;
import cl.duoc.bff.movil.dto.MovimientoMovilResponse;
import cl.duoc.bff.movil.servicio.ServicioBffMovil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bff/movil")
public class ControladorBffMovil {

    private final ServicioBffMovil servicio;

    public ControladorBffMovil(ServicioBffMovil servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/cuentas/{cuentaId}")
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public InicioMovilResponse obtenerInicio(@PathVariable Long cuentaId) {
        return servicio.obtenerInicio(cuentaId);
    }

    @GetMapping("/cuentas/{cuentaId}/movimientos")
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public List<MovimientoMovilResponse> obtenerMovimientos(
            @PathVariable Long cuentaId,
            @RequestParam(defaultValue = "5") int limite) {

        return servicio.obtenerMovimientos(cuentaId, limite);
    }
}
