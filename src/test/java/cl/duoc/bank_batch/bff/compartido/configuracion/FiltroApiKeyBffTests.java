package cl.duoc.bank_batch.bff.compartido.configuracion;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FiltroApiKeyBffTests {

    @Test
    void autenticaLaClaveDelCanalConSuRol() throws Exception {
        PropiedadesBff propiedades = propiedadesWeb();
        var filtro = new FiltroApiKeyBff(propiedades);
        var solicitud = new MockHttpServletRequest(
                "GET",
                "/api/bff/web/resumen"
        );
        solicitud.addHeader("X-BFF-API-Key", "web-local-key");
        var respuesta = new MockHttpServletResponse();
        var autenticacionCapturada = new AtomicReference<Authentication>();

        filtro.doFilter(
                solicitud,
                respuesta,
                (request, response) -> autenticacionCapturada.set(
                        SecurityContextHolder.getContext().getAuthentication()
                )
        );

        assertThat(autenticacionCapturada.get()).isNotNull();
        assertThat(autenticacionCapturada.get().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_WEB");
    }

    @Test
    void rechazaUnaClaveIncorrecta() throws Exception {
        var filtro = new FiltroApiKeyBff(propiedadesWeb());
        var solicitud = new MockHttpServletRequest(
                "GET",
                "/api/bff/web/resumen"
        );
        solicitud.addHeader("X-BFF-API-Key", "clave-incorrecta");
        var respuesta = new MockHttpServletResponse();

        filtro.doFilter(solicitud, respuesta, (request, response) -> {
            throw new AssertionError("La solicitud no debía continuar");
        });

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(respuesta.getContentAsString()).contains("API key");
    }

    private PropiedadesBff propiedadesWeb() {
        var propiedades = new PropiedadesBff();
        propiedades.setCanal(PropiedadesBff.Canal.WEB);
        propiedades.getSeguridad().setHeader("X-BFF-API-Key");
        propiedades.getSeguridad().setApiKey("web-local-key");
        return propiedades;
    }
}
