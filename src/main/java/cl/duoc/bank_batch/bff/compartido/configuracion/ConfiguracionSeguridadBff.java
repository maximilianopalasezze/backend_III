package cl.duoc.bank_batch.bff.compartido.configuracion;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
@Profile({"web", "movil", "cajero"})
@EnableConfigurationProperties(PropiedadesBff.class)
public class ConfiguracionSeguridadBff {

    @Bean
    public FiltroApiKeyBff filtroApiKeyBff(PropiedadesBff propiedades) {
        return new FiltroApiKeyBff(propiedades);
    }

    @Bean
    public SecurityFilterChain cadenaSeguridadBff(
            HttpSecurity http,
            FiltroApiKeyBff filtroApiKeyBff) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(autorizacion -> autorizacion
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/bff/web/**").hasRole("WEB")
                        .requestMatchers("/api/bff/movil/**").hasRole("MOVIL")
                        .requestMatchers("/api/bff/cajero/**").hasRole("CAJERO")
                        .anyRequest().denyAll())
                .exceptionHandling(excepciones -> excepciones
                        .authenticationEntryPoint((solicitud, respuesta, excepcion) -> {
                            respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            respuesta.getWriter().write(
                                    "{\"estado\":401,\"error\":\"No autorizado\"}"
                            );
                        })
                        .accessDeniedHandler((solicitud, respuesta, excepcion) -> {
                            respuesta.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            respuesta.getWriter().write(
                                    "{\"estado\":403,\"error\":\"Acceso denegado para el canal\"}"
                            );
                        }))
                .addFilterBefore(
                        filtroApiKeyBff,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
