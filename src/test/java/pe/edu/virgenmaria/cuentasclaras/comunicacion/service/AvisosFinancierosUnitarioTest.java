package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AplicacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.OrigenPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAplicacion;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AplicacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.service.PagoRegistrado;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sprint 5: el aviso de un pago que entró por el banco (recaudación) dice «banco»; uno a cada responsable de pago. */
class AvisosFinancierosUnitarioTest {

	private final CreadorMensajes creador = mock(CreadorMensajes.class);

	private final PagoRepository pagos = mock(PagoRepository.class);

	private final AplicacionPagoRepository aplicaciones = mock(AplicacionPagoRepository.class);

	private final AvisosFinancieros avisos = new AvisosFinancieros(creador, pagos, aplicaciones,
			mock(ApoderadoRepository.class), mock(AlumnoRepository.class), mock(CuotaRepository.class),
			mock(UsuarioRepository.class));

	private static AplicacionPago aplicacion(String concepto, String nombres, Apoderado responsable) {
		Alumno alumno = mock(Alumno.class);
		when(alumno.getNombres()).thenReturn(nombres);
		when(alumno.getResponsablePago()).thenReturn(responsable);
		Cuota cuota = mock(Cuota.class);
		when(cuota.getDescripcion()).thenReturn(concepto);
		when(cuota.getAlumno()).thenReturn(alumno);
		AplicacionPago a = mock(AplicacionPago.class);
		when(a.getTipo()).thenReturn(TipoAplicacion.APLICACION);
		when(a.getCuota()).thenReturn(cuota);
		return a;
	}

	private static Apoderado apoderado(long id) {
		Apoderado a = mock(Apoderado.class);
		when(a.getId()).thenReturn(id);
		when(a.isActivo()).thenReturn(true);
		return a;
	}

	private Pago pago(OrigenPago origen, MedioPago medio) {
		Comprobante comprobante = mock(Comprobante.class);
		when(comprobante.numeroCompleto()).thenReturn("B001-00000231");
		Pago pago = mock(Pago.class);
		when(pago.getId()).thenReturn(15L);
		when(pago.getOrigen()).thenReturn(origen);
		when(pago.getMedio()).thenReturn(medio);
		when(pago.getTotal()).thenReturn(new BigDecimal("800.00"));
		when(pago.getComprobante()).thenReturn(comprobante);
		when(pago.getCreadoEn()).thenReturn(LocalDateTime.of(2026, 10, 5, 9, 30));
		when(pagos.findById(15L)).thenReturn(Optional.of(pago));
		return pago;
	}

	@Test
	void elPagoPorElBancoAvisaACadaResponsableYDiceBanco() {
		pago(OrigenPago.RECAUDACION, MedioPago.RECAUDACION_BANCARIA);
		Apoderado madre = apoderado(3);
		Apoderado padre = apoderado(4);
		List<AplicacionPago> lineas = List.of(aplicacion("Pensión de marzo", "Ana María", madre),
				aplicacion("Pensión de marzo", "Luis", padre), aplicacion("Pensión de abril", "Ana María", madre));
		when(aplicaciones.dePagos(List.of(15L))).thenReturn(lineas);

		avisos.alPagoRegistrado(new PagoRegistrado(15L));

		ArgumentCaptor<CreadorMensajes.Contenido> contenido = ArgumentCaptor.forClass(CreadorMensajes.Contenido.class);
		verify(creador).paraApoderado(eq(madre), contenido.capture(), eq(false), isNull());
		verify(creador).paraApoderado(eq(padre), any(), eq(false), isNull());
		assertThat(contenido.getValue().parametros()).containsExactly("S/ 800.00",
				"Pensión de marzo de Ana; Pensión de marzo de Luis; Pensión de abril de Ana", "B001-00000231",
				"05/10/2026 09:30", "Banco (código de alumno)", "banco");
	}

	@Test
	void sinMensajeNoHayPago() {
		pago(OrigenPago.CAJA, MedioPago.EFECTIVO);
		List<AplicacionPago> lineas = List.of(aplicacion("Pensión de marzo", "Ana", apoderado(3)));
		when(aplicaciones.dePagos(List.of(15L))).thenReturn(lineas);
		when(creador.paraApoderado(any(), any(), anyBoolean(), any())).thenThrow(new IllegalStateException("la base "
				+ "rechazó el mensaje"));

		// El oyente es síncrono: la excepción sube a la transacción del pago y la revierte (ver SinMensajeNoHayPagoTest).
		assertThatThrownBy(() -> avisos.alPagoRegistrado(new PagoRegistrado(15L))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unPagoSinCuotasAplicadasNoAvisaANadie() {
		pago(OrigenPago.CAJA, MedioPago.EFECTIVO);
		when(aplicaciones.dePagos(anyList())).thenReturn(List.of());
		avisos.alPagoRegistrado(new PagoRegistrado(15L));
		verify(creador, never()).paraApoderado(any(), any(), anyBoolean(), any());
	}
}
