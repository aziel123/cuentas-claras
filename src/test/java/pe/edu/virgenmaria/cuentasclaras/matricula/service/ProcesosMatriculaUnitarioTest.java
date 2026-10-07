package pe.edu.virgenmaria.cuentasclaras.matricula.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ReservasMatricula;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.PlanPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.PlanPensionRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.EstadoRenovacion;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.RenovacionMatricula;
import pe.edu.virgenmaria.cuentasclaras.matricula.repository.MatriculasReservadasRepository;
import pe.edu.virgenmaria.cuentasclaras.matricula.repository.RenovacionMatriculaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 5, tanda 2 (unitaria, con Mockito): qué reserva, activa y vence {@code sistema.matricula}. Una reservada se
 * activa con la matrícula pagada o con un plan que cobra 0; nunca si su cuota de matrícula se anuló (desistimiento).
 */
@ExtendWith(MockitoExtension.class)
class ProcesosMatriculaUnitarioTest {

	@Mock
	private RenovacionMatriculaRepository renovaciones;

	@Mock
	private MatriculasReservadasRepository reservadas;

	@Mock
	private MatriculaRepository matriculas;

	@Mock
	private PlanPensionRepository planes;

	@Mock
	private ReservasMatricula reservas;

	@Mock
	private AuditoriaService auditoria;

	private ProcesosMatricula procesos;

	@BeforeEach
	void preparar() {
		procesos = new ProcesosMatricula(renovaciones, reservadas, matriculas, planes, reservas, auditoria);
	}

	private static Matricula reservada(long id, long anioId) {
		Matricula m = mock(Matricula.class);
		AnioEscolar anio = mock(AnioEscolar.class);
		when(m.getId()).thenReturn(id);
		when(m.getAnioEscolar()).thenReturn(anio);
		when(anio.getId()).thenReturn(anioId);
		when(m.nivel()).thenReturn(Nivel.PRIMARIA);
		return m;
	}

	private static PlanPension plan(String matricula) {
		PlanPension plan = mock(PlanPension.class);
		when(plan.aprobado()).thenReturn(true);
		when(plan.getMontoMatricula()).thenReturn(new BigDecimal(matricula));
		return plan;
	}

	@Test
	void seActivanLasPagadasYLasDeUnPlanSinMatriculaPeroNoLasDesistidas() {
		when(reservadas.reservadasConMatriculaPagada()).thenReturn(List.of(5L));
		Matricula sinCobro = reservada(7L, 2L);
		Matricula desistida = mock(Matricula.class);
		when(desistida.getId()).thenReturn(9L);
		when(reservadas.reservadasSinCuotaDeMatriculaVigente()).thenReturn(List.of(sinCobro, desistida));
		when(reservadas.reservadasConMatriculaAnulada()).thenReturn(List.of(9L));
		PlanPension cero = plan("0.00");
		when(planes.findByAnioEscolarIdAndNivelAndVigenteTrue(2L, Nivel.PRIMARIA)).thenReturn(Optional.of(cero));

		assertThat(procesos.paraActivar()).containsExactly(5L, 7L);
	}

	@Test
	void conUnPlanQueCobraMatriculaSinPagarNoSeActiva() {
		when(reservadas.reservadasConMatriculaPagada()).thenReturn(List.of());
		Matricula sinCuota = reservada(7L, 2L);
		when(reservadas.reservadasSinCuotaDeMatriculaVigente()).thenReturn(List.of(sinCuota));
		when(reservadas.reservadasConMatriculaAnulada()).thenReturn(List.of());
		PlanPension trescientos = plan("300.00");
		when(planes.findByAnioEscolarIdAndNivelAndVigenteTrue(2L, Nivel.PRIMARIA)).thenReturn(Optional.of(trescientos));

		assertThat(procesos.paraActivar()).isEmpty();
	}

	@Test
	void unaRenovacionQueNoEstaConfirmadaNoSeReserva() {
		RenovacionMatricula propuesta = mock(RenovacionMatricula.class);
		when(propuesta.getEstado()).thenReturn(EstadoRenovacion.PROPUESTA);
		when(renovaciones.bloquear(3L)).thenReturn(Optional.of(propuesta));

		assertThat(procesos.reservar(3L)).isFalse();
		verify(reservas, never()).reservar(any(), any());
	}

	@Test
	void siYaTieneMatriculaEnEsaSeccionSoloLaEnlaza() {
		RenovacionMatricula confirmada = mock(RenovacionMatricula.class);
		Alumno alumno = mock(Alumno.class);
		AnioEscolar anio = mock(AnioEscolar.class);
		Seccion seccion = mock(Seccion.class);
		when(confirmada.getEstado()).thenReturn(EstadoRenovacion.CONFIRMADA);
		when(confirmada.getAlumno()).thenReturn(alumno);
		when(confirmada.getAnioDestino()).thenReturn(anio);
		when(confirmada.getSeccionDestino()).thenReturn(seccion);
		when(alumno.getId()).thenReturn(10L);
		when(anio.getId()).thenReturn(2L);
		when(seccion.getId()).thenReturn(20L);
		Matricula existente = mock(Matricula.class);
		when(existente.getId()).thenReturn(30L);
		when(existente.getSeccion()).thenReturn(seccion);
		when(renovaciones.bloquear(3L)).thenReturn(Optional.of(confirmada));
		when(matriculas.findByAlumnoIdAndAnioEscolarId(10L, 2L)).thenReturn(Optional.of(existente));

		assertThat(procesos.reservar(3L)).isTrue();
		verify(confirmada).matricular(30L);
		verify(reservas, never()).reservar(any(), any());
		verify(renovaciones).saveAndFlush(confirmada);
	}

	@Test
	void sinPropuestasVencidasNoAudita() {
		when(renovaciones.findByEstadoAndVenceEnBeforeOrderByIdAsc(EstadoRenovacion.PROPUESTA, LocalDate.of(2027, 2, 1)))
				.thenReturn(List.of());
		assertThat(procesos.vencer(LocalDate.of(2027, 2, 1))).isZero();
		verify(auditoria, never()).registrar(any(), any(), any(), any(), any(), any());
	}
}
