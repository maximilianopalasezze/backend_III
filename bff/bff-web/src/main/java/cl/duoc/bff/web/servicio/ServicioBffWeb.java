package cl.duoc.bff.web.servicio;

import cl.duoc.bff.compartido.excepcion.RecursoNoEncontradoException;
import cl.duoc.bff.compartido.modelo.EstadoAnualCuenta;
import cl.duoc.bff.compartido.modelo.InteresCuenta;
import cl.duoc.bff.compartido.repositorio.RepositorioDatosBancarios;
import cl.duoc.bff.web.dto.CuentaWebResponse;
import cl.duoc.bff.web.dto.ResumenWebResponse;
import org.springframework.stereotype.Service;

@Service
public class ServicioBffWeb {

    private static final int LIMITE_MOVIMIENTOS_WEB = 20;

    private final RepositorioDatosBancarios repositorio;

    public ServicioBffWeb(RepositorioDatosBancarios repositorio) {
        this.repositorio = repositorio;
    }

    public CuentaWebResponse obtenerCuenta(Long cuentaId) {
        var cuenta = repositorio.buscarCuenta(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la cuenta " + cuentaId
                ));

        var movimientos = repositorio
                .buscarUltimosMovimientos(cuentaId, LIMITE_MOVIMIENTOS_WEB)
                .stream()
                .map(movimiento -> new CuentaWebResponse.Movimiento(
                        movimiento.fecha(),
                        movimiento.tipo(),
                        movimiento.monto(),
                        movimiento.descripcion(),
                        movimiento.archivoOrigen()
                ))
                .toList();

        return new CuentaWebResponse(
                "WEB",
                new CuentaWebResponse.Titular(
                        cuenta.cuentaId(),
                        cuenta.nombre(),
                        cuenta.edad()
                ),
                new CuentaWebResponse.Producto(
                        cuenta.tipoCuenta(),
                        cuenta.saldo(),
                        cuenta.fechaActualizacion()
                ),
                repositorio.buscarUltimoInteres(cuentaId)
                        .map(this::mapearInteres)
                        .orElse(null),
                repositorio.buscarUltimoEstadoAnual(cuentaId)
                        .map(this::mapearEstadoAnual)
                        .orElse(null),
                movimientos
        );
    }

    public ResumenWebResponse obtenerResumen() {
        var resumen = repositorio.obtenerResumenOperacional();

        return new ResumenWebResponse(
                "WEB",
                resumen.cuentas(),
                resumen.transacciones(),
                resumen.movimientosAnuales(),
                resumen.registrosRechazados(),
                resumen.saldoTotal()
        );
    }

    private CuentaWebResponse.Interes mapearInteres(InteresCuenta interes) {
        return new CuentaWebResponse.Interes(
                interes.periodo(),
                interes.saldoInicial(),
                interes.tasaInteres(),
                interes.interesCalculado(),
                interes.saldoFinal(),
                interes.archivoOrigen()
        );
    }

    private CuentaWebResponse.EstadoAnual mapearEstadoAnual(
            EstadoAnualCuenta estado) {

        return new CuentaWebResponse.EstadoAnual(
                estado.anio(),
                estado.cantidadMovimientos(),
                estado.totalDepositos(),
                estado.totalRetiros(),
                estado.totalCompras(),
                estado.totalPagos(),
                estado.saldoAnual(),
                estado.archivoOrigen()
        );
    }
}
