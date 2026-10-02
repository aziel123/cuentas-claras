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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PlanRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.ResultadoGeneracion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Collections;
import java.time.LocalDate;
import java.util.ArrayList;
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

	@Autowired
	private AnioEscolarRepository anios;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private Clock reloj;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		como(ADMINISTRACION);
		escuela = EscenarioEscolar.crearEstructura(estructura);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/**
	 * Misma transacción de verdad: dentro de una transacción que luego se revierte, las cuotas ya existen al terminar la
	 * matrícula; al revertir, no queda ni la matrícula ni sus cuotas. (GeneracionTodoONadaTest prueba la falla.)
	 */
	@Test
	void matricularConPlanAprobadoGeneraElCronogramaEnLaMismaTransaccion() {
		planAprobado2027(Nivel.PRIMARIA, "450");

		Long dentro = new TransactionTemplate(transacciones).execute(estado -> {
			Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())).alumnoId();
			long cuotas = contar(jdbc, "cuota WHERE alumno_id = " + mateo);
			estado.setRollbackOnly();
			return cuotas;
		});

		assertThat(dentro).isEqualTo(11);
		assertThat(contar(jdbc, "cuota")).isZero();
		assertThat(contar(jdbc, "matricula")).isZero();
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())).alumnoId();
		List<Map<String, Object>> cuotas = jdbc.queryForList(
				"SELECT * FROM cuota WHERE alumno_id = ? ORDER BY fecha_vencimiento", mateo);
		assertThat(cuotas).hasSize(11);
		assertThat(cuotas.get(0)).containsEntry("tipo", "MATRICULA").containsEntry("clave",
				"MAT:" + cuotas.get(0).get("matricula_id")).containsEntry("estado", "PENDIENTE");
		assertThat(cuotas).extracting(c -> c.get("descripcion")).contains("Pensión setiembre 2027");
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

	/** Las dos generaciones esperan al mismo bloqueo del año (lo retiene un tercer hilo): se solapan de verdad. */
	@Test
	void dosGeneracionesConcurrentesNoDuplican() throws Exception {
		Long seccion = escuela.primaria6A2027();
		for (int i = 0; i < 8; i++) {
			alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("7100000" + i, "Ramos", "Paz", "Hijo " + (char) ('A' + i),
					LocalDate.of(2015, 3, 10), "4100000" + i, "Paz", "Lima", "Ana", "98765432" + i, null, seccion));
		}
		planAprobado2027(Nivel.PRIMARIA, "450");
		jdbc.update("DELETE FROM cuota");

		List<Integer> generadas = Collections.synchronizedList(new ArrayList<>());
		conElAnioBloqueado(escuela.anio2027(),
				() -> generadas.add(generador.generarPendientes(escuela.anio2027()).cuotas()),
				() -> generadas.add(generador.generarPendientes(escuela.anio2027()).cuotas()));

		assertThat(generadas).containsExactlyInAnyOrder(88, 0);
		assertThat(contar(jdbc, "cuota")).isEqualTo(88);
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT clave) FROM cuota", Long.class)).isEqualTo(88);
	}

	/** QA: una matrícula y la aprobación del plan al mismo tiempo; el alumno no puede quedar sin cronograma. */
	@Test
	void matriculaSimultaneaConAprobacionDelPlanRecibeSuCronograma() throws Exception {
		Long plan = planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, plan(2027, "450", "350", null));
		planes.enviar(plan);
		como(DIRECCION);
		Long version = planes.obtener(plan).version();
		como(ADMINISTRACION);

		conElAnioBloqueado(escuela.anio2027(),
				() -> alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())),
				() -> {
					UsuariosDePrueba.iniciarSesion(DIRECCION);
					planes.aprobar(plan, version);
				});

		assertThat(contar(jdbc, "cuota c JOIN alumno a ON a.id = c.alumno_id WHERE a.numero_documento = '"
				+ EscenarioEscolar.DNI_MATEO + "'")).isEqualTo(11);
	}

	/** QA: cambiar de nivel mientras se aprueba el plan no deja cuotas de un nivel que ya no es el suyo. */
	@Test
	void cambioDeNivelSimultaneoConAprobacionNoDejaCuotasDeOtroNivel() throws Exception {
		Long secundaria2027 = estructura.crearSeccion(escuela.anio2027(), new CrearSeccionRequest(Grado.SECUNDARIA_1, "A"));
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())).alumnoId();
		Long matricula = jdbc.queryForObject("SELECT id FROM matricula WHERE alumno_id = ?", Long.class, mateo);
		Long plan = planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, plan(2027, "450", "350", null));
		planes.enviar(plan);
		como(DIRECCION);
		Long version = planes.obtener(plan).version();
		como(ADMINISTRACION);

		List<Throwable> errores = Collections.synchronizedList(new ArrayList<>());
		conElAnioBloqueado(escuela.anio2027(),
				() -> {
					try {
						matriculas.cambiarSeccion(matricula, new CambiarSeccionRequest(secundaria2027, MOTIVO));
					}
					catch (ReglaNegocioException e) {
						errores.add(e);
					}
				},
				() -> {
					UsuariosDePrueba.iniciarSesion(DIRECCION);
					planes.aprobar(plan, version);
				});

		Long seccionFinal = jdbc.queryForObject("SELECT seccion_id FROM matricula WHERE id = ?", Long.class, matricula);
		long cuotasPrimaria = contar(jdbc, "cuota WHERE alumno_id = " + mateo);
		if (seccionFinal.equals(secundaria2027)) {
			assertThat(cuotasPrimaria).as("se movió a Secundaria: sin cuotas del plan de Primaria").isZero();
			assertThat(errores).isEmpty();
		}
		else {
			assertThat(cuotasPrimaria).isEqualTo(11);
			assertThat(errores).singleElement().asString().contains("ya tiene cuotas de Primaria");
		}
	}

	/**
	 * Un tercer hilo retiene el bloqueo del año mientras las dos acciones arrancan (con la sesión de Administración);
	 * luego lo suelta. Así las dos compiten por el mismo bloqueo, en cualquier orden.
	 */
	private void conElAnioBloqueado(Long anioId, Runnable primera, Runnable segunda) throws Exception {
		CountDownLatch bloqueado = new CountDownLatch(1);
		CountDownLatch arrancaron = new CountDownLatch(2);
		ExecutorService hilos = Executors.newFixedThreadPool(3);
		try {
			Future<?> retenedor = hilos.submit(() -> {
				UsuariosDePrueba.iniciarSesion(ADMINISTRACION);
				try {
					new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
						anios.bloquear(anioId);
						bloqueado.countDown();
						try {
							arrancaron.await(10, TimeUnit.SECONDS);
							Thread.sleep(300);
						}
						catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
					});
				}
				finally {
					SecurityContextHolder.clearContext();
				}
				return null;
			});
			bloqueado.await(10, TimeUnit.SECONDS);
			List<Future<?>> acciones = new ArrayList<>();
			for (Runnable accion : List.of(primera, segunda)) {
				acciones.add(hilos.submit(() -> {
					UsuariosDePrueba.iniciarSesion(ADMINISTRACION);
					try {
						arrancaron.countDown();
						accion.run();
					}
					finally {
						SecurityContextHolder.clearContext();
					}
					return null;
				}));
			}
			retenedor.get(60, TimeUnit.SECONDS);
			for (Future<?> accion : acciones) {
				accion.get(60, TimeUnit.SECONDS);
			}
		}
		finally {
			hilos.shutdownNow();
		}
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

	/**
	 * Una deuda que ya está en el cronograma (aquí, la matrícula 2026 cargada como saldo inicial) no se vuelve a
	 * generar, y la omisión queda en la bitácora resaltada, por alumno (auditoría C1: nunca en silencio).
	 */
	@Test
	void noGeneraUnaDeudaYaCargadaComoSaldoInicialYLoAudita() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026())).alumnoId();
		como(ADMINISTRACION);
		Long lote = saldoInicial.crearLote(new LoteRequest(escuela.anio2026(), LocalDate.of(2026, 9, 30),
				"Informe del contador 001", new BigDecimal("350.00")));
		saldoInicial.agregarLinea(lote, new LineaSaldoRequest(EscenarioEscolar.DNI_MATEO, ConceptoSaldo.MATRICULA, 2026,
				null, null, new BigDecimal("350.00"), LocalDate.of(2026, 2, 28)));
		saldoInicial.enviar(lote);
		como(PROMOTORIA);
		EscenarioCobranza.confirmar(saldoInicial, lote, "350.00");
		// Plan 2026 que cobra desde octubre, con la matrícula venciendo en octubre: la generaría otra vez.
		como(ADMINISTRACION);
		Long plan = planes.crearBorrador(escuela.anio2026(), Nivel.PRIMARIA, new PlanRequest(new BigDecimal("350"),
				LocalDate.of(2026, 10, 15), new BigDecimal("450"), Calendario.vencimientosPorDefecto(2026, 3, 10),
				LocalDate.of(2026, 10, 1)));
		como(DIRECCION);
		ResultadoGeneracion resultado = EscenarioCobranza.aprobar(planes, plan);

		assertThat(resultado.cuotas()).isEqualTo(3);
		assertThat(resultado.omitidas()).singleElement().asString().contains("Matrícula 2026");
		assertThat(jdbc.queryForList("SELECT tipo FROM cuota WHERE alumno_id = ? AND obligacion = 'MAT-2026'",
				String.class, mateo)).containsExactly("SALDO_INICIAL");
		Map<String, Object> evento = ultimoEvento(jdbc, "CUOTA_OMITIDA_DEUDA_EXISTENTE");
		assertThat(evento.get("detalle").toString()).contains("Mateo Quispe Huamán").contains("Matrícula 2026")
				.contains("Saldo inicial");
		assertThat(AccionAuditoria.CUOTA_OMITIDA_DEUDA_EXISTENTE.requiereAtencion()).isTrue();
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
		EscenarioCobranza.aprobar(planes, plan);
		assertThat(contar(jdbc, "cuota WHERE alumno_id = " + mateo)).isEqualTo(1);

		como(ADMINISTRACION);
		assertThatThrownBy(() -> matriculas.cambiarSeccion(matricula,
				new CambiarSeccionRequest(escuela.secundaria1A2026(), MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya tiene cuotas de Primaria").hasMessageContaining("cambiaría su pensión");
		// Dentro del mismo nivel sí.
		matriculas.cambiarSeccion(matricula, new CambiarSeccionRequest(escuela.primaria5B2026(), MOTIVO));
	}

	/** QA (mutación): basta una cuota de matrícula o de saldo inicial para que no cambie de nivel. */
	@Test
	void cambioDeNivelConSoloMatriculaOSaldoInicialEsRechazado() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026())).alumnoId();
		Long matricula = jdbc.queryForObject("SELECT id FROM matricula WHERE alumno_id = ?", Long.class, mateo);
		como(ADMINISTRACION);
		Long lote = saldoInicial.crearLote(new LoteRequest(escuela.anio2026(), LocalDate.of(2026, 9, 30),
				"Informe del contador 001", new BigDecimal("120.00")));
		saldoInicial.agregarLinea(lote, new LineaSaldoRequest(EscenarioEscolar.DNI_MATEO, ConceptoSaldo.OTRO, null, null,
				"Excursión", new BigDecimal("120.00"), LocalDate.of(2026, 5, 10)));
		saldoInicial.enviar(lote);
		como(PROMOTORIA);
		EscenarioCobranza.confirmar(saldoInicial, lote, "120.00");
		como(ADMINISTRACION);
		assertThatThrownBy(() -> matriculas.cambiarSeccion(matricula,
				new CambiarSeccionRequest(escuela.secundaria1A2026(), MOTIVO))).hasMessageContaining("ya tiene cuotas");

		// Solo con la cuota de matrícula (sin pensiones), también.
		Long valeria = alumnos.registrar(EscenarioEscolar.valeriaConRosaRegistrada(escuela.primaria2B2026())).alumnoId();
		como(ADMINISTRACION);
		Long plan = planes.crearBorrador(escuela.anio2026(), Nivel.PRIMARIA, new PlanRequest(new BigDecimal("350"),
				LocalDate.of(2026, 10, 15), new BigDecimal("450"), Calendario.vencimientosPorDefecto(2026, 3, 10),
				LocalDate.of(2026, 10, 1)));
		como(DIRECCION);
		EscenarioCobranza.aprobar(planes, plan);
		jdbc.update("DELETE FROM cuota WHERE alumno_id = ? AND tipo = 'PENSION'", valeria);
		assertThat(jdbc.queryForList("SELECT tipo FROM cuota WHERE alumno_id = ?", String.class, valeria))
				.containsExactly("MATRICULA");
		Long matriculaValeria = jdbc.queryForObject("SELECT id FROM matricula WHERE alumno_id = ?", Long.class, valeria);
		como(ADMINISTRACION);
		assertThatThrownBy(() -> matriculas.cambiarSeccion(matriculaValeria,
				new CambiarSeccionRequest(escuela.secundaria1A2026(), MOTIVO))).hasMessageContaining("ya tiene cuotas");
	}

	@Test
	void matricula2026SoloGeneraDesdeElCorte() {
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026())).alumnoId();
		como(ADMINISTRACION);
		Long id = planes.crearBorrador(escuela.anio2026(), Nivel.PRIMARIA, plan(2026, "450", "350",
				LocalDate.of(2026, 12, 1)));
		como(DIRECCION);
		EscenarioCobranza.aprobar(planes, id);

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
		ResultadoGeneracion resultado = EscenarioCobranza.aprobar(planes, id);
		como(ADMINISTRACION);
		return resultado;
	}
}
