package pe.edu.virgenmaria.cuentasclaras.matricula.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.matricula.dto.CampanaRenovacion;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion.RESPONDEN_HASTA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion.renovacionDe;

/**
 * Sprint 5, tanda 2 (decisiones 53 a 56; G16): la campaña de renovación propone el grado siguiente, no inventa deuda, y
 * toda respuesta registrada en persona se avisa a la familia.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioCampanaRenovacionTest {

	@Autowired
	private ServicioCampanaRenovacion campana;

	@Autowired
	private ServicioRenovacionFamilia familia;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioRenovacion.Datos d;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private void abrir() {
		d = EscenarioRenovacion.preparar(estructura, alumnos, planes, jdbc, true);
		como(ADMINISTRACION);
		assertThat(campana.abrir(d.anio2027(), RESPONDEN_HASTA)).isEqualTo(2);
	}

	@Test
	void proponeElGradoSiguienteYLaMismaLetra() {
		abrir();
		// Mateo (5.° B) va a 6.° B; Valeria (2.° B) a 3.° A, porque en 2027 no hay 3.° B.
		assertThat(jdbc.queryForMap("SELECT grado_destino, seccion_destino_id, estado, deuda_al_proponer FROM "
				+ "renovacion_matricula WHERE alumno_id = ?", d.mateo())).containsEntry("grado_destino", "PRIMARIA_6")
				.containsEntry("seccion_destino_id", d.p6B2027()).containsEntry("estado", "PROPUESTA");
		assertThat(jdbc.queryForObject("SELECT seccion_destino_id FROM renovacion_matricula WHERE alumno_id = ?",
				Long.class, d.valeria())).isEqualTo(d.p3A2027());
		// Sin respuesta no hay deuda: ninguna matrícula ni cuota de 2027.
		assertThat(contar(jdbc, "matricula WHERE anio_escolar_id = " + d.anio2027())).isZero();
		assertThat(contar(jdbc, "cuota WHERE anio_escolar_id = " + d.anio2027())).isZero();
		// Cada responsable recibe su invitación (solo el nombre de pila del alumno), y la campaña queda en la bitácora.
		List<Map<String, Object>> invitaciones = jdbc.queryForList("SELECT apoderado_id, parametros FROM mensaje "
				+ "WHERE tipo = 'RENOVACION_MATRICULA'");
		assertThat(invitaciones).hasSize(2).allSatisfy(m -> assertThat(m.get("apoderado_id")).isEqualTo(d.rosa()));
		assertThat(invitaciones).extracting(m -> (String) m.get("parametros"))
				.anySatisfy(p -> assertThat(p).startsWith("Mateo\n6.° Primaria\n2027\n31/01/2027"))
				.allSatisfy(p -> assertThat(p).doesNotContain("Quispe"));
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "RENOVACION_CAMPANA_ABIERTA")).containsEntry("valor_nuevo",
				"2 propuestas; responden hasta el 31/01/2027");
		// Abrirla de nuevo no duplica.
		assertThat(campana.abrir(d.anio2027(), RESPONDEN_HASTA)).isZero();
		CampanaRenovacion vista = campana.campana(d.anio2027());
		assertThat(vista.porResponder()).isEqualTo(2);
		assertThat(vista.filas()).extracting(CampanaRenovacion.Fila::seccion).containsExactlyInAnyOrder("A", "B");
	}

	@Test
	void quintoDeSecundariaNoSePropone() {
		abrir();
		assertThat(contar(jdbc, "renovacion_matricula WHERE alumno_id = " + d.sebastian())).isZero();
		assertThat(contar(jdbc, "mensaje WHERE apoderado_id = " + d.pedro())).isZero();
	}

	@Test
	void sinPlanAprobadoNoAbre() {
		d = EscenarioRenovacion.preparar(estructura, alumnos, planes, jdbc, false);
		como(ADMINISTRACION);
		assertThatThrownBy(() -> campana.abrir(d.anio2027(), RESPONDEN_HASTA))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("el plan de pensiones 2027 de Primaria aprobado");
		assertThat(contar(jdbc, "renovacion_matricula")).isZero();
		// Solo Administración abre la campaña.
		como(DIRECCION);
		assertThatThrownBy(() -> campana.abrir(d.anio2027(), RESPONDEN_HASTA)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void laRenovacionPresencialAvisaALaFamilia() {
		abrir();
		Long renovacion = renovacionDe(jdbc, d.mateo());
		campana.registrarPresencial(renovacion, true);
		SecurityContextHolder.clearContext();

		// G16: la familia se entera por sus dos canales de que Administración registró la renovación.
		List<Map<String, Object>> avisos = jdbc.queryForList("SELECT canal, parametros FROM mensaje "
				+ "WHERE tipo = 'RENOVACION_REGISTRADA' AND entidad_id = ? ORDER BY canal", renovacion);
		assertThat(avisos).extracting(m -> m.get("canal")).containsExactly("CORREO", "WHATSAPP");
		assertThat((String) avisos.getFirst().get("parametros")).startsWith("Mateo\ncontinúa en 6.° Primaria\n2027");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "RENOVACION_PRESENCIAL"))
				.containsEntry("nombre_usuario", "administracion").containsEntry("valor_nuevo", "CONFIRMADA");
		assertThat(pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria.RENOVACION_PRESENCIAL
				.requiereAtencion()).as("queda resaltada en la bitácora").isTrue();
		// Y después del commit, sistema.matricula reservó su matrícula con la cuota del plan.
		assertThat(jdbc.queryForMap("SELECT estado, canal_respuesta, respondido_por FROM renovacion_matricula WHERE id = ?",
				renovacion)).containsEntry("estado", "MATRICULADA").containsEntry("canal_respuesta", "PRESENCIAL")
				.containsEntry("respondido_por", "administracion");
	}

	@Test
	void direccionCambiaElDestinoSoloEnPropuesta() {
		abrir();
		Long renovacion = renovacionDe(jdbc, d.mateo());
		como(ADMINISTRACION);
		assertThatThrownBy(() -> campana.cambiarDestino(renovacion, d.p6A2027()))
				.isInstanceOf(AccessDeniedException.class);
		como(DIRECCION);
		campana.cambiarDestino(renovacion, d.p6A2027());
		assertThat(jdbc.queryForObject("SELECT seccion_destino_id FROM renovacion_matricula WHERE id = ?", Long.class,
				renovacion)).isEqualTo(d.p6A2027());
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "RENOVACION_DESTINO_CAMBIADO"))
				.containsEntry("valor_anterior", "6.° Primaria B").containsEntry("valor_nuevo", "6.° Primaria A");
		// Repitencia: Dirección lo deja en 5.° … pero solo si hay una sección de ese año.
		assertThatThrownBy(() -> campana.cambiarDestino(renovacion, null)).isInstanceOf(ReglaNegocioException.class);

		como(d.rosaEnLinea());
		familia.responder(renovacion, true);
		como(DIRECCION);
		assertThatThrownBy(() -> campana.cambiarDestino(renovacion, d.p6B2027()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya respondió");
	}
}
