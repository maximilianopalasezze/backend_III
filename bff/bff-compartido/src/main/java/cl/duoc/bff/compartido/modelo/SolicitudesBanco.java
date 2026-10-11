package cl.duoc.bff.compartido.modelo;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Contratos de canal: nunca permiten escribir directamente un saldo. */
public final class SolicitudesBanco {
    private SolicitudesBanco() {}

    public record AperturaCuenta(
            @NotNull @Positive Long cuentaId,
            @NotBlank @Size(max = 150) String nombre,
            @NotNull @Min(18) @Max(120) Integer edad,
            @NotBlank @Pattern(regexp = "ahorro|corriente|prestamo|hipoteca") String tipoCuenta) {}

    public record MantenimientoCuenta(
            @NotBlank @Pattern(regexp = "ahorro|corriente|prestamo|hipoteca") String tipoCuenta) {}

    // La cuenta del alta se toma de la ruta autorizada, no de un campo del JSON.
    public record AltaCliente(
            @NotNull @Positive Long clienteId,
            @NotBlank @Size(max = 150) String nombre,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 25) String telefono,
            @NotBlank @Size(max = 255) String direccion,
            @NotBlank @Pattern(regexp = "ESTANDAR|PREFERENTE|EMPRESA") String perfil) {}

    public record ActualizarCliente(
            @NotBlank @Size(max = 150) String nombre,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 25) String telefono,
            @NotBlank @Size(max = 255) String direccion,
            @NotBlank @Pattern(regexp = "ESTANDAR|PREFERENTE|EMPRESA") String perfil,
            @NotNull @PositiveOrZero Integer version) {}

    public record VincularCuenta(@NotNull @Positive Long cuentaId) {}

    public record Deposito(
            @NotBlank @Pattern(regexp = "[a-z0-9_-]{8,80}") String solicitudId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal monto,
            @NotBlank @Size(max = 200) String concepto) {}

    public record Pago(
            @NotBlank @Pattern(regexp = "[a-z0-9_-]{8,80}") String solicitudId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal monto,
            @NotBlank @Size(max = 150) String beneficiario,
            @NotBlank @Size(max = 200) String concepto) {}

    public record Transferencia(
            @NotBlank @Pattern(regexp = "[a-z0-9_-]{8,80}") String solicitudId,
            @NotNull @Positive Long cuentaDestinoId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal monto,
            @NotBlank @Size(max = 200) String concepto) {}
}
