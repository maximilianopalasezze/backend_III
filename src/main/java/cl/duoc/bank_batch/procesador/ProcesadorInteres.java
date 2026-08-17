package cl.duoc.bank_batch.procesador;

import cl.duoc.bank_batch.excepcion.ValidacionDatoException;
import cl.duoc.bank_batch.modelo.InteresCsv;
import cl.duoc.bank_batch.modelo.InteresProcesado;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;

public class ProcesadorInteres
        implements ItemProcessor<InteresCsv, InteresProcesado> {

    private final String archivoOrigen;
    private final String periodo;
    private final BigDecimal tasaAhorro;
    private final BigDecimal tasaPrestamo;
    private final BigDecimal tasaHipoteca;

    private final Set<Long> cuentasProcesadas = new HashSet<>();

    public ProcesadorInteres(
            String archivoOrigen,
            String periodo,
            BigDecimal tasaAhorro,
            BigDecimal tasaPrestamo,
            BigDecimal tasaHipoteca) {

        this.archivoOrigen = archivoOrigen;
        this.periodo = periodo;
        this.tasaAhorro = tasaAhorro;
        this.tasaPrestamo = tasaPrestamo;
        this.tasaHipoteca = tasaHipoteca;

        validarConfiguracion();
    }

    @Override
    public InteresProcesado process(InteresCsv item) {

        Long cuentaId = convertirCuentaId(item.getCuentaId());
        String nombre = validarNombre(item.getNombre());
        BigDecimal saldoInicial = convertirSaldo(item.getSaldo());
        Integer edad = convertirEdad(item.getEdad());
        String tipoCuenta = normalizarTipoCuenta(item.getTipo());

        if (!cuentasProcesadas.add(cuentaId)) {
            throw new ValidacionDatoException(
                    "Cuenta duplicada en el archivo: " + cuentaId
            );
        }

        BigDecimal tasaInteres = obtenerTasa(tipoCuenta);

        BigDecimal interesCalculado = saldoInicial
                .multiply(tasaInteres)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal saldoFinal = saldoInicial
                .add(interesCalculado)
                .setScale(2, RoundingMode.HALF_UP);

        return new InteresProcesado(
                cuentaId,
                nombre,
                saldoInicial.setScale(2, RoundingMode.HALF_UP),
                edad,
                tipoCuenta,
                tasaInteres,
                interesCalculado,
                saldoFinal,
                periodo,
                archivoOrigen
        );
    }

    private Long convertirCuentaId(String valor) {

        if (valor == null || valor.isBlank()) {
            throw new ValidacionDatoException(
                    "El identificador de la cuenta está vacío"
            );
        }

        try {
            long cuentaId = Long.parseLong(valor.trim());

            if (cuentaId <= 0) {
                throw new ValidacionDatoException(
                        "El identificador de la cuenta debe ser positivo"
                );
            }

            return cuentaId;

        } catch (NumberFormatException excepcion) {
            throw new ValidacionDatoException(
                    "El identificador de la cuenta no es numérico: " + valor,
                    excepcion
            );
        }
    }

    private String validarNombre(String valor) {

        if (valor == null || valor.isBlank()) {
            throw new ValidacionDatoException(
                    "El nombre del titular está vacío"
            );
        }

        String nombre = valor.trim();

        if (nombre.equalsIgnoreCase("unknown")) {
            throw new ValidacionDatoException(
                    "El nombre del titular es desconocido"
            );
        }

        return nombre;
    }

    private BigDecimal convertirSaldo(String valor) {

        if (valor == null || valor.isBlank()) {
            throw new ValidacionDatoException(
                    "El saldo está vacío"
            );
        }

        try {
            BigDecimal saldo = new BigDecimal(valor.trim());

            if (saldo.compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidacionDatoException(
                        "El saldo no puede ser negativo"
                );
            }

            return saldo;

        } catch (NumberFormatException excepcion) {
            throw new ValidacionDatoException(
                    "El saldo no es numérico: " + valor,
                    excepcion
            );
        }
    }

    private Integer convertirEdad(String valor) {

        if (valor == null || valor.isBlank()) {
            throw new ValidacionDatoException(
                    "La edad está vacía"
            );
        }

        try {
            int edad = Integer.parseInt(valor.trim());

            if (edad < 18 || edad > 99) {
                throw new ValidacionDatoException(
                        "La edad está fuera del rango permitido: " + edad
                );
            }

            return edad;

        } catch (NumberFormatException excepcion) {
            throw new ValidacionDatoException(
                    "La edad no es numérica: " + valor,
                    excepcion
            );
        }
    }

    private String normalizarTipoCuenta(String valor) {

        if (valor == null || valor.isBlank()) {
            throw new ValidacionDatoException(
                    "El tipo de cuenta está vacío"
            );
        }

        String tipoNormalizado = Normalizer
                .normalize(valor.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        if (!tipoNormalizado.equals("ahorro")
                && !tipoNormalizado.equals("prestamo")
                && !tipoNormalizado.equals("hipoteca")) {

            throw new ValidacionDatoException(
                    "Tipo de cuenta no permitido: " + valor.trim()
            );
        }

        return tipoNormalizado;
    }

    private BigDecimal obtenerTasa(String tipoCuenta) {

        return switch (tipoCuenta) {
            case "ahorro" -> tasaAhorro;
            case "prestamo" -> tasaPrestamo;
            case "hipoteca" -> tasaHipoteca;
            default -> throw new ValidacionDatoException(
                    "No existe una tasa para el tipo de cuenta: " + tipoCuenta
            );
        };
    }

    private void validarConfiguracion() {

        if (periodo == null
                || !periodo.matches("\\d{4}-(0[1-9]|1[0-2])")) {

            throw new IllegalArgumentException(
                    "El periodo debe tener el formato AAAA-MM"
            );
        }

        validarTasa(tasaAhorro, "ahorro");
        validarTasa(tasaPrestamo, "préstamo");
        validarTasa(tasaHipoteca, "hipoteca");
    }

    private void validarTasa(BigDecimal tasa, String tipoCuenta) {

        if (tasa == null || tasa.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "La tasa de " + tipoCuenta + " no puede ser negativa"
            );
        }
    }
}