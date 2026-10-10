package cl.duoc.bank_batch.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MovimientoAnualProcesado {

    private Long cuentaId;
    private LocalDate fecha;
    private String tipoMovimiento;
    private BigDecimal monto;
    private String descripcion;
    private String archivoOrigen;

    public MovimientoAnualProcesado() {
    }

    public MovimientoAnualProcesado(
            Long cuentaId,
            LocalDate fecha,
            String tipoMovimiento,
            BigDecimal monto,
            String descripcion,
            String archivoOrigen) {

        this.cuentaId = cuentaId;
        this.fecha = fecha;
        this.tipoMovimiento = tipoMovimiento;
        this.monto = monto;
        this.descripcion = descripcion;
        this.archivoOrigen = archivoOrigen;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getTipoMovimiento() {
        return tipoMovimiento;
    }

    public void setTipoMovimiento(String tipoMovimiento) {
        this.tipoMovimiento = tipoMovimiento;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getArchivoOrigen() {
        return archivoOrigen;
    }

    public void setArchivoOrigen(String archivoOrigen) {
        this.archivoOrigen = archivoOrigen;
    }

    @Override
    public String toString() {
        return "MovimientoAnualProcesado{" +
                "cuentaId=" + cuentaId +
                ", fecha=" + fecha +
                ", tipoMovimiento='" + tipoMovimiento + '\'' +
                ", monto=" + monto +
                ", descripcion='" + descripcion + '\'' +
                ", archivoOrigen='" + archivoOrigen + '\'' +
                '}';
    }
}