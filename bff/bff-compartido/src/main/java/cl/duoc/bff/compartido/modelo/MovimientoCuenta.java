package cl.duoc.bff.compartido.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimientoCuenta(
        LocalDate fecha,
        String tipo,
        BigDecimal monto,
        String descripcion,
        String archivoOrigen
) {
}
