package cl.duoc.bff.web.servicio;

import cl.duoc.bff.compartido.integracion.ClienteServiciosBanco;
import cl.duoc.bff.compartido.modelo.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.List;
import static org.assertj.core.api.Assertions.assertThat; import static org.mockito.Mockito.*;

class ServicioBffWebTests {
 @Test void componeRespuestaDesdeServiciosBackend(){
  var backend=mock(ClienteServiciosBanco.class); var servicio=new ServicioBffWeb(backend);
  when(backend.obtenerCuenta(106L)).thenReturn(new CuentaBancaria(106L,"Jane Smith",new BigDecimal("12180"),40,"prestamo",LocalDateTime.of(2026,9,6,12,0)));
  when(backend.obtenerMovimientos(106L,20)).thenReturn(List.of());
  var r=servicio.obtenerCuenta(106L);
  assertThat(r.canal()).isEqualTo("WEB"); assertThat(r.titular().nombre()).isEqualTo("Jane Smith"); verify(backend).obtenerCuenta(106L); verify(backend).obtenerMovimientos(106L,20);
 }
 @Test void entregaResumenObtenidoPorHttp(){
  var backend=mock(ClienteServiciosBanco.class); var servicio=new ServicioBffWeb(backend);
  when(backend.obtenerResumen()).thenReturn(new ResumenOperacional(50L,491L,723L,1736L,new BigDecimal("420850")));
  var r=servicio.obtenerResumen(); assertThat(r.transaccionesProcesadas()).isEqualTo(491L);
 }
}
