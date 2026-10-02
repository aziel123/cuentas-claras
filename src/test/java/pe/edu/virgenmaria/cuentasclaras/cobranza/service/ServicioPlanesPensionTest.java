package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PlanDetalle;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PlanRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.ResultadoGeneracion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION_2;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION_Y_ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA_Y_ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.plan;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.MOTIVO;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/** Planes de pensiones: Administración propone, otra persona de Promotoría o Dirección aprueba. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioPlanesPensionTest {

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private JdbcTemplate jdbc;

	private Estructura escuela;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		como(ADMINISTRACION);
		escuela = EscenarioEscolar.crearEstructura(estructura);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void administracionCreaBorradorYDireccionAprueba() {
		Long id = planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, plan(2027, "450", "350", null));
		assertThat(estado(id)).isEqualTo("BORRADOR");
		assertThat(jdbc.queryForObject("SELECT vigente FROM plan_pension WHERE id = ?", Boolean.class, id)).isNull();

		como(DIRECCION);
		PlanDetalle antes = planes.obtener(id);
		assertThat(antes.puedeAprobar()).isTrue();
		assertThat(antes.avisoAutor()).isNull();
		planes.aprobar(id);

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM plan_pension WHERE id = ?", id);
		assertThat(fila).containsEntry("estado", "APROBADO").containsEntry("vigente", true)
				.containsEntry("aprobado_por", "director").containsEntry("creado_por", "administracion");
		assertThat((BigDecimal) fila.get("monto_pension")).isEqualByComparingTo("450.00");
		assertThat(planes.obtener(id).puedeAprobar()).isFalse();
	}

	@Test
	void quienCreaOEditaElPlanNoPuedeAprobarloAunqueSeaDirector() {
		// Un usuario con Dirección y Administración (solo en sesión: ReglasSegregacion no permite crearlo).
		como(DIRECCION_Y_ADMINISTRACION);
		Long id = planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, plan(2027, "450", "350", null));
		assertThat(planes.obtener(id).avisoAutor()).contains("debe aprobarlo otra persona");
		assertThat(planes.obtener(id).puedeAprobar()).isFalse();

		assertThatThrownBy(() -> planes.aprobar(id)).isInstanceOf(AutoaprobacionException.class)
				.hasMessageContaining("No puedes aprobar un plan que tú creaste o editaste");
		// El intento queda en la bitácora aunque se lance la excepción (noRollbackFor), y el plan no cambia.
		assertThat(ultimoEvento(jdbc, "AUTOAPROBACION_RECHAZADA")).containsEntry("nombre_usuario", "subdirector")
				.containsEntry("entidad", "plan_pension").containsEntry("entidad_id", id.toString());
		assertThat(estado(id)).isEqualTo("BORRADOR");

		// Quien solo editó (Promotoría que también es Administración) tampoco.
		como(ADMINISTRACION);
		Long otro = planes.crearBorrador(escuela.anio2027(), Nivel.SECUNDARIA, plan(2027, "480", "350", null));
		como(PROMOTORIA_Y_ADMINISTRACION);
		planes.editarBorrador(otro, plan(2027, "485", "350", null));
		assertThatThrownBy(() -> planes.aprobar(otro)).isInstanceOf(AutoaprobacionException.class);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'AUTOAPROBACION_RECHAZADA'")).isEqualTo(2);
		assertThat(estado(otro)).isEqualTo("BORRADOR");

		// Y la base también lo impide: aprobado_por no puede ser el creador ni el último editor.
		assertThatThrownBy(() -> jdbc.update("UPDATE plan_pension SET estado = 'APROBADO', vigente = TRUE, "
				+ "aprobado_por = 'subdirector', aprobado_en = CURRENT_TIMESTAMP WHERE id = ?", id))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("UPDATE plan_pension SET estado = 'APROBADO', vigente = TRUE, "
				+ "aprobado_por = 'promotora.adm', aprobado_en = CURRENT_TIMESTAMP WHERE id = ?", otro))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void planAprobadoNoSeEdita() {
		Long id = aprobado(Nivel.PRIMARIA, "450");
		como(ADMINISTRACION);

		assertThatThrownBy(() -> planes.editarBorrador(id, plan(2027, "500", "350", null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("«Cambiar montos»");
		assertThatThrownBy(() -> planes.datosParaEditar(id)).isInstanceOf(ReglaNegocioException.class);
		como(DIRECCION);
		assertThatThrownBy(() -> planes.aprobar(id)).hasMessageContaining("Solo se aprueba un plan en borrador");
		assertThat(jdbc.queryForObject("SELECT monto_pension FROM plan_pension WHERE id = ?", BigDecimal.class, id))
				.isEqualByComparingTo("450.00");
	}

	@Test
	void cambiarMontoCreaNuevaVersionConMotivo() {
		Long v1 = aprobado(Nivel.PRIMARIA, "450");
		como(ADMINISTRACION_2);

		assertThatThrownBy(() -> planes.nuevaVersion(v1, "corto")).hasMessageContaining("entre 10 y 500");
		Long v2 = planes.nuevaVersion(v1, "Reajuste aprobado por la asamblea de padres");
		planes.editarBorrador(v2, plan(2027, "465", "350", null));

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM plan_pension WHERE id = ?", v2);
		assertThat(fila).containsEntry("numero_version", 2).containsEntry("estado", "BORRADOR")
				.containsEntry("motivo_cambio", "Reajuste aprobado por la asamblea de padres");
		assertThat(estado(v1)).isEqualTo("APROBADO");
		assertThat(planes.obtener(v2).historial()).extracting(h -> h.numeroVersion()).containsExactly(2, 1);
		// En la base, desde la versión 2 el motivo es obligatorio.
		assertThatThrownBy(() -> jdbc.update("UPDATE plan_pension SET motivo_cambio = NULL WHERE id = ?", v2))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aprobarNuevaVersionReemplazaLaAnteriorYNoTocaCuotasGeneradas() {
		Long seccion = escuela.primaria6A2027();
		alumnos.registrar(EscenarioEscolar.mateoConRosa(seccion));
		Long v1 = aprobado(Nivel.PRIMARIA, "450");
		assertThat(contar(jdbc, "cuota")).isEqualTo(11);
		como(ADMINISTRACION);
		Long v2 = planes.nuevaVersion(v1, "Reajuste aprobado por la asamblea de padres");
		planes.editarBorrador(v2, plan(2027, "465", "350", null));

		como(PROMOTORIA);
		ResultadoGeneracion resultado = planes.aprobar(v2);

		assertThat(estado(v1)).isEqualTo("REEMPLAZADO");
		assertThat(estado(v2)).isEqualTo("APROBADO");
		assertThat(jdbc.queryForObject("SELECT vigente FROM plan_pension WHERE id = ?", Boolean.class, v1)).isNull();
		assertThat(resultado.cuotas()).isZero();
		assertThat(jdbc.queryForList("SELECT DISTINCT monto FROM cuota WHERE tipo = 'PENSION'", BigDecimal.class))
				.singleElement().satisfies(m -> assertThat(m).isEqualByComparingTo("450.00"));
		assertThat(jdbc.queryForList("SELECT DISTINCT plan_pension_id FROM cuota", Long.class)).containsExactly(v1);
		assertThat(ultimoEvento(jdbc, "PLAN_PENSION_APROBADO").get("detalle").toString()).contains("Reemplaza a Plan Primaria 2027 v1");
	}

	@Test
	void soloUnBorradorPorAnioYNivel() {
		planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, plan(2027, "450", "350", null));

		assertThatThrownBy(() -> planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, plan(2027, "460", "350", null)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessage("Ya hay un borrador de Primaria 2027 por aprobar: edítalo o descártalo antes de proponer otro.");
		// Otro nivel u otro año sí.
		planes.crearBorrador(escuela.anio2027(), Nivel.SECUNDARIA, plan(2027, "480", "350", null));
		planes.crearBorrador(escuela.anio2026(), Nivel.PRIMARIA, plan(2026, "450", "350", null));
		assertThat(contar(jdbc, "plan_pension")).isEqualTo(3);
	}

	@Test
	void descartarExigeMotivoYQuedaEnElHistorial() {
		Long id = planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, plan(2027, "450", "350", null));
		assertThatThrownBy(() -> planes.descartar(id, "no")).hasMessageContaining("entre 10 y 500");

		planes.descartar(id, MOTIVO);

		assertThat(estado(id)).isEqualTo("DESCARTADO");
		assertThat(ultimoEvento(jdbc, "PLAN_PENSION_DESCARTADO").get("detalle").toString()).contains(MOTIVO);
		// Ya puede proponer otro: es la versión 2 (los números no se reutilizan) y explica por qué.
		Long otro = planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, plan(2027, "455", "350", null));
		assertThat(jdbc.queryForMap("SELECT numero_version, motivo_cambio FROM plan_pension WHERE id = ?", otro))
				.containsEntry("numero_version", 2)
				.containsEntry("motivo_cambio", "Nueva propuesta: la versión 1 se descartó.");
	}

	@Test
	void promotorRecibe403AlCrearBorrador() {
		como(PROMOTORIA);
		assertThatThrownBy(() -> planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, plan(2027, "450", "350", null)))
				.isInstanceOf(AuthorizationDeniedException.class);
		como(ADMINISTRACION);
		Long id = planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, plan(2027, "450", "350", null));
		// Administración no aprueba (ni siquiera lo ajeno).
		como(ADMINISTRACION_2);
		assertThatThrownBy(() -> planes.aprobar(id)).isInstanceOf(AuthorizationDeniedException.class);
		assertThat(contar(jdbc, "plan_pension WHERE estado = 'BORRADOR'")).isEqualTo(1);
	}

	@Test
	void aprobacionAuditaMontosYVencimientos() {
		Long id = aprobado(Nivel.PRIMARIA, "450");

		Map<String, Object> creado = ultimoEvento(jdbc, "PLAN_PENSION_CREADO");
		assertThat(creado).containsEntry("nombre_usuario", "administracion").containsEntry("entidad_id", id.toString());
		Map<String, Object> aprobado = ultimoEvento(jdbc, "PLAN_PENSION_APROBADO");
		assertThat(aprobado).containsEntry("nombre_usuario", "director");
		assertThat(aprobado.get("valor_nuevo").toString())
				.contains("Matrícula S/ 350.00 (vence 28/02/2027)")
				.contains("Pensión S/ 450.00 × 10: 31/03/2027, 30/04/2027")
				.contains("30/09/2027").contains("31/12/2027");
	}

	@Test
	void datosInvalidosNoCreanNada() {
		PlanRequest matriculaCara = plan(2027, "450", "451", null);
		assertThatThrownBy(() -> planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, matriculaCara))
				.hasMessageContaining("DS 005-2021-MINEDU");
		assertThat(contar(jdbc, "plan_pension")).isZero();
		assertThat(contar(jdbc, "evento_auditoria WHERE accion LIKE 'PLAN_PENSION%'")).isZero();
	}

	@Test
	void propuestaUsaLosMontosDelAnioAnterior() {
		como(ADMINISTRACION);
		Long id2026 = planes.crearBorrador(escuela.anio2026(), Nivel.PRIMARIA, plan(2026, "440", "340", null));
		como(DIRECCION);
		planes.aprobar(id2026);
		como(ADMINISTRACION);

		PlanRequest propuesta = planes.propuestaPorDefecto(escuela.anio2027(), Nivel.PRIMARIA);

		assertThat(propuesta.montoPension()).isEqualByComparingTo("440.00");
		assertThat(propuesta.montoMatricula()).isEqualByComparingTo("340.00");
		assertThat(propuesta.fechas()).hasSize(10).first().hasToString("2027-03-31");
		assertThat(planes.propuestaPorDefecto(escuela.anio2027(), Nivel.INICIAL).montoPension()).isNull();
	}

	private Long aprobado(Nivel nivel, String pension) {
		como(ADMINISTRACION);
		Long id = planes.crearBorrador(escuela.anio2027(), nivel, plan(2027, pension, "350", null));
		como(DIRECCION);
		planes.aprobar(id);
		return id;
	}

	private String estado(Long id) {
		return jdbc.queryForObject("SELECT estado FROM plan_pension WHERE id = ?", String.class, id);
	}
}
