package cl.duoc.cloud.movimientos.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoProcesadoEvent(
        String solicitudId, String referencia, String tipo, Long cuentaId, Long cuentaDestinoId,
        BigDecimal monto, String beneficiario, String concepto,
        BigDecimal saldoAnterior, BigDecimal saldoPosterior,
        BigDecimal saldoDestinoAnterior, BigDecimal saldoDestinoPosterior,
        String estado, LocalDateTime fecha) {}
