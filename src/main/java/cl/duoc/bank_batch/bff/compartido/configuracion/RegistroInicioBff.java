package cl.duoc.bank_batch.bff.compartido.configuracion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Component
@Profile({"web", "movil", "cajero"})
public class RegistroInicioBff implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RegistroInicioBff.class);
    private static final Set<String> PERFILES_BFF = Set.of("web", "movil", "cajero");

    private final PropiedadesBff propiedades;
    private final Environment environment;

    public RegistroInicioBff(
            PropiedadesBff propiedades,
            Environment environment) {

        this.propiedades = propiedades;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        var perfilesActivos = Arrays.stream(environment.getActiveProfiles())
                .filter(PERFILES_BFF::contains)
                .toList();

        if (perfilesActivos.size() != 1) {
            throw new IllegalStateException(
                    "Debe activar exactamente un perfil BFF: web, movil o cajero"
            );
        }

        String canal = propiedades.getCanal().name();
        String ruta = "/api/bff/" + canal.toLowerCase() + "/**";

        LOG.info("BFF {} iniciado", canal);
        LOG.info("Ruta autorizada para el canal: {}", ruta);
        LOG.info("Autenticación requerida mediante header {}",
                propiedades.getSeguridad().getHeader());
    }
}
