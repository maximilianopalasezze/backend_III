package cl.duoc.bff.compartido.modelo;

import java.math.BigDecimal;

public record InteresCuenta(
        String periodo,
        BigDecimal saldoInicial,
        BigDecimal tasaInteres,
        BigDecimal interesCalculado,
        BigDecimal saldoFinal,
        String archivoOrigen
) {
}
