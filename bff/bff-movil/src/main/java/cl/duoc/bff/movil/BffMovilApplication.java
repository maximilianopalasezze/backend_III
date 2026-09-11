package cl.duoc.bff.movil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"cl.duoc.bff.movil", "cl.duoc.bff.compartido"})
public class BffMovilApplication {
    public static void main(String[] args) { SpringApplication.run(BffMovilApplication.class, args); }
}
