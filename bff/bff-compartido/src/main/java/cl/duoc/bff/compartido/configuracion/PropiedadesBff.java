package cl.duoc.bff.compartido.configuracion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix="bff")
public record PropiedadesBff(
        @NotNull Canal canal,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String jwkSetUri) {

    public enum Canal {
        WEB,
        MOVIL,
        CAJERO
    }
}
