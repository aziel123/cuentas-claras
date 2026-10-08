package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.MatricularRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioMatriculas;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.MOTIVO;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/**
 * Solicitudes que tocan dinero (auditoría A3 y anulación de cuotas): el ingreso tardío y la anulación los pide una
 * persona y los aprueba otra en la bandeja; mientras tanto, se cobra el cronograma completo.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class SolicitudesCobranzaTest {

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioAnulacionCuotas anulaciones;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioMatriculas matriculas;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	private Estructura escuela;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		como(ADMINISTRACION);
		escuela = EscenarioEscolar.crearEstructura(estructura);
		EscenarioCobranza.planAprobado(planes, escuela.anio2026(), 2026, Nivel.PRIMARIA, "450", "350", null);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/**
	 * A3: antes, una fecha de matrícula «tardía» (setiembre) puesta por una sola persona borraba del cronograma las
	 * pensiones de marzo a agosto (S/ 2,700 por alumno).
	 */
	@Test
	void ingresoTardioSeCobraCompletoHastaQueOtraPersonaLoApruebe() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(null)).alumnoId();

		matriculas.matricular(mateo, new MatricularRequest(escuela.primaria5A2026(), LocalDate.of(2026, 9, 15)));

		assertThat(jdbc.queryForObject("SELECT fecha_matricula FROM matricula WHERE alumno_id = ?", LocalDate.class,
				mateo)).isEqualTo(LocalDate.of(2026, 3, 2));
		assertThat(pensionesVigentes(mateo)).isEqualTo(10);
		Map<String, Object> solicitud = jdbc.queryForMap("SELECT * FROM solicitud_cambio");
		assertThat(solicitud).containsEntry("tipo", "FECHA_MATRICULA").containsEntry("entidad", "matricula")
				.containsEntry("solicitado_por", "administracion");
		assertThat((String) solicitud.get("resumen")).contains("desde el 15/09/2026", "inicio de clases es el 02/03/2026");

		como(PROMOTORIA);
		List<SolicitudVista> tardios = bandeja.bandeja().ingresosTardios();
		assertThat(tardios).singleElement().extracting(SolicitudVista::estado).isEqualTo("PENDIENTE");
		bandeja.aprobar(((Number) solicitud.get("id")).longValue(), "Constancia de traslado del 14/09/2026");

		assertThat(jdbc.queryForObject("SELECT fecha_matricula FROM matricula WHERE alumno_id = ?", LocalDate.class,
				mateo)).isEqualTo(LocalDate.of(2026, 9, 15));
		// Se anulan marzo a agosto (6), con quien lo pidió y quien lo aprobó; setiembre a diciembre se cobran.
		List<Map<String, Object>> anuladas = jdbc.queryForList("SELECT obligacion, anulacion_solicitada_por, "
				+ "anulacion_aprobada_por, numero FROM cuota WHERE alumno_id = ? AND estado = 'ANULADA' ORDER BY numero",
				mateo);
		assertThat(anuladas).hasSize(6).allSatisfy(c -> assertThat(c).containsEntry("anulacion_solicitada_por",
				"administracion").containsEntry("anulacion_aprobada_por", "promotor").containsEntry("obligacion", null));
		assertThat(anuladas).extracting(c -> c.get("numero")).containsExactly(3, 4, 5, 6, 7, 8);
		assertThat(pensionesVigentes(mateo)).isEqualTo(4);
		// La matrícula no se toca (su vencimiento es inmutable).
		assertThat(contar(jdbc, "cuota WHERE tipo = 'MATRICULA' AND estado <> 'ANULADA'")).isEqualTo(1);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'CUOTA_ANULADA'")).isEqualTo(6);
		assertThat((String) ultimoEvento(jdbc, "MATRICULA_FECHA_CAMBIADA").get("detalle"))
				.contains("Pedido por administracion, aprobado por promotor");
	}

	@Test
	void ingresoTardioRechazadoConservaElCronogramaCompleto() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(null)).alumnoId();
		matriculas.matricular(mateo, new MatricularRequest(escuela.primaria5A2026(), LocalDate.of(2026, 9, 15)));
		Long id = jdbc.queryForObject("SELECT id FROM solicitud_cambio", Long.class);

		como(DIRECCION);
		bandeja.rechazar(id, "No presentó la constancia de traslado");

		assertThat(jdbc.queryForObject("SELECT fecha_matricula FROM matricula WHERE alumno_id = ?", LocalDate.class,
				mateo)).isEqualTo(LocalDate.of(2026, 3, 2));
		assertThat(pensionesVigentes(mateo)).isEqualTo(10);
		assertThat(contar(jdbc, "cuota WHERE estado = 'ANULADA'")).isZero();
	}

	@Test
	void ingresoTardioFuturoEsRechazadoYUnaFechaNoTardiaNoCreaSolicitud() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(null)).alumnoId();

		assertThatThrownBy(() -> matriculas.matricular(mateo, new MatricularRequest(escuela.primaria5A2026(),
				LocalDate.of(2026, 11, 2)))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("futura");
		matriculas.matricular(mateo, new MatricularRequest(escuela.primaria5A2026(), null));

		assertThat(jdbc.queryForObject("SELECT fecha_matricula FROM matricula WHERE alumno_id = ?", LocalDate.class,
				mateo)).isEqualTo(LocalDate.of(2026, 3, 2));
		assertThat(contar(jdbc, "solicitud_cambio")).isZero();
	}

	@Test
	void anulacionDeCuotaSeApruebaEnLaBandejaPorOtraPersona() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026())).alumnoId();
		Long setiembre = jdbc.queryForObject("SELECT id FROM cuota WHERE alumno_id = ? AND obligacion = 'PEN-2026-09'",
				Long.class, mateo);

		anulaciones.solicitar(setiembre, MOTIVO);
		Long id = jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'ANULACION_CUOTA'", Long.class);
		assertThat(jdbc.queryForObject("SELECT estado FROM cuota WHERE id = ?", String.class, setiembre))
				.isEqualTo("PENDIENTE");

		como(DIRECCION);
		bandeja.aprobar(id, null);

		assertThat(jdbc.queryForMap("SELECT estado, anulacion_solicitada_por, anulacion_aprobada_por FROM cuota "
				+ "WHERE id = ?", setiembre)).containsEntry("estado", "ANULADA")
				.containsEntry("anulacion_solicitada_por", "administracion").containsEntry("anulacion_aprobada_por", "director");
		assertThat((String) ultimoEvento(jdbc, "CUOTA_ANULADA").get("detalle"))
				.contains("Pedido por administracion, aprobado por director");
	}

	@Test
	void anulacionRechazadaDejaLaCuotaSinMarca() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026())).alumnoId();
		Long setiembre = jdbc.queryForObject("SELECT id FROM cuota WHERE alumno_id = ? AND obligacion = 'PEN-2026-09'",
				Long.class, mateo);
		anulaciones.solicitar(setiembre, MOTIVO);
		Long id = jdbc.queryForObject("SELECT id FROM solicitud_cambio", Long.class);

		como(PROMOTORIA);
		bandeja.rechazar(id, "La familia sí debe setiembre: asistió todo el mes");

		assertThat(jdbc.queryForMap("SELECT estado, anulacion_solicitada_por, anulacion_motivo FROM cuota WHERE id = ?",
				setiembre)).containsEntry("estado", "PENDIENTE").containsEntry("anulacion_solicitada_por", null)
				.containsEntry("anulacion_motivo", null);
		// Se puede volver a pedir.
		como(ADMINISTRACION);
		anulaciones.solicitar(setiembre, MOTIVO);
		assertThat(contar(jdbc, "solicitud_cambio WHERE estado = 'PENDIENTE'")).isEqualTo(1);
	}

	private long pensionesVigentes(Long alumno) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM cuota WHERE alumno_id = ? AND tipo = 'PENSION' "
				+ "AND estado <> 'ANULADA'", Long.class, alumno);
	}
}
