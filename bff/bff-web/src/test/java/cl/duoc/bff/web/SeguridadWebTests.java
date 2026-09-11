package cl.duoc.bff.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK, properties={
    "bff.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "bff.password=password-integracion-completo",
    "bff.password-consulta=password-integracion-consulta",
    "server.ssl.enabled=false",
    "server.ssl.key-store=classpath:no-necesario-en-mock.p12",
    "server.ssl.key-store-password=solo-test",
    "spring.datasource.url=jdbc:h2:mem:seguridad_web;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:schema-integracion.sql"
})
class SeguridadWebTests {
    @Autowired WebApplicationContext context;
    @Autowired JwtEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach void preparar() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("DELETE FROM operaciones_cajero");jdbc.update("DELETE FROM cuentas");
        jdbc.update("INSERT INTO cuentas(cuenta_id,nombre,saldo,edad,tipo_cuenta) VALUES(106,'Prueba',12180,40,'prestamo')");
    }
    String login(boolean consulta) throws Exception {
        String usuario=consulta?"web-consulta":"web-demo";
        String password=consulta?"password-integracion-consulta":"password-integracion-completo";
        String body=mvc.perform(post("/api/auth/token").secure(true).contentType("application/json")
            .content("{\"usuario\":\""+usuario+"\",\"password\":\""+password+"\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body,"$.access_token");
    }
    String token(String issuer,String audience,String canal,Instant expires,String cuenta) {
        Instant now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer(issuer).subject("prueba")
            .audience(List.of(audience)).issuedAt(now.minusSeconds(600))
            .notBefore(now.minusSeconds(600)).expiresAt(expires)
            .claim("canal",canal).claim("cuentaId",cuenta)
            .claim("scope","web:lectura").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    @Test void loginYConsultaConTokenFirmado() throws Exception {
        mvc.perform(get("/api/bff/web/cuentas/106").secure(true).header("Authorization","Bearer "+login(false)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.canal").value("WEB"))
            .andExpect(header().exists("Strict-Transport-Security"));
    }
    @Test void rechazaPasswordIncorrecta() throws Exception {
        mvc.perform(post("/api/auth/token").secure(true).contentType("application/json")
            .content("{\"usuario\":\"web-demo\",\"password\":\"incorrecta\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void rechazaAusenciaDeTokenYAntiguaApiKey() throws Exception {
        mvc.perform(get("/api/bff/web/cuentas/106").secure(true)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bff/web/cuentas/106").secure(true).header("X-BFF-API-Key","web-local-key"))
            .andExpect(status().isUnauthorized());
    }
    @Test void rechazaTokenExpirado() throws Exception {
        String t=token("urn:banco-xyz:web","bff-web","WEB",Instant.now().minusSeconds(1),"106");
        mvc.perform(get("/api/bff/web/cuentas/106").secure(true).header("Authorization","Bearer "+t))
            .andExpect(status().isUnauthorized());
    }
    @Test void rechazaFirmaAlterada() throws Exception {
        String t=login(false);String[] partes=t.split("\\.");
        String firma=partes[2];partes[2]=(firma.charAt(0)=='A'?"B":"A")+firma.substring(1);
        mvc.perform(get("/api/bff/web/cuentas/106").secure(true).header("Authorization","Bearer "+String.join(".",partes)))
            .andExpect(status().isUnauthorized());
    }
    @Test void validaEmisorAudienciaYCanal() throws Exception {
        for(String t:List.of(
                token("urn:otro","bff-web","WEB",Instant.now().plusSeconds(60),"106"),
                token("urn:banco-xyz:web","bff-otro","WEB",Instant.now().plusSeconds(60),"106"),
                token("urn:banco-xyz:web","bff-web","OTRO",Instant.now().plusSeconds(60),"106"))) {
            mvc.perform(get("/api/bff/web/cuentas/106").secure(true).header("Authorization","Bearer "+t))
                .andExpect(status().isUnauthorized());
        }
    }
    @Test void rechazaRutaDeOtroCanal() throws Exception {
        mvc.perform(get("/api/bff/movil/cuentas/106").secure(true).header("Authorization","Bearer "+login(false)))
            .andExpect(status().isForbidden());
    }
    @Test void impideConsultarOtraCuenta() throws Exception {
        mvc.perform(get("/api/bff/web/cuentas/107").secure(true).header("Authorization","Bearer "+login(false)))
            .andExpect(status().isForbidden());
    }
    @Test void noAceptaHttpNiCabeceraHttpsFalsificada() throws Exception {
        mvc.perform(post("/api/auth/token").header("X-Forwarded-Proto","https")
            .contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
    @Test void noIncluyeBatchNiAplicacionesDeOtrosCanales() {
        for(String nombre:List.of("org.springframework.batch.core.job.Job", "cl.duoc.bank_batch.BankBatchApplication",
                "cl.duoc.bff.movil.BffMovilApplication","cl.duoc.bff.cajero.BffCajeroApplication"))
            assertThat(org.springframework.util.ClassUtils.isPresent(nombre,getClass().getClassLoader())).isFalse();
    }
    @Test void consultaNoTienePermisoDeResumen() throws Exception {
        mvc.perform(get("/api/bff/web/resumen").secure(true).header("Authorization","Bearer "+login(true)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/bff/web/resumen").secure(true).header("Authorization","Bearer "+login(false)))
            .andExpect(status().isOk());
    }
}
