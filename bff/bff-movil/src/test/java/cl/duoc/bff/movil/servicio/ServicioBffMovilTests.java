package cl.duoc.bff.movil.servicio;

import cl.duoc.bff.compartido.integracion.ClienteServiciosBanco; import cl.duoc.bff.compartido.modelo.*; import cl.duoc.bff.compartido.excepcion.OperacionInvalidaException;
import org.junit.jupiter.api.Test; import java.math.BigDecimal; import java.time.LocalDate; import java.time.LocalDateTime; import java.util.List;
import static org.assertj.core.api.Assertions.*; import static org.mockito.Mockito.*;
class ServicioBffMovilTests {
 @Test void reduceDatosParaMovil(){var backend=mock(ClienteServiciosBanco.class); var s=new ServicioBffMovil(backend); when(backend.obtenerCuenta(106L)).thenReturn(new CuentaBancaria(106L,"Jane",new BigDecimal("10000"),40,"ahorro",LocalDateTime.now())); when(backend.obtenerMovimientos(106L,5)).thenReturn(List.of(new MovimientoCuenta(LocalDate.now(),"DEPOSITO",new BigDecimal("1000"),"x","a.csv"))); var r=s.obtenerMovimientos(106L,5); assertThat(r).hasSize(1); assertThat(r.get(0).monto()).isEqualByComparingTo("1000");}
 @Test void validaLimite(){var s=new ServicioBffMovil(mock(ClienteServiciosBanco.class)); assertThatThrownBy(()->s.obtenerMovimientos(106L,11)).isInstanceOf(OperacionInvalidaException.class);}
}
