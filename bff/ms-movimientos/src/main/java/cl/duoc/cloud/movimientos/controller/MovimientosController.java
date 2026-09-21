package cl.duoc.cloud.movimientos.controller;
import cl.duoc.cloud.movimientos.model.MovimientoResponse; import cl.duoc.cloud.movimientos.repo.MovimientosRepository; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/movimientos") public class MovimientosController { private final MovimientosRepository repo; public MovimientosController(MovimientosRepository repo){this.repo=repo;}
 @GetMapping("/cuenta/{id}") public List<MovimientoResponse> movimientos(@PathVariable Long id,@RequestParam(defaultValue="10") int limite){if(limite<1||limite>50) throw new IllegalArgumentException("El límite debe estar entre 1 y 50"); return repo.ultimos(id,limite);}
}
