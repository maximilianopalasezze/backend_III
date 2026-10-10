package cl.duoc.bank_batch.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransaccionProcesada {

    private Long transaccionId;
    private LocalDate fecha;
    private BigDecimal monto;
    private String tipo;
    private boolean anomalia;
    private String detalleAnomalia;
    private String archivoOrigen;

    public TransaccionProcesada() {
    }

    public TransaccionProcesada(
            Long transaccionId,
            LocalDate fecha,
            BigDecimal monto,
            String tipo,
            boolean anomalia,
            String detalleAnomalia,
            String archivoOrigen
    ) {
        this.transaccionId = transaccionId;
        this.fecha = fecha;
        this.monto = monto;
        this.tipo = tipo;
        this.anomalia = anomalia;
        this.detalleAnomalia = detalleAnomalia;
        this.archivoOrigen = archivoOrigen;
    }

    public Long getTransaccionId() {
        return transaccionId;
    }

    public void setTransaccionId(Long transaccionId) {
        this.transaccionId = transaccionId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public boolean isAnomalia() {
        return anomalia;
    }

    public void setAnomalia(boolean anomalia) {
        this.anomalia = anomalia;
    }

    public String getDetalleAnomalia() {
        return detalleAnomalia;
    }

    public void setDetalleAnomalia(String detalleAnomalia) {
        this.detalleAnomalia = detalleAnomalia;
    }

    public String getArchivoOrigen() {
        return archivoOrigen;
    }

    public void setArchivoOrigen(String archivoOrigen) {
        this.archivoOrigen = archivoOrigen;
    }

    @Override
    public String toString() {
        return "TransaccionProcesada{" +
                "transaccionId=" + transaccionId +
                ", fecha=" + fecha +
                ", monto=" + monto +
                ", tipo='" + tipo + '\'' +
                ", anomalia=" + anomalia +
                ", detalleAnomalia='" + detalleAnomalia + '\'' +
                ", archivoOrigen='" + archivoOrigen + '\'' +
                '}';
    }
}