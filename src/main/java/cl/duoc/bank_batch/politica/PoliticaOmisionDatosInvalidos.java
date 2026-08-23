package cl.duoc.bank_batch.politica;

import cl.duoc.bank_batch.excepcion.ValidacionDatoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Permite omitir solamente errores de calidad de datos conocidos.
 * Los errores técnicos no contemplados detienen el Step para evitar
 * ocultar fallos de infraestructura o programación.
 */
public class PoliticaOmisionDatosInvalidos implements SkipPolicy {

    private static final Logger logger =
            LoggerFactory.getLogger(PoliticaOmisionDatosInvalidos.class);

    private final long limiteOmisiones;

    public PoliticaOmisionDatosInvalidos(long limiteOmisiones) {
        if (limiteOmisiones < 0) {
            throw new IllegalArgumentException(
                    "El límite de omisiones no puede ser negativo"
            );
        }

        this.limiteOmisiones = limiteOmisiones;
    }

    @Override
    public boolean shouldSkip(Throwable excepcion, long cantidadOmitida) {
        boolean errorOmitible = contieneCausa(
                excepcion,
                ValidacionDatoException.class
        ) || contieneCausa(
                excepcion,
                FlatFileParseException.class
        ) || contieneCausa(
                excepcion,
                DataIntegrityViolationException.class
        );

        if (!errorOmitible) {
            return false;
        }

        boolean dentroDelLimite = cantidadOmitida < limiteOmisiones;

        if (!dentroDelLimite) {
            logger.error(
                    "Se alcanzó el límite de {} omisiones. El Step será detenido.",
                    limiteOmisiones
            );
        }

        return dentroDelLimite;
    }

    private boolean contieneCausa(
            Throwable excepcion,
            Class<? extends Throwable> tipoEsperado) {

        Throwable causaActual = excepcion;

        while (causaActual != null) {
            if (tipoEsperado.isInstance(causaActual)) {
                return true;
            }

            causaActual = causaActual.getCause();
        }

        return false;
    }
}
