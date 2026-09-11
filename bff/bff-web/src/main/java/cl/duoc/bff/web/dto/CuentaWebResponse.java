package cl.duoc.bff.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CuentaWebResponse(
        String canal,
        Titular titular,
        Producto producto,
        Interes ultimoInteres,
        EstadoAnual ultimoEstadoAnual,
        List<Movimiento> movimientosRecientes
) {

    public record Titular(
            Long cuentaId,
            String nombre,
            Integer edad
    ) {
    }

    public record Producto(
            String tipoCuenta,
            BigDecimal saldoDisponible,
            LocalDateTime fechaActualizacion
    ) {
    }

    public record Interes(
            String periodo,
            BigDecimal saldoInicial,
            BigDecimal tasa,
            BigDecimal interesCalculado,
            BigDecimal saldoFinal,
            String archivoOrigen
    ) {
    }

    public record EstadoAnual(
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

    public record Movimiento(
            LocalDate fecha,
            String tipo,
            BigDecimal monto,
            String descripcion,
            String archivoOrigen
    ) {
    }
}
