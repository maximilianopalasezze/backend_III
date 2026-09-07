package cl.duoc.bank_batch.bff.movil.servicio;

import cl.duoc.bank_batch.bff.compartido.excepcion.OperacionInvalidaException;
import cl.duoc.bank_batch.bff.compartido.excepcion.RecursoNoEncontradoException;
import cl.duoc.bank_batch.bff.compartido.repositorio.RepositorioDatosBancarios;
import cl.duoc.bank_batch.bff.movil.dto.InicioMovilResponse;
import cl.duoc.bank_batch.bff.movil.dto.MovimientoMovilResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("movil")
public class ServicioBffMovil {

    private static final int LIMITE_MAXIMO_MOVIL = 10;

    private final RepositorioDatosBancarios repositorio;

    public ServicioBffMovil(RepositorioDatosBancarios repositorio) {
        this.repositorio = repositorio;
    }

    public InicioMovilResponse obtenerInicio(Long cuentaId) {
        var cuenta = repositorio.buscarCuenta(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la cuenta " + cuentaId
                ));

        return new InicioMovilResponse(
                "MOVIL",
                cuenta.cuentaId(),
                cuenta.nombre(),
                cuenta.tipoCuenta(),
                cuenta.saldo(),
                cuenta.fechaActualizacion()
        );
    }

    public List<MovimientoMovilResponse> obtenerMovimientos(
            Long cuentaId,
            int limite) {

        if (limite < 1 || limite > LIMITE_MAXIMO_MOVIL) {
            throw new OperacionInvalidaException(
                    "El límite móvil debe estar entre 1 y " + LIMITE_MAXIMO_MOVIL
            );
        }

        if (repositorio.buscarCuenta(cuentaId).isEmpty()) {
            throw new RecursoNoEncontradoException(
                    "No existe la cuenta " + cuentaId
            );
        }

        return repositorio.buscarUltimosMovimientos(cuentaId, limite)
                .stream()
                .map(movimiento -> new MovimientoMovilResponse(
                        movimiento.fecha(),
                        movimiento.tipo(),
                        movimiento.monto()
                ))
                .toList();
    }
}
