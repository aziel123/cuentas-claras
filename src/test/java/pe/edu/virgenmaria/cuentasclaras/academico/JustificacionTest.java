package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.HojaDelDia;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.JustificarRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.MesAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MotivoJustificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 9, tanda 2 (sección 3.5; E11, E13, E14, E35, E53): la familia justifica una falta reciente de SU hijo al
 * instante; Dirección la revierte con su firma; lo revertido no se vuelve a justificar; la nota la ve solo el tutor.
 */
class JustificacionTest extends PruebaAsistencia {

	private static final JustificarRequest SALUD = new JustificarRequest(MotivoJustificacion.SALUD, "Tuvo fiebre");

	private Long faltaDeMateo() {
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		return EscenarioAsistencia.marcaVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES);
	}

	@Test
	void laFamiliaJustificaAlInstante() {
		Long falta = faltaDeMateo();
		MesAsistencia mes = como(datos.rosaEnLinea(), () -> justificaciones.delMes(null));
		assertThat(mes.hijos()).extracting(h -> h.nombre()).containsExactlyInAnyOrder("Mateo", "Valeria");
		assertThat(mes.hijos()).filteredOn(h -> h.nombre().equals("Mateo")).singleElement()
				.satisfies(h -> assertThat(h.dias()).singleElement().satisfies(d -> assertThat(d.justificable()).isTrue()));
		como(datos.rosaEnLinea(), () -> justificaciones.justificar(falta, SALUD));
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES))
				.isEqualTo("FALTA_JUSTIFICADA");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE clave = ?", Long.class,
				"marca_asistencia:" + falta + ":JUSTIFICADA")).isEqualTo(1);
		// La bitácora guarda el motivo de la lista, nunca la nota (dato de salud).
		assertThat(EscenarioEscolar.todaLaBitacora(jdbc)).contains("Salud").doesNotContain("fiebre");
	}

	@Test
	void otraFamiliaResponde404() {
		Long falta = faltaDeMateo();
		assertThatThrownBy(() -> como(datos.pedroEnLinea(), () -> justificaciones.justificar(falta, SALUD)))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES)).isEqualTo("FALTA");
		MesAsistencia mes = como(datos.pedroEnLinea(), () -> justificaciones.delMes(null));
		assertThat(mes.hijos()).extracting(h -> h.nombre()).containsExactly("Lucas");
	}

	@Test
	void elDocenteNoRevierte() {
		Long falta = faltaDeMateo();
		como(datos.rosaEnLinea(), () -> justificaciones.justificar(falta, SALUD));
		Long justificada = EscenarioAsistencia.marcaVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES);
		assertThatThrownBy(() -> como(tutora(), () -> direccion.revertir(justificada, "La justificación es falsa")))
				.isInstanceOf(AccessDeniedException.class);
		como(direccion(), () -> direccion.revertir(justificada, "La justificación es falsa"));
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES)).isEqualTo("FALTA");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "JUSTIFICACION_REVERTIDA").get("VALOR_ANTERIOR"))
				.isEqualTo("FALTA_JUSTIFICADA");
	}

	@Test
	void fueraDePlazoSeRechaza() {
		Long falta = faltaDeMateo();
		alas(14, 9, 0);
		assertThatThrownBy(() -> como(datos.rosaEnLinea(), () -> justificaciones.justificar(falta, SALUD)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("días hábiles");
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES)).isEqualTo("FALTA");
	}

	@Test
	void loRevertidoNoSeJustifica() {
		Long falta = faltaDeMateo();
		como(datos.rosaEnLinea(), () -> justificaciones.justificar(falta, SALUD));
		Long justificada = EscenarioAsistencia.marcaVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES);
		como(direccion(), () -> direccion.revertir(justificada, "La justificación es falsa"));
		Long revertida = EscenarioAsistencia.marcaVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES);
		assertThatThrownBy(() -> como(datos.rosaEnLinea(), () -> justificaciones.justificar(revertida, SALUD)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("revirtió");
		MesAsistencia mes = como(datos.rosaEnLinea(), () -> justificaciones.delMes(null));
		assertThat(mes.hijos()).filteredOn(h -> h.nombre().equals("Mateo")).singleElement()
				.satisfies(h -> assertThat(h.dias()).singleElement().satisfies(d -> assertThat(d.justificable()).isFalse()));
	}

	@Test
	void elDocenteQueNoEsTutorNoVeLaNota() {
		EscenarioAsistencia.asignar(asignaciones, bandeja, jdbc, datos.seccion(), datos.otroDocente(),
				TipoAsignacion.DOCENTE);
		Long falta = faltaDeMateo();
		como(datos.rosaEnLinea(), () -> justificaciones.justificar(falta, SALUD));
		HojaDelDia deLaTutora = como(tutora(), () -> asistencia.hoja(datos.seccion()));
		HojaDelDia delOtro = como(otroDocente(), () -> asistencia.hoja(datos.seccion()));
		assertThat(deLaTutora.alumnos()).filteredOn(a -> a.alumnoId() == datos.mateo()).singleElement()
				.satisfies(a -> assertThat(a.nota()).isEqualTo("Tuvo fiebre"));
		assertThat(delOtro.alumnos()).filteredOn(a -> a.alumnoId() == datos.mateo()).singleElement()
				.satisfies(a -> {
					assertThat(a.nota()).isNull();
					assertThat(a.justificada()).isTrue();
				});
	}

	@Test
	void elDocenteNoCorrigeUnaJustificacion() {
		Long falta = faltaDeMateo();
		como(datos.rosaEnLinea(), () -> justificaciones.justificar(falta, SALUD));
		assertThatThrownBy(() -> corregirHoy(datos.mateo(), EstadoAsistencia.PRESENTE))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("justificó la familia");
	}
}
