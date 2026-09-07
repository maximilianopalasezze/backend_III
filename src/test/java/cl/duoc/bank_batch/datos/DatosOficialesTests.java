package cl.duoc.bank_batch.datos;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatosOficialesTests {

    private static final int REGISTROS_ESPERADOS = 1000;

    @Test
    void losTresArchivosCoincidenConElZipOficial() throws Exception {
        List<ArchivoOficial> archivos = List.of(
                new ArchivoOficial(
                        "cuentas_anuales.csv",
                        "0690d81fe446affe39857a448ff5590c4f6e6f998150c08934db3aa41683e328"
                ),
                new ArchivoOficial(
                        "intereses.csv",
                        "69cc39f468db47e4d752657db95ee9caf00ebcaf31581e45e104c4dc4a5e3120"
                ),
                new ArchivoOficial(
                        "transacciones.csv",
                        "9a209c71d5556380481731f0fe9ac148a586a7c91698b377481b62e96341e836"
                )
        );

        for (ArchivoOficial archivo : archivos) {
            Path ruta = Path.of(
                    "src",
                    "main",
                    "resources",
                    "data",
                    "semana_3",
                    archivo.nombre()
            );

            assertThat(ruta)
                    .as("El archivo oficial debe existir")
                    .exists();

            long registros;
            try (var lineas = Files.lines(ruta)) {
                registros = lineas.skip(1).count();
            }

            byte[] contenidoNormalizado = Files.readString(ruta, StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                    .getBytes(StandardCharsets.UTF_8);

            String hash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(contenidoNormalizado)
            );

            assertThat(registros)
                    .as("Cantidad de registros de %s", archivo.nombre())
                    .isEqualTo(REGISTROS_ESPERADOS);
            assertThat(hash)
                    .as("SHA-256 de %s", archivo.nombre())
                    .isEqualTo(archivo.sha256());

            System.out.printf(
                    "DATO OFICIAL VERIFICADO: %s | registros=%d | sha256=%s%n",
                    archivo.nombre(),
                    registros,
                    hash
            );
        }
    }

    private record ArchivoOficial(String nombre, String sha256) {
    }
}
