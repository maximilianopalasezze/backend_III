package cl.duoc.bff.compartido.modelo;

import java.time.LocalDateTime;
import java.util.List;

public record PerfilCliente(Long clienteId, String nombre, String email,
        String telefono, String direccion, String perfil, int version,
        LocalDateTime fechaActualizacion, List<Long> cuentas) {}
