package cl.duoc.bank_batch.listener;

import cl.duoc.bank_batch.modelo.InteresCsv;
import cl.duoc.bank_batch.modelo.InteresProcesado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.jdbc.core.JdbcTemplate;

public class ListenerRechazosInteres
        implements SkipListener<InteresCsv, InteresProcesado> {

    private static final Logger logger =
            LoggerFactory.getLogger(ListenerRechazosInteres.class);

    private static final String NOMBRE_JOB =
            "jobInteresesMensuales";

    private static final String NOMBRE_STEP =
            "stepProcesarIntereses";

    private final JdbcTemplate jdbcTemplate;
    private final String archivoOrigen;

    public ListenerRechazosInteres(
            JdbcTemplate jdbcTemplate,
            String archivoOrigen) {

        this.jdbcTemplate = jdbcTemplate;
        this.archivoOrigen = archivoOrigen;
    }

    @Override
    public void onSkipInProcess(
            InteresCsv item,
            Throwable excepcion) {

        registrarRechazo(
                item.getNumeroLinea(),
                item.getContenidoOriginal(),
                obtenerMotivo(excepcion)
        );
    }

    @Override
    public void onSkipInRead(Throwable excepcion) {

        registrarRechazo(
                null,
                "No fue posible recuperar el contenido de la línea",
                obtenerMotivo(excepcion)
        );
    }

    @Override
    public void onSkipInWrite(
            InteresProcesado item,
            Throwable excepcion) {

        registrarRechazo(
                null,
                String.valueOf(item),
                obtenerMotivo(excepcion)
        );
    }

    private void registrarRechazo(
            Long numeroLinea,
            String contenidoOriginal,
            String motivoRechazo) {

        String contenidoSeguro =
                contenidoOriginal == null
                        ? "Contenido no disponible"
                        : contenidoOriginal;

        String motivoSeguro =
                motivoRechazo == null || motivoRechazo.isBlank()
                        ? "Error no especificado"
                        : motivoRechazo;

        jdbcTemplate.update("""
                INSERT INTO registros_rechazados (
                    nombre_job,
                    nombre_step,
                    archivo_origen,
                    numero_linea,
                    contenido_original,
                    motivo_rechazo
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                NOMBRE_JOB,
                NOMBRE_STEP,
                archivoOrigen,
                numeroLinea,
                contenidoSeguro,
                motivoSeguro
        );

        logger.warn(
                "Registro de interés rechazado. Archivo: {}, línea: {}, motivo: {}",
                archivoOrigen,
                numeroLinea,
                motivoSeguro
        );
    }

    private String obtenerMotivo(Throwable excepcion) {

        if (excepcion == null) {
            return "Error no especificado";
        }

        if (excepcion.getMessage() != null
                && !excepcion.getMessage().isBlank()) {
            return excepcion.getMessage();
        }

        return excepcion.getClass().getSimpleName();
    }
}