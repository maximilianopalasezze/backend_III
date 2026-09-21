package cl.duoc.bff.compartido.excepcion;

public class ServicioBackendNoDisponibleException extends RuntimeException {
    public ServicioBackendNoDisponibleException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
