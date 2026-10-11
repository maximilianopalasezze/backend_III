package cl.duoc.bff.web.controlador;

import cl.duoc.bff.compartido.configuracion.*;
import cl.duoc.bff.compartido.excepcion.*;
import cl.duoc.bff.compartido.integracion.ClienteGestionBanco;
import cl.duoc.bff.compartido.modelo.*;
import cl.duoc.bff.compartido.modelo.SolicitudesBanco.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ControladorGestionWeb.class, properties = {
        "bff.canal=WEB", "bff.issuer=http://localhost:9000", "bff.audience=bff-web",
        "bff.jwk-set-uri=http://localhost:9000/oauth2/jwks", "spring.cloud.config.enabled=false"})
@Import({ConfiguracionSeguridadBff.class, AccesoCuenta.class, ManejadorErroresBff.class})
class GestionWebSeguridadTests {
    @Autowired MockMvc mvc;
    @MockitoBean ClienteGestionBanco banco;
    @MockitoBean JwtDecoder decoder;
    private static final String BASE = "/api/bff/web/cuentas/900201";
    private static final String TRANSFERENCIA = """
            {"solicitudId":"s9-web-transferencia-01","cuentaDestinoId":900202,"monto":2000,"concepto":"Prueba"}
            """;
    private static final String APERTURA = """
            {"cuentaId":900203,"nombre":"Prueba Web","edad":30,"tipoCuenta":"ahorro"}
            """;
    private static final OperacionPago RECIBO = new OperacionPago("s9-web-transferencia-01", "ref-test",
            "TRANSFERENCIA",900201L,900202L,new BigDecimal("2000"),"","Prueba",
            new BigDecimal("9000"),new BigDecimal("7000"),BigDecimal.ZERO,new BigDecimal("2000"),
            "APROBADA",LocalDateTime.of(2026,10,10,20,0));

    private static RequestPostProcessor token(String scope) {
        return jwt().jwt(j -> j.claim("cuentaId","900201").claim("canal","WEB")
                .claim("cuentas",List.of("900201","900203")))
                .authorities(new SimpleGrantedAuthority("SCOPE_"+scope));
    }

    @Test void transferenciaAutorizadaConservaSolicitudYOcultaSaldoDelDestinatario() throws Exception {
        when(banco.transferir(eq(900201L),any())).thenReturn(RECIBO);
        mvc.perform(post(BASE+"/transferencias").secure(true).with(token("web:pagos"))
                .contentType("application/json").content(TRANSFERENCIA))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.solicitudId").value("s9-web-transferencia-01"))
                .andExpect(jsonPath("$.saldoPosterior").value(7000))
                .andExpect(jsonPath("$.saldoDestinoAnterior").doesNotExist())
                .andExpect(jsonPath("$.saldoDestinoPosterior").doesNotExist());
        verify(banco).transferir(eq(900201L),argThat(r -> r.solicitudId().equals("s9-web-transferencia-01")));
    }
    @Test void sinTokenResponde401SinInvocarMicroservicio() throws Exception {
        mvc.perform(post(BASE+"/transferencias").secure(true).contentType("application/json").content(TRANSFERENCIA))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(banco);
    }
    @Test void lecturaNoAutorizaPagos() throws Exception {
        mvc.perform(post(BASE+"/transferencias").secure(true).with(token("web:lectura"))
                .contentType("application/json").content(TRANSFERENCIA)).andExpect(status().isForbidden());
        verifyNoInteractions(banco);
    }
    @Test void scopeCorrectoNoPermiteOperarCuentaAjena() throws Exception {
        mvc.perform(post("/api/bff/web/cuentas/900999/transferencias").secure(true).with(token("web:pagos"))
                .contentType("application/json").content(TRANSFERENCIA)).andExpect(status().isForbidden());
        verifyNoInteractions(banco);
    }
    @Test void headerDeProxyNoPermiteEnviarCredencialesPorHttp() throws Exception {
        mvc.perform(post(BASE+"/transferencias").with(token("web:pagos")).header("X-Forwarded-Proto","https")
                .contentType("application/json").content(TRANSFERENCIA)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("HTTPS requerido"));
        verifyNoInteractions(banco);
    }
    @Test void cuentaAdicionalAutorizadaPuedeAbrirse() throws Exception {
        when(banco.abrirCuenta(any())).thenReturn(new CuentaBancaria(900203L,"Prueba Web",BigDecimal.ZERO,30,
                "ahorro",LocalDateTime.now()));
        mvc.perform(post("/api/bff/web/cuentas").secure(true).with(token("web:gestion"))
                .contentType("application/json").content(APERTURA)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldo").value(0));
        verify(banco).abrirCuenta(argThat(r -> r.cuentaId().equals(900203L)));
    }
    @Test void aperturaNoPermiteElegirCuentaAjenaEnBody() throws Exception {
        mvc.perform(post("/api/bff/web/cuentas").secure(true).with(token("web:gestion"))
                .contentType("application/json").content(APERTURA.replace("900203","900999")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(banco);
    }
    @Test void vinculoExigeAutorizacionDeAmbasCuentas() throws Exception {
        mvc.perform(post(BASE+"/cliente/cuentas").secure(true).with(token("web:gestion"))
                .contentType("application/json").content("{\"cuentaId\":900999}")).andExpect(status().isForbidden());
        verifyNoInteractions(banco);
    }
    @Test void versionObsoletaConserva409DelBackend() throws Exception {
        when(banco.actualizarCliente(eq(900201L),any()))
                .thenThrow(new ConflictoNegocioException("El perfil fue modificado; consulta la versión actual antes de actualizar"));
        mvc.perform(put(BASE+"/cliente").secure(true).with(token("web:gestion"))
                .contentType("application/json").content("""
                {"nombre":"Prueba","email":"prueba@example.com","telefono":"+56911111111",
                 "direccion":"Maipu","perfil":"ESTANDAR","version":0}
                """)).andExpect(status().isConflict()).andExpect(jsonPath("$.estado").value(409));
    }
    @Test void saldoInsuficienteNoSeConvierteEn503() throws Exception {
        when(banco.transferir(eq(900201L),any())).thenThrow(new ConflictoNegocioException("Saldo insuficiente"));
        mvc.perform(post(BASE+"/transferencias").secure(true).with(token("web:pagos"))
                .contentType("application/json").content(TRANSFERENCIA)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Saldo insuficiente"));
    }
    @Test void microservicioCaidoEntrega503Controlado() throws Exception {
        when(banco.transferir(eq(900201L),any())).thenThrow(new ServicioBackendNoDisponibleException(
                "MS-PAGOS no disponible temporalmente",new IllegalStateException()));
        mvc.perform(post(BASE+"/transferencias").secure(true).with(token("web:pagos"))
                .contentType("application/json").content(TRANSFERENCIA)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("MS-PAGOS no disponible temporalmente"));
    }
    @Test void montoConTresDecimalesSeRechazaAntesDelBackend() throws Exception {
        mvc.perform(post(BASE+"/transferencias").secure(true).with(token("web:pagos"))
                .contentType("application/json").content(TRANSFERENCIA.replace("2000","2.001")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(banco);
    }
    @Test void jsonInvalidoEntrega400SinTrazaInterna() throws Exception {
        mvc.perform(post(BASE+"/transferencias").secure(true).with(token("web:pagos"))
                .contentType("application/json").content("{")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La solicitud contiene datos inválidos"));
        verifyNoInteractions(banco);
    }
    @Test void reciboSeConsultaConLecturaYSinSaldosDelDestinatario() throws Exception {
        when(banco.consultarPago(900201L,"s9-web-transferencia-01")).thenReturn(RECIBO);
        mvc.perform(get(BASE+"/solicitudes/s9-web-transferencia-01").secure(true).with(token("web:lectura")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.referencia").value("ref-test"))
                .andExpect(jsonPath("$.saldoDestinoPosterior").doesNotExist());
    }
}
