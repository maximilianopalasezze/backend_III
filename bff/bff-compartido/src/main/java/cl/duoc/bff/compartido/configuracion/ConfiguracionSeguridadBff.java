package cl.duoc.bff.compartido.configuracion;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(PropiedadesBff.class)
public class ConfiguracionSeguridadBff {

    @Bean
    JwtDecoder jwtDecoder(PropiedadesBff p) {

        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(
                        p.jwkSetUri()
                ).build();

        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(
                        p.issuer()
                );

        OAuth2TokenValidator<Jwt> claimsValidator = jwt -> {

            boolean audienceOk =
                    jwt.getAudience().contains(
                            p.audience()
                    );

            boolean canalOk =
                    p.canal().name().equals(
                            jwt.getClaimAsString("canal")
                    );

            String cuentaId =
                    jwt.getClaimAsString("cuentaId");

            boolean cuentaOk =
                    cuentaId != null &&
                    !cuentaId.isBlank();

            if (audienceOk && canalOk && cuentaOk) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            "invalid_token",
                            "Token OAuth2 no válido para este canal",
                            null
                    )
            );
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        issuerValidator,
                        claimsValidator
                )
        );

        return decoder;
    }

    @Bean
    SecurityFilterChain cadenaSeguridadBff(
            HttpSecurity http,
            PropiedadesBff p) throws Exception {

        String canal =
                p.canal().name().toLowerCase();

        String base =
                "/api/bff/" + canal;

        http
                .addFilterBefore(
                        new FiltroHttps(),
                        BearerTokenAuthenticationFilter.class
                )
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                base + "/resumen"
                        ).hasAuthority(
                                "SCOPE_" + canal + ":resumen"
                        )

                        .requestMatchers(
                                HttpMethod.GET,
                                base + "/cuentas/**"
                        ).hasAuthority(
                                "SCOPE_" + canal + ":lectura"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                base + "/cuentas/*/retiros"
                        ).hasAuthority(
                                "SCOPE_cajero:retiro"
                        )

                        .anyRequest().denyAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                (req, res, error) ->
                                        responderError(
                                                res,
                                                401,
                                                "Token OAuth2 ausente o inválido"
                                        )
                        )
                        .accessDeniedHandler(
                                (req, res, error) ->
                                        responderError(
                                                res,
                                                403,
                                                "Scope OAuth2 insuficiente"
                                        )
                        )
                )
                .oauth2ResourceServer(oauth ->
                        oauth
                                .jwt(jwt -> {
                                })
                                .authenticationEntryPoint(
                                        (req, res, error) ->
                                                responderError(
                                                        res,
                                                        401,
                                                        "Token OAuth2 ausente o inválido"
                                                )
                                )
                                .accessDeniedHandler(
                                        (req, res, error) ->
                                                responderError(
                                                        res,
                                                        403,
                                                        "Scope OAuth2 insuficiente"
                                                )
                                )
                );

        return http.build();
    }

    private static void responderError(
            HttpServletResponse res,
            int status,
            String mensaje) throws java.io.IOException {

        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");

        if (status == 401) {
            res.setHeader(
                    "WWW-Authenticate",
                    "Bearer"
            );
        }

        res.getWriter().write(
                "{\"estado\":" + status +
                ",\"mensaje\":\"" + mensaje + "\"}"
        );
    }
}
