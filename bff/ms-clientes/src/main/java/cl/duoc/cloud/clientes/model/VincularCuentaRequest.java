package cl.duoc.cloud.clientes.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VincularCuentaRequest(@NotNull @Positive Long cuentaId) {}
