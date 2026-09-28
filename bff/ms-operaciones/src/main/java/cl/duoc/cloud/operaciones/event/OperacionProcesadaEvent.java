package cl.duoc.cloud.operaciones.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OperacionProcesadaEvent(
        String referencia,
        Long cuentaId,
        String tipo,
        BigDecimal monto,
        BigDecimal saldoAnterior,
        BigDecimal saldoPosterior,
        String estado,
        LocalDateTime fechaOperacion
) {
}
