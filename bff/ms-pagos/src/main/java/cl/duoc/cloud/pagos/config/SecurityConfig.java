package cl.duoc.cloud.pagos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean UserDetailsService users(PasswordEncoder encoder,
            @Value("${backend.security.usuario:svc-bff}") String usuario,
            @Value("${backend.security.password:ChangeMe-Semana6-Backend!}") String password,
            @Value("${backend.security.viewer-usuario:viewer}") String viewer,
            @Value("${backend.security.viewer-password:ChangeMe-Semana6-Viewer!}") String viewerPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername(usuario).password(encoder.encode(password)).roles("SERVICE").build(),
                User.withUsername(viewer).password(encoder.encode(viewerPassword)).roles("VIEWER").build());
    }

    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/**").hasRole("SERVICE")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults()).build();
    }
}
