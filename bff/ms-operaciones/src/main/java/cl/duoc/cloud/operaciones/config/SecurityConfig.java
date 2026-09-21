package cl.duoc.cloud.operaciones.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
    @Bean UserDetailsService users(PasswordEncoder encoder,
            @Value("${backend.security.usuario:svc-bff}") String svcUser,
            @Value("${backend.security.password:ChangeMe-Semana6-Backend!}") String svcPass,
            @Value("${backend.security.viewer-usuario:viewer}") String viewerUser,
            @Value("${backend.security.viewer-password:ChangeMe-Semana6-Viewer!}") String viewerPass) {
        var service = User.withUsername(svcUser).password(encoder.encode(svcPass)).roles("SERVICE").build();
        var viewer = User.withUsername(viewerUser).password(encoder.encode(viewerPass)).roles("VIEWER").build();
        return new InMemoryUserDetailsManager(service, viewer);
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/**").hasRole("SERVICE")
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
