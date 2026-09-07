package cl.duoc.bank_batch.bff.movil.servicio;

import cl.duoc.bank_batch.bff.compartido.excepcion.OperacionInvalidaException;
import cl.duoc.bank_batch.bff.compartido.modelo.CuentaBancaria;
import cl.duoc.bank_batch.bff.compartido.modelo.MovimientoCuenta;
import cl.duoc.bank_batch.bff.compartido.repositorio.RepositorioDatosBancarios;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServicioBffMovilTests {

    private final RepositorioDatosBancarios repositorio =
            mock(RepositorioDatosBancarios.class);
    private final ServicioBffMovil servicio = new ServicioBffMovil(repositorio);

    @Test
    void entregaSoloDatosEsencialesEnElInicioMovil() {
        when(repositorio.buscarCuenta(106L)).thenReturn(Optional.of(cuenta()));

        var respuesta = servicio.obtenerInicio(106L);

        assertThat(respuesta.canal()).isEqualTo("MOVIL");
        assertThat(respuesta.cuentaId()).isEqualTo(106L);
        assertThat(respuesta.saldoDisponible()).isEqualByComparingTo("12180.00");
    }

    @Test
    void limitaLosMovimientosParaReducirElPesoDeLaRespuesta() {
        when(repositorio.buscarCuenta(106L)).thenReturn(Optional.of(cuenta()));
        when(repositorio.buscarUltimosMovimientos(106L, 5)).thenReturn(List.of(
                new MovimientoCuenta(
                        LocalDate.of(2024, 1, 10),
                        "deposito",
                        new BigDecimal("5000.00"),
                        "Dato completo no expuesto por móvil",
                        "data/semana_3/cuentas_anuales.csv"
                )
        ));

        var respuesta = servicio.obtenerMovimientos(106L, 5);

        assertThat(respuesta).hasSize(1);
        assertThat(respuesta.get(0).tipo()).isEqualTo("deposito");
    }

    @Test
    void rechazaLimitesQueExcedenElMaximoMovil() {
        assertThatThrownBy(() -> servicio.obtenerMovimientos(106L, 11))
                .isInstanceOf(OperacionInvalidaException.class);
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
