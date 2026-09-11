package cl.duoc.bff.compartido.configuracion;

import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

@Component("accesoCuenta")
public class AccesoCuenta {
    public boolean permitir(Authentication auth, Long cuentaId) {
        return auth!=null && auth.getPrincipal() instanceof Jwt jwt && cuentaId!=null
                && cuentaId.toString().equals(jwt.getClaimAsString("cuentaId"));
    }
}
