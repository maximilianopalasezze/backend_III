package cl.duoc.bff.compartido.autenticacion;

import cl.duoc.bff.compartido.configuracion.PropiedadesBff;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@RestController
public class ControladorToken {
    private final PropiedadesBff p;
    private final JwtEncoder encoder;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
    private final String hashCompleto;
    private final String hashConsulta;
    public ControladorToken(PropiedadesBff p,JwtEncoder encoder){
        this.p=p;this.encoder=encoder;
        if(p.usuario().equals(p.usuarioConsulta()) || p.password().equals(p.passwordConsulta()))
            throw new IllegalArgumentException("Los usuarios completo y consulta deben tener credenciales distintas");
        hashCompleto=passwords.encode(p.password());hashConsulta=passwords.encode(p.passwordConsulta());
    }
    public record Credenciales(@NotBlank @Size(max=100) String usuario,
                               @NotBlank @Size(max=64) String password) {}
    public record TokenResponse(String access_token,String token_type,long expires_in,String scope) {}
    @PostMapping("/api/auth/token")
    public ResponseEntity<?> token(@Valid @RequestBody Credenciales c){
        boolean completo=p.usuario().equals(c.usuario());
        boolean consulta=p.usuarioConsulta().equals(c.usuario());
        boolean coincide=passwords.matches(c.password(),completo?hashCompleto:hashConsulta);
        if(!coincide || (!completo && !consulta))
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                .body(Map.of("estado",401,"mensaje","Credenciales inválidas"));
        String canal=p.canal().name().toLowerCase();
        String scope=canal+":lectura";
        if(completo && p.canal()==PropiedadesBff.Canal.WEB) scope+=" web:resumen";
        if(completo && p.canal()==PropiedadesBff.Canal.CAJERO) scope+=" cajero:retiro";
        Instant now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer(p.issuer()).subject(c.usuario())
            .audience(List.of(p.audience())).issuedAt(now).notBefore(now)
            .expiresAt(now.plusSeconds(p.tokenSeconds())).id(UUID.randomUUID().toString())
            .claim("canal",p.canal().name()).claim("cuentaId",Long.toString(p.cuentaId()))
            .claim("scope",scope).build();
        var header=JwsHeader.with(MacAlgorithm.HS256).build();
        String value=encoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new TokenResponse(value,"Bearer",p.tokenSeconds(),scope));
    }
}
