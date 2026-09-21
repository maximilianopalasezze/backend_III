package cl.duoc.cloud.cuentas.model;
import java.math.BigDecimal;
public record EstadoAnualResponse(Integer anio,Integer cantidadMovimientos,BigDecimal totalDepositos,BigDecimal totalRetiros,BigDecimal totalCompras,BigDecimal totalPagos,BigDecimal saldoAnual,String archivoOrigen) {}
