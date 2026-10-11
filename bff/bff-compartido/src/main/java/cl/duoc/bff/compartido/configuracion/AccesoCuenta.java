package cl.duoc.bff.compartido.configuracion;

import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

@Component("accesoCuenta")
public class AccesoCuenta {
    public boolean permitir(Authentication auth, Long cuentaId) {
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt) || cuentaId == null) return false;
        String id = cuentaId.toString();
        if (id.equals(jwt.getClaimAsString("cuentaId"))) return true;
        var cuentas = jwt.getClaimAsStringList("cuentas");
        return cuentas != null && cuentas.contains(id);
    }
}
