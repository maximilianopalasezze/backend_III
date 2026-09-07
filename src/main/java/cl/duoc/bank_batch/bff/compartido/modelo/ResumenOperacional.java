package cl.duoc.bank_batch.bff.compartido.modelo;

import java.math.BigDecimal;

public record ResumenOperacional(
        Long cuentas,
        Long transacciones,
        Long movimientosAnuales,
        Long registrosRechazados,
        BigDecimal saldoTotal
) {
}
