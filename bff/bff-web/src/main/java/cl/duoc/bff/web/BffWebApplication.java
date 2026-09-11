package cl.duoc.bff.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"cl.duoc.bff.web", "cl.duoc.bff.compartido"})
public class BffWebApplication {
    public static void main(String[] args) { SpringApplication.run(BffWebApplication.class, args); }
}
