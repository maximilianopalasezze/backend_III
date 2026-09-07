package cl.duoc.bank_batch.servicio;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!web & !movil & !cajero")
public class ServicioControlReinicio {

    private final JobRepository jobRepository;

    public ServicioControlReinicio(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Un JobInstance conserva los mismos parámetros identificadores.
     * Si ya tuvo otra ejecución, la actual es un reinicio y debe conservar
     * los datos confirmados para continuar desde el último checkpoint.
     */
    public boolean esReinicio(JobExecution ejecucionActual) {
        return jobRepository
                .getJobExecutions(ejecucionActual.getJobInstance())
                .stream()
                .anyMatch(ejecucionAnterior ->
                        ejecucionAnterior.getId()
                                != ejecucionActual.getId()
                );
    }
}
