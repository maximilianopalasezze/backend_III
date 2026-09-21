package cl.duoc.cloud.cuentas.model;
import java.math.BigDecimal; import java.time.LocalDateTime;
public record CuentaResponse(Long cuentaId,String nombre,BigDecimal saldo,Integer edad,String tipoCuenta,LocalDateTime fechaActualizacion) {}
