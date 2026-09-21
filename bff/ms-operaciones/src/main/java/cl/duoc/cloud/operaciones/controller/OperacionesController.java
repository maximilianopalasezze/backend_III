package cl.duoc.cloud.operaciones.controller;
import cl.duoc.cloud.operaciones.model.*; import cl.duoc.cloud.operaciones.service.OperacionesService; import jakarta.validation.Valid; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/operaciones") public class OperacionesController { private final OperacionesService service; public OperacionesController(OperacionesService service){this.service=service;}
 @PostMapping("/cuentas/{id}/retiros") public RetiroResponse retirar(@PathVariable Long id,@Valid @RequestBody RetiroRequest req){return service.retirar(id,req);}
}
