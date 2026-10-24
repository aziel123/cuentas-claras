package pe.edu.virgenmaria.cuentasclaras.plataforma.proceso;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.EmisorLibretas;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.CambioEstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.MotivoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.CambioEstadoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AplicadorCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.PendientesCicloDeVida;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CierreSesionesColegio;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 11, tanda 1 (AP-4, AP-13, H3 y AP-10): la suspensión vencida ESPERA mientras el colegio tenga cierres por revisar,
 * comprobantes sin aceptar, pagos en línea en curso, libretas sin emitir o la objeción del titular sin responder; avisa la
 * espera una vez por día; sin pendientes, se aplica y expira las sesiones HTTP del personal (F10). Servicio puro.
 */
class AplicacionCambiosEstadoTest {

	private static final long COLEGIO = 7L;

	/** 3 de noviembre de 2026, 10:00 en Lima. */
	private final Clock reloj = Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ConfiguracionTiempo.ZONA_LIMA);

	private final ColegioRepository colegios = mock(ColegioRepository.class);

	private final CambioEstadoColegioRepository cambios = mock(CambioEstadoColegioRepository.class);

	private final AplicadorCambioEstado aplicador = mock(AplicadorCambioEstado.class);

	private final PendientesCicloDeVida pendientes = mock(PendientesCicloDeVida.class);

	private final EmisorLibretas libretas = mock(EmisorLibretas.class);

	private final CierreSesionesColegio cierreSesiones = mock(CierreSesionesColegio.class);

	private final AuditoriaService auditoria = mock(AuditoriaService.class);

	private AplicacionCambiosEstado aplicacion;

	private CambioEstadoColegio suspension;

	@BeforeEach
	void preparar() {
		aplicacion = new AplicacionCambiosEstado(colegios, cambios, aplicador, pendientes, libretas, cierreSesiones, auditoria,
				mock(ApplicationEventPublisher.class), mock(PlatformTransactionManager.class), reloj);
		LocalDateTime aprobada = LocalDateTime.now(reloj).minusDays(31);
		suspension = CambioEstadoColegio.propuesto(TipoCambioEstado.SUSPENSION, MotivoCambioEstado.FALTA_PAGO,
				"Tres meses sin pagar la suscripción, con aviso escrito.", "Carta notarial 2026-118", EstadoColegio.PRODUCCION,
				1L, 2L, "operador.ana");
		suspension.aprobar("operador.beto", 3L, aprobada, false);
		org.springframework.test.util.ReflectionTestUtils.setField(suspension, "id", 99L);
		when(cambios.findByTipoAndEstadoOrderByIdAsc(TipoCambioEstado.SUSPENSION, EstadoCambioEstado.APROBADO))
				.thenReturn(List.of());
		when(cambios.findByEstadoAndEfectivaDesdeLessThanEqualOrderByIdAsc(eq(EstadoCambioEstado.APROBADO), any()))
				.thenReturn(List.of(suspension));
		when(cambios.bloquear(any())).thenReturn(Optional.of(suspension));
		when(colegios.findFirstByTipo(any())).thenReturn(Optional.empty());
		when(libretas.pendientes()).thenReturn(List.of());
		when(pendientes.esperanLaSuspension()).thenReturn(List.of());
	}

	@Test
	void a4LaSuspensionEsperaLosCierresYLosComprobantesYAvisaUnaVezPorDia() {
		when(pendientes.esperanLaSuspension()).thenReturn(List.of("1 cierre de caja por revisar",
				"2 comprobantes sin aceptar por SUNAT"));

		assertThat(aplicacion.enColegio(COLEGIO)).isZero();
		assertThat(aplicacion.enColegio(COLEGIO)).isZero();

		verify(aplicador, never()).aplicar(any(), any());
		verify(aplicador, times(1)).avisarAlColegio(eq(COLEGIO), eq(AvisoPlataforma.Tipo.CAMBIO_ESTADO_EN_ESPERA), anyList(),
				eq(suspension), anyString());
		verify(auditoria, times(1)).registrar(eq(AccionAuditoria.SUSPENSION_EN_ESPERA), eq("cambio_estado_colegio"), any(),
				any(), eq("1 cierre de caja por revisar, 2 comprobantes sin aceptar por SUNAT"), anyString());
	}

	@Test
	void a13LaSuspensionEsperaLasLibretas() {
		when(libretas.pendientes()).thenReturn(List.of(new EmisorLibretas.Pendiente(1L, 2L)));

		assertThat(aplicacion.esperan(COLEGIO, suspension)).containsExactly("1 sección con libretas sin emitir");
		assertThat(aplicacion.enColegio(COLEGIO)).isZero();
		verify(aplicador, never()).aplicar(any(), any());
	}

	@Test
	void a10LaObjecionSinResponderTambienHaceEsperar() {
		suspension.objetar("titular.colegio", "Ya pagamos la deuda por transferencia el lunes pasado.",
				LocalDateTime.now(reloj).minusDays(2));

		assertThat(aplicacion.esperan(COLEGIO, suspension)).containsExactly("la objeción del titular sin responder");
		assertThat(aplicacion.enColegio(COLEGIO)).isZero();
	}

	@Test
	void f10SinPendientesSeAplicaYExpiraLasSesionesDelPersonal() {
		when(aplicador.aplicar(eq(suspension), any())).thenReturn(List.of(11L, 12L));

		assertThat(aplicacion.enColegio(COLEGIO)).isEqualTo(1);

		verify(aplicador).aplicar(eq(suspension), eq(LocalDateTime.now(reloj)));
		verify(cierreSesiones).expirarHttp(List.of(11L, 12L));
	}
}
