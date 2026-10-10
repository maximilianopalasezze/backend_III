package cl.duoc.cloud.pagos.model;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TransferenciaRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9_-]{8,80}") String solicitudId,
        @NotNull @Positive Long cuentaDestinoId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal monto,
        @NotBlank @Size(max = 200) String concepto) {}
