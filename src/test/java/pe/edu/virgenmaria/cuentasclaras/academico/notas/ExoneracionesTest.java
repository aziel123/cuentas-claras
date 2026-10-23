package pe.edu.virgenmaria.cuentasclaras.academico.notas;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.ExonerarRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.HojaNotas;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 10, tanda 2 (E21, E22 y A3): la exoneración de Religión o Educación Física la registra SOLO Dirección con su firma
 * y el código del escrito del padre; rige desde el periodo ABIERTO (las notas de los anteriores quedan) y desde ahí el área
 * no se califica para ese alumno.
 */
class ExoneracionesTest extends PruebaNotas {

	private static final String DOCUMENTO = "EXP-2026-014";

	private static final String RAZON = "Solicitud escrita del padre por motivos de fe";

	private Long exonerarComoDireccion(Long matricula, String area) {
		return como(EscenarioCobranza.DIRECCION, () -> exoneraciones.exonerar(new ExonerarRequest(matricula, area(area),
				DOCUMENTO, RAZON)));
	}

	/** E21: solo Dirección, con su firma. */
	@Test
	void soloDireccionConSuFirma() {
		assertThatThrownBy(() -> como(UsuariosDePrueba.autenticado(ana), () -> exoneraciones.exonerar(new ExonerarRequest(
				mateo, area("REL"), DOCUMENTO, RAZON)))).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> como(EscenarioCobranza.PROMOTORIA, () -> exoneraciones.exonerar(new ExonerarRequest(
				mateo, area("REL"), DOCUMENTO, RAZON)))).isInstanceOf(AccessDeniedException.class);
		exonerarComoDireccion(mateo, "REL");
		assertThat(contar("SELECT COUNT(*) FROM firma_operacion WHERE clave = ?", "exoneracion_area:" + mateo + ":"
				+ area("REL") + ":AUTORIZADA")).isEqualTo(1);
		assertThat(jdbc.queryForMap("SELECT autorizado_por, documento, desde_periodo_id FROM exoneracion_area"))
				.containsEntry("AUTORIZADO_POR", "director").containsEntry("DOCUMENTO", DOCUMENTO)
				.containsEntry("DESDE_PERIODO_ID", periodo);
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'AREA_EXONERADA'")).isEqualTo(1);
	}

	@Test
	void soloReligionOEducacionFisicaYUnaVez() {
		assertThatThrownBy(() -> exonerarComoDireccion(mateo, "MAT")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Solo Religión y Educación Física");
		exonerarComoDireccion(mateo, "EF");
		assertThatThrownBy(() -> exonerarComoDireccion(mateo, "EF")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya está exonerado");
	}

	/**
	 * A3 y E22: exonerado en el II bimestre, el I conserva sus notas; desde el II el área no se califica para ese alumno y la
	 * hoja lo marca. Sin un periodo abierto no se exonera.
	 */
	@Test
	void rigeDesdeElPeriodoAbiertoYConservaLasNotasAnteriores() {
		Usuario profeReligion = docente("rel.notas");
		asignar(profeReligion, TipoAsignacion.DOCENTE, List.of(area("REL")));
		guardar(profeReligion, "REL", hoja("REL", List.of(mateo, valeria), "A", null));
		Long primero = periodo;
		cerrarPeriodo();
		assertThatThrownBy(() -> exonerarComoDireccion(mateo, "REL")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("periodo abierto");
		Long segundo = como(EscenarioCobranza.DIRECCION, () -> periodos.programar(escuela.anio2026(),
				LocalDate.of(2026, 5, 18), LocalDate.of(2026, 7, 24)));
		como(EscenarioCobranza.DIRECCION, () -> periodos.abrir(segundo));
		hoyEs(LocalDate.of(2026, 5, 18));
		exonerarComoDireccion(mateo, "REL");

		HojaNotas delPrimero = como(UsuariosDePrueba.autenticado(profeReligion), () -> registro.hoja(seccion, area("REL"),
				primero, null));
		assertThat(delPrimero.filas()).filteredOn(f -> f.matriculaId().equals(mateo)).singleElement()
				.satisfies(f -> {
					assertThat(f.exonerado()).isFalse();
					assertThat(f.celdas()).allMatch(HojaNotas.Celda::conNota);
				});
		HojaNotas delSegundo = como(UsuariosDePrueba.autenticado(profeReligion), () -> registro.hoja(seccion, area("REL"),
				segundo, null));
		assertThat(delSegundo.filas()).filteredOn(f -> f.matriculaId().equals(mateo)).singleElement()
				.satisfies(f -> assertThat(f.exonerado()).isTrue());
		periodo = segundo;
		assertThatThrownBy(() -> guardar(profeReligion, "REL", hoja("REL", List.of(mateo), "A", null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("está exonerado");
		assertThat(guardar(profeReligion, "REL", hoja("REL", List.of(valeria), "A", null)).nuevas()).isPositive();
	}
}
