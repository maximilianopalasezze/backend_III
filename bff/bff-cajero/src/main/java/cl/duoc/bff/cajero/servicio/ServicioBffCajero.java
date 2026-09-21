package cl.duoc.bff.cajero.servicio;

import cl.duoc.bff.cajero.dto.RetiroCajeroResponse;
import cl.duoc.bff.cajero.dto.SaldoCajeroResponse;
import cl.duoc.bff.cajero.dto.SolicitudRetiro;
import cl.duoc.bff.compartido.excepcion.OperacionInvalidaException;
import cl.duoc.bff.compartido.integracion.ClienteServiciosBanco;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class ServicioBffCajero {
    private static final BigDecimal MULTIPLO_RETIRO = new BigDecimal("1000.00");
    private final ClienteServiciosBanco backend;
    public ServicioBffCajero(ClienteServiciosBanco backend) { this.backend = backend; }

    public SaldoCajeroResponse consultarSaldo(Long cuentaId) {
        var cuenta = backend.obtenerCuenta(cuentaId);
        return new SaldoCajeroResponse("CAJERO", enmascarar(cuenta.cuentaId()), "CLP",
                cuenta.saldo(), cuenta.saldo().compareTo(MULTIPLO_RETIRO) >= 0);
    }

    public RetiroCajeroResponse retirar(Long cuentaId, SolicitudRetiro solicitud) {
        validarMonto(solicitud.monto());
        var r = backend.retirar(cuentaId, solicitud.monto());
        return new RetiroCajeroResponse("CAJERO", r.referencia(), r.estado(), enmascarar(r.cuentaId()),
                r.monto(), r.saldoAnterior(), r.saldoPosterior(), r.fechaOperacion());
    }
    private void validarMonto(BigDecimal monto) {
        if (monto == null || monto.compareTo(MULTIPLO_RETIRO) < 0)
            throw new OperacionInvalidaException("El monto mínimo del retiro es 1000");
        if (monto.remainder(MULTIPLO_RETIRO).compareTo(BigDecimal.ZERO) != 0)
            throw new OperacionInvalidaException("El monto del retiro debe ser múltiplo de 1000");
    }
    private String enmascarar(Long cuentaId) {
        String v = cuentaId.toString(); return "****" + v.substring(Math.max(0, v.length()-2));
    }
}
