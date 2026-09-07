package cl.duoc.bank_batch.bff.web.controlador;

import cl.duoc.bank_batch.bff.web.dto.CuentaWebResponse;
import cl.duoc.bank_batch.bff.web.dto.ResumenWebResponse;
import cl.duoc.bank_batch.bff.web.servicio.ServicioBffWeb;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("web")
@RequestMapping("/api/bff/web")
public class ControladorBffWeb {

    private final ServicioBffWeb servicio;

    public ControladorBffWeb(ServicioBffWeb servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/cuentas/{cuentaId}")
    public CuentaWebResponse obtenerCuenta(@PathVariable Long cuentaId) {
        return servicio.obtenerCuenta(cuentaId);
    }

    @GetMapping("/resumen")
    public ResumenWebResponse obtenerResumen() {
        return servicio.obtenerResumen();
    }
}
