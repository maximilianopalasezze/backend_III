package cl.duoc.bank_batch.modelo;

import java.math.BigDecimal;

public class InteresProcesado {

    private Long cuentaId;
    private String nombre;
    private BigDecimal saldoInicial;
    private Integer edad;
    private String tipoCuenta;
    private BigDecimal tasaInteres;
    private BigDecimal interesCalculado;
    private BigDecimal saldoFinal;
    private String periodo;
    private String archivoOrigen;

    public InteresProcesado() {
    }

    public InteresProcesado(
            Long cuentaId,
            String nombre,
            BigDecimal saldoInicial,
            Integer edad,
            String tipoCuenta,
            BigDecimal tasaInteres,
            BigDecimal interesCalculado,
            BigDecimal saldoFinal,
            String periodo,
            String archivoOrigen
    ) {
        this.cuentaId = cuentaId;
        this.nombre = nombre;
        this.saldoInicial = saldoInicial;
        this.edad = edad;
        this.tipoCuenta = tipoCuenta;
        this.tasaInteres = tasaInteres;
        this.interesCalculado = interesCalculado;
        this.saldoFinal = saldoFinal;
        this.periodo = periodo;
        this.archivoOrigen = archivoOrigen;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public BigDecimal getSaldoInicial() {
        return saldoInicial;
    }

    public void setSaldoInicial(BigDecimal saldoInicial) {
        this.saldoInicial = saldoInicial;
    }

    public Integer getEdad() {
        return edad;
    }

    public void setEdad(Integer edad) {
        this.edad = edad;
    }

    public String getTipoCuenta() {
        return tipoCuenta;
    }

    public void setTipoCuenta(String tipoCuenta) {
        this.tipoCuenta = tipoCuenta;
    }

    public BigDecimal getTasaInteres() {
        return tasaInteres;
    }

    public void setTasaInteres(BigDecimal tasaInteres) {
        this.tasaInteres = tasaInteres;
    }

    public BigDecimal getInteresCalculado() {
        return interesCalculado;
    }

    public void setInteresCalculado(BigDecimal interesCalculado) {
        this.interesCalculado = interesCalculado;
    }

    public BigDecimal getSaldoFinal() {
        return saldoFinal;
    }

    public void setSaldoFinal(BigDecimal saldoFinal) {
        this.saldoFinal = saldoFinal;
    }

    public String getPeriodo() {
        return periodo;
    }

    public void setPeriodo(String periodo) {
        this.periodo = periodo;
    }

    public String getArchivoOrigen() {
        return archivoOrigen;
    }

    public void setArchivoOrigen(String archivoOrigen) {
        this.archivoOrigen = archivoOrigen;
    }

    @Override
    public String toString() {
        return "InteresProcesado{" +
                "cuentaId=" + cuentaId +
                ", nombre='" + nombre + '\'' +
                ", saldoInicial=" + saldoInicial +
                ", edad=" + edad +
                ", tipoCuenta='" + tipoCuenta + '\'' +
                ", tasaInteres=" + tasaInteres +
                ", interesCalculado=" + interesCalculado +
                ", saldoFinal=" + saldoFinal +
                ", periodo='" + periodo + '\'' +
                '}';
    }
}