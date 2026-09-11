package cl.duoc.bff.compartido.configuracion;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.time.Duration;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(PropiedadesBff.class)
public class ConfiguracionSeguridadBff {
    @Bean
    public SecretKey claveJwt(PropiedadesBff p) {
        byte[] bytes=Base64.getDecoder().decode(p.jwtSecret());
        if(bytes.length<32) throw new IllegalArgumentException("La clave JWT debe tener al menos 256 bits");
        return new SecretKeySpec(bytes,"HmacSHA256");
    }
    @Bean
    public JwtEncoder jwtEncoder(SecretKey clave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
    }
    @Bean
    public JwtDecoder jwtDecoder(SecretKey clave, PropiedadesBff p) {
        var decoder=NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            boolean ok=jwt.getExpiresAt()!=null && jwt.getIssuedAt()!=null
                    && jwt.getSubject()!=null && !jwt.getSubject().isBlank()
                    && jwt.getAudience().contains(p.audience())
                    && p.canal().name().equals(jwt.getClaimAsString("canal"))
                    && jwt.getClaimAsString("cuentaId")!=null;
            return ok ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token","Token no válido para este canal",null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(p.issuer()), claims));
        return decoder;
    }
    @Bean
    public SecurityFilterChain cadenaSeguridadBff(HttpSecurity http, PropiedadesBff p) throws Exception {
        String canal=p.canal().name().toLowerCase();
        String base="/api/bff/"+canal;
        http.addFilterBefore(new FiltroHttps(), org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
            .csrf(c -> c.disable())
            .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable())
            .formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
            .headers(c -> c.httpStrictTransportSecurity(h -> h.maxAgeInSeconds(31536000).includeSubDomains(false)))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST,"/api/auth/token").permitAll()
                .requestMatchers(HttpMethod.GET,base+"/resumen").hasAuthority("SCOPE_"+canal+":resumen")
                .requestMatchers(HttpMethod.GET,base+"/cuentas/**").hasAuthority("SCOPE_"+canal+":lectura")
                .requestMatchers(HttpMethod.POST,base+"/cuentas/*/retiros").hasAuthority("SCOPE_cajero:retiro")
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex)->error(res,401,"Token ausente o inválido"))
                .accessDeniedHandler((req,res,ex)->error(res,403,"Permisos insuficientes")))
            .oauth2ResourceServer(o -> o.jwt(j -> {})
                .authenticationEntryPoint((req,res,ex)->error(res,401,"Token ausente o inválido"))
                .accessDeniedHandler((req,res,ex)->error(res,403,"Permisos insuficientes")));
        return http.build();
    }
    private static void error(HttpServletResponse res,int status,String message) throws java.io.IOException {
        res.setStatus(status);res.setContentType("application/json");res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control","no-store");
        if(status==401) res.setHeader("WWW-Authenticate","Bearer");
        res.getWriter().write("{\"estado\":"+status+",\"mensaje\":\""+message+"\"}");
    }
}
