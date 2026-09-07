package cl.duoc.bank_batch.bff.cajero.dto;

import java.math.BigDecimal;

public record SaldoCajeroResponse(
        String canal,
        String cuentaEnmascarada,
        String moneda,
        BigDecimal saldoDisponible,
        boolean retiroDisponible
) {
}
