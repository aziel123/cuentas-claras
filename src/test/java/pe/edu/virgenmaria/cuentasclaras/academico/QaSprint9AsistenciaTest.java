package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.CorreccionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.HojaRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.MarcaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.JustificarRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MotivoJustificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA del sprint 9 (asistencia): bordes de fechas, feriados, la hora de Lima frente a UTC (la suite corre también con la
 * JVM en Los Ángeles), la cobertura con {@code hasta}, A1 en la hoja y en la corrección del día, una marca de otra sección y
 * el primer envío de avisos de un colegio que ya tenía asistencia (QA-S9-3).
 * <p>
 * El escenario: martes 6 de octubre de 2026; el jueves 8 es feriado nacional (Combate de Angamos).
 */
class QaSprint9AsistenciaTest extends PruebaAsistencia {

	private static final LocalDate JUEVES_FERIADO = EscenarioAsistencia.MARTES.plusDays(2);

	private List<Long> todos() {
		return List.of(datos.mateo(), datos.valeria(), datos.lucas());
	}

	private Long faltaDeMateoDelMartes() {
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		return EscenarioAsistencia.marcaVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES);
	}

	private void justificarComoRosa(Long marca) {
		como(datos.rosaEnLinea(), () -> justificaciones.justificar(marca, new JustificarRequest(MotivoJustificacion.SALUD,
				null)));
	}

	// ---- QA-S9-3: el primer envío de un colegio sin marca de agua ----

	@Test
	void debeAvisarSoloLaAsistenciaDeHoyEnElPrimerEnvioDeUnColegioSinMarcaDeAgua() {
		// Dado un colegio que registró asistencia el lunes (en preparación, sin avisos) y vuelve a registrar el martes.
		alas(-1, 8, 0);
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		alas(0, 8, 0);
		registrar(Map.of(datos.lucas(), EstadoAsistencia.FALTA));
		// Cuando corre su primer envío (nunca antes hubo uno).
		alas(0, 10, 30);
		pasadaDeAvisos();
		// Entonces sale el aviso de HOY y ninguna «corrección» de lo registrado antes de tener avisos.
		assertThat(mensajes("ASISTENCIA_CORREGIDA")).isZero();
		assertThat(parametros("ASISTENCIA_DIA")).singleElement().asString().contains("Lucas: falta")
				.doesNotContain("Mateo");
	}

	@Test
	void debeAvisarAlDiaSiguienteLoCambiadoDespuesDelUltimoEnvioCuandoYaHuboUno() {
		// Control de QA-S9-3: con un envío previo, lo de ayer después del último envío sí sale (A2).
		Long falta = faltaDeMateoDelMartes();
		alas(0, 16, 0);
		pasadaDeAvisos();
		alas(0, 17, 0);
		justificarComoRosa(falta);
		alas(1, 10, 30);
		pasadaDeAvisos();
		assertThat(mensajes("ASISTENCIA_CORREGIDA")).isEqualTo(1);
	}

	// ---- A1: nadie del personal crea una FALTA_JUSTIFICADA ----

	@Test
	void debeRechazarUnaHojaQueTraeUnaFaltaJustificada() {
		HojaRequest hoja = EscenarioAsistencia.hoja(Map.of(datos.mateo(), EstadoAsistencia.FALTA_JUSTIFICADA), todos());
		assertThatThrownBy(() -> como(tutora(), () -> asistencia.guardar(datos.seccion(), hoja)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("familia");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registro_asistencia", Long.class))
				.as("la hoja rechazada no deja un registro a medias").isZero();
	}

	@Test
	void debeRechazarLaCorreccionDelDiaHaciaFaltaJustificada() {
		Long falta = faltaDeMateoDelMartes();
		alas(0, 9, 0);
		assertThatThrownBy(() -> como(tutora(), () -> asistencia.corregirHoy(falta, new CorreccionRequest(
				EstadoAsistencia.FALTA_JUSTIFICADA, null, EscenarioAsistencia.MOTIVO))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("pide la corrección");
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES)).isEqualTo("FALTA");
	}

	@Test
	void debeRechazarUnaHojaQueRepiteUnAlumno() {
		HojaRequest hoja = new HojaRequest(List.of(new MarcaPedida(datos.mateo(), EstadoAsistencia.PRESENTE, null),
				new MarcaPedida(datos.mateo(), EstadoAsistencia.FALTA, null),
				new MarcaPedida(datos.lucas(), EstadoAsistencia.PRESENTE, null)), null);
		assertThatThrownBy(() -> como(tutora(), () -> asistencia.guardar(datos.seccion(), hoja)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("repite");
	}

	// ---- Fechas, feriados y la hora de Lima ----

	@Test
	void debeRechazarElRegistroEnUnFeriadoNacional() {
		alas(2, 8, 0);
		assertThatThrownBy(() -> registrar(Map.of())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("feriado");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registro_asistencia WHERE fecha = ?", Long.class,
				JUEVES_FERIADO)).isZero();
	}

	@Test
	void debeRegistrarConLaFechaDeLimaAunqueEnUtcYaSeaElDiaSiguiente() {
		// Martes 21:00 en Lima = miércoles 02:00 UTC (y martes 19:00 en Los Ángeles, si la JVM corre allí).
		alas(0, 21, 0);
		registrar(Map.of(datos.lucas(), EstadoAsistencia.FALTA));
		assertThat(jdbc.queryForObject("SELECT fecha FROM registro_asistencia", LocalDate.class))
				.isEqualTo(EscenarioAsistencia.MARTES);
	}

	@Test
	void debeCerrarLaCorreccionDelDiaALaHoraDelSegundoAvisoEnHoraDeLima() {
		Long falta = faltaDeMateoDelMartes();
		alas(0, 15, 59);
		como(tutora(), () -> asistencia.corregirHoy(falta, new CorreccionRequest(EstadoAsistencia.PRESENTE, null,
				EscenarioAsistencia.MOTIVO)));
		Long presente = EscenarioAsistencia.marcaVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES);
		alas(0, 16, 0);
		assertThatThrownBy(() -> como(tutora(), () -> asistencia.corregirHoy(presente, new CorreccionRequest(
				EstadoAsistencia.FALTA, null, EscenarioAsistencia.MOTIVO)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("16:00");
	}

	@Test
	void debeAceptarLaJustificacionDelQuintoDiaHabilSinContarElFeriado() {
		Long falta = faltaDeMateoDelMartes();
		// Del martes 6 al martes 13 hay 4 días hábiles (7, 9, 12 y 13): el jueves 8 es feriado nacional.
		alas(7, 9, 0);
		justificarComoRosa(falta);
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES))
				.isEqualTo("FALTA_JUSTIFICADA");
	}

	@Test
	void debeRechazarLaJustificacionDelSextoDiaHabil() {
		Long falta = faltaDeMateoDelMartes();
		// El miércoles 14 ya son 5 días hábiles después de la falta (fuera de los 5 contando el de la falta).
		alas(8, 9, 0);
		assertThatThrownBy(() -> justificarComoRosa(falta)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("días hábiles");
	}

	@Test
	void debeRechazarJustificarUnaFaltaDeHoyDosVeces() {
		Long falta = faltaDeMateoDelMartes();
		justificarComoRosa(falta);
		assertThatThrownBy(() -> justificarComoRosa(falta)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya cambió");
	}

	// ---- Cobertura con «hasta» ----

	@Test
	void debeDarAccesoLaCoberturaHastaElUltimoMinutoDeSuDiaEnHoraDeLima() {
		Long id = coberturaDeLuisEnQuintoA(EscenarioAsistencia.MARTES.plusDays(1));
		EscenarioAprobaciones.aprueba(direccion(), bandeja, jdbc, "asignacion_docente", id);
		SecurityContextHolder.clearContext();
		// Miércoles 23:59 en Lima (jueves 04:59 UTC): todavía es el día «hasta».
		alas(1, 23, 59);
		assertThat(como(otroDocente(), () -> asistencia.hoja(datos.seccion())).seccion()).isEqualTo("5.° Primaria A");
		// Jueves 00:00 en Lima: ya no.
		alas(2, 0, 0);
		assertThatThrownBy(() -> como(otroDocente(), () -> asistencia.hoja(datos.seccion())))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void debeImpedirQueUnaCoberturaVencidaRegistreLaAsistencia() {
		Long id = coberturaDeLuisEnQuintoA(EscenarioAsistencia.MARTES);
		EscenarioAprobaciones.aprueba(direccion(), bandeja, jdbc, "asignacion_docente", id);
		SecurityContextHolder.clearContext();
		// El viernes (después del martes «hasta», con el jueves feriado de por medio).
		alas(3, 8, 0);
		assertThatThrownBy(() -> como(otroDocente(), () -> asistencia.guardar(datos.seccion(),
				EscenarioAsistencia.hoja(Map.of(), todos())))).isInstanceOf(RecursoNoEncontradoException.class);
	}

	// ---- IDOR entre secciones ----

	@Test
	void debeResponder404AlCorregirUnaMarcaDeOtraSeccion() {
		Long falta = faltaDeMateoDelMartes();
		alas(0, 9, 0);
		assertThatThrownBy(() -> como(otroDocente(), () -> asistencia.corregirHoy(falta, new CorreccionRequest(
				EstadoAsistencia.PRESENTE, null, EscenarioAsistencia.MOTIVO)))).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> como(otroDocente(), () -> asistencia.pedirCorreccion(falta, new CorreccionRequest(
				EstadoAsistencia.PRESENTE, null, EscenarioAsistencia.MOTIVO)))).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES)).isEqualTo("FALTA");
	}

	@Test
	void debeResponder404AlPedirElRegistroTardioDeOtraSeccion() {
		alas(1, 8, 0);
		assertThatThrownBy(() -> como(otroDocente(), () -> asistencia.pedirRegistroTardio(datos.seccion(),
				new pe.edu.virgenmaria.cuentasclaras.academico.dto.RegistroTardioRequest(EscenarioAsistencia.MARTES,
						"No hubo internet: se anotó en papel")))).isInstanceOf(RecursoNoEncontradoException.class);
	}

	private Long coberturaDeLuisEnQuintoA(LocalDate hasta) {
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		try {
			return asignaciones.proponer(new ProponerAsignacionRequest(datos.seccion(), datos.otroDocente().getId(),
					TipoAsignacion.AUXILIAR, null, "Cubre a la tutora con licencia médica", hasta));
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}
}
