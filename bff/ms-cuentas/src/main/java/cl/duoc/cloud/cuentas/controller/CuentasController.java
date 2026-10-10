package cl.duoc.cloud.cuentas.controller;

import cl.duoc.cloud.cuentas.config.ApiErrorHandler.RecursoNoEncontrado;
import cl.duoc.cloud.cuentas.model.*;
import cl.duoc.cloud.cuentas.repo.CuentasRepository;
import cl.duoc.cloud.cuentas.service.CuentasService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/cuentas")
public class CuentasController {
 private final CuentasRepository repo;
 private final CuentasService service;
 public CuentasController(CuentasRepository repo,CuentasService service){this.repo=repo;this.service=service;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public CuentaResponse abrir(@Valid @RequestBody AperturaCuentaRequest solicitud){return service.abrir(solicitud);}
 @PutMapping("/{id}")
 public CuentaResponse mantener(@PathVariable Long id,@Valid @RequestBody MantenimientoCuentaRequest solicitud){return service.mantener(id,solicitud);}
 @PostMapping("/{id}/cierre")
 public CuentaResponse cerrar(@PathVariable Long id){return service.cerrar(id);}
 @GetMapping("/{id}") public CuentaResponse cuenta(@PathVariable Long id){return repo.cuenta(id).orElseThrow(()->new RecursoNoEncontrado("No existe la cuenta "+id));}
 @GetMapping("/{id}/interes") public InteresResponse interes(@PathVariable Long id){return repo.interes(id).orElseThrow(()->new RecursoNoEncontrado("No existe interés para la cuenta "+id));}
 @GetMapping("/{id}/estado-anual") public EstadoAnualResponse estado(@PathVariable Long id){return repo.estado(id).orElseThrow(()->new RecursoNoEncontrado("No existe estado anual para la cuenta "+id));}
 @GetMapping("/resumen") public ResumenResponse resumen(){return repo.resumen();}
}

