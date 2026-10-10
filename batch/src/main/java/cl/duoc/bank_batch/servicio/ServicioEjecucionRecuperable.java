package cl.duoc.bank_batch.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Ejecuta y reintenta una misma instancia, sin incrementar sus parámetros. */
@Service
public class ServicioEjecucionRecuperable {
    private static final Logger logger = LoggerFactory.getLogger(ServicioEjecucionRecuperable.class);
    private final JobOperator jobOperator;
    private final JobRepository jobRepository;

    public ServicioEjecucionRecuperable(JobOperator jobOperator, JobRepository jobRepository) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
    }

    @SuppressWarnings("removal")
    public JobExecution ejecutar(Job job, JobParameters parametros, int maximoEjecuciones) throws Exception {
        if (maximoEjecuciones < 1) {
            throw new IllegalArgumentException("Debe permitirse al menos una ejecución por lote");
        }
        JobExecution ultima = jobRepository.getLastJobExecution(job.getName(), parametros);
        int realizadas = 0;
        if (ultima != null) {
            validarConfiguracion(ultima, parametros);
            if (ultima.getStatus() == BatchStatus.COMPLETED) {
                logger.info("Lote ya COMPLETED: instancia={}, ejecución={}. No se vuelve a procesar.",
                        ultima.getJobInstance().getId(), ultima.getId());
                return ultima;
            }
            validarEstadoReiniciable(ultima);
            realizadas = jobRepository.getJobExecutions(ultima.getJobInstance()).size();
        }

        while (realizadas < maximoEjecuciones) {
            if (ultima != null) {
                logger.warn("REINICIO AUTOMÁTICO: instancia={}, ejecución anterior={}, estado={}; se conserva el checkpoint.",
                        ultima.getJobInstance().getId(), ultima.getId(), ultima.getStatus());
            }
            // run respeta los parámetros. start ignora los parámetros cuando existe RunIdIncrementer.
            ultima = jobOperator.run(job, parametros);
            realizadas++;
            logger.info("RECUPERACIÓN BATCH: instancia={}, ejecución={}, intento={}/{}, estado={}",
                    ultima.getJobInstance().getId(), ultima.getId(), realizadas,
                    maximoEjecuciones, ultima.getStatus());
            if (ultima.getStatus() == BatchStatus.COMPLETED) {
                return ultima;
            }
            validarEstadoReiniciable(ultima);
        }
        throw new IllegalStateException("Lote detenido tras " + maximoEjecuciones
                + " ejecuciones. Última ejecución=" + ultima.getId() + ", estado=" + ultima.getStatus(),
                ultima.getAllFailureExceptions().stream().findFirst().orElse(null));
    }

    private void validarEstadoReiniciable(JobExecution ejecucion) {
        if (ejecucion.getStatus() != BatchStatus.FAILED && ejecucion.getStatus() != BatchStatus.STOPPED) {
            throw new IllegalStateException("No se reinicia una ejecución con estado " + ejecucion.getStatus()
                    + ". Verifique que no exista otro proceso activo; no se modifica su estado automáticamente.");
        }
    }

    private void validarConfiguracion(JobExecution anterior, JobParameters parametros) {
        parametros.forEach(valor -> {
            String nombre = valor.name();
            var previo = anterior.getJobParameters().getParameter(nombre);
            // La inyección de falla puede desactivarse al volver a iniciar el mismo lote.
            if (!nombre.equals("simular.fallo")
                    && (previo == null || !Objects.equals(valor.value(), previo.value())
                        || !Objects.equals(valor.type(), previo.type()))) {
                throw new IllegalArgumentException("La configuración del lote cambió: " + nombre
                        + ". Use el archivo y la configuración originales para recuperar su checkpoint.");
            }
        });
    }
}
