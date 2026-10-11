package cl.duoc.bff.movil.controlador;

import cl.duoc.bff.compartido.configuracion.*;
import cl.duoc.bff.compartido.excepcion.ManejadorErroresBff;
import cl.duoc.bff.compartido.integracion.ClienteGestionBanco;
import cl.duoc.bff.compartido.modelo.OperacionPago;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ControladorPagosMovil.class, properties = {
        "bff.canal=MOVIL", "bff.issuer=http://localhost:9000", "bff.audience=bff-movil",
        "bff.jwk-set-uri=http://localhost:9000/oauth2/jwks", "spring.cloud.config.enabled=false"})
@Import({ConfiguracionSeguridadBff.class, AccesoCuenta.class, ManejadorErroresBff.class})
class PagosMovilSeguridadTests {
    @Autowired MockMvc mvc;
    @MockitoBean ClienteGestionBanco banco;
    @MockitoBean JwtDecoder decoder;
    private static final String BASE = "/api/bff/movil/cuentas/900201";
    private static final String PAGO = """
            {"solicitudId":"s9-movil-pago-01","monto":1000,"beneficiario":"Servicio","concepto":"Prueba"}
            """;
    private static final OperacionPago RECIBO = new OperacionPago("s9-movil-pago-01","ref-test","PAGO",900201L,
            null,new BigDecimal("1000"),"Servicio","Prueba",new BigDecimal("7000"),new BigDecimal("6000"),
            null,null,"APROBADA",LocalDateTime.of(2026,10,10,20,0));
    private static RequestPostProcessor token(String scope) {
        return jwt().jwt(j -> j.claim("cuentaId","900201").claim("canal","MOVIL"))
                .authorities(new SimpleGrantedAuthority("SCOPE_"+scope));
    }
    @Test void pagoMovilAutorizadoEntregaComprobante() throws Exception {
        when(banco.pagar(eq(900201L),any())).thenReturn(RECIBO);
        mvc.perform(post(BASE+"/pagos").secure(true).with(token("movil:pagos"))
                .contentType("application/json").content(PAGO)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoPosterior").value(6000));
    }
    @Test void scopeWebNoAutorizaPagoMovil() throws Exception {
        mvc.perform(post(BASE+"/pagos").secure(true).with(token("web:pagos"))
                .contentType("application/json").content(PAGO)).andExpect(status().isForbidden());
        verifyNoInteractions(banco);
    }
    @Test void lecturaMovilNoAutorizaEscritura() throws Exception {
        mvc.perform(post(BASE+"/pagos").secure(true).with(token("movil:lectura"))
                .contentType("application/json").content(PAGO)).andExpect(status().isForbidden());
        verifyNoInteractions(banco);
    }
    @Test void anotacionDeClaseImpideCargarCuentaAjena() throws Exception {
        mvc.perform(post("/api/bff/movil/cuentas/900999/pagos").secure(true).with(token("movil:pagos"))
                .contentType("application/json").content(PAGO)).andExpect(status().isForbidden());
        verifyNoInteractions(banco);
    }
    @Test void movilNoExponeGestionDeClientesAunqueTokenIncluyaScopeInventado() throws Exception {
        mvc.perform(post(BASE+"/cliente").secure(true).with(token("movil:gestion"))
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(banco);
    }
    @Test void reciboMovilRespetaCuentaDelToken() throws Exception {
        when(banco.consultarPago(900201L,"s9-movil-pago-01")).thenReturn(RECIBO);
        mvc.perform(get(BASE+"/solicitudes/s9-movil-pago-01").secure(true).with(token("movil:lectura")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.solicitudId").value("s9-movil-pago-01"));
        mvc.perform(get("/api/bff/movil/cuentas/900999/solicitudes/s9-movil-pago-01")
                .secure(true).with(token("movil:lectura"))).andExpect(status().isForbidden());
    }
    @Test void identificadorInvalidoNoLlegaAlMicroservicio() throws Exception {
        mvc.perform(post(BASE+"/pagos").secure(true).with(token("movil:pagos"))
                .contentType("application/json").content(PAGO.replace("s9-movil-pago-01","INVALIDO")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(banco);
    }
}
