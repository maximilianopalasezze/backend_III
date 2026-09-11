package cl.duoc.bff.compartido.excepcion;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice(basePackages = "cl.duoc.bff")
public class ManejadorErroresBff {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<RespuestaErrorApi> manejarNoEncontrado(
            RecursoNoEncontradoException excepcion,
            HttpServletRequest solicitud) {

        return construir(HttpStatus.NOT_FOUND, excepcion.getMessage(), solicitud);
    }

    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<RespuestaErrorApi> manejarSaldoInsuficiente(
            SaldoInsuficienteException excepcion,
            HttpServletRequest solicitud) {

        return construir(HttpStatus.CONFLICT, excepcion.getMessage(), solicitud);
    }

    @ExceptionHandler({OperacionInvalidaException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<RespuestaErrorApi> manejarSolicitudInvalida(
            Exception excepcion,
            HttpServletRequest solicitud) {

        String mensaje = excepcion instanceof MethodArgumentNotValidException validacion
                ? validacion.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .orElse("La solicitud contiene datos inválidos")
                : excepcion.getMessage();

        return construir(HttpStatus.BAD_REQUEST, mensaje, solicitud);
    }

    private ResponseEntity<RespuestaErrorApi> construir(
            HttpStatus estado,
            String mensaje,
            HttpServletRequest solicitud) {

        return ResponseEntity.status(estado).body(new RespuestaErrorApi(
                LocalDateTime.now(),
                estado.value(),
                estado.getReasonPhrase(),
                mensaje,
                solicitud.getRequestURI()
        ));
    }
}
