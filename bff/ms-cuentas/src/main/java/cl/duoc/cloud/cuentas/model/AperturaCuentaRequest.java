package cl.duoc.cloud.cuentas.model;

import jakarta.validation.constraints.*;

public record AperturaCuentaRequest(
        @NotNull @Positive Long cuentaId,
        @NotBlank @Size(max = 150) String nombre,
        @NotNull @Min(18) @Max(120) Integer edad,
        @NotBlank @Pattern(regexp = "ahorro|corriente|prestamo|hipoteca") String tipoCuenta) {
}
