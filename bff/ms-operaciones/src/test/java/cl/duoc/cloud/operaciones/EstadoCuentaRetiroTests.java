package cl.duoc.cloud.operaciones;

import cl.duoc.cloud.operaciones.config.ApiErrorHandler.CuentaCerrada;
import cl.duoc.cloud.operaciones.config.ApiErrorHandler.SaldoInsuficiente;
import cl.duoc.cloud.operaciones.model.RetiroRequest;
import cl.duoc.cloud.operaciones.repo.OperacionesRepository;
import cl.duoc.cloud.operaciones.service.OperacionesService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(EstadoCuentaRetiroTests.Config.class)
class EstadoCuentaRetiroTests {
    @Configuration @EnableTransactionManagement
    @Import({OperacionesRepository.class,OperacionesService.class})
    static class Config {
        @Bean DataSource ds() {
            var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:estado_retiros;MODE=MySQL;DB_CLOSE_DELAY=-1");return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean DataSourceTransactionManager txManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired OperacionesService service;

    @BeforeEach void preparar() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS cuentas(cuenta_id BIGINT PRIMARY KEY,saldo DECIMAL(15,2) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS cuentas_estado(cuenta_id BIGINT PRIMARY KEY REFERENCES cuentas(cuenta_id),estado VARCHAR(10))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS operaciones_cajero(id BIGINT AUTO_INCREMENT PRIMARY KEY,referencia VARCHAR(36) UNIQUE,cuenta_id BIGINT,tipo_operacion VARCHAR(20),monto DECIMAL(15,2),saldo_anterior DECIMAL(15,2),saldo_posterior DECIMAL(15,2),estado VARCHAR(20))");
        jdbc.update("DELETE FROM operaciones_cajero");jdbc.update("DELETE FROM cuentas_estado");jdbc.update("DELETE FROM cuentas");
        jdbc.update("INSERT INTO cuentas VALUES(900001,2000)");
    }

    @Test void cerradaRechazaRetiroYConservaSaldoSinRegistrarOperacion() {
        jdbc.update("INSERT INTO cuentas_estado VALUES(900001,'CERRADA')");
        assertThrows(CuentaCerrada.class, () -> service.retirar(900001L,new RetiroRequest(new BigDecimal("1000"))));
        assertEquals(0,new BigDecimal("2000").compareTo(jdbc.queryForObject("SELECT saldo FROM cuentas",BigDecimal.class)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operaciones_cajero",Integer.class));
    }

    @Test void cuentaAnteriorSinEstadoConservaElRetiroTransaccional() {
        var r=service.retirar(900001L,new RetiroRequest(new BigDecimal("1000")));
        assertEquals("APROBADA",r.estado());
        assertEquals(0,new BigDecimal("1000").compareTo(jdbc.queryForObject("SELECT saldo FROM cuentas",BigDecimal.class)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM operaciones_cajero",Integer.class));
    }

    @Test void cuentaActivaConSaldoInsuficienteConservaSaldoSinOperacion() {
        jdbc.update("INSERT INTO cuentas_estado VALUES(900001,'ACTIVA')");
        assertThrows(SaldoInsuficiente.class, () -> service.retirar(900001L,new RetiroRequest(new BigDecimal("3000"))));
        assertEquals(0,new BigDecimal("2000").compareTo(jdbc.queryForObject("SELECT saldo FROM cuentas",BigDecimal.class)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operaciones_cajero",Integer.class));
    }
}
