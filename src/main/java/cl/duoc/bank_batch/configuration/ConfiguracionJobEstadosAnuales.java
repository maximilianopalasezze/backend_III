package cl.duoc.bank_batch.configuration;

import cl.duoc.bank_batch.excepcion.ValidacionDatoException;
import cl.duoc.bank_batch.listener.ListenerRechazosMovimientoAnual;
import cl.duoc.bank_batch.listener.ListenerResumenEstadosAnuales;
import cl.duoc.bank_batch.modelo.MovimientoAnualCsv;
import cl.duoc.bank_batch.modelo.MovimientoAnualProcesado;
import cl.duoc.bank_batch.procesador.ProcesadorMovimientoAnual;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

@Configuration
public class ConfiguracionJobEstadosAnuales {

    @Bean
    public FlatFileItemReader<MovimientoAnualCsv>
    lectorMovimientosAnuales(

            @Value("${batch.archivo.estados}")
            String archivoOrigen) {

        return new FlatFileItemReaderBuilder<MovimientoAnualCsv>()
                .name("lectorMovimientosAnuales")
                .resource(new ClassPathResource(archivoOrigen))
                .encoding(StandardCharsets.UTF_8.name())
                .linesToSkip(1)
                .strict(true)
                .lineMapper((linea, numeroLinea) -> {

                    String[] campos = linea.split(",", -1);

                    if (campos.length != 5) {
                        throw new ValidacionDatoException(
                                "La línea no contiene las 5 columnas requeridas"
                        );
                    }

                    return new MovimientoAnualCsv(
                            campos[0],
                            campos[1],
                            campos[2],
                            campos[3],
                            campos[4],
                            numeroLinea,
                            linea
                    );
                })
                .build();
    }

    @Bean
    public ProcesadorMovimientoAnual
    procesadorMovimientosAnuales(

            @Value("${batch.archivo.estados}")
            String archivoOrigen,

            @Value("${batch.estados.anio}")
            int anioProcesado) {

        return new ProcesadorMovimientoAnual(
                archivoOrigen,
                anioProcesado
        );
    }

    @Bean
    public JdbcBatchItemWriter<MovimientoAnualProcesado>
    escritorMovimientosAnuales(DataSource dataSource) {

        return new JdbcBatchItemWriterBuilder<
                MovimientoAnualProcesado>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO movimientos_anuales_procesados (
                            cuenta_id,
                            fecha,
                            tipo_movimiento,
                            monto,
                            descripcion,
                            archivo_origen
                        )
                        VALUES (
                            :cuentaId,
                            :fecha,
                            :tipoMovimiento,
                            :monto,
                            :descripcion,
                            :archivoOrigen
                        )
                        ON DUPLICATE KEY UPDATE
                            descripcion = VALUES(descripcion)
                        """)
                .beanMapped()
                .build();
    }

    @Bean
    public ListenerRechazosMovimientoAnual
    listenerRechazosMovimientoAnual(

            JdbcTemplate jdbcTemplate,

            @Value("${batch.archivo.estados}")
            String archivoOrigen) {

        return new ListenerRechazosMovimientoAnual(
                jdbcTemplate,
                archivoOrigen
        );
    }

    @Bean
    public ListenerResumenEstadosAnuales
    listenerResumenEstadosAnuales(

            JdbcTemplate jdbcTemplate,

            @Value("${batch.archivo.estados}")
            String archivoOrigen,

            @Value("${batch.estados.anio}")
            int anioProcesado) {

        return new ListenerResumenEstadosAnuales(
                jdbcTemplate,
                archivoOrigen,
                anioProcesado
        );
    }

    @Bean
    public Step stepProcesarMovimientosAnuales(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,

            @Qualifier("lectorMovimientosAnuales")
            FlatFileItemReader<MovimientoAnualCsv>
                    lectorMovimientosAnuales,

            @Qualifier("procesadorMovimientosAnuales")
            ProcesadorMovimientoAnual
                    procesadorMovimientosAnuales,

            @Qualifier("escritorMovimientosAnuales")
            JdbcBatchItemWriter<MovimientoAnualProcesado>
                    escritorMovimientosAnuales,

            @Qualifier("listenerRechazosMovimientoAnual")
            ListenerRechazosMovimientoAnual
                    listenerRechazosMovimientoAnual) {

        return new StepBuilder(
                "stepProcesarMovimientosAnuales",
                jobRepository
        )
                .<MovimientoAnualCsv,
                        MovimientoAnualProcesado>chunk(100)
                .transactionManager(transactionManager)
                .reader(lectorMovimientosAnuales)
                .processor(procesadorMovimientosAnuales)
                .writer(escritorMovimientosAnuales)
                .faultTolerant()
                .skip(
                        ValidacionDatoException.class,
                        DataIntegrityViolationException.class
                )
                .skipLimit(2000)
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .skipListener(
                        listenerRechazosMovimientoAnual
                )
                .build();
    }

    @Bean
    public Job jobEstadosCuentaAnuales(
            JobRepository jobRepository,

            @Qualifier("stepProcesarMovimientosAnuales")
            Step stepProcesarMovimientosAnuales,

            @Qualifier("listenerResumenEstadosAnuales")
            ListenerResumenEstadosAnuales
                    listenerResumenEstadosAnuales) {

        return new JobBuilder(
                "jobEstadosCuentaAnuales",
                jobRepository
        )
                .start(stepProcesarMovimientosAnuales)
                .listener(listenerResumenEstadosAnuales)
                .build();
    }
}