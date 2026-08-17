package cl.duoc.bank_batch.procesador;

import cl.duoc.bank_batch.excepcion.ValidacionDatoException;
import cl.duoc.bank_batch.modelo.MovimientoAnualCsv;
import cl.duoc.bank_batch.modelo.MovimientoAnualProcesado;
import cl.duoc.bank_batch.utilidad.ConversorFecha;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ProcesadorMovimientoAnual
        implements ItemProcessor<
        MovimientoAnualCsv,
        MovimientoAnualProcesado> {

    private final String archivoOrigen;
    private final int anioProcesado;

    private final Set<String> movimientosProcesados =
            new HashSet<>();

    public ProcesadorMovimientoAnual(
            String archivoOrigen,
            int anioProcesado) {

        if (anioProcesado < 1900 || anioProcesado > 2100) {
            throw new IllegalArgumentException(
                    "El año procesado está fuera del rango permitido"
            );
        }

        this.archivoOrigen = archivoOrigen;
        this.anioProcesado = anioProcesado;
    }

    @Override
    public MovimientoAnualProcesado process(
            MovimientoAnualCsv item) {

        Long cuentaId = convertirCuentaId(
                item.getCuentaId()
        );

        LocalDate fecha = ConversorFecha.convertir(
                item.getFecha()
        );

        validarAnio(fecha);

        String tipoMovimiento = normalizarTipoMovimiento(
                item.getTransaccion()
        );

        BigDecimal montoOriginal = convertirMonto(
                item.getMonto()
        );

        BigDecimal montoNormalizado = normalizarMonto(
                montoOriginal,
                tipoMovimiento
        );

        String descripcion = validarDescripcion(
                item.getDescripcion()
        );

        validarDuplicado(
                cuentaId,
                fecha,
                tipoMovimiento,
                montoNormalizado,
                descripcion
        );

        return new MovimientoAnualProcesado(
                cuentaId,
                fecha,
                tipoMovimiento,
                montoNormalizado,
                descripcion,
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
                    "El identificador de la cuenta no es numérico: "
                            + valor,
                    excepcion
            );
        }
    }

    private void validarAnio(LocalDate fecha) {

        if (fecha.getYear() != anioProcesado) {
            throw new ValidacionDatoException(
                    "La fecha no pertenece al año procesado: "
                            + fecha
            );
        }
    }

    private String normalizarTipoMovimiento(String valor) {

        if (valor == null || valor.isBlank()) {
            throw new ValidacionDatoException(
                    "El tipo de movimiento está vacío"
            );
        }

        String tipoNormalizado = Normalizer
                .normalize(
                        valor.trim().toLowerCase(Locale.ROOT),
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}", "");

        if (!tipoNormalizado.equals("deposito")
                && !tipoNormalizado.equals("retiro")
                && !tipoNormalizado.equals("compra")
                && !tipoNormalizado.equals("pago")) {

            throw new ValidacionDatoException(
                    "Tipo de movimiento no permitido: "
                            + valor.trim()
            );
        }

        return tipoNormalizado;
    }

    private BigDecimal convertirMonto(String valor) {

        if (valor == null || valor.isBlank()) {
            throw new ValidacionDatoException(
                    "El monto está vacío"
            );
        }

        try {
            BigDecimal monto = new BigDecimal(
                    valor.trim()
            );

            if (monto.compareTo(BigDecimal.ZERO) == 0) {
                throw new ValidacionDatoException(
                        "El monto no puede ser cero"
                );
            }

            return monto;

        } catch (NumberFormatException excepcion) {
            throw new ValidacionDatoException(
                    "El monto no es numérico: " + valor,
                    excepcion
            );
        }
    }

    private BigDecimal normalizarMonto(
            BigDecimal monto,
            String tipoMovimiento) {

        BigDecimal montoAbsoluto = monto.abs();

        BigDecimal montoNormalizado;

        if (tipoMovimiento.equals("deposito")) {
            montoNormalizado = montoAbsoluto;
        } else {
            montoNormalizado = montoAbsoluto.negate();
        }

        return montoNormalizado.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private String validarDescripcion(String valor) {

        if (valor == null || valor.isBlank()) {
            throw new ValidacionDatoException(
                    "La descripción está vacía"
            );
        }

        return valor.trim();
    }

    private void validarDuplicado(
            Long cuentaId,
            LocalDate fecha,
            String tipoMovimiento,
            BigDecimal monto,
            String descripcion) {

        String identificadorMovimiento =
                cuentaId
                        + "|"
                        + fecha
                        + "|"
                        + tipoMovimiento
                        + "|"
                        + monto.stripTrailingZeros().toPlainString()
                        + "|"
                        + descripcion.toLowerCase(Locale.ROOT);

        if (!movimientosProcesados.add(
                identificadorMovimiento)) {

            throw new ValidacionDatoException(
                    "Movimiento duplicado en el archivo"
            );
        }
    }
}