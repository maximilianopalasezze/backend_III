package cl.duoc.bank_batch.bff.compartido.excepcion;

import java.time.LocalDateTime;

public record RespuestaErrorApi(
        LocalDateTime fecha,
        int estado,
        String error,
        String mensaje,
        String ruta
) {
}
