package cl.duoc.cloud.pagos;

import cl.duoc.cloud.pagos.config.*;
import cl.duoc.cloud.pagos.controller.PagosController;
import cl.duoc.cloud.pagos.model.*;
import cl.duoc.cloud.pagos.service.PagosService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PagosController.class, properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false", "spring.config.import="})
@Import({SecurityConfig.class, ApiErrorHandler.class})
class SeguridadPagosTests {
    @Autowired MockMvc mvc;
    @MockBean PagosService service;
    private static final String BASE = "/api/pagos/cuentas/101";
    private static final String DEPOSITO = "{\"solicitudId\":\"deposito-001\",\"monto\":100,\"concepto\":\"Depósito\"}";

    @Test void sinCredencialesNoPermiteCrearNiLeerOperaciones() throws Exception {
        mvc.perform(post(BASE + "/depositos").contentType(MediaType.APPLICATION_JSON).content(DEPOSITO)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/solicitudes/deposito-001")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void viewerAutenticadoNoPuedeCobrarNiLeerRecibos() throws Exception {
        mvc.perform(post(BASE + "/depositos").with(httpBasic("viewer", "ChangeMe-Semana6-Viewer!"))
                .contentType(MediaType.APPLICATION_JSON).content(DEPOSITO)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/solicitudes/deposito-001").with(httpBasic("viewer", "ChangeMe-Semana6-Viewer!"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void credencialesDeServicioPermitenDepositoValidado() throws Exception {
        when(service.deposito(eq(101L), any())).thenReturn(new OperacionPagoResponse("deposito-001", "referencia", "DEPOSITO", 101L, null,
                new BigDecimal("100"), "", "Depósito", new BigDecimal("5000"), new BigDecimal("5100"), null, null, "APROBADA", LocalDateTime.of(2026,10,10,19,50)));
        mvc.perform(post(BASE + "/depositos").with(httpBasic("svc-bff", "ChangeMe-Semana6-Backend!"))
                .contentType(MediaType.APPLICATION_JSON).content(DEPOSITO)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROBADA")).andExpect(jsonPath("$.saldoPosterior").value(5100));
    }

    @Test void montoCeroOPrecisionExcesivaSeRechazanAntesDeCobrar() throws Exception {
        for (String monto : new String[]{"0", "1.001"}) {
            mvc.perform(post(BASE + "/depositos").with(httpBasic("svc-bff", "ChangeMe-Semana6-Backend!"))
                    .contentType(MediaType.APPLICATION_JSON).content(DEPOSITO.replace("100", monto))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void pagoExigeBeneficiario() throws Exception {
        mvc.perform(post(BASE + "/pagos").with(httpBasic("svc-bff", "ChangeMe-Semana6-Backend!"))
                .contentType(MediaType.APPLICATION_JSON).content(DEPOSITO)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void transferenciaExigeDestinoPositivoYClaveDeSolicitudValida() throws Exception {
        mvc.perform(post(BASE + "/transferencias").with(httpBasic("svc-bff", "ChangeMe-Semana6-Backend!"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"solicitudId\":\"corta\",\"cuentaDestinoId\":0,\"monto\":100,\"concepto\":\"Transferencia\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void conflictoDeSaldoProduceRespuestaControlada() throws Exception {
        when(service.transferencia(eq(101L), any())).thenThrow(new ApiErrorHandler.OperacionConflicto("Saldo insuficiente"));
        mvc.perform(post(BASE + "/transferencias").with(httpBasic("svc-bff", "ChangeMe-Semana6-Backend!"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"solicitudId\":\"transferencia-01\",\"cuentaDestinoId\":102,\"monto\":100,\"concepto\":\"Transferencia\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje").value("Saldo insuficiente"));
    }
}
