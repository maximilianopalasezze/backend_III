package cl.duoc.bank_batch.bff.compartido.configuracion;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class FiltroApiKeyBff extends OncePerRequestFilter {

    private final PropiedadesBff propiedades;

    public FiltroApiKeyBff(PropiedadesBff propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/bff/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        SecurityContextHolder.clearContext();

        String apiKeyRecibida = request.getHeader(
                propiedades.getSeguridad().getHeader()
        );
        String apiKeyEsperada = propiedades.getSeguridad().getApiKey();

        if (!coincide(apiKeyRecibida, apiKeyEsperada)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("""
                    {"estado":401,"error":"No autorizado","mensaje":"API key ausente o inválida"}
                    """);
            return;
        }

        String canal = propiedades.getCanal().name();
        var autoridad = new SimpleGrantedAuthority("ROLE_" + canal);
        var autenticacion = new UsernamePasswordAuthenticationToken(
                "bff-" + canal.toLowerCase(),
                null,
                List.of(autoridad)
        );

        SecurityContextHolder.getContext().setAuthentication(autenticacion);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean coincide(String recibida, String esperada) {
        if (recibida == null || esperada == null || esperada.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                recibida.getBytes(StandardCharsets.UTF_8),
                esperada.getBytes(StandardCharsets.UTF_8)
        );
    }
}
