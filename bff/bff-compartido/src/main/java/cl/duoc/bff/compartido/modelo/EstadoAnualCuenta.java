package cl.duoc.bff.compartido.modelo;

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
