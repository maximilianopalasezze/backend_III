package cl.duoc.bank_batch.modelo;

public class MovimientoAnualCsv {

    private String cuentaId;
    private String fecha;
    private String transaccion;
    private String monto;
    private String descripcion;
    private long numeroLinea;
    private String contenidoOriginal;

    public MovimientoAnualCsv() {
    }

    public MovimientoAnualCsv(
            String cuentaId,
            String fecha,
            String transaccion,
            String monto,
            String descripcion,
            long numeroLinea,
            String contenidoOriginal) {

        this.cuentaId = cuentaId;
        this.fecha = fecha;
        this.transaccion = transaccion;
        this.monto = monto;
        this.descripcion = descripcion;
        this.numeroLinea = numeroLinea;
        this.contenidoOriginal = contenidoOriginal;
    }

    public String getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(String cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getTransaccion() {
        return transaccion;
    }

    public void setTransaccion(String transaccion) {
        this.transaccion = transaccion;
    }

    public String getMonto() {
        return monto;
    }

    public void setMonto(String monto) {
        this.monto = monto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public long getNumeroLinea() {
        return numeroLinea;
    }

    public void setNumeroLinea(long numeroLinea) {
        this.numeroLinea = numeroLinea;
    }

    public String getContenidoOriginal() {
        return contenidoOriginal;
    }

    public void setContenidoOriginal(String contenidoOriginal) {
        this.contenidoOriginal = contenidoOriginal;
    }

    @Override
    public String toString() {
        return "MovimientoAnualCsv{" +
                "cuentaId='" + cuentaId + '\'' +
                ", fecha='" + fecha + '\'' +
                ", transaccion='" + transaccion + '\'' +
                ", monto='" + monto + '\'' +
                ", descripcion='" + descripcion + '\'' +
                ", numeroLinea=" + numeroLinea +
                '}';
    }
}