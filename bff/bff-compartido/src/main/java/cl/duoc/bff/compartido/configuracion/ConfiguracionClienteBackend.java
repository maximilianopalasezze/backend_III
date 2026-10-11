package cl.duoc.bff.compartido.configuracion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(PropiedadesServiciosBackend.class)
public class ConfiguracionClienteBackend {
    @Bean
    @LoadBalanced
    public RestTemplate restTemplateBackend(PropiedadesServiciosBackend p) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        RestTemplate rest = new RestTemplate(factory);
        rest.getInterceptors().add(new BasicAuthenticationInterceptor(p.usuario(), p.password()));
        return rest;
    }
}
