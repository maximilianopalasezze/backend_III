package cl.duoc.bank_batch.utilidad;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/** Resuelve CSV empaquetados o archivos externos indicados mediante file:. */
public final class RecursoEntradaBatch {

    private RecursoEntradaBatch() {
    }

    public static Resource cargar(String ubicacion) {
        if (ubicacion == null || ubicacion.isBlank()) {
            throw new IllegalArgumentException("La ubicacion del CSV no puede estar vacia");
        }

        String recurso = ubicacion.trim();
        if (!recurso.startsWith("file:") && !recurso.startsWith("classpath:")) {
            recurso = "classpath:" + recurso;
        }
        return new DefaultResourceLoader().getResource(recurso);
    }
}
