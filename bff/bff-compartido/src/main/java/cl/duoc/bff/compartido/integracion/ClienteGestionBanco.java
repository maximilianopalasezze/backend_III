package cl.duoc.bff.compartido.integracion;

import cl.duoc.bff.compartido.excepcion.*;
import cl.duoc.bff.compartido.modelo.*;
import cl.duoc.bff.compartido.modelo.SolicitudesBanco.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Component
public class ClienteGestionBanco {
    private final RestTemplate rest;
    private final ObjectMapper json;

    public ClienteGestionBanco(RestTemplate restTemplateBackend, ObjectMapper json) {
        this.rest = restTemplateBackend; this.json = json;
    }

    @CircuitBreaker(name = "cuentasService", fallbackMethod = "fallbackAbrir")
    public CuentaBancaria abrirCuenta(AperturaCuenta req) {
        return enviar(HttpMethod.POST, "http://MS-CUENTAS/api/cuentas", req, CuentaBancaria.class);
    }

    @CircuitBreaker(name = "cuentasService", fallbackMethod = "fallbackMantener")
    public CuentaBancaria mantenerCuenta(Long cuentaId, MantenimientoCuenta req) {
        return enviar(HttpMethod.PUT, "http://MS-CUENTAS/api/cuentas/{id}", req, CuentaBancaria.class, cuentaId);
    }

    @CircuitBreaker(name = "cuentasService", fallbackMethod = "fallbackCerrar")
    @Retry(name = "cuentasService")
    public CuentaBancaria cerrarCuenta(Long cuentaId) {
        return enviar(HttpMethod.POST, "http://MS-CUENTAS/api/cuentas/{id}/cierre", null, CuentaBancaria.class, cuentaId);
    }

    @CircuitBreaker(name = "clientesService", fallbackMethod = "fallbackConsultarCliente")
    @Retry(name = "clientesService")
    public PerfilCliente consultarCliente(Long cuentaId) { return leerCliente(cuentaId); }

    @CircuitBreaker(name = "clientesService", fallbackMethod = "fallbackCrearCliente")
    public PerfilCliente crearCliente(Long cuentaId, AltaCliente req) {
        var cuerpo = Map.of("clienteId", req.clienteId(), "cuentaId", cuentaId,
                "nombre", req.nombre(), "email", req.email(), "telefono", req.telefono(),
                "direccion", req.direccion(), "perfil", req.perfil());
        return enviar(HttpMethod.POST, "http://MS-CLIENTES/api/clientes", cuerpo, PerfilCliente.class);
    }

    @CircuitBreaker(name = "clientesService", fallbackMethod = "fallbackActualizarCliente")
    public PerfilCliente actualizarCliente(Long cuentaId, ActualizarCliente req) {
        // Resolver el cliente desde la cuenta autorizada evita aceptar un clienteId ajeno.
        var perfil = leerCliente(cuentaId);
        return enviar(HttpMethod.PUT, "http://MS-CLIENTES/api/clientes/{id}", req,
                PerfilCliente.class, perfil.clienteId());
    }

    @CircuitBreaker(name = "clientesService", fallbackMethod = "fallbackVincular")
    public PerfilCliente vincularCuenta(Long cuentaId, VincularCuenta req) {
        var perfil = leerCliente(cuentaId);
        return enviar(HttpMethod.POST, "http://MS-CLIENTES/api/clientes/{id}/cuentas", req,
                PerfilCliente.class, perfil.clienteId());
    }

    private PerfilCliente leerCliente(Long cuentaId) {
        return enviar(HttpMethod.GET, "http://MS-CLIENTES/api/clientes/cuentas/{id}", null,
                PerfilCliente.class, cuentaId);
    }

    @CircuitBreaker(name = "pagosService", fallbackMethod = "fallbackDeposito")
    @Retry(name = "pagosService")
    public OperacionPago depositar(Long cuentaId, Deposito req) {
        return enviar(HttpMethod.POST, "http://MS-PAGOS/api/pagos/cuentas/{id}/depositos", req,
                OperacionPago.class, cuentaId);
    }

    @CircuitBreaker(name = "pagosService", fallbackMethod = "fallbackPago")
    @Retry(name = "pagosService")
    public OperacionPago pagar(Long cuentaId, Pago req) {
        return enviar(HttpMethod.POST, "http://MS-PAGOS/api/pagos/cuentas/{id}/pagos", req,
                OperacionPago.class, cuentaId);
    }

    @CircuitBreaker(name = "pagosService", fallbackMethod = "fallbackTransferencia")
    @Retry(name = "pagosService")
    public OperacionPago transferir(Long cuentaId, Transferencia req) {
        // El registro inmutable conserva solicitudId y todos los datos en cada reintento.
        return enviar(HttpMethod.POST, "http://MS-PAGOS/api/pagos/cuentas/{id}/transferencias", req,
                OperacionPago.class, cuentaId);
    }

    @CircuitBreaker(name = "pagosService", fallbackMethod = "fallbackConsultarPago")
    @Retry(name = "pagosService")
    public OperacionPago consultarPago(Long cuentaId, String solicitudId) {
        return enviar(HttpMethod.GET, "http://MS-PAGOS/api/pagos/cuentas/{id}/solicitudes/{solicitud}",
                null, OperacionPago.class, cuentaId, solicitudId);
    }

    private <T> T enviar(HttpMethod metodo, String url, Object cuerpo, Class<T> tipo, Object... variables) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            T respuesta = rest.exchange(url, metodo, new HttpEntity<>(cuerpo, headers), tipo, variables).getBody();
            if (respuesta == null) throw new IllegalStateException("El microservicio devolvió un cuerpo vacío");
            return respuesta;
        } catch (HttpClientErrorException ex) {
            String mensaje = mensajeNegocio(ex);
            switch (ex.getStatusCode().value()) {
                case 400 -> throw new OperacionInvalidaException(mensaje);
                case 404 -> throw new RecursoNoEncontradoException(mensaje);
                case 409 -> throw new ConflictoNegocioException(mensaje);
                default -> throw ex;
            }
        }
    }

    private String mensajeNegocio(HttpClientErrorException ex) {
        try {
            String mensaje = json.readTree(ex.getResponseBodyAsString()).path("mensaje").asText();
            if (!mensaje.isBlank() && mensaje.length() <= 300) return mensaje;
        } catch (Exception ignored) {}
        return "Solicitud rechazada por el microservicio";
    }

    private RuntimeException fallo(String servicio, Throwable ex) {
        if (ex instanceof RecursoNoEncontradoException negocio) return negocio;
        if (ex instanceof OperacionInvalidaException negocio) return negocio;
        if (ex instanceof ConflictoNegocioException negocio) return negocio;
        return new ServicioBackendNoDisponibleException(servicio + " no disponible temporalmente", ex);
    }

    private CuentaBancaria fallbackAbrir(AperturaCuenta r, Throwable e) { throw fallo("MS-CUENTAS", e); }
    private CuentaBancaria fallbackMantener(Long id, MantenimientoCuenta r, Throwable e) { throw fallo("MS-CUENTAS", e); }
    private CuentaBancaria fallbackCerrar(Long id, Throwable e) { throw fallo("MS-CUENTAS", e); }
    private PerfilCliente fallbackConsultarCliente(Long id, Throwable e) { throw fallo("MS-CLIENTES", e); }
    private PerfilCliente fallbackCrearCliente(Long id, AltaCliente r, Throwable e) { throw fallo("MS-CLIENTES", e); }
    private PerfilCliente fallbackActualizarCliente(Long id, ActualizarCliente r, Throwable e) { throw fallo("MS-CLIENTES", e); }
    private PerfilCliente fallbackVincular(Long id, VincularCuenta r, Throwable e) { throw fallo("MS-CLIENTES", e); }
    private OperacionPago fallbackDeposito(Long id, Deposito r, Throwable e) { throw fallo("MS-PAGOS", e); }
    private OperacionPago fallbackPago(Long id, Pago r, Throwable e) { throw fallo("MS-PAGOS", e); }
    private OperacionPago fallbackTransferencia(Long id, Transferencia r, Throwable e) { throw fallo("MS-PAGOS", e); }
    private OperacionPago fallbackConsultarPago(Long id, String solicitud, Throwable e) { throw fallo("MS-PAGOS", e); }
}
