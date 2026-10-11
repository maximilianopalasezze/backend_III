package cl.duoc.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
        "eureka.client.enabled=false","OAUTH_WEB_SECRET=test-web-secret","OAUTH_MOVIL_SECRET=test-movil-secret",
        "OAUTH_CAJERO_SECRET=test-cajero-secret","OAUTH_WEB_CUENTA_ID=900201",
        "OAUTH_WEB_CUENTAS=900201,900203","OAUTH_MOVIL_CUENTA_ID=900201","OAUTH_CAJERO_CUENTA_ID=900201"})
@AutoConfigureMockMvc
class AuthorizationServerTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtDecoder decoder;
    @Test void emiteTokenFirmadoConScopesWebYCuentasDelServidor() throws Exception {
        String body=mvc.perform(post("/oauth2/token").with(httpBasic("bff-web","test-web-secret"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type","client_credentials")
                .param("scope","web:lectura web:gestion web:pagos")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var token=decoder.decode(json.readTree(body).path("access_token").asText());
        assertThat(token.getClaimAsString("canal")).isEqualTo("WEB");
        assertThat(token.getAudience()).containsExactly("bff-web");
        assertThat(token.getClaimAsString("cuentaId")).isEqualTo("900201");
        assertThat(token.getClaimAsStringList("cuentas")).containsExactly("900201","900203");
        assertThat(token.getClaimAsStringList("scope")).containsExactlyInAnyOrder("web:lectura","web:gestion","web:pagos");
    }
    @Test void pedirSoloLecturaNoAgregaPermisosDeEscritura() throws Exception {
        String body=mvc.perform(post("/oauth2/token").with(httpBasic("bff-web","test-web-secret"))
                .param("grant_type","client_credentials").param("scope","web:lectura"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var token=decoder.decode(json.readTree(body).path("access_token").asText());
        assertThat(token.getClaimAsStringList("scope")).containsExactly("web:lectura");
    }
    @Test void cajeroNoPuedeSolicitarPermisosWeb() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("bff-cajero","test-cajero-secret"))
                .param("grant_type","client_credentials").param("scope","web:gestion"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_scope"));
    }
    @Test void movilPuedeSolicitarPagosPeroNoGestion() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("bff-movil","test-movil-secret"))
                .param("grant_type","client_credentials").param("scope","movil:lectura movil:pagos"))
                .andExpect(status().isOk());
        mvc.perform(post("/oauth2/token").with(httpBasic("bff-movil","test-movil-secret"))
                .param("grant_type","client_credentials").param("scope","web:gestion"))
                .andExpect(status().isBadRequest());
    }
    @Test void secretoIncorrectoNoEmiteToken() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("bff-web","incorrecto"))
                .param("grant_type","client_credentials")).andExpect(status().isUnauthorized());
    }
}
