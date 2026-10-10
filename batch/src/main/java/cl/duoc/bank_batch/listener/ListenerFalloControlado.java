package cl.duoc.bank_batch.listener;

import cl.duoc.bank_batch.servicio.ServicioControlReinicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.ItemReadListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/** Falla antes de leer el siguiente chunk, luego de N commits, solo en el primer intento. */
public class ListenerFalloControlado implements ItemReadListener<Object>, StepExecutionListener {
    private static final Logger logger = LoggerFactory.getLogger(ListenerFalloControlado.class);
    private final ServicioControlReinicio controlReinicio;
    private final int commitsAntesDelFallo;
    private StepExecution ejecucion;
    private boolean armado;

    public ListenerFalloControlado(ServicioControlReinicio controlReinicio, int commitsAntesDelFallo) {
        if (commitsAntesDelFallo < 0) {
            throw new IllegalArgumentException("La cantidad de commits antes del fallo no puede ser negativa");
        }
        this.controlReinicio = controlReinicio;
        this.commitsAntesDelFallo = commitsAntesDelFallo;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        ejecucion = stepExecution;
        armado = commitsAntesDelFallo > 0
                && "true".equals(stepExecution.getJobExecution().getJobParameters().getString("simular.fallo"))
                && !controlReinicio.esReinicio(stepExecution.getJobExecution());
    }

    @Override
    public void beforeRead() {
        if (armado && ejecucion.getCommitCount() >= commitsAntesDelFallo) {
            armado = false;
            logger.error("FALLO CONTROLADO: instancia={}, ejecución={}, commits confirmados={}, leídos={}; checkpoint conservado.",
                    ejecucion.getJobExecution().getJobInstance().getId(),
                    ejecucion.getJobExecution().getId(), ejecucion.getCommitCount(), ejecucion.getReadCount());
            throw new IllegalStateException("Interrupción controlada para verificar el reinicio desde el checkpoint");
        }
    }
}
