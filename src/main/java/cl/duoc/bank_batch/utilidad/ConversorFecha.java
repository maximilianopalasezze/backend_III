package cl.duoc.bank_batch.utilidad;

import cl.duoc.bank_batch.excepcion.ValidacionDatoException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

public final class ConversorFecha {

    private static final List<DateTimeFormatter> FORMATOS_PERMITIDOS =
            List.of(
                    DateTimeFormatter
                            .ofPattern("uuuu-MM-dd")
                            .withResolverStyle(ResolverStyle.STRICT),

                    DateTimeFormatter
                            .ofPattern("uuuu/MM/dd")
                            .withResolverStyle(ResolverStyle.STRICT),

                    DateTimeFormatter
                            .ofPattern("dd-MM-uuuu")
                            .withResolverStyle(ResolverStyle.STRICT),

                    DateTimeFormatter
                            .ofPattern("dd/MM/uuuu")
                            .withResolverStyle(ResolverStyle.STRICT)
            );

    private ConversorFecha() {
    }

    public static LocalDate convertir(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ValidacionDatoException(
                    "La fecha está vacía"
            );
        }

        String fechaLimpia = valor.trim();

        for (DateTimeFormatter formato : FORMATOS_PERMITIDOS) {
            try {
                return LocalDate.parse(fechaLimpia, formato);
            } catch (DateTimeParseException ignored) {
                // Se intenta con el formato siguiente.
            }
        }

        throw new ValidacionDatoException(
                "Formato de fecha inválido: " + fechaLimpia
        );
    }
}