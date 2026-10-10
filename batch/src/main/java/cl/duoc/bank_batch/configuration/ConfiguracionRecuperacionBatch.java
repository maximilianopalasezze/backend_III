package cl.duoc.bank_batch.configuration;

import cl.duoc.bank_batch.listener.ListenerFalloControlado;
import cl.duoc.bank_batch.servicio.ServicioControlReinicio;
import cl.duoc.bank_batch.servicio.ServicioEjecucionRecuperable;
import cl.duoc.bank_batch.utilidad.RecursoEntradaBatch;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Configuration
public class ConfiguracionRecuperacionBatch {
    @Bean
    public ListenerFalloControlado listenerFalloControlado(ServicioControlReinicio control,
            @Value("${batch.prueba.fallar-despues-commits:0}") int commits) {
        return new ListenerFalloControlado(control, commits);
    }

    @Bean
    @ConditionalOnProperty(name = "batch.reinicio.automatico", havingValue = "true")
    public ApplicationRunner ejecutarLoteRecuperable(List<Job> jobs, Environment entorno,
            ServicioEjecucionRecuperable servicio) {
        return args -> {
            if (entorno.getProperty("spring.batch.job.enabled", Boolean.class, false)) {
                throw new IllegalArgumentException("Use BATCH_JOB_ENABLED=false con BATCH_REINICIO_AUTOMATICO=true");
            }
            String nombreJob = entorno.getRequiredProperty("spring.batch.job.name");
            Job job = jobs.stream().filter(candidato -> candidato.getName().equals(nombreJob))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Job no encontrado: " + nombreJob));
            String lote = entorno.getProperty("batch.reinicio.lote", "").trim();
            if (lote.isEmpty()) {
                throw new IllegalArgumentException("Defina BATCH_LOTE para identificar el lote recuperable");
            }
            String propiedadArchivo = switch (nombreJob) {
                case "jobTransaccionesDiarias" -> "batch.archivo.transacciones";
                case "jobInteresesMensuales" -> "batch.archivo.intereses";
                case "jobEstadosCuentaAnuales" -> "batch.archivo.estados";
                default -> throw new IllegalArgumentException("Job sin archivo configurado: " + nombreJob);
            };
            String archivo = entorno.getRequiredProperty(propiedadArchivo);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream entrada = new DigestInputStream(RecursoEntradaBatch.cargar(archivo).getInputStream(), digest)) {
                entrada.transferTo(java.io.OutputStream.nullOutputStream());
            }
            JobParametersBuilder parametros = new JobParametersBuilder()
                    .addString("lote", lote)
                    .addString("archivo", archivo, false)
                    .addString("sha256", HexFormat.of().formatHex(digest.digest()), false)
                    .addString("hilos", entorno.getRequiredProperty("batch.escalamiento.hilos"), false)
                    .addString("chunk", entorno.getRequiredProperty("batch.escalamiento.chunk"), false)
                    .addString("simular.fallo", Boolean.toString(entorno.getProperty(
                            "batch.prueba.fallar-despues-commits", Integer.class, 0) > 0), false);
            if (nombreJob.equals("jobInteresesMensuales")) {
                for (String clave : List.of("periodo", "tasa-ahorro", "tasa-prestamo", "tasa-hipoteca")) {
                    parametros.addString(clave, entorno.getRequiredProperty("batch.intereses." + clave), false);
                }
            } else if (nombreJob.equals("jobEstadosCuentaAnuales")) {
                parametros.addString("anio", entorno.getRequiredProperty("batch.estados.anio"), false);
            }
            servicio.ejecutar(job, parametros.toJobParameters(),
                    entorno.getProperty("batch.reinicio.max-ejecuciones-step", Integer.class, 3));
        };
    }
}
