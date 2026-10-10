package cl.duoc.cloud.clientes.model;

import java.time.LocalDateTime;
import java.util.List;

public record ClienteResponse(Long clienteId, String nombre, String email,
                              String telefono, String direccion, String perfil,
                              int version, LocalDateTime fechaActualizacion,
                              List<Long> cuentas) {}
