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
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteDetalle;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TotalNoCoincideException;
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
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION_2;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION_Y_ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA_Y_ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.DNI_MATEO;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.DNI_VALERIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.MOTIVO;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/**
 * Saldo inicial: total de control, doble control (también en la base), confirmación a ciegas y todo o nada. Incluye
 * los ataques de la auditoría antifraude (C1 y A2): deudas que bloquean cuotas futuras, deudas inventadas o duplicadas.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioSaldoInicialTest {

	private static final LocalDate CORTE = LocalDate.of(2026, 9, 30);

	@Autowired
	private ServicioSaldoInicial saldo;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

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
		alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		alumnos.registrar(EscenarioEscolar.valeriaConRosaRegistrada(escuela.primaria2B2026()));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void administracionCreaLoteYAgregaLineas() {
		Long lote = lote("900.00");
		saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00"));
		saldo.agregarLinea(lote, otro(" 80127745 ", "taller de verano", "450"));

		LoteDetalle detalle = saldo.obtener(lote);
		assertThat(detalle.estado()).isEqualTo("BORRADOR");
		assertThat(detalle.cuadra()).isTrue();
		assertThat(detalle.puedeEnviar()).isTrue();
		assertThat(detalle.lineas()).extracting(l -> l.descripcion())
				.containsExactly("Pensión setiembre 2026", "Taller de verano");
		assertThat(detalle.lineas().get(0).vencimiento()).isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(detalle.lineas().get(0).cronograma()).isEqualTo("2026: sin cuotas");
		assertThat(jdbc.queryForObject("SELECT creado_por FROM lote_saldo_inicial WHERE id = ?", String.class, lote))
				.isEqualTo("administracion");
		assertThat(jdbc.queryForList("SELECT anio_deuda FROM linea_saldo_inicial ORDER BY id", Integer.class))
				.containsExactly(2026, null);
		assertThatThrownBy(() -> saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00")))
				.hasMessageContaining("ya tiene en el lote la deuda «Pensión setiembre 2026»");
		assertThatThrownBy(() -> saldo.agregarLinea(lote, pension("99999999", 9, "450.00")))
				.hasMessageContaining("No hay un alumno con el documento «99999999»");
	}

	/** QA y auditoría C1: «Pensión diciembre 2025» en un lote 2026 ocupaba PEN-2026-12 y bloqueaba diciembre 2026. */
	@Test
	void pensionDelAnioAnteriorEnSaldoInicialNoBloqueaLaPensionDelMismoMesDelAnioActual() {
		Long lote = lote("450.00");

		assertThatThrownBy(() -> saldo.agregarLinea(lote, new LineaSaldoRequest(DNI_MATEO, ConceptoSaldo.PENSION, 2025,
				12, null, new BigDecimal("450.00"), LocalDate.of(2025, 12, 31))))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessage("La deuda es de 2025 y el lote es de 2026: cada deuda va en un lote de su propio año.");
		assertThat(contar(jdbc, "linea_saldo_inicial")).isZero();

		// Y diciembre 2026 se genera con el plan.
		EscenarioCobranza.planAprobado(planes, escuela.anio2026(), 2026, Nivel.PRIMARIA, "450", "350",
				LocalDate.of(2026, 12, 1));
		assertThat(jdbc.queryForList("SELECT obligacion FROM cuota", String.class)).contains("PEN-2026-12");
	}

	/** Auditoría C1: deudas de meses posteriores al corte bloqueaban las pensiones de esos meses. */
	@Test
	void deudasPosterioresAlCorteNoSonSaldoInicial() {
		Long lote = lote("450.00");

		assertThatThrownBy(() -> saldo.agregarLinea(lote, pension(DNI_MATEO, 10, "450.00")))
				.hasMessageContaining("es posterior al corte (30/09/2026)");
		assertThatThrownBy(() -> saldo.agregarLinea(lote, new LineaSaldoRequest(DNI_MATEO, ConceptoSaldo.PENSION, 2026,
				12, null, new BigDecimal("450.00"), LocalDate.of(2026, 9, 1))))
				.hasMessageContaining("es posterior al corte");
		assertThatThrownBy(() -> saldo.agregarLinea(lote, new LineaSaldoRequest(DNI_MATEO, ConceptoSaldo.PENSION, 2026,
				9, null, new BigDecimal("450.00"), LocalDate.of(2026, 8, 31))))
				.hasMessage("La pensión de setiembre debe vencer en ese mismo mes.");
		assertThatThrownBy(() -> saldo.agregarLinea(lote, otro(DNI_MATEO, "Uniforme escolar", "450.00",
				LocalDate.of(2026, 10, 15)))).hasMessageContaining("después del corte");
		assertThatThrownBy(() -> saldo.agregarLinea(lote, new LineaSaldoRequest(DNI_MATEO, ConceptoSaldo.MATRICULA, 2026,
				null, null, new BigDecimal("350.00"), LocalDate.of(2026, 12, 1)))).hasMessageContaining("después del corte");
		// Un lote de 2027 con corte en 2026 no admite pensiones de 2027 (marzo a mayo bloqueaban el plan 2027).
		Long lote2027 = saldo.crearLote(new LoteRequest(escuela.anio2027(), CORTE, "Informe", new BigDecimal("450.00")));
		assertThatThrownBy(() -> saldo.agregarLinea(lote2027, new LineaSaldoRequest(DNI_MATEO, ConceptoSaldo.PENSION,
				2027, 3, null, new BigDecimal("450.00"), null))).isInstanceOf(ReglaNegocioException.class);
		assertThat(contar(jdbc, "linea_saldo_inicial")).isZero();
	}

	/** Auditoría A2 (b): una pensión disfrazada de «otro concepto», o un concepto que no está en el reglamento. */
	@Test
	void otroConceptoSoloDelReglamentoYNuncaUnaPension() {
		Long lote = lote("450.00");
		for (String disfraz : new String[] { "Pensión setiembre", "Mensualidad", "Matrícula atrasada", "Cuota de agosto" }) {
			assertThatThrownBy(() -> saldo.agregarLinea(lote, otro(DNI_MATEO, disfraz, "450.00")))
					.as(disfraz).hasMessageContaining("parece una pensión o una matrícula");
		}
		assertThatThrownBy(() -> saldo.agregarLinea(lote, otro(DNI_MATEO, "Donación voluntaria", "450.00")))
				.hasMessageContaining("no está entre los otros conceptos del reglamento");
		assertThat(contar(jdbc, "linea_saldo_inicial")).isZero();
	}

	/** Auditoría A2 (c): deudas de alumnos retirados o sin matrícula en el año. */
	@Test
	void alumnoRetiradoOSinMatriculaNoRecibeSaldoInicial() {
		Long lote = lote("450.00");
		alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("71112223", "Sin", "Matrícula", "Pía", LocalDate.of(2015, 1, 5),
				"41112223", "Mamá", "De", "Pía", "987111333", null, null));
		assertThatThrownBy(() -> saldo.agregarLinea(lote, pension("71112223", 9, "450.00")))
				.hasMessage("Pía Sin Matrícula no tiene una matrícula activa en 2026: no se le carga una deuda de ese año.");
		jdbc.update("UPDATE alumno SET estado = 'RETIRADO', retirado_en = DATE '2026-08-01', retirado_por = 'x', "
				+ "motivo_retiro = 'Se mudó de ciudad con su familia' WHERE numero_documento = ?", DNI_VALERIA);
		assertThatThrownBy(() -> saldo.agregarLinea(lote, pension(DNI_VALERIA, 9, "450.00")))
				.hasMessageContaining("está retirado");
	}

	/** QA: una deuda OTRO sin obligación se podía cobrar dos veces con dos lotes. */
	@Test
	void dosLotesConLaMismaDeudaOtroNoDuplicanCobro() {
		Long primero = enviadoCon(otro(DNI_MATEO, "Taller de verano", "120.00"), "120.00");
		como(PROMOTORIA);
		confirmar(saldo, primero, "120.00");
		como(ADMINISTRACION);
		Long segundo = lote("120.00");

		assertThatThrownBy(() -> saldo.agregarLinea(segundo, otro(DNI_MATEO, "TALLER DE VERANO", "120.00")))
				.hasMessageContaining("ya tiene la cuota «Taller de verano» en su cronograma");
		assertThat(jdbc.queryForObject("SELECT obligacion FROM cuota", String.class)).startsWith("OT-2026-");
	}

	@Test
	void fechaDeCorteFuturaEsRechazada() {
		assertThatThrownBy(() -> saldo.crearLote(new LoteRequest(escuela.anio2026(), LocalDate.of(2026, 12, 31),
				"Informe del contador 001", new BigDecimal("100.00")))).hasMessage("La fecha de corte no puede ser futura.");
		assertThat(contar(jdbc, "lote_saldo_inicial")).isZero();
	}

	@Test
	void noSeEnviaSiElTotalNoCuadra() {
		Long lote = lote("500.00");
		saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00"));

		assertThat(saldo.obtener(lote).puedeEnviar()).isFalse();
		assertThatThrownBy(() -> saldo.enviar(lote)).isInstanceOf(ReglaNegocioException.class)
				.hasMessage("No cuadra: la suma de las líneas es S/ 450.00 y el total declarado es S/ 500.00. Revisa las "
						+ "líneas contra el informe del contador.");
		assertThat(estado(lote)).isEqualTo("BORRADOR");
	}

	@Test
	void noSeEnviaUnLoteVacio() {
		Long lote = lote("450.00");
		assertThatThrownBy(() -> saldo.enviar(lote)).hasMessageContaining("no tiene líneas");
		Long linea = saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00"));
		saldo.quitarLinea(lote, linea, MOTIVO);
		assertThatThrownBy(() -> saldo.enviar(lote)).hasMessageContaining("no tiene líneas");
	}

	@Test
	void loteEnviadoNoAdmiteCambios() {
		Long lote = enviado();

		assertThatThrownBy(() -> saldo.agregarLinea(lote, pension(DNI_VALERIA, 9, "450.00")))
				.hasMessageContaining("no admite cambios");
		Long linea = jdbc.queryForObject("SELECT MIN(id) FROM linea_saldo_inicial", Long.class);
		assertThatThrownBy(() -> saldo.quitarLinea(lote, linea, MOTIVO)).hasMessageContaining("no admite cambios");
		assertThatThrownBy(() -> saldo.descartar(lote, MOTIVO)).hasMessageContaining("no admite cambios");
		assertThatThrownBy(() -> saldo.enviar(lote)).hasMessageContaining("no admite cambios");
	}

	@Test
	void quienCreaEnviaOAgregoLineasNoPuedeConfirmar() {
		como(DIRECCION_Y_ADMINISTRACION);
		Long propio = lote("450.00");
		saldo.agregarLinea(propio, pension(DNI_MATEO, 9, "450.00"));
		saldo.enviar(propio);
		assertThat(saldo.obtener(propio).puedeConfirmar()).isFalse();
		assertThat(saldo.obtener(propio).avisoConfirmacion()).contains("Tú participaste");
		assertThatThrownBy(() -> confirmar(saldo, propio, "450.00")).isInstanceOf(AutoaprobacionException.class);
		assertThat(ultimoEvento(jdbc, "AUTOAPROBACION_RECHAZADA")).containsEntry("nombre_usuario", "subdirector")
				.containsEntry("entidad", "lote_saldo_inicial");
		assertThat(estado(propio)).isEqualTo("ENVIADO");

		como(ADMINISTRACION);
		Long ajeno = lote("900.00");
		saldo.agregarLinea(ajeno, pension(DNI_MATEO, 8, "450.00"));
		como(PROMOTORIA_Y_ADMINISTRACION);
		saldo.agregarLinea(ajeno, pension(DNI_VALERIA, 8, "450.00"));
		como(ADMINISTRACION);
		saldo.enviar(ajeno);
		como(PROMOTORIA_Y_ADMINISTRACION);
		assertThatThrownBy(() -> confirmar(saldo, ajeno, "900.00")).isInstanceOf(AutoaprobacionException.class);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'AUTOAPROBACION_RECHAZADA'")).isEqualTo(2);
		assertThat(contar(jdbc, "cuota")).isZero();
	}

	@Test
	void laBaseRechazaConfirmacionDelCreadorOSinTotalConfirmado() {
		Long lote = enviado();
		assertThatThrownBy(() -> jdbc.update("UPDATE lote_saldo_inicial SET estado = 'CONFIRMADO', total_confirmado = "
				+ "total_declarado, confirmado_por = 'administracion', confirmado_en = CURRENT_TIMESTAMP WHERE id = ?", lote))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("UPDATE lote_saldo_inicial SET estado = 'CONFIRMADO' WHERE id = ?", lote))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("UPDATE lote_saldo_inicial SET estado = 'CONFIRMADO', confirmado_por = "
				+ "'promotor', confirmado_en = CURRENT_TIMESTAMP WHERE id = ?", lote))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("UPDATE lote_saldo_inicial SET estado = 'CONFIRMADO', total_confirmado = 1, "
				+ "confirmado_por = 'promotor', confirmado_en = CURRENT_TIMESTAMP WHERE id = ?", lote))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("UPDATE lote_saldo_inicial SET enviado_por = NULL WHERE id = ?", lote))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	/** Auditoría A2 (a): quien confirma escribe a ciegas el total del informe; si no coincide, no se confirma. */
	@Test
	void confirmacionConElTotalDelInformeACiegas() {
		Long lote = enviado();
		como(PROMOTORIA);
		LoteDetalle vista = saldo.obtener(lote);
		assertThat(vista.mostrarTotales()).isFalse();
		assertThat(vista.totalDeclarado()).isNull();
		assertThat(vista.totalLineas()).isNull();

		assertThatThrownBy(() -> confirmar(saldo, lote, "540.00")).isInstanceOf(TotalNoCoincideException.class);
		assertThatThrownBy(() -> saldo.confirmar(lote, vista.version(), null)).isInstanceOf(TotalNoCoincideException.class);
		assertThat(estado(lote)).isEqualTo("ENVIADO");
		assertThat(contar(jdbc, "cuota")).isZero();
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'SALDO_INICIAL_TOTAL_NO_COINCIDE'")).isEqualTo(2);

		assertThat(confirmar(saldo, lote, "450")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT total_confirmado FROM lote_saldo_inicial WHERE id = ?", BigDecimal.class,
				lote)).isEqualByComparingTo("450.00");
		como(ADMINISTRACION);
		assertThat(saldo.obtener(lote).totalDeclarado()).isEqualByComparingTo("450.00");
	}

	/** Auditoría A1, igual que en los planes: se confirma la versión que se vio. */
	@Test
	void siElLoteCambioDesdeQueSeAbrioNoSeConfirma() {
		Long lote = enviado();
		como(PROMOTORIA);
		Long vista = saldo.obtener(lote).version();
		como(DIRECCION);
		saldo.devolver(lote, vista, "Falta una deuda de Valeria en el lote");
		como(ADMINISTRACION);
		Long linea = jdbc.queryForObject("SELECT MIN(id) FROM linea_saldo_inicial", Long.class);
		saldo.quitarLinea(lote, linea, "Se reemplaza por la correcta");
		saldo.agregarLinea(lote, otro(DNI_VALERIA, "Excursión", "450.00"));
		saldo.enviar(lote);

		como(PROMOTORIA);
		assertThatThrownBy(() -> saldo.confirmar(lote, vista, new BigDecimal("450.00")))
				.hasMessage("El lote cambió desde que lo abriste; revísalo de nuevo.");
		assertThat(confirmar(saldo, lote, "450.00")).isEqualTo(1);
	}

	@Test
	void promotoriaConfirmaYSeCreanCuotasDeSaldoInicial() {
		Long lote = enviado();
		como(PROMOTORIA);

		assertThat(saldo.obtener(lote).puedeConfirmar()).isTrue();
		assertThat(confirmar(saldo, lote, "450.00")).isEqualTo(1);

		Map<String, Object> cuota = jdbc.queryForMap("SELECT * FROM cuota");
		assertThat(cuota).containsEntry("tipo", "SALDO_INICIAL").containsEntry("obligacion", "PEN-2026-09")
				.containsEntry("descripcion", "Pensión setiembre 2026").containsEntry("estado", "PENDIENTE");
		assertThat(cuota.get("clave")).asString().startsWith("SI:");
		assertThat(cuota.get("matricula_id")).isNotNull();
		assertThat(jdbc.queryForMap("SELECT * FROM lote_saldo_inicial WHERE id = ?", lote))
				.containsEntry("estado", "CONFIRMADO").containsEntry("confirmado_por", "promotor");
	}

	@Test
	void confirmarDosVecesNoDuplicaCuotas() {
		Long lote = enviado();
		como(DIRECCION);
		confirmar(saldo, lote, "450.00");

		assertThatThrownBy(() -> confirmar(saldo, lote, "450.00")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Solo se confirma un lote enviado");
		assertThat(contar(jdbc, "cuota")).isEqualTo(1);
	}

	@Test
	void lineaQueDuplicaUnaPensionGeneradaBloqueaTodaLaConfirmacion() {
		Long lote = lote("900.00");
		saldo.agregarLinea(lote, pension(DNI_MATEO, 8, "450.00"));
		saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00"));
		saldo.enviar(lote);
		// Mientras tanto se aprueba un plan 2026 que cobra todo el año: genera agosto y setiembre de Mateo.
		EscenarioCobranza.planAprobado(planes, escuela.anio2026(), 2026, Nivel.PRIMARIA, "450", "350", null);
		long antes = contar(jdbc, "cuota");

		como(PROMOTORIA);
		assertThat(saldo.obtener(lote).bloqueos()).hasSize(2);
		assertThat(saldo.obtener(lote).puedeConfirmar()).isFalse();
		assertThatThrownBy(() -> confirmar(saldo, lote, "900.00")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("No se confirmó nada")
				.hasMessageContaining("Mateo Quispe Huamán: Pensión setiembre 2026 ya está en su cronograma");
		assertThat(contar(jdbc, "cuota")).isEqualTo(antes);
		assertThat(estado(lote)).isEqualTo("ENVIADO");
	}

	/** Auditoría C1: antes de confirmar se ve qué cuotas futuras quedarían bloqueadas; si hay alguna, no se confirma. */
	@Test
	void unaDeudaQueElPlanVigenteTambienCobraBloqueaLaConfirmacion() {
		Long lote = enviadoCon(pension(DNI_MATEO, 9, "450.00"), "450.00");
		EscenarioCobranza.planAprobado(planes, escuela.anio2026(), 2026, Nivel.PRIMARIA, "450", "350", null);
		// Simula que la generación aún no ocurrió para Mateo (por ejemplo, matrícula de antes del plan).
		jdbc.update("DELETE FROM cuota");

		como(PROMOTORIA);
		assertThat(saldo.obtener(lote).bloqueos()).singleElement().asString()
				.contains("Pensión setiembre 2026 la cobra también el Plan Primaria 2026 v1");
		assertThatThrownBy(() -> confirmar(saldo, lote, "450.00")).hasMessageContaining("No se confirmó nada");
		assertThat(contar(jdbc, "cuota")).isZero();
	}

	/** Auditoría C1: con saldo inicial confirmado, el plan del año debe cobrar desde después del corte. */
	@Test
	void conSaldoInicialConfirmadoElPlanDebeCobrarDespuesDelCorte() {
		Long lote = enviado();
		como(PROMOTORIA);
		confirmar(saldo, lote, "450.00");

		como(ADMINISTRACION);
		Long sinCorte = planes.crearBorrador(escuela.anio2026(), Nivel.PRIMARIA, EscenarioCobranza.plan(2026, "450", "350",
				LocalDate.of(2026, 9, 1)));
		como(DIRECCION);
		assertThatThrownBy(() -> EscenarioCobranza.aprobar(planes, sinCorte))
				.hasMessageContaining("tiene saldo inicial confirmado hasta el 30/09/2026");
		assertThat(contar(jdbc, "cuota WHERE tipo = 'PENSION'")).isZero();
	}

	@Test
	void devolverExigeMotivoYVuelveABorrador() {
		Long lote = enviado();
		como(DIRECCION);
		Long version = saldo.obtener(lote).version();
		assertThatThrownBy(() -> saldo.devolver(lote, version, "no")).hasMessageContaining("entre 10 y 500");

		saldo.devolver(lote, version, "El monto de Mateo no coincide con el informe");

		assertThat(jdbc.queryForMap("SELECT * FROM lote_saldo_inicial WHERE id = ?", lote))
				.containsEntry("estado", "BORRADOR").containsEntry("devuelto_por", "director")
				.containsEntry("motivo_devolucion", "El monto de Mateo no coincide con el informe");
		como(ADMINISTRACION);
		assertThat(saldo.obtener(lote).puedeEditar()).isTrue();
	}

	@Test
	void lineasQuitadasNoGeneranCuotas() {
		Long lote = lote("450.00");
		Long quitada = saldo.agregarLinea(lote, pension(DNI_VALERIA, 9, "300.00"));
		saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00"));
		assertThat(saldo.obtener(lote).cuadra()).isFalse();
		saldo.quitarLinea(lote, quitada, "Valeria pagó en efectivo antes del corte");
		saldo.enviar(lote);
		como(PROMOTORIA);
		confirmar(saldo, lote, "450.00");

		assertThat(jdbc.queryForList("SELECT descripcion FROM cuota", String.class)).containsExactly("Pensión setiembre 2026");
		assertThat(jdbc.queryForObject("SELECT quitada FROM linea_saldo_inicial WHERE id = ?", Boolean.class, quitada)).isTrue();
	}

	@Test
	void sumaExactaConBigDecimalYBordesDeDinero() {
		Long lote = lote("0.30");
		saldo.agregarLinea(lote, otro(DNI_MATEO, "Materiales educativos", "0.1"));
		saldo.agregarLinea(lote, otro(DNI_VALERIA, "Materiales educativos", "0.20"));

		assertThat(saldo.obtener(lote).cuadra()).isTrue();
		saldo.enviar(lote);
		assertThat(estado(lote)).isEqualTo("ENVIADO");
		assertThatThrownBy(() -> saldo.crearLote(new LoteRequest(escuela.anio2026(), CORTE, "Informe", new BigDecimal("1.005"))))
				.hasMessage("El monto debe tener como máximo 2 decimales.");
		// QA: bordes del total declarado y de una línea.
		assertThat(saldo.crearLote(new LoteRequest(escuela.anio2026(), CORTE, "Informe", new BigDecimal("99999999.99"))))
				.isNotNull();
		assertThatThrownBy(() -> saldo.crearLote(new LoteRequest(escuela.anio2026(), CORTE, "Informe",
				new BigDecimal("100000000")))).hasMessage("El total declarado debe estar entre S/ 0.01 y S/ 99,999,999.99.");
		Long otroLote = lote("10.00");
		assertThatThrownBy(() -> saldo.agregarLinea(otroLote, otro(DNI_MATEO, "Excursión", "10.005")))
				.hasMessage("El monto debe tener como máximo 2 decimales.");
		assertThatThrownBy(() -> saldo.crearLote(new LoteRequest(escuela.anio2026(), CORTE, "=1+1", BigDecimal.TEN)))
				.hasMessageContaining("no puede empezar con");
	}

	@Test
	void cadaPasoQuedaAuditado() {
		Long lote = lote("450.00");
		Long linea = saldo.agregarLinea(lote, pension(DNI_VALERIA, 9, "450.00"));
		saldo.quitarLinea(lote, linea, MOTIVO);
		saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00"));
		saldo.enviar(lote);
		como(DIRECCION);
		saldo.devolver(lote, saldo.obtener(lote).version(), MOTIVO);
		como(ADMINISTRACION_2);
		saldo.enviar(lote);
		como(PROMOTORIA);
		confirmar(saldo, lote, "450.00");
		como(ADMINISTRACION);
		Long descartado = lote("100.00");
		saldo.descartar(descartado, MOTIVO);

		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE entidad = 'lote_saldo_inicial' "
				+ "ORDER BY secuencia", String.class)).containsExactly("SALDO_INICIAL_LOTE_CREADO",
				"SALDO_INICIAL_LINEA_AGREGADA", "SALDO_INICIAL_LINEA_QUITADA", "SALDO_INICIAL_LINEA_AGREGADA",
				"SALDO_INICIAL_ENVIADO", "SALDO_INICIAL_DEVUELTO", "SALDO_INICIAL_ENVIADO", "SALDO_INICIAL_CONFIRMADO",
				"SALDO_INICIAL_LOTE_CREADO", "SALDO_INICIAL_DESCARTADO");
		assertThat(ultimoEvento(jdbc, "SALDO_INICIAL_CONFIRMADO").get("valor_nuevo").toString())
				.contains("Total declarado S/ 450.00").contains("1 cuotas creadas por S/ 450.00");
		assertThat(ultimoEvento(jdbc, "SALDO_INICIAL_LINEA_AGREGADA").get("valor_nuevo").toString())
				.contains("DNI ****1236").doesNotContain(DNI_MATEO);
	}

	/** QA: confirmar el lote mientras se aprueba un plan que cobraría lo mismo: ni duplicados ni error 500. */
	@Test
	void confirmarLoteMientrasSeApruebaElPlanNoDuplicaNiDaError500() throws Exception {
		Long lote = enviadoCon(pension(DNI_MATEO, 9, "450.00"), "450.00");
		como(ADMINISTRACION);
		Long plan = planes.crearBorrador(escuela.anio2026(), Nivel.PRIMARIA, EscenarioCobranza.plan(2026, "450", "350", null));
		planes.enviar(plan);
		como(DIRECCION);
		Long versionPlan = planes.obtener(plan).version();
		como(PROMOTORIA);
		Long versionLote = saldo.obtener(lote).version();

		List<Throwable> errores = Collections.synchronizedList(new ArrayList<>());
		CountDownLatch salida = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			Future<?> confirmacion = hilos.submit(enHilo(PROMOTORIA, salida, errores,
					() -> saldo.confirmar(lote, versionLote, new BigDecimal("450.00"))));
			Future<?> aprobacion = hilos.submit(enHilo(DIRECCION, salida, errores, () -> planes.aprobar(plan, versionPlan)));
			salida.countDown();
			confirmacion.get(60, TimeUnit.SECONDS);
			aprobacion.get(60, TimeUnit.SECONDS);
		}
		finally {
			hilos.shutdownNow();
		}
		assertThat(errores).hasSize(1).allSatisfy(e -> assertThat(e).isInstanceOf(ReglaNegocioException.class));
		assertThat(contar(jdbc, "cuota c JOIN alumno a ON a.id = c.alumno_id WHERE a.numero_documento = '" + DNI_MATEO
				+ "' AND c.obligacion = 'PEN-2026-09'")).isEqualTo(1);
	}

	@Test
	void cajaRecibe403() {
		Long lote = lote("450.00");
		como(CAJA);
		assertThatThrownBy(() -> saldo.listar()).isInstanceOf(AuthorizationDeniedException.class);
		assertThatThrownBy(() -> saldo.obtener(lote)).isInstanceOf(AuthorizationDeniedException.class);
		assertThatThrownBy(() -> saldo.crearLote(new LoteRequest(escuela.anio2026(), CORTE, "Informe", BigDecimal.TEN)))
				.isInstanceOf(AuthorizationDeniedException.class);
		assertThatThrownBy(() -> saldo.confirmar(lote, 0L, BigDecimal.TEN)).isInstanceOf(AuthorizationDeniedException.class);
		como(ADMINISTRACION_2);
		assertThatThrownBy(() -> saldo.confirmar(lote, 0L, BigDecimal.TEN)).isInstanceOf(AuthorizationDeniedException.class);
		como(PROMOTORIA);
		assertThatThrownBy(() -> saldo.crearLote(new LoteRequest(escuela.anio2026(), CORTE, "Informe", BigDecimal.TEN)))
				.isInstanceOf(AuthorizationDeniedException.class);
	}

	private static Callable<Void> enHilo(UsuarioAutenticado quien, CountDownLatch salida, List<Throwable> errores,
			Runnable accion) {
		return () -> {
			UsuariosDePrueba.iniciarSesion(quien);
			try {
				salida.await();
				accion.run();
			}
			catch (RuntimeException e) {
				errores.add(e);
			}
			finally {
				SecurityContextHolder.clearContext();
			}
			return null;
		};
	}

	private Long lote(String total) {
		return saldo.crearLote(new LoteRequest(escuela.anio2026(), CORTE, "Informe CPC Ramos N.° 014-2026",
				new BigDecimal(total)));
	}

	private Long enviado() {
		return enviadoCon(pension(DNI_MATEO, 9, "450.00"), "450.00");
	}

	private Long enviadoCon(LineaSaldoRequest linea, String total) {
		como(ADMINISTRACION);
		Long lote = lote(total);
		saldo.agregarLinea(lote, linea);
		saldo.enviar(lote);
		return lote;
	}

	private static LineaSaldoRequest pension(String dni, int mes, String monto) {
		return new LineaSaldoRequest(dni, ConceptoSaldo.PENSION, 2026, mes, null, new BigDecimal(monto), null);
	}

	private static LineaSaldoRequest otro(String dni, String concepto, String monto) {
		return otro(dni, concepto, monto, LocalDate.of(2026, 2, 15));
	}

	private static LineaSaldoRequest otro(String dni, String concepto, String monto, LocalDate vence) {
		return new LineaSaldoRequest(dni, ConceptoSaldo.OTRO, null, null, concepto, new BigDecimal(monto), vence);
	}

	private String estado(Long lote) {
		return jdbc.queryForObject("SELECT estado FROM lote_saldo_inicial WHERE id = ?", String.class, lote);
	}
}
