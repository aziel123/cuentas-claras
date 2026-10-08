package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/** Planes, cuotas y lotes de un colegio no existen para otro: ni por los servicios ni en la base (FK compuestas). */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AislamientoCobranzaTest {

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioCronograma cronogramas;

	@Autowired
	private ServicioSaldoInicial saldo;

	@Autowired
	private GeneradorCronograma generador;

	@Autowired
	private ServicioAnulacionCuotas anulaciones;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private JdbcTemplate jdbc;

	private Estructura escuelaA;

	private Long mateoA;

	private Long planA;

	private Long loteA;

	private long colegioB;

	private UsuarioAutenticado directorB;

	private UsuarioAutenticado administracionB;

	private Long anioB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		como(ADMINISTRACION);
		escuelaA = EscenarioEscolar.crearEstructura(estructura);
		mateoA = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuelaA.primaria6A2027())).alumnoId();
		planA = EscenarioCobranza.planAprobado(planes, escuelaA.anio2027(), 2027, Nivel.PRIMARIA, "450", "350", null);
		loteA = saldo.crearLote(new LoteRequest(escuelaA.anio2026(), LocalDate.of(2026, 9, 30), "Informe A",
				new BigDecimal("450.00")));

		colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		directorB = UsuariosDePrueba.autenticado(colegioB, 90L, "director.b", "Director B", false, EnumSet.of(Rol.DIRECTOR));
		administracionB = UsuariosDePrueba.autenticado(colegioB, 91L, "administracion.b", "Administración B", false,
				EnumSet.of(Rol.ADMINISTRACION));
		como(administracionB);
		anioB = estructura.crearAnio(new CrearAnioEscolarRequest(2027, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 12, 17),
				false));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void colegioBNoVePlanesCuotasNiLotesDelA() {
		como(directorB);
		assertThat(planes.resumen(null).anio().id()).isEqualTo(anioB);
		assertThat(planes.resumen(null).niveles()).allSatisfy(n -> {
			assertThat(n.vigente()).isNull();
			assertThat(n.borradores()).isEmpty();
		});
		assertThat(planes.resumen(null).cuotasGeneradas()).isZero();
		assertThatThrownBy(() -> planes.obtener(planA)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> planes.resumen(escuelaA.anio2027())).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> cronogramas.deAlumno(mateoA)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(saldo.listar()).isEmpty();
		assertThatThrownBy(() -> saldo.obtener(loteA)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void colegioBNoApruebaNiGeneraNiAnulaEnElA() {
		como(ADMINISTRACION);
		Long borrador = planes.crearBorrador(escuelaA.anio2027(), Nivel.SECUNDARIA,
				EscenarioCobranza.plan(2027, "480", "350", null));
		Long cuotaA = jdbc.queryForObject("SELECT MIN(id) FROM cuota", Long.class);

		como(directorB);
		assertThatThrownBy(() -> planes.aprobar(borrador, 0L)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> generador.generarPendientes(escuelaA.anio2027()))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> anulaciones.solicitar(cuotaA, "Intento desde otro colegio"))
				.isInstanceOf(RecursoNoEncontradoException.class);
		como(administracionB);
		assertThatThrownBy(() -> saldo.agregarLinea(loteA, new LineaSaldoRequest(EscenarioEscolar.DNI_MATEO,
				ConceptoSaldo.PENSION, 2026, 9, null, BigDecimal.TEN, null))).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> saldo.crearLote(new LoteRequest(escuelaA.anio2026(), LocalDate.of(2026, 9, 30),
				"Informe", BigDecimal.TEN))).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> planes.crearBorrador(escuelaA.anio2027(), Nivel.PRIMARIA,
				EscenarioCobranza.plan(2027, "1", "0", null))).isInstanceOf(RecursoNoEncontradoException.class);

		assertThat(jdbc.queryForObject("SELECT estado FROM plan_pension WHERE id = ?", String.class, borrador))
				.isEqualTo("BORRADOR");
		assertThat(jdbc.queryForObject("SELECT anulacion_solicitada_por FROM cuota WHERE id = ?", String.class, cuotaA))
				.isNull();
	}

	@Test
	void unLoteDelBNoEncuentraAlumnosDelA() {
		como(administracionB);
		Long loteB = saldo.crearLote(new LoteRequest(anioB, LocalDate.of(2026, 9, 30), "Informe B", BigDecimal.TEN));
		assertThatThrownBy(() -> saldo.agregarLinea(loteB, new LineaSaldoRequest(EscenarioEscolar.DNI_MATEO,
				ConceptoSaldo.OTRO, null, null, "Excursión", BigDecimal.TEN, LocalDate.of(2026, 9, 1))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("No hay un alumno con el documento");
	}

	@Test
	void laBaseRechazaCuotaDeAlumnoDeOtroColegio() {
		Long matriculaA = jdbc.queryForObject("SELECT id FROM matricula WHERE alumno_id = ?", Long.class, mateoA);
		// Una cuota del colegio B que apunta al alumno, la matrícula y el plan del A: las FK compuestas lo impiden.
		assertThatThrownBy(() -> jdbc.update("INSERT INTO cuota (colegio_id, alumno_id, anio_escolar_id, matricula_id, "
				+ "plan_pension_id, tipo, numero, descripcion, monto, fecha_vencimiento, estado, clave, obligacion, "
				+ "creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, 'PENSION', 9, 'Pensión setiembre 2027', "
				+ "450.00, DATE '2027-09-30', 'PENDIENTE', 'X:1', 'PEN-2027-09', CURRENT_TIMESTAMP, 'prueba', "
				+ "CURRENT_TIMESTAMP)", colegioB, mateoA, anioB, matriculaA, planA))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("INSERT INTO plan_pension (colegio_id, anio_escolar_id, nivel, "
				+ "numero_version, estado, monto_matricula, vencimiento_matricula, monto_pension, vencimientos_pension, "
				+ "editado_por, creado_en, creado_por, actualizado_en) VALUES (?, ?, 'PRIMARIA', 1, 'BORRADOR', 0, "
				+ "DATE '2027-02-28', 450, '2027-03-31', 'x', CURRENT_TIMESTAMP, 'x', CURRENT_TIMESTAMP)", colegioB,
				escuelaA.anio2027())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("INSERT INTO linea_saldo_inicial (colegio_id, lote_id, alumno_id, concepto, "
				+ "descripcion, monto, fecha_vencimiento, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, 'OTRO', "
				+ "'x', 1, DATE '2026-09-01', CURRENT_TIMESTAMP, 'x', CURRENT_TIMESTAMP)", loteA, alumnoDelB()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Long alumnoDelB() {
		como(administracionB);
		Long id = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("70000001", "Torres", "Lima", "Andrés",
				LocalDate.of(2015, 2, 1), "40000001", "Lima", "Paz", "Gloria", "999888777", null, null)).alumnoId();
		como(ADMINISTRACION);
		return id;
	}
}
