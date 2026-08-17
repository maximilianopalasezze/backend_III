package cl.duoc.bank_batch.configuration;

import cl.duoc.bank_batch.excepcion.ValidacionDatoException;
import cl.duoc.bank_batch.listener.ListenerRechazosTransaccion;
import cl.duoc.bank_batch.listener.ListenerResumenTransacciones;
import cl.duoc.bank_batch.modelo.TransaccionCsv;
import cl.duoc.bank_batch.modelo.TransaccionProcesada;
import cl.duoc.bank_batch.procesador.ProcesadorTransaccion;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class ConfiguracionJobTransacciones {

    @Bean
    public FlatFileItemReader<TransaccionCsv> lectorTransacciones(
            @Value("${batch.archivo.transacciones}")
            String archivoOrigen
    ) {
        return new FlatFileItemReaderBuilder<TransaccionCsv>()
                .name("lectorTransacciones")
                .resource(new ClassPathResource(archivoOrigen))
                .encoding("UTF-8")
                .linesToSkip(1)
                .strict(true)
                .lineMapper(this::mapearLinea)
                .build();
    }

    @Bean
    public ProcesadorTransaccion procesadorTransaccion(
            @Value("${batch.archivo.transacciones}")
            String archivoOrigen
    ) {
        return new ProcesadorTransaccion(archivoOrigen);
    }

    @Bean
    public JdbcBatchItemWriter<TransaccionProcesada>
    escritorTransacciones(DataSource dataSource) {

        String sql = """
                INSERT INTO transacciones_procesadas (
                    transaccion_id,
                    fecha,
                    monto,
                    tipo,
                    es_anomalia,
                    detalle_anomalia,
                    archivo_origen
                )
                VALUES (
                    :transaccionId,
                    :fecha,
                    :monto,
                    :tipo,
                    :anomalia,
                    :detalleAnomalia,
                    :archivoOrigen
                )
                ON DUPLICATE KEY UPDATE
                    fecha = :fecha,
                    monto = :monto,
                    tipo = :tipo,
                    es_anomalia = :anomalia,
                    detalle_anomalia = :detalleAnomalia
                """;

        return new JdbcBatchItemWriterBuilder<TransaccionProcesada>()
                .dataSource(dataSource)
                .sql(sql)
                .beanMapped()
                .build();
    }

    @Bean
    public ListenerRechazosTransaccion listenerRechazosTransaccion(
            JdbcTemplate jdbcTemplate,
            @Value("${batch.archivo.transacciones}")
            String archivoOrigen
    ) {
        return new ListenerRechazosTransaccion(
                jdbcTemplate,
                archivoOrigen
        );
    }

    @Bean
    public ListenerResumenTransacciones listenerResumenTransacciones(
            JdbcTemplate jdbcTemplate,
            @Value("${batch.archivo.transacciones}")
            String archivoOrigen
    ) {
        return new ListenerResumenTransacciones(
                jdbcTemplate,
                archivoOrigen
        );
    }

    @Bean
    public Step stepProcesarTransacciones(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,

            @Qualifier("lectorTransacciones")
            FlatFileItemReader<TransaccionCsv> lector,

            @Qualifier("procesadorTransaccion")
            ProcesadorTransaccion procesador,

            @Qualifier("escritorTransacciones")
            JdbcBatchItemWriter<TransaccionProcesada> escritor,

            ListenerRechazosTransaccion listenerRechazos
    ) {
        return new StepBuilder(
                "stepProcesarTransacciones",
                jobRepository
        )
                .<TransaccionCsv, TransaccionProcesada>chunk(100)
                .transactionManager(transactionManager)
                .reader(lector)
                .processor(procesador)
                .writer(escritor)
                .faultTolerant()
                .skip(ValidacionDatoException.class)
                .skipLimit(1000)
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .skipListener(listenerRechazos)
                .build();
    }

    @Bean
    public Job jobTransaccionesDiarias(
            JobRepository jobRepository,

            @Qualifier("stepProcesarTransacciones")
            Step step,

            ListenerResumenTransacciones listenerResumen
    ) {
        return new JobBuilder(
                "jobTransaccionesDiarias",
                jobRepository
        )
                .listener(listenerResumen)
                .start(step)
                .build();
    }

    private TransaccionCsv mapearLinea(
            String linea,
            int numeroLinea
    ) {
        String[] columnas = linea.split(",", -1);

        if (columnas.length != 4) {
            throw new ValidacionDatoException(
                    "Cantidad de columnas inválida en la línea "
                            + numeroLinea
                            + ". Contenido: "
                            + linea
            );
        }

        return new TransaccionCsv(
                columnas[0],
                columnas[1],
                columnas[2],
                columnas[3],
                numeroLinea,
                linea
        );
    }
}