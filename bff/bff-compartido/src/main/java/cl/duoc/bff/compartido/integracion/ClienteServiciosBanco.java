package cl.duoc.bff.compartido.integracion;

import cl.duoc.bff.compartido.excepcion.RecursoNoEncontradoException;
import cl.duoc.bff.compartido.excepcion.ServicioBackendNoDisponibleException;
import cl.duoc.bff.compartido.excepcion.SaldoInsuficienteException;
import cl.duoc.bff.compartido.excepcion.OperacionInvalidaException;
import cl.duoc.bff.compartido.modelo.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class ClienteServiciosBanco {
    private final RestTemplate rest;
    public ClienteServiciosBanco(RestTemplate restTemplateBackend) { this.rest = restTemplateBackend; }

    @CircuitBreaker(name = "cuentasService", fallbackMethod = "fallbackCuenta")
    @Retry(name = "cuentasService")
    public CuentaBancaria obtenerCuenta(Long cuentaId) {
        try {
            return rest.getForObject("http://MS-CUENTAS/api/cuentas/{id}", CuentaBancaria.class, cuentaId);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new RecursoNoEncontradoException("No existe la cuenta " + cuentaId);
        }
    }

    @CircuitBreaker(name = "cuentasService", fallbackMethod = "fallbackInteres")
    @Retry(name = "cuentasService")
    public InteresCuenta obtenerUltimoInteres(Long cuentaId) {
        try { return rest.getForObject("http://MS-CUENTAS/api/cuentas/{id}/interes", InteresCuenta.class, cuentaId); }
        catch (HttpClientErrorException.NotFound ex) { return null; }
    }

    @CircuitBreaker(name = "cuentasService", fallbackMethod = "fallbackEstado")
    @Retry(name = "cuentasService")
    public EstadoAnualCuenta obtenerUltimoEstadoAnual(Long cuentaId) {
        try { return rest.getForObject("http://MS-CUENTAS/api/cuentas/{id}/estado-anual", EstadoAnualCuenta.class, cuentaId); }
        catch (HttpClientErrorException.NotFound ex) { return null; }
    }

    @CircuitBreaker(name = "cuentasService", fallbackMethod = "fallbackResumen")
    @Retry(name = "cuentasService")
    public ResumenOperacional obtenerResumen() {
        return rest.getForObject("http://MS-CUENTAS/api/cuentas/resumen", ResumenOperacional.class);
    }

    @CircuitBreaker(name = "movimientosService", fallbackMethod = "fallbackMovimientos")
    @Retry(name = "movimientosService")
    public List<MovimientoCuenta> obtenerMovimientos(Long cuentaId, int limite) {
        var respuesta = rest.exchange(
                "http://MS-MOVIMIENTOS/api/movimientos/cuenta/{id}?limite={limite}",
                HttpMethod.GET, null, new ParameterizedTypeReference<List<MovimientoCuenta>>() {},
                cuentaId, limite);
        return respuesta.getBody() == null ? List.of() : respuesta.getBody();
    }

    @CircuitBreaker(name = "operacionesService", fallbackMethod = "fallbackRetiro")
    @Retry(name = "operacionesService")
    public RetiroBackend retirar(Long cuentaId, BigDecimal monto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            return rest.postForObject("http://MS-OPERACIONES/api/operaciones/cuentas/{id}/retiros",
                    new HttpEntity<>(Map.of("monto", monto), headers), RetiroBackend.class, cuentaId);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 404)
                throw new RecursoNoEncontradoException("No existe la cuenta " + cuentaId);
            if (ex.getStatusCode().value() == 409)
                throw new SaldoInsuficienteException("La cuenta no tiene saldo suficiente para realizar el retiro");
            if (ex.getStatusCode().value() == 400)
                throw new OperacionInvalidaException("El retiro fue rechazado por MS-OPERACIONES");
            throw ex;
        }
    }

    private CuentaBancaria fallbackCuenta(Long cuentaId, Throwable ex) {
        if (ex instanceof RecursoNoEncontradoException negocio) throw negocio;
        throw new ServicioBackendNoDisponibleException("MS-CUENTAS no disponible temporalmente", ex);
    }
    private InteresCuenta fallbackInteres(Long cuentaId, Throwable ex) { return null; }
    private EstadoAnualCuenta fallbackEstado(Long cuentaId, Throwable ex) { return null; }
    private ResumenOperacional fallbackResumen(Throwable ex) {
        throw new ServicioBackendNoDisponibleException("MS-CUENTAS no disponible temporalmente", ex);
    }
    private List<MovimientoCuenta> fallbackMovimientos(Long cuentaId, int limite, Throwable ex) { return List.of(); }
    private RetiroBackend fallbackRetiro(Long cuentaId, BigDecimal monto, Throwable ex) {
        if (ex instanceof RecursoNoEncontradoException negocio) throw negocio;
        if (ex instanceof SaldoInsuficienteException negocio) throw negocio;
        if (ex instanceof OperacionInvalidaException negocio) throw negocio;
        throw new ServicioBackendNoDisponibleException("MS-OPERACIONES no disponible temporalmente", ex);
    }
}
