package cl.duoc.cloud.clientes;

import cl.duoc.cloud.clientes.config.ApiErrorHandler;
import cl.duoc.cloud.clientes.config.SecurityConfig;
import cl.duoc.cloud.clientes.controller.ClientesController;
import cl.duoc.cloud.clientes.model.ClienteResponse;
import cl.duoc.cloud.clientes.service.ClientesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientesController.class)
@Import({SecurityConfig.class, ApiErrorHandler.class})
class SeguridadClientesTests {
    @Autowired MockMvc mvc;
    @MockBean ClientesService service;
    private static final String SOLICITUD = """
            {"clienteId":900101,"cuentaId":101,"nombre":"Jane Smith","email":"jane@example.com",
             "telefono":"+56911111111","direccion":"Santiago","perfil":"ESTANDAR"}
            """;

    @Test void consultaSinCredencialesEs401() throws Exception {
        mvc.perform(get("/api/clientes/900101")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void creacionSinCredencialesEs401() throws Exception {
        mvc.perform(post("/api/clientes").contentType("application/json").content(SOLICITUD))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void viewerNoPuedeConsultarNiModificarDatosPersonales() throws Exception {
        mvc.perform(get("/api/clientes/900101").with(httpBasic("viewer", "ChangeMe-Semana6-Viewer!")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/clientes/900101").with(httpBasic("viewer", "ChangeMe-Semana6-Viewer!"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void servicioPuedeCrearClienteCon201() throws Exception {
        when(service.crear(any())).thenReturn(new ClienteResponse(900101L, "Jane Smith", "jane@example.com",
                "+56911111111", "Santiago", "ESTANDAR", 0, LocalDateTime.now(), List.of(101L)));
        mvc.perform(post("/api/clientes").with(httpBasic("svc-bff", "ChangeMe-Semana6-Backend!"))
                        .contentType("application/json").content(SOLICITUD))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.clienteId").value(900101))
                .andExpect(jsonPath("$.cuentas[0]").value(101));
    }

    @Test void emailInvalidoEs400YNoLlegaAlServicio() throws Exception {
        mvc.perform(post("/api/clientes").with(httpBasic("svc-bff", "ChangeMe-Semana6-Backend!"))
                        .contentType("application/json").content(SOLICITUD.replace("jane@example.com", "sin-correo")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void perfilDesconocidoEs400YNoLlegaAlServicio() throws Exception {
        mvc.perform(post("/api/clientes").with(httpBasic("svc-bff", "ChangeMe-Semana6-Backend!"))
                        .contentType("application/json").content(SOLICITUD.replace("ESTANDAR", "ADMIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void conflictoDeVersionConserva409EnLaAPI() throws Exception {
        when(service.actualizar(eq(900101L), any())).thenThrow(new ApiErrorHandler.ClienteConflicto("Consulta la versión actual"));
        String actualizacion = """
                {"nombre":"Jane","email":"jane@example.com","telefono":"+56911111111",
                 "direccion":"Santiago","perfil":"ESTANDAR","version":0}
                """;
        mvc.perform(put("/api/clientes/900101").with(httpBasic("svc-bff", "ChangeMe-Semana6-Backend!"))
                        .contentType("application/json").content(actualizacion))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.estado").value(409));
    }
}
