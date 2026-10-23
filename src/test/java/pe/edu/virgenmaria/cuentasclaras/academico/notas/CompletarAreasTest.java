package pe.edu.virgenmaria.cuentasclaras.academico.notas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.CompletarAreasVista;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.HojaNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.service.CompletarAreas;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 10, tanda 2 (sección 4 del brief): «Completar áreas» de los docentes del sprint 9. La sugerencia sale del texto
 * libre de V32 (por nombre, sin tildes); la propuesta lleva las áreas ANTES de su solicitud (A2) y la aprueba otra persona,
 * que reemplaza la vigente; sin aprobar, el docente sigue sin calificar.
 */
class CompletarAreasTest extends PruebaNotas {

	@Autowired
	private CompletarAreas completar;

	private Usuario delSprint9;

	private Long anterior;

	private void prepararDelSprint9(String texto) {
		delSprint9 = docente("pepe.sprint9");
		anterior = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(new ProponerAsignacionRequest(
				escuela.primaria5B2026(), delSprint9.getId(), TipoAsignacion.DOCENTE, texto, MOTIVO)));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", anterior);
		SecurityContextHolder.clearContext();
	}

	@Test
	void laSugerenciaSaleDelTextoAnteriorSinTildes() {
		prepararDelSprint9("matematica, Ciencia y Tecnología");
		CompletarAreasVista vista = como(EscenarioCobranza.DIRECCION, () -> completar.vista(anterior, null));
		assertThat(vista.propuestas()).singleElement().satisfies(p -> {
			assertThat(p.textoAnterior()).isEqualTo("matematica, Ciencia y Tecnología");
			assertThat(p.areas()).filteredOn(CompletarAreasVista.OpcionArea::sugerida)
					.extracting(CompletarAreasVista.OpcionArea::id).containsExactlyInAnyOrder(area("MAT"), area("CYT"));
			assertThat(p.areas()).extracting(CompletarAreasVista.OpcionArea::id).doesNotContain(area("TRV"));
		});
		CompletarAreasVista deLaSeccion = como(EscenarioCobranza.DIRECCION, () -> completar.vista(null,
				escuela.primaria5B2026()));
		assertThat(deLaSeccion.propuestas()).hasSize(1);
	}

	@Test
	void laPropuestaLlevaLasAreasYLaApruebaOtraPersonaQueReemplazaLaVigente() {
		prepararDelSprint9("Matemática");
		HojaNotas antes = como(UsuariosDePrueba.autenticado(delSprint9), () -> registro.hoja(escuela.primaria5B2026(),
				area("MAT"), null, null));
		assertThat(antes.editable()).isFalse();

		int creadas = como(EscenarioCobranza.ADMINISTRACION, () -> completar.proponer(Map.of(anterior,
				List.of(area("MAT"))), "Completar las áreas del catálogo del sprint 9"));
		assertThat(creadas).isEqualTo(1);
		Long nueva = jdbc.queryForObject("SELECT id FROM asignacion_docente WHERE estado = 'PROPUESTA' AND usuario_id = ?",
				Long.class, delSprint9.getId());
		assertThat(jdbc.queryForList("SELECT area_id FROM asignacion_area WHERE asignacion_id = ?", Long.class, nueva))
				.containsExactly(area("MAT"));
		HojaNotas sinAprobar = como(UsuariosDePrueba.autenticado(delSprint9), () -> registro.hoja(escuela.primaria5B2026(),
				area("MAT"), null, null));
		assertThat(sinAprobar.editable()).as("sin aprobar, sigue sin calificar").isFalse();

		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", nueva);
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForObject("SELECT estado FROM asignacion_docente WHERE id = ?", String.class, anterior))
				.isEqualTo("TERMINADA");
		HojaNotas despues = como(UsuariosDePrueba.autenticado(delSprint9), () -> registro.hoja(escuela.primaria5B2026(),
				area("MAT"), null, null));
		assertThat(despues.editable()).isTrue();
		assertThat(como(EscenarioCobranza.DIRECCION, () -> completar.vista(null, escuela.primaria5B2026())).propuestas())
				.isEmpty();
	}

	@Test
	void elDocenteNoCompletaSusPropiasAreas() {
		prepararDelSprint9("Matemática");
		assertThatThrownBy(() -> como(UsuariosDePrueba.autenticado(delSprint9), () -> completar.proponer(Map.of(anterior,
				List.of(area("MAT"))), "Completar las áreas del catálogo"))).isInstanceOf(AccessDeniedException.class);
	}
}
