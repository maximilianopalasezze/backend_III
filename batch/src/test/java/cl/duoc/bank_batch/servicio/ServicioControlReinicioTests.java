package cl.duoc.bank_batch.servicio;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicioControlReinicioTests {
    @Test
    void comparaIdentificadoresPorValorAunqueSeanObjetosDistintos() {
        JobRepository repositorio = mock(JobRepository.class);
        JobInstance instancia = mock(JobInstance.class);
        JobExecution actual = mock(JobExecution.class);
        JobExecution copia = mock(JobExecution.class);
        when(actual.getJobInstance()).thenReturn(instancia);
        Long idActual = Long.valueOf("1000");
        Long idCopia = Long.valueOf("1000");
        assertNotSame(idActual, idCopia);
        when(actual.getId()).thenReturn(idActual);
        when(copia.getId()).thenReturn(idCopia);
        when(repositorio.getJobExecutions(instancia)).thenReturn(List.of(copia));
        assertFalse(new ServicioControlReinicio(repositorio).esReinicio(actual));
    }

    @Test
    void reconoceUnaEjecucionAnteriorDeLaMismaInstancia() {
        JobRepository repositorio = mock(JobRepository.class);
        JobInstance instancia = mock(JobInstance.class);
        JobExecution actual = mock(JobExecution.class);
        JobExecution anterior = mock(JobExecution.class);
        when(actual.getJobInstance()).thenReturn(instancia);
        when(actual.getId()).thenReturn(1001L);
        when(anterior.getId()).thenReturn(1000L);
        when(repositorio.getJobExecutions(instancia)).thenReturn(List.of(actual, anterior));
        assertTrue(new ServicioControlReinicio(repositorio).esReinicio(actual));
    }
}
