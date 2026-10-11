package cl.duoc.bff.compartido.configuracion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Evidencia del destino elegido por el balanceador; no registra cuerpos ni tokens. */
@Component
public class ObservadorBalanceo implements LoadBalancerLifecycle<Object, Object, ServiceInstance> {
    private static final Logger log = LoggerFactory.getLogger(ObservadorBalanceo.class);

    @Override public boolean supports(Class request, Class response, Class server) {
        return ServiceInstance.class.isAssignableFrom(server);
    }
    @Override public void onStart(Request<Object> request) {}

    @Override public void onStartRequest(Request<Object> request, Response<ServiceInstance> response) {
        if (!response.hasServer()) return;
        ServiceInstance instance = response.getServer();
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)
                || attrs.getResponse() == null) return;
        String nombre = switch (instance.getServiceId().toUpperCase(java.util.Locale.ROOT)) {
            case "MS-CUENTAS" -> "Cuentas";
            case "MS-CLIENTES" -> "Clientes";
            case "MS-PAGOS" -> "Pagos";
            case "MS-MOVIMIENTOS" -> "Movimientos";
            case "MS-OPERACIONES" -> "Operaciones";
            default -> null;
        };
        if (nombre != null) attrs.getResponse().setHeader("X-Instancia-" + nombre, instance.getInstanceId());
    }

    @Override public void onComplete(CompletionContext<Object, ServiceInstance, Object> context) {
        var response = context.getLoadBalancerResponse();
        if (response == null || !response.hasServer()) return;
        var instance = response.getServer();
        log.info("Balanceo {} -> {} [{}]", instance.getServiceId(), instance.getInstanceId(), context.status());
    }
}
