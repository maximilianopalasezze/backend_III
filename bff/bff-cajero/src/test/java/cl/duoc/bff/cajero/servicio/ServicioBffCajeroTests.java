package cl.duoc.bff.cajero.servicio;

import cl.duoc.bff.cajero.dto.SolicitudRetiro;
import cl.duoc.bff.compartido.excepcion.OperacionInvalidaException;
import cl.duoc.bff.compartido.excepcion.SaldoInsuficienteException;
import cl.duoc.bff.compartido.modelo.CuentaBancaria;
import cl.duoc.bff.compartido.repositorio.RepositorioDatosBancarios;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioBffCajeroTests {

    private final RepositorioDatosBancarios repositorio =
            mock(RepositorioDatosBancarios.class);
    private final ServicioBffCajero servicio = new ServicioBffCajero(repositorio);

    @Test
    void enmascaraLaCuentaAlConsultarSaldo() {
        when(repositorio.buscarCuenta(106L)).thenReturn(Optional.of(cuenta()));

        var respuesta = servicio.consultarSaldo(106L);

        assertThat(respuesta.canal()).isEqualTo("CAJERO");
        assertThat(respuesta.cuentaEnmascarada()).isEqualTo("****06");
        assertThat(respuesta.saldoDisponible()).isEqualByComparingTo("12180.00");
    }

    @Test
    void realizaElRetiroYRegistraLaAuditoria() {
        when(repositorio.buscarCuentaParaActualizar(106L))
                .thenReturn(Optional.of(cuenta()));

        var respuesta = servicio.retirar(
                106L,
                new SolicitudRetiro(new BigDecimal("2000.00"))
        );

        assertThat(respuesta.estado()).isEqualTo("APROBADA");
        assertThat(respuesta.saldoDisponible()).isEqualByComparingTo("10180.00");
        verify(repositorio).actualizarSaldo(106L, new BigDecimal("10180.00"));
        verify(repositorio).registrarRetiro(
                anyString(),
                org.mockito.ArgumentMatchers.eq(106L),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("2000.00")),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("12180.00")),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("10180.00"))
        );
    }

    @Test
    void rechazaUnRetiroConSaldoInsuficiente() {
        when(repositorio.buscarCuentaParaActualizar(106L))
                .thenReturn(Optional.of(cuenta()));

        assertThatThrownBy(() -> servicio.retirar(
                106L,
                new SolicitudRetiro(new BigDecimal("13000.00"))))
                .isInstanceOf(SaldoInsuficienteException.class);

        verify(repositorio, never())
                .actualizarSaldo(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rechazaMontosQueNoSonMultiplosDeMil() {
        assertThatThrownBy(() -> servicio.retirar(
                106L,
                new SolicitudRetiro(new BigDecimal("1500.00"))))
                .isInstanceOf(OperacionInvalidaException.class);
    }

    @Test
    void rechazaMontosInferioresAlMinimo() {
        assertThatThrownBy(() -> servicio.retirar(
                106L,
                new SolicitudRetiro(new BigDecimal("500.00"))))
                .isInstanceOf(OperacionInvalidaException.class);

        verify(repositorio, never())
                .buscarCuentaParaActualizar(org.mockito.ArgumentMatchers.anyLong());
    }

    private CuentaBancaria cuenta() {
        return new CuentaBancaria(
                106L,
                "Jane Smith",
                new BigDecimal("12180.00"),
                40,
                "prestamo",
                LocalDateTime.of(2026, 9, 6, 12, 0)
        );
    }
}
