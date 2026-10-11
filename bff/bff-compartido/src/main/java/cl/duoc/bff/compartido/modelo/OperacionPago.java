package cl.duoc.bff.compartido.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Recibo interno del microservicio; los BFF ocultan los saldos del destinatario. */
public record OperacionPago(String solicitudId, String referencia, String tipo,
        Long cuentaId, Long cuentaDestinoId, BigDecimal monto, String beneficiario,
        String concepto, BigDecimal saldoAnterior, BigDecimal saldoPosterior,
        BigDecimal saldoDestinoAnterior, BigDecimal saldoDestinoPosterior,
        String estado, LocalDateTime fecha) {}
