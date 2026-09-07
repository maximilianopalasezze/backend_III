package cl.duoc.bank_batch.bff.web.servicio;

import cl.duoc.bank_batch.bff.compartido.modelo.CuentaBancaria;
import cl.duoc.bank_batch.bff.compartido.modelo.ResumenOperacional;
import cl.duoc.bank_batch.bff.compartido.repositorio.RepositorioDatosBancarios;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServicioBffWebTests {

    private final RepositorioDatosBancarios repositorio =
            mock(RepositorioDatosBancarios.class);
    private final ServicioBffWeb servicio = new ServicioBffWeb(repositorio);

    @Test
    void entregaInformacionCompletaParaElCanalWeb() {
        var cuenta = new CuentaBancaria(
                106L,
                "Jane Smith",
                new BigDecimal("12180.00"),
                40,
                "prestamo",
                LocalDateTime.of(2026, 9, 6, 12, 0)
        );

        when(repositorio.buscarCuenta(106L)).thenReturn(Optional.of(cuenta));
        when(repositorio.buscarUltimosMovimientos(106L, 20))
                .thenReturn(List.of());
        when(repositorio.buscarUltimoInteres(106L)).thenReturn(Optional.empty());
        when(repositorio.buscarUltimoEstadoAnual(106L)).thenReturn(Optional.empty());

        var respuesta = servicio.obtenerCuenta(106L);

        assertThat(respuesta.canal()).isEqualTo("WEB");
        assertThat(respuesta.titular().nombre()).isEqualTo("Jane Smith");
        assertThat(respuesta.titular().edad()).isEqualTo(40);
        assertThat(respuesta.producto().saldoDisponible())
                .isEqualByComparingTo("12180.00");
    }

    @Test
    void entregaResumenParaLaInterfazComplejaWeb() {
        when(repositorio.obtenerResumenOperacional()).thenReturn(
                new ResumenOperacional(50L, 491L, 723L, 1736L,
                        new BigDecimal("420850.00"))
        );

        var respuesta = servicio.obtenerResumen();

        assertThat(respuesta.canal()).isEqualTo("WEB");
        assertThat(respuesta.transaccionesProcesadas()).isEqualTo(491L);
        assertThat(respuesta.movimientosAnualesProcesados()).isEqualTo(723L);
    }
}
