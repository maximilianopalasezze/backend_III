package cl.duoc.bff.cajero.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RetiroCajeroResponse(
        String canal,
        String referencia,
        String estado,
        String cuentaEnmascarada,
        BigDecimal monto,
        BigDecimal saldoAnterior,
        BigDecimal saldoDisponible,
        LocalDateTime fecha
) {
}
