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
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.Extracto;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.proceso.ImportadorLiquidaciones;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.SimuladorPagos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.partidas;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.verificacionDePago;

/**
 * Sprint 4, tanda 3: las alertas de la conciliación en el inicio de Promotoría (el extracto al día, el muestreo) y la
 * liquidación de la pasarela conciliada contra su abono neto.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AlertasConciliacionTest {

	/** Viernes 2 de octubre de 2026, 12:30 en Lima: pasó la hora límite del extracto (12:00). */
	static final Instant VIERNES_MEDIODIA = Instant.parse("2026-10-02T17:30:00Z");

	@Autowired
	private AlertasConciliacion alertas;

	@Autowired
	private ServicioExtractos extractos;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private ServicioPagoEnLinea pagoEnLinea;

	@Autowired
	private SimuladorPagos simulador;

	@Autowired
	private ImportadorLiquidaciones importador;

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

	private List<AlertaRevision> deLaConciliacion() {
		como(PROMOTORIA);
		return alertas.alertas();
	}

	@Test
	void elExtractoDelDiaAnteriorDebeEstarSubidoYConfirmado() {
		assertThat(deLaConciliacion()).noneMatch(a -> a.texto().contains("no está subido"));
		reloj.fijar(VIERNES_MEDIODIA);
		assertThat(deLaConciliacion()).anyMatch(a -> a.gravedad() == Gravedad.ATENCION
				&& a.texto().contains("del 01/10/2026 no está subido (pasó la hora límite, 12:00)"));

		Extracto jueves = extracto("800.00").abono("2026-10-01", "ABONO", "", "10.00");
		registrar(extractos, ADMINISTRACION, jueves);
		assertThat(deLaConciliacion()).noneMatch(a -> a.texto().contains("no está subido"));
		assertThat(deLaConciliacion()).noneMatch(a -> a.texto().contains("sin confirmar"));
		reloj.avanzar(Duration.ofHours(3));
		assertThat(deLaConciliacion()).anyMatch(a -> a.gravedad() == Gravedad.ATENCION
				&& a.texto().contains("cargado(s) sin confirmar (hasta el 01/10/2026)")
				&& a.enlace().equals("/conciliacion/cuentas/" + cuentaId + "/confirmar"));
		confirmar(extractos, PROMOTORIA, cuentaId, jueves.saldoFinal().toPlainString());
		assertThat(deLaConciliacion()).noneMatch(a -> a.texto().contains("sin confirmar"));
	}

	/** El muestreo diario es el mismo durante todo el día (no se «refresca» hasta dar con lo que conviene). */
	@Test
	void elMuestreoEsElMismoTodoElDiaYSoloLoVePromotoria() {
		Extracto jueves = extracto("800.00").abono("2026-10-01", "ABONO UNO", "", "10.00")
				.abono("2026-10-01", "ABONO DOS", "", "20.00").abono("2026-10-01", "ABONO TRES", "", "30.00")
				.cargo("2026-10-01", "CARGO UNO", "", "1.00").cargo("2026-10-01", "CARGO DOS", "", "2.00");
		registrar(extractos, ADMINISTRACION, jueves);
		confirmar(extractos, PROMOTORIA, cuentaId, jueves.saldoFinal().toPlainString());

		String manana = deLaConciliacion().stream().filter(a -> a.texto().startsWith("Muestreo de hoy")).findFirst()
				.orElseThrow().texto();
		reloj.avanzar(Duration.ofHours(8));
		String tarde = deLaConciliacion().stream().filter(a -> a.texto().startsWith("Muestreo de hoy")).findFirst()
				.orElseThrow().texto();
		assertThat(tarde).isEqualTo(manana);
		assertThat(manana.split(";")).hasSize(3);

		como(DIRECCION);
		assertThatThrownBy(() -> alertas.alertas()).isInstanceOf(AccessDeniedException.class);
	}

	/** La liquidación de la pasarela (neta de comisión e IGV) se empareja sola con su abono y verifica sus pagos. */
	@Test
	void laLiquidacionDeLaPasarelaSeConciliaConSuAbonoNeto() {
		Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		// Un día que ninguna otra prueba usa: la pasarela simulada guarda sus cobros en memoria y liquida por día.
		reloj.fijar(Instant.parse("2026-10-13T15:00:00Z"));
		UsuariosDePrueba.iniciarSesion(new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa()));
		List<Long> cuotas = List.of(cuota(jdbc, f.mateo(), "PEN-2027-03"));
		BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(cuotas)).total();
		String referencia = pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), cuotas, total, false));
		simulador.simular(referencia, PasarelaSimulada.Accion.YAPE);
		SecurityContextHolder.clearContext();
		Long pago = jdbc.queryForObject("SELECT id FROM pago WHERE origen = 'PASARELA'", Long.class);

		// Al día siguiente la pasarela liquida lo del martes (abono el día hábil siguiente).
		reloj.fijar(Instant.parse("2026-10-14T14:00:00Z"));
		assertThat(importador.importar(1L, LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 14))).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT pago_id FROM liquidacion_linea", Long.class)).containsExactly(pago);
		BigDecimal neto = jdbc.queryForObject("SELECT total_neto FROM liquidacion_pasarela", BigDecimal.class);
		assertThat(neto).isLessThan(total);

		// Jueves: el extracto del miércoles trae el abono neto con la referencia de la liquidación.
		reloj.fijar(Instant.parse("2026-10-15T14:00:00Z"));
		Extracto lunes = extracto("1000.00").abono("2026-10-14", "ABONO PASARELA LIQ SIMLIQ20261013", "",
				neto.toPlainString());
		registrar(extractos, ADMINISTRACION, lunes);
		assertThat(partidas(jdbc)).containsExactly("EXACTA PROPUESTA LIQUIDACION");
		confirmar(extractos, PROMOTORIA, cuentaId, lunes.saldoFinal().toPlainString());
		assertThat(partidas(jdbc)).containsExactly("EXACTA CONFIRMADA LIQUIDACION");
		assertThat(verificacionDePago(jdbc, pago)).isEqualTo("AUTOMATICA ENCONTRADO");
		assertThat(deLaConciliacion()).noneMatch(a -> a.gravedad() == Gravedad.CRITICA);
	}
}
