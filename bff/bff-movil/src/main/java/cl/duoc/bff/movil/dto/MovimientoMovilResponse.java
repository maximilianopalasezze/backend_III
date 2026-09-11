package cl.duoc.bff.movil.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimientoMovilResponse(
        LocalDate fecha,
        String tipo,
        BigDecimal monto
) {
}
