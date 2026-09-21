package cl.duoc.bff.compartido.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RetiroBackend(String referencia, String estado, Long cuentaId, BigDecimal monto,
                            BigDecimal saldoAnterior, BigDecimal saldoPosterior, LocalDateTime fechaOperacion) {}
