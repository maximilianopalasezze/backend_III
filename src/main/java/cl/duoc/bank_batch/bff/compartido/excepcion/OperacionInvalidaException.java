package cl.duoc.bank_batch.bff.compartido.excepcion;

public class OperacionInvalidaException extends RuntimeException {

    public OperacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
