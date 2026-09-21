package cl.duoc.cloud.operaciones.model;
import jakarta.validation.constraints.DecimalMin; import jakarta.validation.constraints.NotNull; import java.math.BigDecimal;
public record RetiroRequest(@NotNull @DecimalMin("1000.00") BigDecimal monto) {}
