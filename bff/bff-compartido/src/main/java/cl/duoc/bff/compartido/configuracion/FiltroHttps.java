package cl.duoc.bff.compartido.configuracion;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** No recibe credenciales por HTTP ni confia en headers enviados por el cliente. */
public class FiltroHttps extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        if (!request.isSecure()) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"estado\":403,\"mensaje\":\"HTTPS requerido\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
