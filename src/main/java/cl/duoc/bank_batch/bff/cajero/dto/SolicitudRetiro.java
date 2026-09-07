package cl.duoc.bank_batch.bff.cajero.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SolicitudRetiro(
        @NotNull(message = "el monto es obligatorio")
        @DecimalMin(value = "1000.00", message = "el monto mínimo es 1000")
        @Digits(integer = 13, fraction = 2, message = "el monto tiene un formato inválido")
        BigDecimal monto
) {
}
