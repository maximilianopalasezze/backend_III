package cl.duoc.bank_batch.excepcion;

public class ValidacionDatoException extends RuntimeException {

    public ValidacionDatoException(String mensaje) {
        super(mensaje);
    }

    public ValidacionDatoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}