package cl.duoc.bank_batch.bff.compartido.modelo;

import java.math.BigDecimal;

public record EstadoAnualCuenta(
        Integer anio,
        Integer cantidadMovimientos,
        BigDecimal totalDepositos,
        BigDecimal totalRetiros,
        BigDecimal totalCompras,
        BigDecimal totalPagos,
        BigDecimal saldoAnual,
        String archivoOrigen
) {
}
