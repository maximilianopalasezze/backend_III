package cl.duoc.cloud.clientes.config;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ClienteNoEncontrado.class)
    ResponseEntity<?> noEncontrado(ClienteNoEncontrado ex) {
        return error(404, ex.getMessage());
    }

    @ExceptionHandler(ClienteConflicto.class)
    ResponseEntity<?> conflicto(ClienteConflicto ex) {
        return error(409, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validacion(MethodArgumentNotValidException ex) {
        var campo = ex.getBindingResult().getFieldError();
        return error(400, campo == null ? "Solicitud inválida" : campo.getField() + ": " + campo.getDefaultMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> jsonInvalido() {
        return error(400, "El cuerpo JSON es inválido");
    }

    private ResponseEntity<?> error(int estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("estado", estado, "mensaje", mensaje));
    }

    public static class ClienteNoEncontrado extends RuntimeException {
        public ClienteNoEncontrado(String mensaje) { super(mensaje); }
    }

    public static class ClienteConflicto extends RuntimeException {
        public ClienteConflicto(String mensaje) { super(mensaje); }
    }
}
