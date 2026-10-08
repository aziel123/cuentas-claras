package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.Extracto;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.ConfirmacionExtractoVista;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.VistaPreviaExtracto;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION_2;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA_Y_ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.estadoExtracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extractoDe;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.partidas;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.previa;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/**
 * Sprint 4, tanda 3: la carga del extracto y su confirmación a ciegas. La cadena de saldos (cada extracto continúa al
 * anterior), los días repetidos (idénticos se omiten; distintos se rechazan y alertan), quien sube no confirma y el
 * saldo escrito a ciegas con intentos.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioExtractosTest {

	@Autowired
	private ServicioExtractos extractos;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private AlertasConciliacion alertas;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Long cuentaId;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		cuentaId = EscenarioConciliacion.cuenta(cuentas);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private static Extracto delMiercoles() {
		return extracto("5000.00").abono("2026-09-30", "TRANSFERENCIA DE TERCEROS", "TR7788", "350.00")
				.cargo("2026-09-30", "ITF", "", "0.35");
	}

	private static Extracto delJuevesTras(Extracto anterior) {
		return extracto(anterior.saldoFinal().toPlainString()).abono("2026-10-01", "YAPE RECIBIDO", "YP5544", "450.00");
	}

	@Test
	void primerExtractoSeRegistraCargadoConElArchivoYSuHuella() {
		Extracto banco = delMiercoles();
		VistaPreviaExtracto vista = previa(extractos, ADMINISTRACION, banco);
		assertThat(vista.registrable()).isTrue();
		assertThat(vista.continuidad()).startsWith("Primer extracto de la cuenta");
		assertThat(vista.movimientos()).isEqualTo(2);
		assertThat(vista.abonos()).isEqualTo(1);
		assertThat(vista.cargos()).isEqualTo(1);
		assertThat(vista.sha256()).hasSize(64);
		Long id = extractos.registrar(vista, vista.token());

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM extracto_bancario WHERE id = ?", id);
		assertThat(fila).containsEntry("estado", "CARGADO").containsEntry("secuencia", 1)
				.containsEntry("creado_por", "administracion").containsEntry("archivo_sha256", vista.sha256());
		assertThat((BigDecimal) fila.get("saldo_inicial")).isEqualByComparingTo("5000.00");
		assertThat((BigDecimal) fila.get("saldo_final")).isEqualByComparingTo("5349.65");
		assertThat(fila.get("saldo_final_ciego")).isNull();
		assertThat(contar(jdbc, "movimiento_bancario")).isEqualTo(2);
		assertThat(contar(jdbc, "archivo_cargado WHERE tipo = 'EXTRACTO'")).isEqualTo(1);
		// La bitácora no lleva el saldo: quien confirma lo escribe a ciegas.
		assertThat(ultimoEvento(jdbc, "EXTRACTO_CARGADO").get("detalle")).asString()
				.contains("SHA-256 " + vista.sha256()).doesNotContain("5349.65", "5,349.65");
		// La misma revisión no se usa dos veces.
		assertThatThrownBy(() -> extractos.registrar(vista, vista.token())).isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void unaCuentaNoRegistradaNoSeCarga() {
		assertThatThrownBy(() -> previa(extractos, ADMINISTRACION, extractoDe("193-9999999-0-55", "100.00")
				.abono("2026-09-30", "ABONO", "", "1.00"))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no está registrada");
	}

	@Test
	void losMovimientosDeHoyNoSeCargan() {
		VistaPreviaExtracto vista = previa(extractos, ADMINISTRACION, extracto("100.00")
				.abono("2026-10-01", "ABONO", "", "1.00").abono("2026-10-02", "ABONO DE HOY", "", "2.00"));
		assertThat(vista.registrable()).isFalse();
		assertThat(vista.errores()).anyMatch(e -> e.mensaje().contains("trae movimientos de hoy"));
	}

	@Test
	void elSaldoQueNoContinuaAlAnteriorSeRechaza() {
		Extracto miercoles = delMiercoles();
		registrar(extractos, ADMINISTRACION, miercoles);
		// Falta un movimiento entre los dos: el jueves no empieza con el saldo final del miércoles.
		Extracto saltado = extracto(miercoles.saldoFinal().subtract(new BigDecimal("100.00")).toPlainString())
				.abono("2026-10-01", "YAPE RECIBIDO", "YP5544", "450.00");
		VistaPreviaExtracto vista = previa(extractos, ADMINISTRACION, saltado);
		assertThat(vista.registrable()).isFalse();
		assertThat(vista.problema()).contains("El saldo no continúa al extracto anterior");
		assertThatThrownBy(() -> extractos.registrar(vista, vista.token())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no continúa");
		assertThat(contar(jdbc, "extracto_bancario")).isEqualTo(1);
	}

	@Test
	void losDiasYaCargadosIdenticosSeOmitenYSinDiasNuevosNoSeCarga() {
		Extracto miercoles = delMiercoles();
		registrar(extractos, ADMINISTRACION, miercoles);
		// El banco suele dar el extracto acumulado: el miércoles otra vez (idéntico) y el jueves nuevo.
		Extracto acumulado = extracto("5000.00").abono("2026-09-30", "TRANSFERENCIA DE TERCEROS", "TR7788", "350.00")
				.cargo("2026-09-30", "ITF", "", "0.35").abono("2026-10-01", "YAPE RECIBIDO", "YP5544", "450.00");
		VistaPreviaExtracto vista = previa(extractos, ADMINISTRACION, acumulado);
		assertThat(vista.registrable()).isTrue();
		assertThat(vista.repetidas()).isEqualTo(2);
		assertThat(vista.movimientos()).isEqualTo(1);
		assertThat(vista.continuidad()).startsWith("Continúa al extracto N.° 1");
		Long jueves = extractos.registrar(vista, vista.token());
		assertThat(jdbc.queryForObject("SELECT secuencia FROM extracto_bancario WHERE id = ?", Integer.class, jueves))
				.isEqualTo(2);
		assertThat(contar(jdbc, "movimiento_bancario")).isEqualTo(3);

		VistaPreviaExtracto otraVez = previa(extractos, ADMINISTRACION, acumulado);
		assertThat(otraVez.registrable()).isFalse();
		assertThat(otraVez.problema()).contains("no trae días nuevos");
	}

	/** El banco no cambia el pasado: un día ya cargado que viene distinto se rechaza, se audita y alerta CRÍTICO. */
	@Test
	void unDiaYaCargadoDistintoSeRechazaYAlerta() {
		registrar(extractos, ADMINISTRACION, delMiercoles());
		// El mismo miércoles, sin la transferencia (alguien la borró del archivo) y con el jueves.
		Extracto alterado = extracto("5350.00").cargo("2026-09-30", "ITF", "", "0.35")
				.abono("2026-10-01", "YAPE RECIBIDO", "YP5544", "450.00");
		assertThatThrownBy(() -> previa(extractos, ADMINISTRACION, alterado))
				.isInstanceOf(ExtractoDiscontinuoException.class).hasMessageContaining("DISTINTOS");
		assertThat(contar(jdbc, "extracto_bancario")).isEqualTo(1);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'EXTRACTO_DISCONTINUO'")).isEqualTo(1);
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().contains("días ya cargados DISTINTOS"));
	}

	@Test
	void quienSubeNoConfirmaYNadieVeElSaldo() {
		Long id = registrar(extractos, PROMOTORIA_Y_ADMINISTRACION, delMiercoles());
		// Administración sola no confirma extractos.
		como(ADMINISTRACION_2);
		assertThatThrownBy(() -> extractos.paraConfirmar(cuentaId)).isInstanceOf(AccessDeniedException.class);

		UsuariosDePrueba.iniciarSesion(PROMOTORIA_Y_ADMINISTRACION);
		ConfirmacionExtractoVista vista = extractos.paraConfirmar(cuentaId);
		assertThat(vista.participaste()).isTrue();
		assertThat(vista.cierreDel()).isEqualTo(java.time.LocalDate.of(2026, 9, 30));
		// S4-A1: la muestra fija nunca es todo el extracto (2 movimientos: se muestra 1), sin monto ni tipo.
		assertThat(vista.muestra()).hasSize(1);
		assertThat(jdbc.queryForObject("SELECT muestra FROM extracto_bancario WHERE id = ?", String.class, id))
				.matches("[12]");
		assertThatThrownBy(() -> extractos.confirmar(cuentaId, vista.extractoId(), vista.version(),
				new BigDecimal("5349.65"))).isInstanceOf(AutoaprobacionException.class);
		assertThat(estadoExtracto(jdbc, id)).isEqualTo("CARGADO");
		assertThat(ultimoEvento(jdbc, "AUTOAPROBACION_RECHAZADA")).containsEntry("nombre_usuario", "promotora.adm");

		confirmar(extractos, DIRECCION, cuentaId, "5,349.65".replace(",", ""));
		assertThat(estadoExtracto(jdbc, id)).isEqualTo("CONFIRMADO");
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM extracto_bancario WHERE id = ?", id);
		assertThat(fila).containsEntry("confirmado_por", "director");
		assertThat((BigDecimal) fila.get("saldo_final_ciego")).isEqualByComparingTo("5349.65");
	}

	/**
	 * Correcciones del sprint 4 (S4-A2): dos extractos pendientes se confirman de uno en uno, del más antiguo al más
	 * nuevo, cada uno con SU saldo; el saldo del último ya no sella la cadena.
	 */
	@Test
	void cadaExtractoPendienteSeConfirmaConSuPropioSaldo() {
		Extracto miercoles = delMiercoles();
		Long primero = registrar(extractos, ADMINISTRACION, miercoles);
		Extracto jueves = delJuevesTras(miercoles);
		Long segundo = registrar(extractos, ADMINISTRACION, jueves);

		como(PROMOTORIA);
		ConfirmacionExtractoVista vista = extractos.paraConfirmar(cuentaId);
		assertThat(vista.pendientes()).hasSize(2);
		assertThat(vista.extractoId()).isEqualTo(primero);
		assertThat(vista.cierreDel()).isEqualTo(java.time.LocalDate.of(2026, 9, 30));
		assertThat(vista.intentosRestantes()).isEqualTo(2);

		// El saldo del jueves ya no confirma el miércoles (ni la cadena).
		assertThatThrownBy(() -> confirmar(extractos, PROMOTORIA, cuentaId, jueves.saldoFinal().toPlainString()))
				.isInstanceOf(SaldoNoCoincideException.class).hasMessageContaining("Te queda 1 intento");
		assertThat(estadoExtracto(jdbc, primero)).isEqualTo("CARGADO");
		confirmar(extractos, PROMOTORIA, cuentaId, miercoles.saldoFinal().toPlainString());
		assertThat(estadoExtracto(jdbc, primero)).isEqualTo("CONFIRMADO");
		assertThat(estadoExtracto(jdbc, segundo)).isEqualTo("CARGADO");
		assertThat(ultimoEvento(jdbc, "EXTRACTO_CONFIRMADO").get("detalle")).asString()
				.contains("Quedan 1 extracto(s) por confirmar");
		ConfirmacionExtractoVista siguiente = extractos.paraConfirmar(cuentaId);
		assertThat(siguiente.extractoId()).isEqualTo(segundo);
		assertThat(siguiente.cierreDel()).isEqualTo(java.time.LocalDate.of(2026, 10, 1));
		// Con un id viejo (el del primero) no se confirma el segundo.
		assertThatThrownBy(() -> extractos.confirmar(cuentaId, primero, siguiente.version(), jueves.saldoFinal()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cambiaron");
		confirmar(extractos, PROMOTORIA, cuentaId, jueves.saldoFinal().toPlainString());
		assertThat(estadoExtracto(jdbc, segundo)).isEqualTo("CONFIRMADO");
		assertThat(jdbc.queryForList("SELECT confirmacion_extracto_id FROM extracto_bancario ORDER BY id", Long.class))
				.containsExactly(primero, segundo);
		// El primero gastó un intento con el saldo equivocado: queda en la bitácora resaltado.
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'EXTRACTO_SALDO_NO_COINCIDE'")).isEqualTo(1);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().contains("no coincidieron"));
	}

	/** Dos saldos a ciegas distintos: el extracto queda RECHAZADO, no hay nada más que confirmar y Promotoría lo ve en rojo. */
	@Test
	void unExtractoRechazadoAlertaEnRojo() {
		Extracto miercoles = delMiercoles();
		registrar(extractos, ADMINISTRACION, miercoles);
		como(PROMOTORIA);
		assertThatThrownBy(() -> confirmar(extractos, PROMOTORIA, cuentaId, "1.00"))
				.isInstanceOf(SaldoNoCoincideException.class);
		assertThatThrownBy(() -> confirmar(extractos, PROMOTORIA, cuentaId, "2.00"))
				.isInstanceOf(SaldoNoCoincideException.class).hasMessageContaining("RECHAZADO");
		assertThat(extractos.paraConfirmar(cuentaId).porConfirmar()).isFalse();
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().contains("RECHAZADO"));
	}

	/** Quien lo subió lo descarta antes de que lo confirmen; sus parejas propuestas se liberan. */
	@Test
	void descartarLiberaLasParejasPropuestas() {
		Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(CAJA);
		cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), MedioPago.YAPE, "YP5544",
				"450.00"));
		reloj.avanzar(Duration.ofDays(1));
		Extracto viernes = extracto("100.00").abono("2026-10-02", "YAPE RECIBIDO", "YP5544", "450.00");
		Long id = registrar(extractos, ADMINISTRACION, viernes);
		assertThat(partidas(jdbc)).containsExactly("EXACTA PROPUESTA PAGO");

		como(ADMINISTRACION_2);
		assertThatThrownBy(() -> extractos.descartar(id, "Lo subí de la cuenta equivocada, lo cargo otra vez"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Solo quien subió");
		como(ADMINISTRACION);
		extractos.descartar(id, "Lo subí de la cuenta equivocada, lo cargo otra vez");
		assertThat(estadoExtracto(jdbc, id)).isEqualTo("DESCARTADO");
		assertThat(partidas(jdbc)).isEmpty();
		// Se puede volver a cargar el mismo día (el descartado salió de la cadena).
		Long otraVez = registrar(extractos, ADMINISTRACION, viernes);
		assertThat(jdbc.queryForObject("SELECT secuencia FROM extracto_bancario WHERE id = ?", Integer.class, otraVez))
				.isEqualTo(1);
		assertThat(partidas(jdbc)).containsExactly("EXACTA PROPUESTA PAGO");
	}
}
