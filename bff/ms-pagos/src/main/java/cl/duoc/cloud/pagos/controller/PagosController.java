package cl.duoc.cloud.pagos.controller;

import cl.duoc.cloud.pagos.model.*;
import cl.duoc.cloud.pagos.service.PagosService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pagos/cuentas/{cuentaId}")
public class PagosController {
    private final PagosService service;
    public PagosController(PagosService service) { this.service = service; }

    @PostMapping("/depositos") @ResponseStatus(HttpStatus.CREATED)
    public OperacionPagoResponse deposito(@PathVariable Long cuentaId, @Valid @RequestBody DepositoRequest req) {
        return service.deposito(cuentaId, req);
    }
    @PostMapping("/pagos") @ResponseStatus(HttpStatus.CREATED)
    public OperacionPagoResponse pago(@PathVariable Long cuentaId, @Valid @RequestBody PagoRequest req) {
        return service.pago(cuentaId, req);
    }
    @PostMapping("/transferencias") @ResponseStatus(HttpStatus.CREATED)
    public OperacionPagoResponse transferencia(@PathVariable Long cuentaId, @Valid @RequestBody TransferenciaRequest req) {
        return service.transferencia(cuentaId, req);
    }
    @GetMapping("/solicitudes/{solicitudId}")
    public OperacionPagoResponse consultar(@PathVariable Long cuentaId, @PathVariable String solicitudId) {
        return service.consultar(cuentaId, solicitudId);
    }
}
