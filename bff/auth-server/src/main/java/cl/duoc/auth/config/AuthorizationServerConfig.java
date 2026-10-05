package cl.duoc.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;

@Configuration
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(
                        authorizationServerConfigurer.getEndpointsMatcher()
                )
                .with(
                        authorizationServerConfigurer,
                        authorizationServer -> {
                        }
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain defaultSecurityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()
                        .anyRequest().denyAll()
                );

        return http.build();
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(
            PasswordEncoder encoder,
            @Value("${OAUTH_WEB_SECRET}") String webSecret,
            @Value("${OAUTH_MOVIL_SECRET}") String movilSecret,
            @Value("${OAUTH_CAJERO_SECRET}") String cajeroSecret) {

        RegisteredClient web = RegisteredClient
                .withId(UUID.randomUUID().toString())
                .clientId("bff-web")
                .clientSecret(encoder.encode(webSecret))
                .clientAuthenticationMethod(
                        ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(
                        AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("web:lectura")
                .scope("web:resumen")
                .build();

        RegisteredClient movil = RegisteredClient
                .withId(UUID.randomUUID().toString())
                .clientId("bff-movil")
                .clientSecret(encoder.encode(movilSecret))
                .clientAuthenticationMethod(
                        ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(
                        AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("movil:lectura")
                .build();

        RegisteredClient cajero = RegisteredClient
                .withId(UUID.randomUUID().toString())
                .clientId("bff-cajero")
                .clientSecret(encoder.encode(cajeroSecret))
                .clientAuthenticationMethod(
                        ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(
                        AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("cajero:lectura")
                .scope("cajero:retiro")
                .build();

        return new InMemoryRegisteredClientRepository(
                web,
                movil,
                cajero
        );
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {

        return context -> {

            if (!OAuth2TokenType.ACCESS_TOKEN.equals(
                    context.getTokenType())) {
                return;
            }

            String clientId =
                    context.getRegisteredClient().getClientId();

            switch (clientId) {

                case "bff-web" -> {
                    context.getClaims()
                            .claim("canal", "WEB")
                            .claim("cuentaId", "102")
                            .audience(List.of("bff-web"));
                }

                case "bff-movil" -> {
                    context.getClaims()
                            .claim("canal", "MOVIL")
                            .claim("cuentaId", "102")
                            .audience(List.of("bff-movil"));
                }

                case "bff-cajero" -> {
                    context.getClaims()
                            .claim("canal", "CAJERO")
                            .claim("cuentaId", "102")
                            .audience(List.of("bff-cajero"));
                }

                default -> {
                }
            }
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {

        return PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }

    @Bean
    JWKSource<SecurityContext> jwkSource() {

        KeyPair keyPair = generateRsaKey();

        RSAPublicKey publicKey =
                (RSAPublicKey) keyPair.getPublic();

        RSAPrivateKey privateKey =
                (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();

        return new ImmutableJWKSet<>(
                new JWKSet(rsaKey)
        );
    }

    private static KeyPair generateRsaKey() {

        try {

            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance("RSA");

            generator.initialize(2048);

            return generator.generateKeyPair();

        } catch (Exception ex) {

            throw new IllegalStateException(
                    "No fue posible generar la clave RSA",
                    ex
            );
        }
    }

    @Bean
    JwtDecoder jwtDecoder(
            JWKSource<SecurityContext> jwkSource) {

        return OAuth2AuthorizationServerConfiguration
                .jwtDecoder(jwkSource);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(
            @Value("${spring.security.oauth2.authorizationserver.issuer}")
            String issuer) {

        return AuthorizationServerSettings.builder()
                .issuer(issuer)
                .build();
    }
}
