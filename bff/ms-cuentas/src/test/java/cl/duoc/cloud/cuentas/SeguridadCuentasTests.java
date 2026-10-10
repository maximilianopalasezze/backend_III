package cl.duoc.cloud.cuentas;

import cl.duoc.cloud.cuentas.config.SecurityConfig;
import cl.duoc.cloud.cuentas.controller.CuentasController;
import cl.duoc.cloud.cuentas.model.CuentaResponse;
import cl.duoc.cloud.cuentas.repo.CuentasRepository;
import cl.duoc.cloud.cuentas.service.CuentasService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.Optional;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CuentasController.class) @Import(SecurityConfig.class)
class SeguridadCuentasTests {
 @Autowired MockMvc mvc; @MockBean CuentasRepository repo; @MockBean CuentasService service;
 @Test void sinCredencialesEs401() throws Exception { mvc.perform(get("/api/cuentas/106")).andExpect(status().isUnauthorized()); }
 @Test void viewerEs403() throws Exception { mvc.perform(get("/api/cuentas/106").with(httpBasic("viewer","ChangeMe-Semana6-Viewer!"))).andExpect(status().isForbidden()); }
 @Test void serviceEs200() throws Exception { when(repo.cuenta(106L)).thenReturn(Optional.of(new CuentaResponse(106L,"Jane",new BigDecimal("1000"),40,"ahorro",LocalDateTime.now(),"ACTIVA"))); mvc.perform(get("/api/cuentas/106").with(httpBasic("svc-bff","ChangeMe-Semana6-Backend!"))).andExpect(status().isOk()); }
 @Test void aperturaSinCredencialesEs401() throws Exception {
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/cuentas").contentType("application/json").content("{\"cuentaId\":900001,\"nombre\":\"Prueba\",\"edad\":30,\"tipoCuenta\":\"ahorro\"}")).andExpect(status().isUnauthorized());
 }
 @Test void viewerNoPuedeCerrar() throws Exception {
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/cuentas/106/cierre").with(httpBasic("viewer","ChangeMe-Semana6-Viewer!"))).andExpect(status().isForbidden());
 }
 @Test void aperturaConTipoInvalidoEs400() throws Exception {
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/cuentas").with(httpBasic("svc-bff","ChangeMe-Semana6-Backend!")).contentType("application/json").content("{\"cuentaId\":900001,\"nombre\":\"Prueba\",\"edad\":30,\"tipoCuenta\":\"desconocida\"}")).andExpect(status().isBadRequest());
 }
 @Test void aperturaValidaEs201() throws Exception {
  when(service.abrir(org.mockito.ArgumentMatchers.any())).thenReturn(new CuentaResponse(900001L,"Prueba",BigDecimal.ZERO,30,"ahorro",LocalDateTime.now(),"ACTIVA"));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/cuentas").with(httpBasic("svc-bff","ChangeMe-Semana6-Backend!")).contentType("application/json").content("{\"cuentaId\":900001,\"nombre\":\"Prueba\",\"edad\":30,\"tipoCuenta\":\"ahorro\"}")).andExpect(status().isCreated());
 }
}

