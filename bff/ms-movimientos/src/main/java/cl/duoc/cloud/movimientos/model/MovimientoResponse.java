package cl.duoc.cloud.movimientos.model;
import java.math.BigDecimal; import java.time.LocalDate;
public record MovimientoResponse(LocalDate fecha,String tipo,BigDecimal monto,String descripcion,String archivoOrigen) {}
