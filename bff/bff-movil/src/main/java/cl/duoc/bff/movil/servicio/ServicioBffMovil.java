package cl.duoc.bff.movil.servicio;

import cl.duoc.bff.compartido.excepcion.OperacionInvalidaException;
import cl.duoc.bff.compartido.integracion.ClienteServiciosBanco;
import cl.duoc.bff.movil.dto.InicioMovilResponse;
import cl.duoc.bff.movil.dto.MovimientoMovilResponse;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ServicioBffMovil {
    private static final int LIMITE_MAXIMO_MOVIL = 10;
    private final ClienteServiciosBanco backend;
    public ServicioBffMovil(ClienteServiciosBanco backend) { this.backend = backend; }

    public InicioMovilResponse obtenerInicio(Long cuentaId) {
        var cuenta = backend.obtenerCuenta(cuentaId);
        return new InicioMovilResponse("MOVIL", cuenta.cuentaId(), cuenta.nombre(),
                cuenta.tipoCuenta(), cuenta.saldo(), cuenta.fechaActualizacion());
    }

    public List<MovimientoMovilResponse> obtenerMovimientos(Long cuentaId, int limite) {
        if (limite < 1 || limite > LIMITE_MAXIMO_MOVIL)
            throw new OperacionInvalidaException("El límite móvil debe estar entre 1 y " + LIMITE_MAXIMO_MOVIL);
        backend.obtenerCuenta(cuentaId);
        return backend.obtenerMovimientos(cuentaId, limite).stream()
                .map(m -> new MovimientoMovilResponse(m.fecha(), m.tipo(), m.monto())).toList();
    }
}
