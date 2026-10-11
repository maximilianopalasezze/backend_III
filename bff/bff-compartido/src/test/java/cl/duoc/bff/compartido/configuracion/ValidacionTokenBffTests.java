package cl.duoc.bff.compartido.configuracion;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidacionTokenBffTests {
    private HttpServer server;
    private JwtEncoder encoder;
    private JwtDecoder decoder;
    @BeforeAll void clavesYDecoderReales() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = new RSAKey.Builder((RSAPublicKey)pair.getPublic()).privateKey((RSAPrivateKey)pair.getPrivate())
                .keyID("test-key").build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        byte[] publicas = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/jwks",exchange -> {
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,publicas.length);
            try(var out=exchange.getResponseBody()) { out.write(publicas); }
        });
        server.start();
        decoder = new ConfiguracionSeguridadBff().jwtDecoder(new PropiedadesBff(
                PropiedadesBff.Canal.WEB,"http://localhost:9000","bff-web",
                "http://127.0.0.1:"+server.getAddress().getPort()+"/jwks"));
    }
    @AfterAll void detener() { if(server!=null) server.stop(0); }
    private String firmar(Consumer<JwtClaimsSet.Builder> cambiar) {
        Instant now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer("http://localhost:9000").subject("bff-web")
                .audience(List.of("bff-web")).issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("canal","WEB").claim("cuentaId","900201").claim("cuentas",List.of("900201","900203"));
        cambiar.accept(claims);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims.build())).getTokenValue();
    }
    @Test void firmaIssuerCanalAudienceYCuentasValidosSeAceptan() {
        assertThat(decoder.decode(firmar(c -> {})).getClaimAsStringList("cuentas"))
                .containsExactly("900201","900203");
    }
    @Test void tokenAnteriorConCuentaPrincipalSigueValido() {
        assertThat(decoder.decode(firmar(c -> c.claims(map -> map.remove("cuentas"))))
                .getClaimAsString("cuentaId")).isEqualTo("900201");
    }
    @Test void issuerAjenoSeRechaza() {
        assertThatThrownBy(() -> decoder.decode(firmar(c -> c.issuer("http://otro")))).isInstanceOf(JwtValidationException.class);
    }
    @Test void audienceDeOtroBffSeRechaza() {
        assertThatThrownBy(() -> decoder.decode(firmar(c -> c.audience(List.of("bff-movil"))))).isInstanceOf(JwtValidationException.class);
    }
    @Test void canalCruzadoSeRechaza() {
        assertThatThrownBy(() -> decoder.decode(firmar(c -> c.claim("canal","MOVIL")))).isInstanceOf(JwtValidationException.class);
    }
    @Test void tokenExpiradoSeRechaza() {
        assertThatThrownBy(() -> decoder.decode(firmar(c -> c.issuedAt(Instant.now().minusSeconds(600)).expiresAt(Instant.now().minusSeconds(120)))))
                .isInstanceOf(JwtValidationException.class);
    }
    @Test void cuentaPrincipalFueraDeRangoSeRechaza() {
        assertThatThrownBy(() -> decoder.decode(firmar(c -> c.claim("cuentaId","9999999999999999999"))))
                .isInstanceOf(JwtValidationException.class);
    }
    @Test void cuentasAdicionalesMalformadasSeRechazan() {
        assertThatThrownBy(() -> decoder.decode(firmar(c -> c.claim("cuentas",List.of("900201","otra")))))
                .isInstanceOf(JwtValidationException.class);
    }
}
