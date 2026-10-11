package cl.duoc.bff.compartido.excepcion;

public class ConflictoNegocioException extends RuntimeException {
    public ConflictoNegocioException(String mensaje) { super(mensaje); }
}
