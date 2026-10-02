package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CambiarSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.MatricularRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RetirarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioMatriculas;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.ResultadoGeneracion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.plan;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.MOTIVO;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/** Generación de cronogramas: automática, idempotente, serializada y auditada. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class GeneradorCronogramaTest {

	@Autowired
	private GeneradorCronograma generador;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioSaldoInicial saldoInicial;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioMatriculas matriculas;

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
	void matricularConPlanAprobadoGeneraElCronogramaEnLaMismaTransaccion() {
		planAprobado2027(Nivel.PRIMARIA, "450");

		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())).alumnoId();

		List<Map<String, Object>> cuotas = jdbc.queryForList(
				"SELECT * FROM cuota WHERE alumno_id = ? ORDER BY fecha_vencimiento", mateo);
		assertThat(cuotas).hasSize(11);
		assertThat(cuotas.get(0)).containsEntry("tipo", "MATRICULA").containsEntry("clave",
				"MAT:" + cuotas.get(0).get("matricula_id")).containsEntry("estado", "PENDIENTE");
		assertThat(cuotas).extracting(c -> c.get("descripcion")).contains("Pensión setiembre 2027");
		// La misma transacción: la matrícula y sus cuotas tienen la misma marca de tiempo de creación.
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT c.creado_en) FROM cuota c JOIN matricula m "
				+ "ON m.id = c.matricula_id WHERE m.creado_en = c.creado_en", Long.class)).isEqualTo(1);
	}

	@Test
	void siNoHayPlanAprobadoLaMatriculaQuedaPendiente() {
		alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027()));
		assertThat(contar(jdbc, "cuota")).isZero();
		assertThat(contar(jdbc, "matricula")).isEqualTo(1);
	}

	@Test
	void generarDosVecesNoDuplica() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())).alumnoId();
		ResultadoGeneracion alAprobar = planAprobado2027(Nivel.PRIMARIA, "450");
		assertThat(alAprobar.matriculas()).isEqualTo(1);
		assertThat(alAprobar.cuotas()).isEqualTo(11);
		assertThat(alAprobar.total()).isEqualByComparingTo("4850.00");

		como(ADMINISTRACION);
		ResultadoGeneracion primera = generador.generarPendientes(escuela.anio2027());
		ResultadoGeneracion segunda = generador.generarPendientes(escuela.anio2027());

		assertThat(primera.cuotas()).isZero();
		assertThat(segunda.cuotas()).isZero();
		assertThat(contar(jdbc, "cuota WHERE alumno_id = " + mateo)).isEqualTo(11);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'CRONOGRAMA_GENERADO'")).isEqualTo(1);
	}

	@Test
	void generarPendientesCompletaLasMatriculasSinCronograma() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())).alumnoId();
		planAprobado2027(Nivel.PRIMARIA, "450");
		// Simula una matrícula que quedó sin cronograma (por ejemplo, de antes de este módulo).
		jdbc.update("DELETE FROM cuota");

		como(ADMINISTRACION);
		ResultadoGeneracion resultado = generador.generarPendientes(escuela.anio2027());

		assertThat(resultado.matriculas()).isEqualTo(1);
		assertThat(contar(jdbc, "cuota WHERE alumno_id = " + mateo)).isEqualTo(11);
		como(PROMOTORIA);
		assertThatThrownBy(() -> generador.generarPendientes(escuela.anio2027()))
				.isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void dosGeneracionesConcurrentesNoDuplican() throws Exception {
		Long seccion = escuela.primaria6A2027();
		for (int i = 0; i < 5; i++) {
			alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("7100000" + i, "Ramos", "Paz", "Hijo " + (char) ('A' + i),
					LocalDate.of(2015, 3, 10), "4100000" + i, "Paz", "Lima", "Ana", "98765432" + i, null, seccion));
		}
		planAprobado2027(Nivel.PRIMARIA, "450");
		jdbc.update("DELETE FROM cuota");

		CountDownLatch salida = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			Callable<ResultadoGeneracion> generar = () -> {
				UsuariosDePrueba.iniciarSesion(ADMINISTRACION);
				try {
					salida.await();
					return generador.generarPendientes(escuela.anio2027());
				}
				finally {
					SecurityContextHolder.clearContext();
				}
			};
			Future<ResultadoGeneracion> a = hilos.submit(generar);
			Future<ResultadoGeneracion> b = hilos.submit(generar);
			salida.countDown();
			int generadas = a.get(60, TimeUnit.SECONDS).cuotas() + b.get(60, TimeUnit.SECONDS).cuotas();

			assertThat(generadas).isEqualTo(55);
		}
		finally {
			hilos.shutdownNow();
		}
		assertThat(contar(jdbc, "cuota")).isEqualTo(55);
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT clave) FROM cuota", Long.class)).isEqualTo(55);
	}

	@Test
	void aprobarPlanGeneraLosPendientesDelNivel() {
		Long secundaria2027 = estructura.crearSeccion(escuela.anio2027(), new CrearSeccionRequest(Grado.SECUNDARIA_1, "A"));
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())).alumnoId();
		Long diego = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("76902114", "Castillo", "Vargas", "Diego",
				LocalDate.of(2014, 5, 30), "40917735", "Castillo", "Ruiz", "Martín", "955201774", null, secundaria2027))
				.alumnoId();

		planAprobado2027(Nivel.PRIMARIA, "450");

		assertThat(contar(jdbc, "cuota WHERE alumno_id = " + mateo)).isEqualTo(11);
		assertThat(contar(jdbc, "cuota WHERE alumno_id = " + diego)).isZero();
		planAprobado2027(Nivel.SECUNDARIA, "480");
		assertThat(jdbc.queryForObject("SELECT SUM(monto) FROM cuota WHERE alumno_id = ?", BigDecimal.class, diego))
				.isEqualByComparingTo("5150.00");
	}

	@Test
	void alumnoRetiradoNoRecibeCuotas() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())).alumnoId();
		alumnos.retirar(mateo, new RetirarAlumnoRequest(LocalDate.of(2026, 10, 1), MOTIVO));

		ResultadoGeneracion resultado = planAprobado2027(Nivel.PRIMARIA, "450");

		assertThat(resultado.matriculas()).isZero();
		assertThat(contar(jdbc, "cuota")).isZero();
	}

	@Test
	void noGeneraUnaPensionYaCargadaComoSaldoInicial() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())).alumnoId();
		como(ADMINISTRACION);
		Long lote = saldoInicial.crearLote(new LoteRequest(escuela.anio2027(), LocalDate.of(2026, 9, 30),
				"Informe del contador 001", new BigDecimal("450.00")));
		saldoInicial.agregarLinea(lote, new LineaSaldoRequest(EscenarioEscolar.DNI_MATEO, ConceptoSaldo.PENSION, 9, null,
				new BigDecimal("450.00"), null));
		saldoInicial.enviar(lote);
		como(PROMOTORIA);
		saldoInicial.confirmar(lote);

		ResultadoGeneracion resultado = planAprobado2027(Nivel.PRIMARIA, "450");

		assertThat(resultado.cuotas()).isEqualTo(10);
		assertThat(resultado.omitidas()).singleElement().asString().contains("Pensión setiembre 2027");
		assertThat(jdbc.queryForList("SELECT tipo FROM cuota WHERE alumno_id = ? AND obligacion = 'PEN-2027-09'",
				String.class, mateo)).containsExactly("SALDO_INICIAL");
		assertThat(contar(jdbc, "cuota WHERE alumno_id = " + mateo)).isEqualTo(11);
	}

	@Test
	void cadaCronogramaQuedaAuditadoConMontosYVencimientos() {
		alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027()));
		planAprobado2027(Nivel.PRIMARIA, "450");

		Map<String, Object> evento = ultimoEvento(jdbc, "CRONOGRAMA_GENERADO");
		assertThat(evento.get("entidad")).isEqualTo("matricula");
		assertThat(evento.get("nombre_usuario")).isEqualTo("director");
		assertThat(evento.get("valor_nuevo").toString())
				.contains("Matrícula 2027 S/ 350.00 vence 28/02/2027")
				.contains("Pensión setiembre 2027 S/ 450.00 vence 30/09/2027");
		assertThat(evento.get("detalle").toString()).contains("Mateo Quispe Huamán").contains("Plan Primaria 2027 v1")
				.contains("11 cuotas por S/ 4,850.00");
	}

	@Test
	void cambioDeNivelConCuotasGeneradasEsRechazado() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026())).alumnoId();
		Long matricula = jdbc.queryForObject("SELECT id FROM matricula WHERE alumno_id = ?", Long.class, mateo);
		// Sin cuotas, cambiar de nivel se permite (corrección de un error de carga).
		matriculas.cambiarSeccion(matricula, new CambiarSeccionRequest(escuela.secundaria1A2026(), MOTIVO));
		matriculas.cambiarSeccion(matricula, new CambiarSeccionRequest(escuela.primaria5A2026(), MOTIVO));
		como(ADMINISTRACION);
		Long plan = planes.crearBorrador(escuela.anio2026(), Nivel.PRIMARIA, plan(2026, "450", "350",
				LocalDate.of(2026, 12, 1)));
		como(DIRECCION);
		planes.aprobar(plan);
		assertThat(contar(jdbc, "cuota WHERE alumno_id = " + mateo)).isEqualTo(1);

		como(ADMINISTRACION);
		assertThatThrownBy(() -> matriculas.cambiarSeccion(matricula,
				new CambiarSeccionRequest(escuela.secundaria1A2026(), MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya tiene cuotas de Primaria").hasMessageContaining("cambiaría su pensión");
		// Dentro del mismo nivel sí.
		matriculas.cambiarSeccion(matricula, new CambiarSeccionRequest(escuela.primaria5B2026(), MOTIVO));
	}

	@Test
	void matricula2026SoloGeneraDesdeElCorte() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026())).alumnoId();
		como(ADMINISTRACION);
		Long id = planes.crearBorrador(escuela.anio2026(), Nivel.PRIMARIA, plan(2026, "450", "350",
				LocalDate.of(2026, 12, 1)));
		como(DIRECCION);
		planes.aprobar(id);

		assertThat(jdbc.queryForList("SELECT descripcion FROM cuota WHERE alumno_id = ?", String.class, mateo))
				.containsExactly("Pensión diciembre 2026");
	}

	@Test
	void matriculaEnOtroAnioTieneSuPropioCronograma() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026())).alumnoId();
		planAprobado2027(Nivel.PRIMARIA, "450");
		assertThat(contar(jdbc, "cuota")).isZero();
		como(ADMINISTRACION);
		matriculas.matricular(mateo, new MatricularRequest(escuela.primaria6A2027(), null));
		assertThat(contar(jdbc, "cuota WHERE alumno_id = " + mateo)).isEqualTo(11);
	}

	private ResultadoGeneracion planAprobado2027(Nivel nivel, String pension) {
		como(ADMINISTRACION);
		Long id = planes.crearBorrador(escuela.anio2027(), nivel, plan(2027, pension, "350", null));
		como(DIRECCION);
		ResultadoGeneracion resultado = planes.aprobar(id);
		como(ADMINISTRACION);
		return resultado;
	}
}
