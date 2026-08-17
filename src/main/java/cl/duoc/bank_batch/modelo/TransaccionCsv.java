package cl.duoc.bank_batch.modelo;

public class TransaccionCsv {

    private String id;
    private String fecha;
    private String monto;
    private String tipo;
    private long numeroLinea;
    private String contenidoOriginal;

    public TransaccionCsv() {
    }

    public TransaccionCsv(
            String id,
            String fecha,
            String monto,
            String tipo,
            long numeroLinea,
            String contenidoOriginal
    ) {
        this.id = id;
        this.fecha = fecha;
        this.monto = monto;
        this.tipo = tipo;
        this.numeroLinea = numeroLinea;
        this.contenidoOriginal = contenidoOriginal;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getMonto() {
        return monto;
    }

    public void setMonto(String monto) {
        this.monto = monto;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
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
        return "TransaccionCsv{" +
                "id='" + id + '\'' +
                ", fecha='" + fecha + '\'' +
                ", monto='" + monto + '\'' +
                ", tipo='" + tipo + '\'' +
                ", numeroLinea=" + numeroLinea +
                '}';
    }
}