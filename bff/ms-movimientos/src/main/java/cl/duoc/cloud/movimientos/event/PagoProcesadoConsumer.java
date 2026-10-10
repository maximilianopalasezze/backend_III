package cl.duoc.cloud.movimientos.event;

import cl.duoc.cloud.movimientos.repo.MovimientosPagosRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Component
public class PagoProcesadoConsumer {
    private static final Logger log = LoggerFactory.getLogger(PagoProcesadoConsumer.class);
    private final MovimientosPagosRepository repo;
    public PagoProcesadoConsumer(MovimientosPagosRepository repo) { this.repo = repo; }

    @Transactional
    @KafkaListener(topics = "${app.kafka.topics.pagos-procesados:pagos.procesados}",
            groupId = "${app.kafka.pagos.group-id:ms-movimientos-pagos-group}",
            containerFactory = "pagosKafkaListenerContainerFactory")
    public void consumir(PagoProcesadoEvent evento) {
        validar(evento);
        String tipo = evento.tipo().equals("TRANSFERENCIA") ? "TRANSFERENCIA_SALIDA" : evento.tipo();
        repo.guardar(evento.referencia(), evento.cuentaId(), evento.fecha(), tipo, evento.monto(), evento.concepto());
        if (evento.tipo().equals("TRANSFERENCIA")) {
            repo.guardar(evento.referencia(), evento.cuentaDestinoId(), evento.fecha(), "TRANSFERENCIA_ENTRADA", evento.monto(), evento.concepto());
        }
        log.info("Movimiento de pago registrado | referencia={} | tipo={}", evento.referencia(), evento.tipo());
    }

    private void validar(PagoProcesadoEvent e) {
        if (e == null || e.referencia() == null || e.tipo() == null || e.cuentaId() == null || e.cuentaId() <= 0
                || e.monto() == null || e.monto().signum() <= 0 || e.monto().scale() > 2
                || e.fecha() == null || e.concepto() == null || e.concepto().isBlank() || e.concepto().length() > 200
                || !"APROBADA".equals(e.estado()) || !Set.of("DEPOSITO", "PAGO", "TRANSFERENCIA").contains(e.tipo())
                || e.saldoAnterior() == null || e.saldoPosterior() == null || e.saldoAnterior().signum() < 0 || e.saldoPosterior().signum() < 0) {
            throw new IllegalArgumentException("Evento de pago inválido");
        }
        UUID.fromString(e.referencia());
        BigDecimal esperado = e.tipo().equals("DEPOSITO") ? e.saldoAnterior().add(e.monto()) : e.saldoAnterior().subtract(e.monto());
        if (esperado.compareTo(e.saldoPosterior()) != 0) throw new IllegalArgumentException("Saldos del evento inconsistentes");
        if (e.tipo().equals("TRANSFERENCIA") && (e.cuentaDestinoId() == null || e.cuentaDestinoId() <= 0
                || e.cuentaDestinoId().equals(e.cuentaId()) || e.saldoDestinoAnterior() == null
                || e.saldoDestinoAnterior().signum() < 0 || e.saldoDestinoPosterior() == null
                || e.saldoDestinoAnterior().add(e.monto()).compareTo(e.saldoDestinoPosterior()) != 0)) {
            throw new IllegalArgumentException("Destino del evento inconsistente");
        }
    }
}
