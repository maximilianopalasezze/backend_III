package cl.duoc.cloud.operaciones;

import cl.duoc.cloud.operaciones.config.SecurityConfig; import cl.duoc.cloud.operaciones.controller.OperacionesController; import cl.duoc.cloud.operaciones.model.*; import cl.duoc.cloud.operaciones.service.OperacionesService;
import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest; import org.springframework.boot.test.mock.mockito.MockBean; import org.springframework.context.annotation.Import; import org.springframework.http.MediaType; import org.springframework.test.web.servlet.MockMvc; import java.math.BigDecimal; import java.time.LocalDateTime;
import static org.mockito.ArgumentMatchers.*; import static org.mockito.Mockito.when; import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic; import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@WebMvcTest(OperacionesController.class) @Import(SecurityConfig.class)
class SeguridadOperacionesTests { @Autowired MockMvc mvc; @MockBean OperacionesService service;
 @Test void sinCredencialesEs401() throws Exception {mvc.perform(post("/api/operaciones/cuentas/106/retiros").contentType(MediaType.APPLICATION_JSON).content("{\"monto\":2000}")).andExpect(status().isUnauthorized());}
 @Test void viewerEs403() throws Exception {mvc.perform(post("/api/operaciones/cuentas/106/retiros").with(httpBasic("viewer","ChangeMe-Semana6-Viewer!")).contentType(MediaType.APPLICATION_JSON).content("{\"monto\":2000}")).andExpect(status().isForbidden());}
 @Test void serviceEs200() throws Exception {when(service.retirar(eq(106L),any())).thenReturn(new RetiroResponse("x","APROBADA",106L,new BigDecimal("2000"),new BigDecimal("10000"),new BigDecimal("8000"),LocalDateTime.now())); mvc.perform(post("/api/operaciones/cuentas/106/retiros").with(httpBasic("svc-bff","ChangeMe-Semana6-Backend!")).contentType(MediaType.APPLICATION_JSON).content("{\"monto\":2000}")).andExpect(status().isOk());}
}
