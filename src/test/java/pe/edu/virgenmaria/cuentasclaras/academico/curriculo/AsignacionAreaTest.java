package pe.edu.virgenmaria.cuentasclaras.academico.curriculo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.academico.api.SeccionesDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AsignacionesVista;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 10, tanda 1 (sección 3.4; A2, E24 y E25): las áreas del catálogo viajan en la propuesta de asignación (en la misma
 * transacción, con sus ids ordenados en la solicitud) y se ven en la bandeja; se aprueban con ella. El auxiliar y una
 * cobertura no llevan áreas; un área de otro nivel o la transversal, tampoco. Cambiar las áreas de una vigente es proponer
 * otra igual: al aprobarla, la anterior termina con la firma de quien aprueba. Las vigentes del sprint 9 sin áreas del
 * catálogo salen en «Asignaciones sin áreas del catálogo».
 */
class AsignacionAreaTest extends PruebaCurriculo {

	private static final String MOTIVO = "Carga horaria 2026 aprobada por Dirección";

	@Autowired
	private Asignaciones asignaciones;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private SeccionesDocente seccionesDocente;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	private Usuario docente;

	private Usuario auxiliar;

	@BeforeEach
	void prepararDocentes() {
		docente = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "ana.areas", UsuariosDePrueba.CLAVE, false, Rol.DOCENTE);
		auxiliar = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "luis.auxiliar", UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE);
	}

	private Long area(Nivel nivel, String codigo) {
		return catalogoComo(nivel).stream().filter(a -> a.codigo().equals(codigo)).findFirst().map(AreaDelCatalogo::id)
				.orElseThrow();
	}

	private List<AreaDelCatalogo> catalogoComo(Nivel nivel) {
		return como(EscenarioCobranza.DIRECCION, () -> catalogo.areasDe(version(), nivel));
	}

	private Long proponer(Long seccion, Usuario quien, TipoAsignacion tipo, List<Long> areas) {
		return como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(ProponerAsignacionRequest.conAreas(seccion,
				quien.getId(), tipo, MOTIVO, areas)));
	}

	private void aprobar(Long asignacion) {
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", asignacion);
		SecurityContextHolder.clearContext();
	}

	private List<Long> areasDe(Long asignacion) {
		return jdbc.queryForList("SELECT area_id FROM asignacion_area WHERE asignacion_id = ? ORDER BY area_id", Long.class,
				asignacion);
	}

	@Test
	void lasAreasViajanEnLaPropuestaYSeVenEnLaBandeja() {
		configurar2026();
		Long matematica = area(Nivel.PRIMARIA, "MAT");
		Long ciencia = area(Nivel.PRIMARIA, "CYT");
		Long id = proponer(escuela.primaria5A2026(), docente, TipoAsignacion.DOCENTE, List.of(ciencia, matematica));
		assertThat(areasDe(id)).containsExactly(Math.min(matematica, ciencia), Math.max(matematica, ciencia));
		String datos = jdbc.queryForObject("SELECT s.datos FROM solicitud_cambio s JOIN asignacion_docente a ON "
				+ "a.solicitud_id = s.id WHERE a.id = ?", String.class, id);
		assertThat(datos).contains("\"areas\":\"" + Math.min(matematica, ciencia) + "," + Math.max(matematica, ciencia)
				+ "\"");
		Long solicitud = jdbc.queryForObject("SELECT solicitud_id FROM asignacion_docente WHERE id = ?", Long.class, id);
		SolicitudVista vista = como(EscenarioCobranza.DIRECCION, () -> bandeja.detalle(solicitud));
		assertThat(vista.resumen()).contains("Matemática", "Ciencia y Tecnología");
		assertThat(vista.detalle()).anyMatch(l -> l.startsWith("Áreas que calificará: ") && l.contains("Matemática"));

		aprobar(id);
		assertThat(jdbc.queryForObject("SELECT estado FROM asignacion_docente WHERE id = ?", String.class, id))
				.isEqualTo("VIGENTE");
		List<Long> suyas = como(UsuariosDePrueba.autenticado(docente), () -> seccionesDocente.areasDe(
				escuela.primaria5A2026()));
		assertThat(suyas).containsExactlyInAnyOrder(matematica, ciencia);
		assertThat(como(UsuariosDePrueba.autenticado(docente), () -> seccionesDocente.areasDe(escuela.primaria5B2026())))
				.isEmpty();
	}

	/** E25: el auxiliar y una cobertura no califican: no llevan áreas. */
	@Test
	void elAuxiliarYUnaCoberturaNoLlevanAreas() {
		configurar2026();
		Long matematica = area(Nivel.PRIMARIA, "MAT");
		assertThatThrownBy(() -> proponer(escuela.primaria5A2026(), auxiliar, TipoAsignacion.AUXILIAR, List.of(matematica)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no llevan áreas");
		hoyEs(LocalDate.of(2026, 3, 3));
		assertThatThrownBy(() -> como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(
				new ProponerAsignacionRequest(escuela.primaria5A2026(), docente.getId(), TipoAsignacion.DOCENTE, null, MOTIVO,
						LocalDate.of(2026, 3, 5), List.of(matematica), false))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no llevan áreas");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_docente", Long.class)).isZero();
	}

	@Test
	void unAreaDeOtroNivelOLaTransversalNoSeAsignan() {
		Long matematica = area(Nivel.PRIMARIA, "MAT");
		assertThatThrownBy(() -> proponer(escuela.primaria5A2026(), docente, TipoAsignacion.DOCENTE, List.of(matematica)))
				.as("sin la evaluación del año").isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("guarda primero la evaluación");
		configurar2026();
		assertThatThrownBy(() -> proponer(escuela.primaria5A2026(), docente, TipoAsignacion.DOCENTE,
				List.of(area(Nivel.SECUNDARIA, "MAT")))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("áreas de Primaria");
		assertThatThrownBy(() -> proponer(escuela.primaria5A2026(), docente, TipoAsignacion.TUTOR,
				List.of(area(Nivel.PRIMARIA, "TRV")))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("transversales");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_area", Long.class)).isZero();
	}

	@Test
	void todasLasAreasDelGradoParaElTutorDePrimaria() {
		configurar2026();
		Long id = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(new ProponerAsignacionRequest(
				escuela.primaria5A2026(), docente.getId(), TipoAsignacion.TUTOR, null, MOTIVO, null, List.of(), true)));
		assertThat(areasDe(id)).hasSize(9);
		assertThat(areasDe(id)).doesNotContain(area(Nivel.PRIMARIA, "TRV"));
		assertThatThrownBy(() -> como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(
				new ProponerAsignacionRequest(escuela.secundaria1A2026(), docente.getId(), TipoAsignacion.TUTOR, null, MOTIVO,
						null, List.of(), true)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Inicial o Primaria");
	}

	/**
	 * E24 y sección 3.4: las áreas de una vigente no se agregan; se propone otra igual con las nuevas y, al aprobarla, la
	 * anterior termina con la firma de quien aprueba (en la misma transacción) y la nueva queda VIGENTE.
	 */
	@Test
	void aprobarOtraConLasMismasClavesTerminaLaAnterior() {
		configurar2026();
		Long matematica = area(Nivel.PRIMARIA, "MAT");
		Long ciencia = area(Nivel.PRIMARIA, "CYT");
		Long anterior = proponer(escuela.primaria5A2026(), docente, TipoAsignacion.TUTOR, List.of(matematica));
		aprobar(anterior);
		assertThatThrownBy(() -> proponer(escuela.primaria5A2026(), docente, TipoAsignacion.TUTOR, List.of(matematica)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("con esas mismas áreas");
		Long nueva = proponer(escuela.primaria5A2026(), docente, TipoAsignacion.TUTOR, List.of(matematica, ciencia));
		assertThat(jdbc.queryForObject("SELECT estado FROM asignacion_docente WHERE id = ?", String.class, anterior))
				.as("hasta aprobar la nueva, la anterior sigue").isEqualTo("VIGENTE");
		Long solicitud = jdbc.queryForObject("SELECT solicitud_id FROM asignacion_docente WHERE id = ?", Long.class, nueva);
		assertThat(como(EscenarioCobranza.DIRECCION, () -> bandeja.detalle(solicitud)).detalle())
				.anyMatch(l -> l.startsWith("Reemplaza su asignación vigente"));
		aprobar(nueva);
		assertThat(jdbc.queryForMap("SELECT estado, terminada_por, motivo_fin FROM asignacion_docente WHERE id = ?",
				anterior)).containsEntry("ESTADO", "TERMINADA").containsEntry("TERMINADA_POR", "director")
				.satisfies(f -> assertThat(f.get("MOTIVO_FIN").toString()).contains("Reemplazada por la asignación " + nueva));
		assertThat(jdbc.queryForObject("SELECT estado FROM asignacion_docente WHERE id = ?", String.class, nueva))
				.isEqualTo("VIGENTE");
		assertThat(jdbc.queryForList("SELECT clave FROM firma_operacion WHERE clave = ?", String.class,
				"asignacion_docente:" + anterior + ":TERMINADA")).hasSize(1);
		assertThat(como(UsuariosDePrueba.autenticado(docente), () -> seccionesDocente.areasDe(escuela.primaria5A2026())))
				.containsExactlyInAnyOrder(matematica, ciencia);
	}

	/** Las vigentes del sprint 9 (con el texto libre de entonces) salen en «Asignaciones sin áreas del catálogo». */
	@Test
	void lasDelSprint9SinAreasDelCatalogoSeListan() {
		Long delSprint9 = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(new ProponerAsignacionRequest(
				escuela.primaria5A2026(), docente.getId(), TipoAsignacion.DOCENTE, "Matemática", MOTIVO)));
		aprobar(delSprint9);
		Long aux = proponer(escuela.primaria5A2026(), auxiliar, TipoAsignacion.AUXILIAR, List.of());
		aprobar(aux);
		AsignacionesVista vista = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.vista(escuela.anio2026()));
		assertThat(vista.sinAreas()).singleElement().satisfies(a -> {
			assertThat(a.id()).isEqualTo(delSprint9);
			assertThat(a.areasTexto()).isEqualTo("Matemática");
		});
		assertThat(vista.evaluacionConfigurada()).isFalse();
		configurar2026();
		AsignacionesVista configurada = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.vista(escuela.anio2026()));
		assertThat(configurada.evaluacionConfigurada()).isTrue();
		assertThat(configurada.areasDelAnio().stream().map(g -> g.nivel() + ":" + g.areas().size())
				.collect(Collectors.toList())).containsExactly("INICIAL:6", "PRIMARIA:9", "SECUNDARIA:11");
	}
}
