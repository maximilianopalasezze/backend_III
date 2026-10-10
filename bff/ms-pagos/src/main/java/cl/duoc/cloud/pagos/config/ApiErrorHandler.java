package cl.duoc.cloud.pagos.config;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(OperacionNoEncontrada.class)
    ResponseEntity<?> noEncontrada(OperacionNoEncontrada ex) { return error(404, ex.getMessage()); }
    @ExceptionHandler(OperacionConflicto.class)
    ResponseEntity<?> conflicto(OperacionConflicto ex) { return error(409, ex.getMessage()); }
    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<?> invalida(SolicitudInvalida ex) { return error(400, ex.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validacion(MethodArgumentNotValidException ex) {
        var campo = ex.getBindingResult().getFieldError();
        return error(400, campo == null ? "Solicitud inválida" : campo.getField() + ": " + campo.getDefaultMessage());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> formato() { return error(400, "El formato de la solicitud es inválido"); }
    private ResponseEntity<?> error(int estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("estado", estado, "mensaje", mensaje));
    }
    public static class OperacionNoEncontrada extends RuntimeException {
        public OperacionNoEncontrada(String mensaje) { super(mensaje); }
    }
    public static class OperacionConflicto extends RuntimeException {
        public OperacionConflicto(String mensaje) { super(mensaje); }
    }
    public static class SolicitudInvalida extends RuntimeException {
        public SolicitudInvalida(String mensaje) { super(mensaje); }
    }
}
