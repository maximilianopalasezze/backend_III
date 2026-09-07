package cl.duoc.bank_batch.bff.web.dto;

import java.math.BigDecimal;

public record ResumenWebResponse(
        String canal,
        Long cuentas,
        Long transaccionesProcesadas,
        Long movimientosAnualesProcesados,
        Long registrosRechazados,
        BigDecimal saldoTotal
) {
}
