package cl.duoc.cloud.operaciones.model;
import java.math.BigDecimal; import java.time.LocalDateTime;
public record RetiroResponse(String referencia,String estado,Long cuentaId,BigDecimal monto,BigDecimal saldoAnterior,BigDecimal saldoPosterior,LocalDateTime fechaOperacion) {}
