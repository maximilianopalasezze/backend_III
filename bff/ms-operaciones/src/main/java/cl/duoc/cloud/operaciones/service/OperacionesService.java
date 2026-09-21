package cl.duoc.cloud.operaciones.service;
import cl.duoc.cloud.operaciones.config.ApiErrorHandler.*; import cl.duoc.cloud.operaciones.model.*; import cl.duoc.cloud.operaciones.repo.OperacionesRepository; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.UUID;
@Service public class OperacionesService { private static final BigDecimal MULTIPLO=new BigDecimal("1000.00"); private final OperacionesRepository repo; public OperacionesService(OperacionesRepository repo){this.repo=repo;}
 @Transactional public RetiroResponse retirar(Long id,RetiroRequest req){BigDecimal monto=req.monto(); if(monto.remainder(MULTIPLO).compareTo(BigDecimal.ZERO)!=0) throw new IllegalArgumentException("El monto debe ser múltiplo de 1000");
  BigDecimal antes=repo.saldoParaActualizar(id).orElseThrow(()->new RecursoNoEncontrado("No existe la cuenta "+id)); if(antes.compareTo(monto)<0) throw new SaldoInsuficiente("Saldo insuficiente");
  BigDecimal despues=antes.subtract(monto); String ref=UUID.randomUUID().toString(); repo.actualizarSaldo(id,despues); repo.registrar(ref,id,monto,antes,despues); return new RetiroResponse(ref,"APROBADA",id,monto,antes,despues,LocalDateTime.now());}
}
