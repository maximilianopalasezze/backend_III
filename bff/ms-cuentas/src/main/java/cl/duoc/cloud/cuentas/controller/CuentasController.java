package cl.duoc.cloud.cuentas.controller;

import cl.duoc.cloud.cuentas.config.ApiErrorHandler.RecursoNoEncontrado;
import cl.duoc.cloud.cuentas.model.*;
import cl.duoc.cloud.cuentas.repo.CuentasRepository;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/cuentas")
public class CuentasController {
 private final CuentasRepository repo; public CuentasController(CuentasRepository repo){this.repo=repo;}
 @GetMapping("/{id}") public CuentaResponse cuenta(@PathVariable Long id){return repo.cuenta(id).orElseThrow(()->new RecursoNoEncontrado("No existe la cuenta "+id));}
 @GetMapping("/{id}/interes") public InteresResponse interes(@PathVariable Long id){return repo.interes(id).orElseThrow(()->new RecursoNoEncontrado("No existe interés para la cuenta "+id));}
 @GetMapping("/{id}/estado-anual") public EstadoAnualResponse estado(@PathVariable Long id){return repo.estado(id).orElseThrow(()->new RecursoNoEncontrado("No existe estado anual para la cuenta "+id));}
 @GetMapping("/resumen") public ResumenResponse resumen(){return repo.resumen();}
}
