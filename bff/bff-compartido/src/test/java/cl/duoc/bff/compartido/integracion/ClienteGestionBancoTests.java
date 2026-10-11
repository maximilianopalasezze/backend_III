package cl.duoc.bff.compartido.integracion;

import cl.duoc.bff.compartido.excepcion.*;
import cl.duoc.bff.compartido.modelo.SolicitudesBanco.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.*;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@SpringBootTest(classes=ClienteGestionBancoTests.Aplicacion.class, properties={
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "resilience4j.retry.instances.pagosService.wait-duration=1ms",
        "resilience4j.circuitbreaker.instances.pagosService.wait-duration-in-open-state=60s"})
@TestPropertySource(locations="file:../config-repo/application.properties")
class ClienteGestionBancoTests {
    @SpringBootConfiguration @EnableAutoConfiguration @Import({ClienteGestionBanco.class, ClienteServiciosBanco.class})
    static class Aplicacion {
        @Bean RestTemplate restTemplateBackend() { return new RestTemplate(); }
    }
    @Autowired ClienteGestionBanco banco;
    @Autowired ClienteServiciosBanco legado;
    @Autowired RestTemplate rest;
    @Autowired CircuitBreakerRegistry circuitos;
    private MockRestServiceServer server;
    private static final String URL="http://MS-PAGOS/api/pagos/cuentas/900201/transferencias";
    private static final Transferencia REQ=new Transferencia("s9-reintento-transferencia-01",900202L,new BigDecimal("2000"),"Prueba");
    private static final String JSON_REQ="""
            {"solicitudId":"s9-reintento-transferencia-01","cuentaDestinoId":900202,"monto":2000,"concepto":"Prueba"}
            """;
    private static final String RECIBO="""
            {"solicitudId":"s9-reintento-transferencia-01","referencia":"ref-test","tipo":"TRANSFERENCIA",
             "cuentaId":900201,"cuentaDestinoId":900202,"monto":2000,"beneficiario":"","concepto":"Prueba",
             "saldoAnterior":9000,"saldoPosterior":7000,"saldoDestinoAnterior":0,"saldoDestinoPosterior":2000,
             "estado":"APROBADA","fecha":"2026-10-10T20:00:00"}
            """;
    @BeforeEach void preparar() {
        circuitos.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        server=MockRestServiceServer.bindTo(rest).build();
    }
    @AfterEach void verificar() { server.verify(); }
    @Test void reintentoRealConservaClaveYCuerpoTrasPerderRespuesta() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST)).andExpect(content().json(JSON_REQ))
                .andRespond(withException(new java.net.SocketTimeoutException("Respuesta perdida")));
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST)).andExpect(content().json(JSON_REQ))
                .andRespond(withSuccess(RECIBO,MediaType.APPLICATION_JSON));
        var resultado=banco.transferir(900201L,REQ);
        assertThat(resultado.solicitudId()).isEqualTo(REQ.solicitudId());
        assertThat(resultado.saldoPosterior()).isEqualByComparingTo("7000");
    }
    @Test void conflicto409SePropagaSinRetryNiFalloDeCircuito() {
        server.expect(ExpectedCount.once(),requestTo(URL)).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON).body("{\"estado\":409,\"mensaje\":\"Saldo insuficiente\"}"));
        assertThatThrownBy(() -> banco.transferir(900201L,REQ)).isInstanceOf(ConflictoNegocioException.class)
                .hasMessage("Saldo insuficiente");
        assertThat(circuitos.circuitBreaker("pagosService").getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(circuitos.circuitBreaker("pagosService").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
    @Test void tresFallosDeInfraestructuraAbrenCircuitoYFallbackEsControlado() {
        server.expect(ExpectedCount.times(3),requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> banco.transferir(900201L,REQ)).isInstanceOf(ServicioBackendNoDisponibleException.class)
                .hasMessage("MS-PAGOS no disponible temporalmente");
        assertThat(circuitos.circuitBreaker("pagosService").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> banco.transferir(900201L,REQ)).isInstanceOf(ServicioBackendNoDisponibleException.class);
    }
    @Test void actualizarPerfilResuelveClienteDesdeCuentaAutorizada() {
        server.expect(requestTo("http://MS-CLIENTES/api/clientes/cuentas/900201")).andRespond(withSuccess("""
                {"clienteId":900101,"nombre":"Prueba","email":"prueba@example.com","telefono":"1",
                 "direccion":"Maipu","perfil":"ESTANDAR","version":0,"cuentas":[900201]}
                """,MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://MS-CLIENTES/api/clientes/900101")).andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("""
                {"nombre":"Actualizado","email":"prueba@example.com","telefono":"1","direccion":"Maipu","perfil":"PREFERENTE","version":0}
                """)).andRespond(withSuccess("""
                {"clienteId":900101,"nombre":"Actualizado","email":"prueba@example.com","telefono":"1",
                 "direccion":"Maipu","perfil":"PREFERENTE","version":1,"cuentas":[900201]}
                """,MediaType.APPLICATION_JSON));
        assertThat(banco.actualizarCliente(900201L,new ActualizarCliente("Actualizado","prueba@example.com",
                "1","Maipu","PREFERENTE",0)).version()).isEqualTo(1);
    }
    @Test void retiroLegadoNoRepiteUnCargoCuandoFallaLaRespuesta() {
        server.expect(ExpectedCount.once(),requestTo("http://MS-OPERACIONES/api/operaciones/cuentas/900201/retiros"))
                .andExpect(method(HttpMethod.POST)).andRespond(withException(new java.net.SocketTimeoutException("Respuesta perdida")));
        assertThatThrownBy(() -> legado.retirar(900201L,new BigDecimal("1000")))
                .isInstanceOf(ServicioBackendNoDisponibleException.class);
    }
    @Test void altaClienteTomaCuentaDeRutaAutorizada() {
        server.expect(requestTo("http://MS-CLIENTES/api/clientes")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.cuentaId").value(900201)).andExpect(jsonPath("$.clienteId").value(900101))
                .andRespond(withSuccess("""
                {"clienteId":900101,"nombre":"Prueba","email":"prueba@example.com","telefono":"1",
                 "direccion":"Maipu","perfil":"ESTANDAR","version":0,"cuentas":[900201]}
                """,MediaType.APPLICATION_JSON));
        assertThat(banco.crearCliente(900201L,new AltaCliente(900101L,"Prueba","prueba@example.com",
                "1","Maipu","ESTANDAR")).cuentas()).containsExactly(900201L);
    }
}
