package cl.duoc.bank_batch.bff.compartido.configuracion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "bff")
@Validated
public class PropiedadesBff {

    @NotNull
    private Canal canal;

    @Valid
    private final Seguridad seguridad = new Seguridad();

    public Canal getCanal() {
        return canal;
    }

    public void setCanal(Canal canal) {
        this.canal = canal;
    }

    public Seguridad getSeguridad() {
        return seguridad;
    }

    public enum Canal {
        WEB,
        MOVIL,
        CAJERO
    }

    public static class Seguridad {

        @NotBlank
        private String header = "X-BFF-API-Key";

        @NotBlank
        private String apiKey;

        public String getHeader() {
            return header;
        }

        public void setHeader(String header) {
            this.header = header;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
