package pe.edu.virgenmaria.cuentasclaras.academico.notas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.AlertasNotas;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10, tanda 2 (A5, E26 y sección 4 del brief): toda corrección aprobada que SUBE el nivel o deja SIN nota alerta a
 * Promotoría (sin umbral), y también quien aprueba más de 5 en un periodo; Dirección recibe NOTAS_PENDIENTES desde 3 días
 * antes del fin y ASIGNACIONES_SIN_AREAS al abrir el periodo y desde 7 días antes del fin, con los nombres de las secciones.
 */
class AlertasNotasTest extends PruebaNotas {

	private static final String CONCLUSION = "Necesita acompañamiento en la resolución de problemas con fracciones.";

	@Autowired
	private AlertasNotas alertasNotas;

	private List<AlertaRevision> alertas() {
		return como(EscenarioCobranza.PROMOTORIA, () -> alertasNotas.alertas());
	}

	private List<AlertaRevision> de(TipoAviso tipo) {
		return alertas().stream().filter(a -> a.aviso() != null && a.aviso().tipo() == tipo).toList();
	}

	/** Dirección pide la corrección de Mateo en la competencia i y Promotoría la aprueba. */
	private Long corregir(int competencia, String nivel, String conclusion) {
		Long nota = vigente(mateo, competencias("MAT").get(competencia));
		como(EscenarioCobranza.DIRECCION, () -> correcciones.pedir(nota, nivel, conclusion, MOTIVO_CORRECCION));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "calificacion", nota);
		SecurityContextHolder.clearContext();
		return vigente(mateo, competencias("MAT").get(competencia));
	}

	@Test
	void subirElNivelSiempreAlerta() {
		anaPoneA();
		cerrarPeriodo();
		Long nueva = corregir(0, "AD", null);
		assertThat(de(TipoAviso.CORRECCIONES_NOTAS)).singleElement().satisfies(a -> {
			assertThat(a.aviso().referencia()).isEqualTo("NOTA:" + nueva);
			assertThat(a.texto()).contains("A → AD").contains("Subió el nivel");
			assertThat(a.aviso().excluidos()).as("quien aprobó no se avisa a sí mismo").containsExactly("promotor");
		});
	}

	@Test
	void dejarSinNotaAlerta() {
		anaPoneA();
		cerrarPeriodo();
		corregir(1, null, null);
		assertThat(de(TipoAviso.CORRECCIONES_NOTAS)).singleElement()
				.satisfies(a -> assertThat(a.texto()).contains("A → sin nota").contains("Quedó sin nota"));
	}

	@Test
	void bajarElNivelNoAlertaPorSiSolo() {
		anaPoneA();
		cerrarPeriodo();
		corregir(2, "B", CONCLUSION);
		assertThat(de(TipoAviso.CORRECCIONES_NOTAS)).isEmpty();
	}

	/** E26: más de 5 correcciones aprobadas por la misma persona en un periodo alertan aunque bajen el nivel. */
	@Test
	void masDeCincoCorreccionesPorPersonaAlertan() {
		guardar(ana, "MAT", hoja("MAT", List.of(mateo, valeria), "A", null));
		cerrarPeriodo();
		int hechas = 0;
		for (Long m : List.of(mateo, valeria)) {
			for (Long c : competencias("MAT").subList(0, 3)) {
				Long nota = vigente(m, c);
				como(EscenarioCobranza.DIRECCION, () -> correcciones.pedir(nota, "B", CONCLUSION, MOTIVO_CORRECCION));
				EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "calificacion", nota);
				SecurityContextHolder.clearContext();
				hechas++;
			}
		}
		assertThat(hechas).isEqualTo(6);
		assertThat(de(TipoAviso.CORRECCIONES_NOTAS)).singleElement()
				.satisfies(a -> assertThat(a.texto()).contains("promotor aprobó 6 correcciones"));
	}

	@Test
	void notasPendientesTresDiasAntesDelFin() {
		anaPoneA();
		hoyEs(FIN.minusDays(4));
		assertThat(de(TipoAviso.NOTAS_PENDIENTES)).isEmpty();
		hoyEs(FIN.minusDays(3));
		assertThat(de(TipoAviso.NOTAS_PENDIENTES)).singleElement().satisfies(a -> {
			assertThat(a.aviso().tipo().soloDireccion()).isTrue();
			assertThat(a.aviso().referencia()).isEqualTo("NOTPEND:" + periodo);
			assertThat(a.texto()).contains("faltan").doesNotContain("Mateo", "Quispe");
		});
	}

	@Test
	void asignacionesSinAreasAlAbrirYSieteDiasAntesDelFin() {
		Usuario delSprint9 = docente("pepe.sprint9");
		Long id = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(new ProponerAsignacionRequest(
				escuela.primaria5B2026(), delSprint9.getId(), TipoAsignacion.DOCENTE, "Matemática", MOTIVO)));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", id);
		SecurityContextHolder.clearContext();
		String seccion5B = como(EscenarioCobranza.DIRECCION, () -> avance.avance(null)).sinAreas().getFirst().seccion();

		assertThat(de(TipoAviso.ASIGNACIONES_SIN_AREAS)).as("la semana en que abre").singleElement().satisfies(a -> {
			assertThat(a.aviso().referencia()).isEqualTo("SINAREAS:" + periodo + ":A");
			assertThat(a.aviso().dato()).isEqualTo("1");
			assertThat(a.texto()).contains(seccion5B);
		});
		hoyEs(LocalDate.of(2026, 4, 1));
		assertThat(de(TipoAviso.ASIGNACIONES_SIN_AREAS)).as("a mitad del periodo").isEmpty();
		hoyEs(FIN.minusDays(7));
		assertThat(de(TipoAviso.ASIGNACIONES_SIN_AREAS)).singleElement()
				.satisfies(a -> assertThat(a.aviso().referencia()).isEqualTo("SINAREAS:" + periodo + ":F"));
		assertThat(como(UsuariosDePrueba.autenticado(delSprint9), () -> registro.mias()).sinAreas()).hasSize(1);
	}
}
