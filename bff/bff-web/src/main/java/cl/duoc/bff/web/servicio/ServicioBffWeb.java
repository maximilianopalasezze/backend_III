package cl.duoc.bff.web.servicio;

import cl.duoc.bff.compartido.integracion.ClienteServiciosBanco;
import cl.duoc.bff.compartido.modelo.EstadoAnualCuenta;
import cl.duoc.bff.compartido.modelo.InteresCuenta;
import cl.duoc.bff.web.dto.CuentaWebResponse;
import cl.duoc.bff.web.dto.ResumenWebResponse;
import org.springframework.stereotype.Service;

@Service
public class ServicioBffWeb {
    private static final int LIMITE_MOVIMIENTOS_WEB = 20;
    private final ClienteServiciosBanco backend;
    public ServicioBffWeb(ClienteServiciosBanco backend) { this.backend = backend; }

    public CuentaWebResponse obtenerCuenta(Long cuentaId) {
        var cuenta = backend.obtenerCuenta(cuentaId);
        var movimientos = backend.obtenerMovimientos(cuentaId, LIMITE_MOVIMIENTOS_WEB).stream()
                .map(m -> new CuentaWebResponse.Movimiento(m.fecha(), m.tipo(), m.monto(), m.descripcion(), m.archivoOrigen()))
                .toList();
        return new CuentaWebResponse(
                "WEB",
                new CuentaWebResponse.Titular(cuenta.cuentaId(), cuenta.nombre(), cuenta.edad()),
                new CuentaWebResponse.Producto(cuenta.tipoCuenta(), cuenta.saldo(), cuenta.fechaActualizacion()),
                mapearInteres(backend.obtenerUltimoInteres(cuentaId)),
                mapearEstadoAnual(backend.obtenerUltimoEstadoAnual(cuentaId)),
                movimientos);
    }

    public ResumenWebResponse obtenerResumen() {
        var resumen = backend.obtenerResumen();
        return new ResumenWebResponse("WEB", resumen.cuentas(), resumen.transacciones(),
                resumen.movimientosAnuales(), resumen.registrosRechazados(), resumen.saldoTotal());
    }

    private CuentaWebResponse.Interes mapearInteres(InteresCuenta i) {
        return i == null ? null : new CuentaWebResponse.Interes(i.periodo(), i.saldoInicial(), i.tasaInteres(),
                i.interesCalculado(), i.saldoFinal(), i.archivoOrigen());
    }
    private CuentaWebResponse.EstadoAnual mapearEstadoAnual(EstadoAnualCuenta e) {
        return e == null ? null : new CuentaWebResponse.EstadoAnual(e.anio(), e.cantidadMovimientos(),
                e.totalDepositos(), e.totalRetiros(), e.totalCompras(), e.totalPagos(), e.saldoAnual(), e.archivoOrigen());
    }
}
