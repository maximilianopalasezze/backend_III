package cl.duoc.bff.compartido.configuracion;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix="bff")
public record PropiedadesBff(
        @NotNull Canal canal,
        @NotBlank String jwtSecret,
        @NotBlank String issuer,
        @NotBlank String audience,
        @Min(60) @Max(900) long tokenSeconds,
        @NotBlank String usuario,
        @NotBlank @Size(min=16,max=64) String password,
        @NotBlank String usuarioConsulta,
        @NotBlank @Size(min=16,max=64) String passwordConsulta,
        @Positive long cuentaId) {
    public enum Canal { WEB, MOVIL, CAJERO }
}
