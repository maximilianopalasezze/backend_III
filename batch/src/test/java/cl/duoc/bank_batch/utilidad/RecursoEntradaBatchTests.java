package cl.duoc.bank_batch.utilidad;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RecursoEntradaBatchTests {

    @TempDir
    Path directorioTemporal;

    @Test
    void conservaLaEntradaEmpaquetadaDeSemana9() throws Exception {
        var recurso = RecursoEntradaBatch.cargar(
                "data/semana_9/movimientos_financieros_diarios.csv");
        assertTrue(recurso.exists());
        try (var entrada = recurso.getInputStream()) {
            String csv = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("id,fecha,monto,tipo", csv.lines().findFirst().orElseThrow());
            assertEquals(1001, csv.lines().count());
        }
    }

    @Test
    void leeUnArchivoExternoConEspaciosEnLaRuta() throws Exception {
        Path csv = directorioTemporal.resolve("transacciones de prueba.csv");
        String contenido = "id,fecha,monto,tipo\n1,2024-01-01,100,credito\n";
        Files.writeString(csv, contenido, StandardCharsets.UTF_8);
        var recurso = RecursoEntradaBatch.cargar(csv.toUri().toString());
        assertTrue(recurso.exists());
        try (var entrada = recurso.getInputStream()) {
            assertEquals(contenido,
                    new String(entrada.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void rechazaUnaUbicacionVacia() {
        assertThrows(IllegalArgumentException.class,
                () -> RecursoEntradaBatch.cargar(" "));
    }
}
