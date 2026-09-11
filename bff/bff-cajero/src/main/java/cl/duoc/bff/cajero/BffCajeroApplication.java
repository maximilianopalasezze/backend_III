package cl.duoc.bff.cajero;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"cl.duoc.bff.cajero", "cl.duoc.bff.compartido"})
public class BffCajeroApplication {
    public static void main(String[] args) { SpringApplication.run(BffCajeroApplication.class, args); }
}
