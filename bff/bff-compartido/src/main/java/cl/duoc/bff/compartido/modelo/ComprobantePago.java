package cl.duoc.bff.compartido.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ComprobantePago(String solicitudId, String referencia, String tipo,
        Long cuentaId, Long cuentaDestinoId, BigDecimal monto, String beneficiario,
        String concepto, BigDecimal saldoPosterior, String estado, LocalDateTime fecha) {
    public static ComprobantePago desde(OperacionPago op) {
        return new ComprobantePago(op.solicitudId(), op.referencia(), op.tipo(),
                op.cuentaId(), op.cuentaDestinoId(), op.monto(), op.beneficiario(),
                op.concepto(), op.saldoPosterior(), op.estado(), op.fecha());
    }
}
