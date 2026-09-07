package cl.duoc.bank_batch.configuration;

import cl.duoc.bank_batch.excepcion.ValidacionDatoException;
import cl.duoc.bank_batch.listener.ListenerHilosProcesamiento;
import cl.duoc.bank_batch.listener.ListenerRechazosInteres;
import cl.duoc.bank_batch.listener.ListenerReintentosBatch;
import cl.duoc.bank_batch.listener.ListenerRendimientoBatch;
import cl.duoc.bank_batch.listener.ListenerResumenIntereses;
import cl.duoc.bank_batch.modelo.InteresCsv;
import cl.duoc.bank_batch.modelo.InteresProcesado;
import cl.duoc.bank_batch.procesador.ProcesadorInteres;
import cl.duoc.bank_batch.servicio.ServicioControlReinicio;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.support.CompositeItemWriter;
import org.springframework.batch.infrastructure.item.support.SynchronizedItemStreamReader;
import org.springframework.batch.infrastructure.item.support.builder.CompositeItemWriterBuilder;
import org.springframework.batch.infrastructure.item.support.builder.SynchronizedItemStreamReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Configuration
@Profile("!web & !movil & !cajero")
public class ConfiguracionJobIntereses {

    @Bean
    public FlatFileItemReader<InteresCsv> lectorInteresesBase(
            @Value("${batch.archivo.intereses}")
            String archivoOrigen) {

        return new FlatFileItemReaderBuilder<InteresCsv>()
                .name("lectorIntereses")
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

                    return new InteresCsv(
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
    public SynchronizedItemStreamReader<InteresCsv> lectorIntereses(
            @Qualifier("lectorInteresesBase")
            FlatFileItemReader<InteresCsv> lectorBase) {

        return new SynchronizedItemStreamReaderBuilder<InteresCsv>()
                .delegate(lectorBase)
                .build();
    }

    @Bean
    public ProcesadorInteres procesadorIntereses(
            @Value("${batch.archivo.intereses}")
            String archivoOrigen,

            @Value("${batch.intereses.periodo}")
            String periodo,

            @Value("${batch.intereses.tasa-ahorro}")
            BigDecimal tasaAhorro,

            @Value("${batch.intereses.tasa-prestamo}")
            BigDecimal tasaPrestamo,

            @Value("${batch.intereses.tasa-hipoteca}")
            BigDecimal tasaHipoteca) {

        return new ProcesadorInteres(
                archivoOrigen,
                periodo,
                tasaAhorro,
                tasaPrestamo,
                tasaHipoteca
        );
    }

    @Bean
    public JdbcBatchItemWriter<InteresProcesado>
    escritorActualizarCuentas(DataSource dataSource) {

        return new JdbcBatchItemWriterBuilder<InteresProcesado>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO cuentas (
                            cuenta_id,
                            nombre,
                            saldo,
                            edad,
                            tipo_cuenta
                        )
                        VALUES (
                            :cuentaId,
                            :nombre,
                            :saldoFinal,
                            :edad,
                            :tipoCuenta
                        )
                        ON DUPLICATE KEY UPDATE
                            nombre = VALUES(nombre),
                            saldo = VALUES(saldo),
                            edad = VALUES(edad),
                            tipo_cuenta = VALUES(tipo_cuenta)
                        """)
                .beanMapped()
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<InteresProcesado>
    escritorCalculosIntereses(DataSource dataSource) {

        return new JdbcBatchItemWriterBuilder<InteresProcesado>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO intereses_calculados (
                            cuenta_id,
                            periodo,
                            saldo_inicial,
                            tasa_interes,
                            interes_calculado,
                            saldo_final,
                            archivo_origen
                        )
                        VALUES (
                            :cuentaId,
                            :periodo,
                            :saldoInicial,
                            :tasaInteres,
                            :interesCalculado,
                            :saldoFinal,
                            :archivoOrigen
                        )
                        ON DUPLICATE KEY UPDATE
                            saldo_inicial = VALUES(saldo_inicial),
                            tasa_interes = VALUES(tasa_interes),
                            interes_calculado = VALUES(interes_calculado),
                            saldo_final = VALUES(saldo_final)
                        """)
                .beanMapped()
                .build();
    }

    @Bean
    public CompositeItemWriter<InteresProcesado>
    escritorCompuestoIntereses(

            @Qualifier("escritorActualizarCuentas")
            JdbcBatchItemWriter<InteresProcesado>
                    escritorActualizarCuentas,

            @Qualifier("escritorCalculosIntereses")
            JdbcBatchItemWriter<InteresProcesado>
                    escritorCalculosIntereses) {

        return new CompositeItemWriterBuilder<InteresProcesado>()
                .delegates(
                        escritorActualizarCuentas,
                        escritorCalculosIntereses
                )
                .build();
    }

    @Bean
    public ListenerRechazosInteres listenerRechazosInteres(
            JdbcTemplate jdbcTemplate,

            @Value("${batch.archivo.intereses}")
            String archivoOrigen) {

        return new ListenerRechazosInteres(
                jdbcTemplate,
                archivoOrigen
        );
    }

    @Bean
    public ListenerResumenIntereses listenerResumenIntereses(
            JdbcTemplate jdbcTemplate,
            ServicioControlReinicio servicioControlReinicio,

            @Value("${batch.archivo.intereses}")
            String archivoOrigen,

            @Value("${batch.intereses.periodo}")
            String periodo) {

        return new ListenerResumenIntereses(
                jdbcTemplate,
                archivoOrigen,
                periodo,
                servicioControlReinicio
        );
    }

    @Bean
    public Step stepProcesarIntereses(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,

            @Qualifier("lectorIntereses")
            SynchronizedItemStreamReader<InteresCsv> lectorIntereses,

            @Qualifier("procesadorIntereses")
            ProcesadorInteres procesadorIntereses,

            @Qualifier("escritorCompuestoIntereses")
            CompositeItemWriter<InteresProcesado>
                    escritorCompuestoIntereses,

            @Qualifier("listenerRechazosInteres")
            ListenerRechazosInteres listenerRechazosInteres,

            @Qualifier("politicaOmisionDatosInvalidos")
            SkipPolicy politicaOmisionDatosInvalidos,

            @Qualifier("politicaReintentoTransitorio")
            RetryPolicy politicaReintentoTransitorio,

            @Qualifier("ejecutorBatch")
            AsyncTaskExecutor ejecutorBatch,

            ListenerReintentosBatch listenerReintentosBatch,
            ListenerRendimientoBatch listenerRendimientoBatch,
            ListenerHilosProcesamiento listenerHilosProcesamiento,

            @Value("${batch.escalamiento.chunk:5}")
            int tamanoChunk,

            @Value("${batch.reinicio.max-ejecuciones-step:3}")
            int maximoEjecucionesStep) {

        return new StepBuilder(
                "stepProcesarIntereses",
                jobRepository
                )
                .startLimit(maximoEjecucionesStep)
                .allowStartIfComplete(false)
                .<InteresCsv, InteresProcesado>chunk(tamanoChunk)
                .transactionManager(transactionManager)
                .reader(lectorIntereses)
                .processor(procesadorIntereses)
                .writer(escritorCompuestoIntereses)
                .faultTolerant()
                .skipPolicy(politicaOmisionDatosInvalidos)
                .retryPolicy(politicaReintentoTransitorio)
                .skipListener(listenerRechazosInteres)
                .retryListener(listenerReintentosBatch)
                .listener(listenerRendimientoBatch)
                .listener(listenerHilosProcesamiento)
                .taskExecutor(ejecutorBatch)
                .build();
    }

    @Bean
    public Job jobInteresesMensuales(
            JobRepository jobRepository,

            @Qualifier("stepProcesarIntereses")
            Step stepProcesarIntereses,

            @Qualifier("listenerResumenIntereses")
            ListenerResumenIntereses listenerResumenIntereses) {

        return new JobBuilder(
                "jobInteresesMensuales",
                jobRepository
        )
                .incrementer(new RunIdIncrementer())
                .start(stepProcesarIntereses)
                .listener(listenerResumenIntereses)
                .build();
    }
}
