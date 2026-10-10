package cl.duoc.bank_batch.procesador;

import cl.duoc.bank_batch.excepcion.ValidacionDatoException;
import cl.duoc.bank_batch.modelo.TransaccionCsv;
import cl.duoc.bank_batch.modelo.TransaccionProcesada;
import cl.duoc.bank_batch.utilidad.ConversorFecha;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

public class ProcesadorTransaccion
        implements ItemProcessor<TransaccionCsv, TransaccionProcesada> {

    private static final Pattern ACENTOS =
            Pattern.compile("\\p{M}+");

    private final String archivoOrigen;

    public ProcesadorTransaccion(String archivoOrigen) {
        this.archivoOrigen = archivoOrigen;
    }

    @Override
    public TransaccionProcesada process(TransaccionCsv transaccion) {

        if (transaccion == null) {
            throw new ValidacionDatoException(
                    "La transacción recibida es nula"
            );
        }

        Long id = convertirId(transaccion.getId());

        LocalDate fecha =
                ConversorFecha.convertir(transaccion.getFecha());

        BigDecimal monto =
                convertirMonto(transaccion.getMonto());

        String tipo =
                validarTipo(transaccion.getTipo());

        boolean esAnomalia = false;
        String detalleAnomalia = null;

        if (monto.compareTo(BigDecimal.ZERO) < 0) {
            esAnomalia = true;
            detalleAnomalia = "Monto negativo";
        } else if (monto.compareTo(BigDecimal.ZERO) == 0) {
            esAnomalia = true;
            detalleAnomalia = "Monto igual a cero";
        }

        return new TransaccionProcesada(
                id,
                fecha,
                monto,
                tipo,
                esAnomalia,
                detalleAnomalia,
                archivoOrigen
        );
    }

    private Long convertirId(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ValidacionDatoException(
                    "El identificador está vacío"
            );
        }

        try {
            long id = Long.parseLong(valor.trim());

            if (id <= 0) {
                throw new ValidacionDatoException(
                        "El identificador debe ser mayor que cero"
                );
            }

            return id;

        } catch (NumberFormatException excepcion) {
            throw new ValidacionDatoException(
                    "El identificador no es numérico: " + valor,
                    excepcion
            );
        }
    }

    private BigDecimal convertirMonto(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ValidacionDatoException(
                    "El monto está vacío"
            );
        }

        try {
            return new BigDecimal(valor.trim());

        } catch (NumberFormatException excepcion) {
            throw new ValidacionDatoException(
                    "El monto no es numérico: " + valor,
                    excepcion
            );
        }
    }

    private String validarTipo(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ValidacionDatoException(
                    "El tipo de transacción está vacío"
            );
        }

        String tipoNormalizado = normalizarTexto(valor);

        if (!tipoNormalizado.equals("debito")
                && !tipoNormalizado.equals("credito")) {

            throw new ValidacionDatoException(
                    "Tipo de transacción no permitido: " + valor.trim()
            );
        }

        return tipoNormalizado;
    }

    private String normalizarTexto(String valor) {
        String textoSeparado = Normalizer.normalize(
                valor.trim(),
                Normalizer.Form.NFD
        );

        return ACENTOS.matcher(textoSeparado)
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
    }
}