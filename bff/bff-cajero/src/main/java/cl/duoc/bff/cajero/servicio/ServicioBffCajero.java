package cl.duoc.bff.cajero.servicio;

import cl.duoc.bff.cajero.dto.RetiroCajeroResponse;
import cl.duoc.bff.cajero.dto.SaldoCajeroResponse;
import cl.duoc.bff.cajero.dto.SolicitudRetiro;
import cl.duoc.bff.compartido.excepcion.OperacionInvalidaException;
import cl.duoc.bff.compartido.excepcion.RecursoNoEncontradoException;
import cl.duoc.bff.compartido.excepcion.SaldoInsuficienteException;
import cl.duoc.bff.compartido.repositorio.RepositorioDatosBancarios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ServicioBffCajero {

    private static final BigDecimal MULTIPLO_RETIRO = new BigDecimal("1000.00");

    private final RepositorioDatosBancarios repositorio;

    public ServicioBffCajero(RepositorioDatosBancarios repositorio) {
        this.repositorio = repositorio;
    }

    public SaldoCajeroResponse consultarSaldo(Long cuentaId) {
        var cuenta = repositorio.buscarCuenta(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la cuenta " + cuentaId
                ));

        return new SaldoCajeroResponse(
                "CAJERO",
                enmascarar(cuenta.cuentaId()),
                "CLP",
                cuenta.saldo(),
                cuenta.saldo().compareTo(MULTIPLO_RETIRO) >= 0
        );
    }

    @Transactional
    public RetiroCajeroResponse retirar(Long cuentaId, SolicitudRetiro solicitud) {
        BigDecimal monto = solicitud.monto();
        validarMonto(monto);

        var cuenta = repositorio.buscarCuentaParaActualizar(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la cuenta " + cuentaId
                ));

        if (cuenta.saldo().compareTo(monto) < 0) {
            throw new SaldoInsuficienteException(
                    "La cuenta no tiene saldo suficiente para realizar el retiro"
            );
        }

        BigDecimal saldoPosterior = cuenta.saldo().subtract(monto);
        String referencia = UUID.randomUUID().toString();

        repositorio.actualizarSaldo(cuentaId, saldoPosterior);
        repositorio.registrarRetiro(
                referencia,
                cuentaId,
                monto,
                cuenta.saldo(),
                saldoPosterior
        );

        return new RetiroCajeroResponse(
                "CAJERO",
                referencia,
                "APROBADA",
                enmascarar(cuentaId),
                monto,
                cuenta.saldo(),
                saldoPosterior,
                LocalDateTime.now()
        );
    }

    private void validarMonto(BigDecimal monto) {
        if (monto == null || monto.compareTo(MULTIPLO_RETIRO) < 0) {
            throw new OperacionInvalidaException(
                    "El monto mínimo del retiro es 1000"
            );
        }

        if (monto.remainder(MULTIPLO_RETIRO).compareTo(BigDecimal.ZERO) != 0) {
            throw new OperacionInvalidaException(
                    "El monto del retiro debe ser múltiplo de 1000"
            );
        }
    }

    private String enmascarar(Long cuentaId) {
        String valor = cuentaId.toString();
        String ultimosDigitos = valor.substring(Math.max(0, valor.length() - 2));
        return "****" + ultimosDigitos;
    }
}
