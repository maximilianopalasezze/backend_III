package cl.duoc.bff.cajero.controlador;

import cl.duoc.bff.cajero.servicio.ServicioBffCajero;
import cl.duoc.bff.compartido.configuracion.*;
import cl.duoc.bff.compartido.excepcion.ManejadorErroresBff;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ControladorBffCajero.class, properties = {
        "bff.canal=CAJERO", "bff.issuer=http://localhost:9000", "bff.audience=bff-cajero",
        "bff.jwk-set-uri=http://localhost:9000/oauth2/jwks", "spring.cloud.config.enabled=false"})
@Import({ConfiguracionSeguridadBff.class, AccesoCuenta.class, ManejadorErroresBff.class})
class CanalCajeroSeguridadTests {
    @Autowired MockMvc mvc;
    @MockitoBean ServicioBffCajero servicio;
    @MockitoBean JwtDecoder decoder;
    @Test void cajeroNoExponeTransferenciasNiConScopeDeOtroCanal() throws Exception {
        mvc.perform(post("/api/bff/cajero/cuentas/900201/transferencias").secure(true)
                .with(jwt().jwt(j -> j.claim("cuentaId","900201"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_web:pagos")))
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }
    @Test void retiroExigeScopeDeRetiro() throws Exception {
        mvc.perform(post("/api/bff/cajero/cuentas/900201/retiros").secure(true)
                .with(jwt().jwt(j -> j.claim("cuentaId","900201"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_cajero:lectura")))
                .contentType("application/json").content("{\"monto\":1000}")).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }
    @Test void saldoSinTokenMantiene401() throws Exception {
        mvc.perform(get("/api/bff/cajero/cuentas/900201/saldo").secure(true)).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }
}
