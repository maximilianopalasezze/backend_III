package cl.duoc.cloud.movimientos;

import cl.duoc.cloud.movimientos.config.SecurityConfig; import cl.duoc.cloud.movimientos.controller.MovimientosController; import cl.duoc.cloud.movimientos.repo.MovimientosRepository;
import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest; import org.springframework.boot.test.mock.mockito.MockBean; import org.springframework.context.annotation.Import; import org.springframework.test.web.servlet.MockMvc; import java.util.List;
import static org.mockito.Mockito.when; import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic; import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@WebMvcTest(MovimientosController.class) @Import(SecurityConfig.class)
class SeguridadMovimientosTests { @Autowired MockMvc mvc; @MockBean MovimientosRepository repo;
 @Test void sinCredencialesEs401() throws Exception {mvc.perform(get("/api/movimientos/cuenta/106")).andExpect(status().isUnauthorized());}
 @Test void viewerEs403() throws Exception {mvc.perform(get("/api/movimientos/cuenta/106").with(httpBasic("viewer","ChangeMe-Semana6-Viewer!"))).andExpect(status().isForbidden());}
 @Test void serviceEs200() throws Exception {when(repo.ultimos(106L,10)).thenReturn(List.of()); mvc.perform(get("/api/movimientos/cuenta/106").with(httpBasic("svc-bff","ChangeMe-Semana6-Backend!"))).andExpect(status().isOk());}
}
