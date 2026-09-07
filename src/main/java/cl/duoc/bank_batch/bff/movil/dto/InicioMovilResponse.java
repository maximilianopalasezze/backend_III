package cl.duoc.bank_batch.bff.movil.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InicioMovilResponse(
        String canal,
        Long cuentaId,
        String titular,
        String tipoCuenta,
        BigDecimal saldoDisponible,
        LocalDateTime actualizadoEn
) {
}
