package cl.duoc.cloud.cuentas.model;
import java.math.BigDecimal;
public record ResumenResponse(Long cuentas,Long transacciones,Long movimientosAnuales,Long registrosRechazados,BigDecimal saldoTotal) {}
