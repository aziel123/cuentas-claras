package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.Extracto;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.CierreMensualVista;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoCierreMensual;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.proceso.CierresMensuales;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCierreMensual.CierreNoCoincideException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/**
 * Sprint 5, tanda 3 (G22): el cierre bancario mensual a ciegas. Administración sube el extracto de setiembre,
 * Promotoría lo confirma con el saldo y Dirección (que no subió ni confirmó nada del mes) escribe a ciegas los tres
 * números del estado de cuenta oficial. Un abono inventado y tapado con cargos de OTROS montos deja el saldo igual,
 * pero cambia los totales del mes.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CierreMensualTest {

	/** Viernes 2 de octubre de 2026: el mes anterior es setiembre. */
	private static final LocalDate HOY = LocalDate.of(2026, 10, 2);

	@Autowired
	private ServicioExtractos extractos;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private ServicioCierreMensual cierres;

	@Autowired
	private CierresMensuales proceso;

	@Autowired
	private AlertasCierreMensual alertas;

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

	/** El estado de cuenta oficial de setiembre: abonos 1,800.00, cargos 300.35, saldo final 11,499.65. */
	private static Extracto setiembre() {
		return extracto("10000.00").abono("2026-09-01", "YAPE RECIBIDO", "YP1001", "800.00")
				.cargo("2026-09-15", "COMISION MANTENIMIENTO", "", "300.00")
				.abono("2026-09-30", "TRANSFERENCIA DE TERCEROS", "TR2002", "1000.00")
				.cargo("2026-09-30", "ITF", "", "0.35");
	}

	private Long cierreDeSetiembre(Extracto banco) {
		registrar(extractos, ADMINISTRACION, banco);
		confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		SecurityContextHolder.clearContext();
		assertThat(proceso.enColegio(1L, HOY)).isEqualTo(1);
		return jdbc.queryForObject("SELECT id FROM cierre_mensual_banco WHERE anio = 2026 AND mes = 9", Long.class);
	}

	private EstadoCierreMensual escribir(Long id, String abonos, String cargos, String saldo) {
		UsuariosDePrueba.iniciarSesion(DIRECCION);
		CierreMensualVista vista = cierres.vista(id);
		return cierres.registrar(id, vista.version(), new BigDecimal(abonos), new BigDecimal(cargos),
				new BigDecimal(saldo));
	}

	@Test
	void elSistemaCreaElCierreConLosTotalesDelMesYLaPantallaNoLosMuestra() {
		Long id = cierreDeSetiembre(setiembre());

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cierre_mensual_banco WHERE id = ?", id);
		assertThat(fila).containsEntry("estado", "ABIERTO").containsEntry("creado_por", "sistema.conciliacion")
				.containsEntry("intentos", 0);
		assertThat((BigDecimal) fila.get("total_abonos")).isEqualByComparingTo("1800.00");
		assertThat((BigDecimal) fila.get("total_cargos")).isEqualByComparingTo("300.35");
		assertThat((BigDecimal) fila.get("saldo_final")).isEqualByComparingTo("11499.65");
		// Ni la pantalla ni la bitácora dicen los totales mientras está abierto.
		UsuariosDePrueba.iniciarSesion(DIRECCION);
		CierreMensualVista vista = cierres.vista(id);
		assertThat(vista.puedeRegistrar()).isTrue();
		assertThat(vista.totalAbonos()).isNull();
		assertThat(vista.totalCargos()).isNull();
		assertThat(vista.saldoFinal()).isNull();
		assertThat(ultimoEvento(jdbc, "CIERRE_MENSUAL_CREADO").get("detalle")).asString()
				.doesNotContain("1800", "1,800", "11499", "11,499");
		// Idempotente: la tarea de mañana no crea otro.
		SecurityContextHolder.clearContext();
		assertThat(proceso.enColegio(1L, HOY.plusDays(1))).isZero();
	}

	@Test
	void conElEstadoDeCuentaOficialCuadraYQuedaEnLaBitacora() {
		Long id = cierreDeSetiembre(setiembre());

		assertThat(escribir(id, "1800", "300.35", "11499.65")).isEqualTo(EstadoCierreMensual.CUADRADO);

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cierre_mensual_banco WHERE id = ?", id);
		assertThat(fila).containsEntry("estado", "CUADRADO").containsEntry("registrado_por", "director")
				.containsEntry("intentos", 1);
		Map<String, Object> evento = ultimoEvento(jdbc, "CIERRE_MENSUAL_CUADRADO");
		assertThat(evento).containsEntry("nombre_usuario", "director").containsEntry("valor_anterior", "ABIERTO")
				.containsEntry("valor_nuevo", "CUADRADO");
		assertThat(cierres.vista(id).totalAbonos()).isEqualByComparingTo("1800.00");
	}

	@Test
	void unAbonoInventadoNoCuadraConElEstadoDeCuentaOficial() {
		// En el extracto subido se inventó un abono de 500 (para «cubrir» un Yape falso) y se tapó con dos cargos de 200 y
		// 300: el saldo final es el mismo que el del banco, pero los totales del mes no.
		Extracto alterado = extracto("10000.00").abono("2026-09-01", "YAPE RECIBIDO", "YP1001", "800.00")
				.abono("2026-09-10", "YAPE RECIBIDO", "YP9999", "500.00")
				.cargo("2026-09-15", "COMISION MANTENIMIENTO", "", "300.00")
				.cargo("2026-09-20", "CARGO VARIOS", "", "200.00").cargo("2026-09-25", "CARGO VARIOS", "", "300.00")
				.abono("2026-09-30", "TRANSFERENCIA DE TERCEROS", "TR2002", "1000.00")
				.cargo("2026-09-30", "ITF", "", "0.35");
		assertThat(alterado.saldoFinal()).isEqualByComparingTo(setiembre().saldoFinal());
		Long id = cierreDeSetiembre(alterado);

		assertThatThrownBy(() -> escribir(id, "1800.00", "300.35", "11499.65"))
				.isInstanceOf(CierreNoCoincideException.class).hasMessageContaining("Te queda 1 intento");
		assertThat(jdbc.queryForObject("SELECT estado FROM cierre_mensual_banco WHERE id = ?", String.class, id))
				.isEqualTo("ABIERTO");
		assertThatThrownBy(() -> escribir(id, "1800.00", "300.35", "11499.65"))
				.isInstanceOf(CierreNoCoincideException.class).hasMessageContaining("DISCREPANCIA");

		assertThat(jdbc.queryForObject("SELECT estado FROM cierre_mensual_banco WHERE id = ?", String.class, id))
				.isEqualTo("DISCREPANCIA");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'CIERRE_MENSUAL_NO_COINCIDE'")).isEqualTo(2);
		assertThat(ultimoEvento(jdbc, "CIERRE_MENSUAL_DISCREPANCIA").get("detalle")).asString()
				.contains("abonos S/ 2,300.00", "cargos S/ 800.35");
		assertThat(pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria.CIERRE_MENSUAL_DISCREPANCIA
				.requiereAtencion()).isTrue();
		UsuariosDePrueba.iniciarSesion(PROMOTORIA);
		List<AlertaRevision> lista = alertas.alertas();
		assertThat(lista).anySatisfy(a -> {
			assertThat(a.gravedad()).isEqualTo(Gravedad.CRITICA);
			assertThat(a.texto()).contains("setiembre de 2026", "DISCREPANCIA");
		});
	}

	@Test
	void dosIntentosDejanDiscrepancia() {
		Long id = cierreDeSetiembre(setiembre());

		assertThatThrownBy(() -> escribir(id, "1800", "300.35", "11499.00")).isInstanceOf(CierreNoCoincideException.class);
		assertThatThrownBy(() -> escribir(id, "1800", "300", "11499.65")).isInstanceOf(CierreNoCoincideException.class);

		assertThat(jdbc.queryForMap("SELECT estado, intentos FROM cierre_mensual_banco WHERE id = ?", id))
				.containsEntry("estado", "DISCREPANCIA").containsEntry("intentos", 2);
		// Resuelto: ya no admite otro intento, ni siquiera el correcto.
		assertThatThrownBy(() -> escribir(id, "1800", "300.35", "11499.65"))
				.hasMessageContaining("ya está resuelto");
	}

	@Test
	void quienConfirmoExtractosNoHaceElCierre() {
		Long id = cierreDeSetiembre(setiembre());

		UsuariosDePrueba.iniciarSesion(PROMOTORIA);
		CierreMensualVista vista = cierres.vista(id);
		assertThat(vista.puedeRegistrar()).isFalse();
		assertThat(vista.motivoNoPuede()).contains("otra persona");
		assertThatThrownBy(() -> cierres.registrar(id, vista.version(), new BigDecimal("1800"),
				new BigDecimal("300.35"), new BigDecimal("11499.65"))).isInstanceOf(AutoaprobacionException.class);
		assertThat(ultimoEvento(jdbc, "AUTOAPROBACION_RECHAZADA")).containsEntry("entidad", "cierre_mensual_banco");
		// Administración (subió el extracto) ni siquiera entra al servicio.
		UsuariosDePrueba.iniciarSesion(ADMINISTRACION);
		assertThatThrownBy(() -> cierres.vista(id)).isInstanceOf(AccessDeniedException.class);
		assertThat(jdbc.queryForObject("SELECT intentos FROM cierre_mensual_banco WHERE id = ?", Integer.class, id))
				.isZero();
	}

	@Test
	void sinExtractosCompletosNoSeCrea() {
		// El extracto empieza el 2 de setiembre: no cubre el día 1.
		Extracto incompleto = extracto("10000.00").abono("2026-09-02", "YAPE RECIBIDO", "YP1001", "800.00")
				.abono("2026-09-30", "TRANSFERENCIA DE TERCEROS", "TR2002", "1000.00");
		registrar(extractos, ADMINISTRACION, incompleto);
		confirmar(extractos, PROMOTORIA, cuentaId, incompleto.saldoFinal().toPlainString());
		SecurityContextHolder.clearContext();

		assertThat(proceso.enColegio(1L, HOY)).isZero();
		assertThat(contar(jdbc, "cierre_mensual_banco")).isZero();
	}

	@Test
	void unCierreSinHacerPasadoElDiaDiezEsAtencion() {
		Long id = cierreDeSetiembre(setiembre());
		reloj.fijar(Instant.parse("2026-10-11T15:00:00Z"));

		UsuariosDePrueba.iniciarSesion(PROMOTORIA);
		assertThat(alertas.alertas()).anySatisfy(a -> {
			assertThat(a.gravedad()).isEqualTo(Gravedad.ATENCION);
			assertThat(a.texto()).contains("sigue sin hacerse");
			assertThat(a.enlace()).endsWith("/" + id);
		});
	}
}
