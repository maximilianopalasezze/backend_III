package cl.duoc.cloud.cuentas.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// El mantenimiento del producto no permite modificar directamente el saldo.
public record MantenimientoCuentaRequest(
        @NotBlank @Pattern(regexp = "ahorro|corriente|prestamo|hipoteca") String tipoCuenta) {
}
