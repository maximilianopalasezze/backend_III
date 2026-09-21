package cl.duoc.bff.compartido.configuracion;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "backend.security")
public record PropiedadesServiciosBackend(@NotBlank String usuario, @NotBlank String password) {}
