package cl.duoc.cloud.pagos.service;

import cl.duoc.cloud.pagos.config.ApiErrorHandler.*;
import cl.duoc.cloud.pagos.model.*;
import cl.duoc.cloud.pagos.repo.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class PagosService {
    private static final BigDecimal SALDO_MAXIMO = new BigDecimal("9999999999999.99");
    private final PagosRepository repo;
    private final OutboxRepository outbox;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final String topic;

    public PagosService(PagosRepository repo, OutboxRepository outbox, TransactionTemplate tx,
            ObjectMapper json, @Value("${app.kafka.topics.pagos-procesados:pagos.procesados}") String topic) {
        this.repo = repo; this.outbox = outbox; this.tx = tx; this.json = json; this.topic = topic;
    }

    public OperacionPagoResponse deposito(Long cuentaId, DepositoRequest req) {
        return ejecutar(normalizar("DEPOSITO", cuentaId, null, req.solicitudId(), req.monto(), "", req.concepto()));
    }
    public OperacionPagoResponse pago(Long cuentaId, PagoRequest req) {
        return ejecutar(normalizar("PAGO", cuentaId, null, req.solicitudId(), req.monto(), req.beneficiario(), req.concepto()));
    }
    public OperacionPagoResponse transferencia(Long cuentaId, TransferenciaRequest req) {
        return ejecutar(normalizar("TRANSFERENCIA", cuentaId, req.cuentaDestinoId(), req.solicitudId(), req.monto(), "", req.concepto()));
    }

    public OperacionPagoResponse consultar(Long cuentaId, String solicitudId) {
        exigirId(cuentaId);
        return repo.operacion(solicitudId).filter(op -> op.cuentaId().equals(cuentaId))
                .orElseThrow(() -> new OperacionNoEncontrada("No existe la solicitud para esta cuenta"));
    }

    private record Solicitud(String tipo, Long cuentaId, Long destino, String id,
                             BigDecimal monto, String beneficiario, String concepto) {}

    private Solicitud normalizar(String tipo, Long cuentaId, Long destino, String id,
                                BigDecimal monto, String beneficiario, String concepto) {
        exigirId(cuentaId);
        if (id == null || !id.matches("[a-z0-9_-]{8,80}")) throw new SolicitudInvalida("solicitudId debe tener entre 8 y 80 letras minúsculas, números, guiones o guiones bajos");
        if (monto == null || monto.signum() <= 0 || monto.compareTo(SALDO_MAXIMO) > 0) throw new SolicitudInvalida("El monto debe ser positivo y caber en DECIMAL(15,2)");
        try { monto = monto.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new SolicitudInvalida("El monto admite como máximo dos decimales"); }
        concepto = concepto == null ? "" : concepto.trim();
        beneficiario = beneficiario == null ? "" : beneficiario.trim();
        if (concepto.isEmpty() || concepto.length() > 200) throw new SolicitudInvalida("El concepto es obligatorio y admite hasta 200 caracteres");
        if (tipo.equals("PAGO") && (beneficiario.isEmpty() || beneficiario.length() > 150)) throw new SolicitudInvalida("El beneficiario es obligatorio y admite hasta 150 caracteres");
        if (tipo.equals("TRANSFERENCIA")) {
            exigirId(destino);
            if (cuentaId.equals(destino)) throw new SolicitudInvalida("Origen y destino deben ser cuentas distintas");
        }
        return new Solicitud(tipo, cuentaId, destino, id, monto, beneficiario, concepto);
    }

    private void exigirId(Long id) {
        if (id == null || id <= 0) throw new SolicitudInvalida("El identificador de cuenta debe ser positivo");
    }

    private OperacionPagoResponse ejecutar(Solicitud solicitud) {
        try { return tx.execute(status -> aplicar(solicitud)); }
        catch (DuplicateKeyException ex) {
            // El intento perdedor ya se revirtió. Leer el resultado confirmado por el ganador.
            var existente = repo.operacion(solicitud.id());
            if (existente.isEmpty()) throw ex;
            return comprobarRepeticion(solicitud, existente.get());
        }
    }

    private OperacionPagoResponse aplicar(Solicitud s) {
        var existente = repo.operacion(s.id());
        if (existente.isPresent()) return comprobarRepeticion(s, existente.get());

        // Orden común para transferencias opuestas; mismo bloqueo de cuentas que cierre y retiros.
        var ids = new TreeSet<Long>();
        ids.add(s.cuentaId());
        if (s.destino() != null) ids.add(s.destino());
        var saldos = new HashMap<Long, BigDecimal>();
        for (Long id : ids) saldos.put(id, repo.bloquearCuenta(id));

        // READ_COMMITTED permite ver un duplicado que terminó mientras se esperaba el bloqueo.
        existente = repo.operacion(s.id());
        if (existente.isPresent()) return comprobarRepeticion(s, existente.get());
        for (Long id : ids) repo.exigirActiva(id);

        BigDecimal antes = saldos.get(s.cuentaId());
        BigDecimal despues = s.tipo().equals("DEPOSITO") ? antes.add(s.monto()) : antes.subtract(s.monto());
        exigirSaldo(despues);
        BigDecimal destinoAntes = s.destino() == null ? null : saldos.get(s.destino());
        BigDecimal destinoDespues = destinoAntes == null ? null : destinoAntes.add(s.monto());
        if (destinoDespues != null) exigirSaldo(destinoDespues);

        var op = new OperacionPagoResponse(s.id(), UUID.randomUUID().toString(), s.tipo(), s.cuentaId(),
                s.destino(), s.monto(), s.beneficiario(), s.concepto(), antes, despues,
                destinoAntes, destinoDespues, "APROBADA", null);
        repo.registrar(op);
        repo.actualizarSaldo(s.cuentaId(), despues);
        if (s.destino() != null) repo.actualizarSaldo(s.destino(), destinoDespues);
        var registrado = repo.operacion(s.id()).orElseThrow();
        try { outbox.guardar(registrado.referencia(), s.id(), topic, json.writeValueAsString(registrado)); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("No fue posible preparar el evento de la operación", ex); }
        return registrado;
    }

    private void exigirSaldo(BigDecimal saldo) {
        if (saldo.signum() < 0) throw new OperacionConflicto("Saldo insuficiente");
        if (saldo.compareTo(SALDO_MAXIMO) > 0) throw new OperacionConflicto("El saldo resultante excede el máximo permitido");
    }

    private OperacionPagoResponse comprobarRepeticion(Solicitud s, OperacionPagoResponse op) {
        if (!s.tipo().equals(op.tipo()) || !s.cuentaId().equals(op.cuentaId())
                || !Objects.equals(s.destino(), op.cuentaDestinoId()) || s.monto().compareTo(op.monto()) != 0
                || !s.beneficiario().equals(op.beneficiario()) || !s.concepto().equals(op.concepto())) {
            throw new OperacionConflicto("solicitudId ya fue utilizado con otros datos");
        }
        return op;
    }
}
