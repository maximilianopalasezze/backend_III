package cl.duoc.bff.compartido.configuracion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
@Component
public class RegistroInicioBff implements ApplicationRunner {
    private static final Logger LOG=LoggerFactory.getLogger(RegistroInicioBff.class);
    private final PropiedadesBff p;
    public RegistroInicioBff(PropiedadesBff p){this.p=p;}
    public void run(ApplicationArguments args){
        LOG.info("BFF {} iniciado como aplicación independiente",p.canal());
        LOG.info("Autenticación Bearer JWT, audiencia {}, permisos por canal y cuenta",p.audience());
    }
}
