package cl.duoc.bank_batch.modelo;

public class InteresCsv {

    private String cuentaId;
    private String nombre;
    private String saldo;
    private String edad;
    private String tipo;
    private long numeroLinea;
    private String contenidoOriginal;

    public InteresCsv() {
    }

    public InteresCsv(
            String cuentaId,
            String nombre,
            String saldo,
            String edad,
            String tipo,
            long numeroLinea,
            String contenidoOriginal
    ) {
        this.cuentaId = cuentaId;
        this.nombre = nombre;
        this.saldo = saldo;
        this.edad = edad;
        this.tipo = tipo;
        this.numeroLinea = numeroLinea;
        this.contenidoOriginal = contenidoOriginal;
    }

    public String getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(String cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getSaldo() {
        return saldo;
    }

    public void setSaldo(String saldo) {
        this.saldo = saldo;
    }

    public String getEdad() {
        return edad;
    }

    public void setEdad(String edad) {
        this.edad = edad;
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
        return "InteresCsv{" +
                "cuentaId='" + cuentaId + '\'' +
                ", nombre='" + nombre + '\'' +
                ", saldo='" + saldo + '\'' +
                ", edad='" + edad + '\'' +
                ", tipo='" + tipo + '\'' +
                ", numeroLinea=" + numeroLinea +
                '}';
    }
}