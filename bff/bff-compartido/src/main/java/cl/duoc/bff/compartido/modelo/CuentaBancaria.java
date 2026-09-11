package cl.duoc.bff.compartido.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CuentaBancaria(
        Long cuentaId,
        String nombre,
        BigDecimal saldo,
        Integer edad,
        String tipoCuenta,
        LocalDateTime fechaActualizacion
) {
}
