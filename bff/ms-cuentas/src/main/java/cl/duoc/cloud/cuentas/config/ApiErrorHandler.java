package cl.duoc.cloud.cuentas.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(RecursoNoEncontrado.class)
    ResponseEntity<?> notFound(RecursoNoEncontrado ex) { return ResponseEntity.status(404).body(Map.of("estado",404,"mensaje",ex.getMessage())); }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<?> badRequest(Exception ex) { return ResponseEntity.badRequest().body(Map.of("estado",400,"mensaje",ex.getMessage())); }
    @ExceptionHandler(SaldoInsuficiente.class)
    ResponseEntity<?> conflicto(SaldoInsuficiente ex) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("estado",409,"mensaje",ex.getMessage())); }
    public static class RecursoNoEncontrado extends RuntimeException { public RecursoNoEncontrado(String m) { super(m); } }
    public static class SaldoInsuficiente extends RuntimeException { public SaldoInsuficiente(String m) { super(m); } }
}
