package cl.duoc.bank_batch.listener;

import cl.duoc.bank_batch.modelo.TransaccionCsv;
import cl.duoc.bank_batch.modelo.TransaccionProcesada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.jdbc.core.JdbcTemplate;

public class ListenerRechazosTransaccion
        implements SkipListener<TransaccionCsv, TransaccionProcesada> {

    private static final Logger logger =
            LoggerFactory.getLogger(ListenerRechazosTransaccion.class);

    private static final String NOMBRE_JOB =
            "jobTransaccionesDiarias";

    private static final String NOMBRE_STEP =
            "stepProcesarTransacciones";

    private static final String SQL_INSERTAR_RECHAZO = """
            INSERT INTO registros_rechazados (
                nombre_job,
                nombre_step,
                archivo_origen,
                numero_linea,
                contenido_original,
                motivo_rechazo
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final String archivoOrigen;

    public ListenerRechazosTransaccion(
            JdbcTemplate jdbcTemplate,
            String archivoOrigen
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.archivoOrigen = archivoOrigen;
    }

    @Override
    public void onSkipInProcess(
            TransaccionCsv transaccion,
            Throwable excepcion
    ) {
        guardarRechazo(
                transaccion.getNumeroLinea(),
                obtenerContenido(transaccion),
                excepcion
        );
    }

    @Override
    public void onSkipInRead(Throwable excepcion) {
        guardarRechazo(
                null,
                excepcion.getMessage(),
                excepcion
        );
    }

    @Override
    public void onSkipInWrite(
            TransaccionProcesada transaccion,
            Throwable excepcion
    ) {
        guardarRechazo(
                null,
                transaccion == null
                        ? "Contenido no disponible"
                        : transaccion.toString(),
                excepcion
        );
    }

    private void guardarRechazo(
            Long numeroLinea,
            String contenido,
            Throwable excepcion
    ) {
        String contenidoSeguro =
                contenido == null || contenido.isBlank()
                        ? "Contenido no disponible"
                        : contenido;

        String motivo =
                excepcion.getMessage() == null
                        ? excepcion.getClass().getSimpleName()
                        : excepcion.getMessage();

        jdbcTemplate.update(
                SQL_INSERTAR_RECHAZO,
                NOMBRE_JOB,
                NOMBRE_STEP,
                archivoOrigen,
                numeroLinea,
                contenidoSeguro,
                motivo
        );

        logger.warn(
                "Registro rechazado. Archivo: {}, línea: {}, motivo: {}",
                archivoOrigen,
                numeroLinea,
                motivo
        );
    }

    private String obtenerContenido(TransaccionCsv transaccion) {
        if (transaccion.getContenidoOriginal() != null
                && !transaccion.getContenidoOriginal().isBlank()) {

            return transaccion.getContenidoOriginal();
        }

        return transaccion.toString();
    }
}