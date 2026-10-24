package pe.edu.virgenmaria.cuentasclaras.academico.notas;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.AvanceVista;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 10, tanda 2 (secciones 2 y 4 del brief): antes de cerrar, Dirección ve por sección y área las competencias sin nota
 * y las asignaciones sin áreas del catálogo; cerrar NO se bloquea por ellas y la bitácora guarda lo que faltaba. El avance
 * lo ven Dirección y Promotoría sin alumnos; el cierre es solo de Dirección.
 */
class CierrePeriodoTest extends PruebaNotas {

	private void unaDelSprint9() {
		Usuario delSprint9 = docente("pepe.sprint9");
		Long id = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(new ProponerAsignacionRequest(
				escuela.primaria5B2026(), delSprint9.getId(), TipoAsignacion.DOCENTE, "Matemática", MOTIVO)));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", id);
		SecurityContextHolder.clearContext();
	}

	@Test
	void elResumenListaCompetenciasSinNotaYAsignacionesSinAreas() {
		anaPoneA();
		unaDelSprint9();
		hoyEs(LocalDate.of(2026, 5, 4));
		AvanceVista resumen = como(EscenarioCobranza.DIRECCION, () -> avance.resumenCierre(periodo));
		Long matematica = area("MAT");
		Long ciencia = area("CYT");
		assertThat(resumen.filas()).filteredOn(f -> f.seccionId().equals(seccion) && f.areaId().equals(matematica))
				.singleElement().satisfies(f -> {
					assertThat(f.esperadas()).isEqualTo(12);
					assertThat(f.faltan()).isZero();
					assertThat(f.docentes()).contains(ana.getNombreCompleto());
				});
		assertThat(resumen.pendientes()).anyMatch(f -> f.seccionId().equals(seccion) && f.areaId().equals(ciencia)
				&& f.faltan() == 3 * competencias("CYT").size());
		assertThat(resumen.pendientes()).noneMatch(f -> f.areaId().equals(matematica) && f.seccionId().equals(seccion));
		assertThat(resumen.sinAreas()).singleElement().satisfies(s -> assertThat(s.seccionId())
				.isEqualTo(escuela.primaria5B2026()));
	}

	@Test
	void cerrarNoSeBloqueaYLaBitacoraGuardaLoQueFaltaba() {
		anaPoneA();
		unaDelSprint9();
		cerrarPeriodo();
		assertThat(jdbc.queryForObject("SELECT estado FROM periodo_evaluacion WHERE id = ?", String.class, periodo))
				.isEqualTo("CERRADO");
		assertThat(jdbc.queryForObject("SELECT detalle FROM evento_auditoria WHERE accion = 'PERIODO_CERRADO'", String.class))
				.contains("Al cerrar faltaban").contains("1 asignaciones sin áreas");
	}

	@Test
	void promotoriaVeElAvanceSinAlumnosPeroNoCierra() {
		anaPoneA();
		AvanceVista vista = como(EscenarioCobranza.PROMOTORIA, () -> avance.avance(null));
		assertThat(vista.periodoId()).isEqualTo(periodo);
		assertThat(vista.conNota()).isEqualTo(12);
		assertThat(vista.verCorrecciones()).isFalse();
		assertThat(java.util.Arrays.stream(AvanceVista.FilaAvance.class.getRecordComponents())
				.map(java.lang.reflect.RecordComponent::getName)).doesNotContain("alumno", "matriculaId", "nombres");
		hoyEs(LocalDate.of(2026, 5, 4));
		assertThatThrownBy(() -> como(EscenarioCobranza.PROMOTORIA, () -> avance.cerrar(periodo)))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> como(EscenarioCobranza.PROMOTORIA, () -> avance.correccionesDelPeriodo(periodo)))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> como(pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba.autenticado(ana),
				() -> avance.avance(null))).isInstanceOf(AccessDeniedException.class);
		assertThat(vista.filas()).isNotEmpty();
	}
}
