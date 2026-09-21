package cl.duoc.bff.cajero.servicio;

import cl.duoc.bff.cajero.dto.SolicitudRetiro; import cl.duoc.bff.compartido.integracion.ClienteServiciosBanco; import cl.duoc.bff.compartido.modelo.*; import cl.duoc.bff.compartido.excepcion.OperacionInvalidaException;
import org.junit.jupiter.api.Test; import java.math.BigDecimal; import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*; import static org.mockito.Mockito.*;
class ServicioBffCajeroTests {
 @Test void delegaRetiroAlMicroservicioOperaciones(){var backend=mock(ClienteServiciosBanco.class); var s=new ServicioBffCajero(backend); when(backend.retirar(106L,new BigDecimal("2000"))).thenReturn(new RetiroBackend("abc","APROBADA",106L,new BigDecimal("2000"),new BigDecimal("12000"),new BigDecimal("10000"),LocalDateTime.now())); var r=s.retirar(106L,new SolicitudRetiro(new BigDecimal("2000"))); assertThat(r.estado()).isEqualTo("APROBADA"); assertThat(r.cuentaEnmascarada()).isEqualTo("****06");}
 @Test void validaMultiplo(){var s=new ServicioBffCajero(mock(ClienteServiciosBanco.class)); assertThatThrownBy(()->s.retirar(106L,new SolicitudRetiro(new BigDecimal("1500")))).isInstanceOf(OperacionInvalidaException.class);}
}
