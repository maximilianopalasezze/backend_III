package cl.duoc.cloud.cuentas.model;
import java.math.BigDecimal;
public record InteresResponse(String periodo,BigDecimal saldoInicial,BigDecimal tasaInteres,BigDecimal interesCalculado,BigDecimal saldoFinal,String archivoOrigen) {}
