package cl.duoc.bff.web.controlador;

import cl.duoc.bff.web.dto.CuentaWebResponse;
import cl.duoc.bff.web.dto.ResumenWebResponse;
import cl.duoc.bff.web.servicio.ServicioBffWeb;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bff/web")
public class ControladorBffWeb {

    private final ServicioBffWeb servicio;

    public ControladorBffWeb(ServicioBffWeb servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/cuentas/{cuentaId}")
    @PreAuthorize("@accesoCuenta.permitir(authentication, #cuentaId)")
    public CuentaWebResponse obtenerCuenta(@PathVariable Long cuentaId) {
        return servicio.obtenerCuenta(cuentaId);
    }

    @GetMapping("/resumen")
    public ResumenWebResponse obtenerResumen() {
        return servicio.obtenerResumen();
    }
}
