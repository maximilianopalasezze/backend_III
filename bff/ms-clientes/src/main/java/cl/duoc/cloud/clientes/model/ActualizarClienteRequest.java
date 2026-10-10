package cl.duoc.cloud.clientes.model;

import jakarta.validation.constraints.*;

public record ActualizarClienteRequest(
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 25) String telefono,
        @NotBlank @Size(max = 255) String direccion,
        @NotBlank @Pattern(regexp = "ESTANDAR|PREFERENTE|EMPRESA") String perfil,
        @NotNull @PositiveOrZero Integer version) {}
