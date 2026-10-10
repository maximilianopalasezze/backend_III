package cl.duoc.bank_batch.listener;

import cl.duoc.bank_batch.modelo.MovimientoAnualCsv;
import cl.duoc.bank_batch.modelo.MovimientoAnualProcesado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.jdbc.core.JdbcTemplate;

public class ListenerRechazosMovimientoAnual
        implements SkipListener<
        MovimientoAnualCsv,
        MovimientoAnualProcesado> {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    ListenerRechazosMovimientoAnual.class
            );

    private static final String NOMBRE_JOB =
            "jobEstadosCuentaAnuales";

    private static final String NOMBRE_STEP =
            "stepProcesarMovimientosAnuales";

    private final JdbcTemplate jdbcTemplate;
    private final String archivoOrigen;

    public ListenerRechazosMovimientoAnual(
            JdbcTemplate jdbcTemplate,
            String archivoOrigen) {

        this.jdbcTemplate = jdbcTemplate;
        this.archivoOrigen = archivoOrigen;
    }

    @Override
    public void onSkipInProcess(
            MovimientoAnualCsv item,
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
            MovimientoAnualProcesado item,
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
                motivoRechazo == null
                        || motivoRechazo.isBlank()
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
                "Movimiento anual rechazado. Archivo: {}, línea: {}, motivo: {}",
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