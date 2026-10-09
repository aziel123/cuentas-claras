package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ReconteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.IndicadoresInicio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * «Para revisar» de Promotoría con las alertas de caja (calculadas al consultar) y el resumen del día en su inicio.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AlertasCajaTest {

	/** 02/10/2026 a las 19:30 en Lima (UTC−5). */
	private static final Instant DESPUES_DE_LAS_19 = Instant.parse("2026-10-03T00:30:00Z");

	@Autowired
	private AlertasCaja alertas;

	@Autowired
	private IndicadoresCaja indicadores;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioCierreCaja cierre;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto derivador;

	@Autowired
	private ServicioVerificacionBancaria verificacion;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillasMuestreo semillas;

	private Familias f;

	private Long pago;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(CAJA);
		pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00", "500.00"));
	}

	@AfterEach
	void limpiar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void cajaSinCerrarDespuesDeLaHoraLimite() {
		como(PROMOTORIA);
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().contains("hora límite"));

		reloj.fijar(DESPUES_DE_LAS_19);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.ATENCION
				&& a.texto().contains("Pasó la hora límite (19:00)") && a.texto().contains("sigue sin cerrar"));
		// Cerrada, ya no alerta.
		como(CAJA);
		cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null));
		como(PROMOTORIA);
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().contains("hora límite"));
	}

	@Test
	void cajaDeAyerAbierta() {
		reloj.avanzar(Duration.ofDays(1));
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().contains("del 02/10/2026 sigue abierta") && "/aprobaciones/cajas?fecha=2026-10-02".equals(a.enlace()));
	}

	@Test
	void anulacionesPendientesConMonto() {
		anulaciones.solicitarDevolucion(pago, EscenarioAprobaciones.MOTIVO_ANULACION);
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.ATENCION
				&& a.texto().equals("1 anulación(es) de pago por S/ 450.00 esperan aprobación."));
	}

	@Test
	void digitalesSinVerificarDeMasDeUnDia() {
		cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03")), MedioPago.YAPE, "YP445566",
				"450.00"));
		como(PROMOTORIA);
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().contains("sin verificarse"));

		reloj.avanzar(Duration.ofDays(1));
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.ATENCION
				&& a.texto().equals("1 pago(s) digital(es) por S/ 450.00 llevan más de 1 día(s) sin verificarse en el banco."));
	}

	@Test
	void huecoEnLaSerieEsCritico() {
		como(PROMOTORIA);
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().contains("La serie"));
		jdbc.update("UPDATE serie_comprobante SET ultimo_numero = ultimo_numero + 1 WHERE serie = 'B001'");
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().startsWith("La serie B001 va en el 2 pero tiene 1 comprobante(s)"));
	}

	@Test
	void efectivoSinDepositarDeDiasAnteriores() {
		cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null));
		como(PROMOTORIA);
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().contains("aún no se deposita"));
		reloj.avanzar(Duration.ofDays(1));
		assertThat(alertas.alertas()).anyMatch(a -> a.texto().contains("(S/ 450.00) aún no se deposita"));
	}

	/** Las críticas primero: un faltante antes que una anulación pendiente o un cierre que cuadró. */
	@Test
	void alertasCriticasPrimero() throws Exception {
		anulaciones.solicitarDevolucion(pago, EscenarioAprobaciones.MOTIVO_ANULACION);
		cierre.contar(new ConteoRequest(new BigDecimal("400.00"), null));
		cierre.recontar(new ReconteoRequest(new BigDecimal("400.00"), null, "Faltan cincuenta soles en el cajón"));
		SecurityContextHolder.clearContext();

		MvcResult inicio = mvc.perform(get("/inicio").with(UsuariosDePrueba.como(PROMOTORIA)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Hoy en caja · 02/10/2026")))
				.andExpect(content().string(containsString("Cobrado hoy")))
				.andExpect(content().string(containsString("Faltante de S/ 50.00 en la caja de")))
				.andExpect(content().string(containsString("data-gravedad=\"CRITICA\"")))
				.andReturn();
		@SuppressWarnings("unchecked")
		List<AlertaRevision> lista = (List<AlertaRevision>) inicio.getModelAndView().getModel().get("alertasRevision");
		assertThat(lista).isSortedAccordingTo(AlertaRevision.POR_GRAVEDAD);
		assertThat(lista.getFirst().gravedad()).isEqualTo(Gravedad.CRITICA);
		assertThat(lista).anyMatch(a -> a.gravedad() == Gravedad.ATENCION);
		// Dirección no ve «Para revisar» (es de Promotoría).
		como(DIRECCION);
		assertThatThrownBy(() -> alertas.alertas()).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void resumenDelDiaDePromotoria() {
		cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03")), MedioPago.YAPE, "YP778899",
				"450.00"));
		como(PROMOTORIA);
		assertThat(indicadores.titulo()).isEqualTo("Hoy en caja · 02/10/2026");
		assertThat(indicadores.indicadores()).extracting(IndicadoresInicio.Indicador::etiqueta,
				IndicadoresInicio.Indicador::valor, IndicadoresInicio.Indicador::detalle).containsExactly(
				org.assertj.core.groups.Tuple.tuple("Cobrado hoy", "S/ 900.00", "2 pago(s)"),
				// Sprint 4: lo que entró por la pasarela (o la recaudación) sin que nadie lo digite.
				org.assertj.core.groups.Tuple.tuple("Entró solo (en línea y por banco)", "S/ 0.00", "0 pago(s) sin que nadie digite"),
				org.assertj.core.groups.Tuple.tuple("Efectivo", "S/ 450.00", "1 pago(s)"),
				org.assertj.core.groups.Tuple.tuple("Digital", "S/ 450.00", "50 % del total"),
				org.assertj.core.groups.Tuple.tuple("Cajas", "1 abierta(s) · 0 cerrada(s)", "Sin diferencias"));
	}

	/** Sprint 5, tanda 3 (G21): dos Yape del viernes verificados; el lunes Promotoría ve su muestra al azar. */
	private List<String> muestraDelLunes() {
		Long yape1 = cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03")), MedioPago.YAPE,
				"YP771001", "450.00"));
		como(CAJA);
		Long yape2 = cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-04")), MedioPago.YAPE,
				"YP771002", "450.00"));
		EscenarioCaja.verificadoEnBanco(verificacion, jdbc, yape1);
		EscenarioCaja.verificadoEnBanco(verificacion, jdbc, yape2);
		reloj.fijar(Instant.parse("2026-10-05T14:00:00Z"));
		como(PROMOTORIA);
		return alertas.alertas().stream().map(AlertaRevision::texto).filter(t -> t.startsWith("Revisa al azar")).toList();
	}

	@Test
	void laMuestraUsaLaSemillaSecretaYNoLaFecha() {
		List<String> muestra = muestraDelLunes();

		assertThat(muestra).hasSize(2);
		// La semilla del día quedó guardada (solo inserción), nació con SecureRandom y no es la fecha (antes era
		// new Random(hoy.toEpochDay()) y la cajera podía calcular qué verificaciones vería Promotoría).
		List<java.util.Map<String, Object>> filas = jdbc.queryForList("SELECT ambito, fecha, semilla, creado_por "
				+ "FROM semilla_muestreo");
		assertThat(filas).singleElement().satisfies(fila -> {
			// Sprint 7, tanda 2 (H8): la planta el actor de sistema del muestreo (cc_sistema), no quien mira el panel.
			assertThat(fila).containsEntry("ambito", "CAJA").containsEntry("creado_por", "sistema.muestreo");
			assertThat(fila.get("fecha")).hasToString("2026-10-05");
			assertThat((Long) fila.get("semilla")).isNotEqualTo(java.time.LocalDate.of(2026, 10, 5).toEpochDay());
		});
		// Con la semilla de la base Y la clave HMAC, la muestra es reproducible para quien audita (y solo para él).
		long guardada = (Long) filas.getFirst().get("semilla");
		long efectiva = derivador.semilla("CAJA", java.time.LocalDate.of(2026, 10, 5), guardada);
		assertThat(efectiva).isNotEqualTo(guardada);
		assertThat(semillas.de(pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillaMuestreo.Ambito.CAJA,
				java.time.LocalDate.of(2026, 10, 5))).isEqualTo(efectiva);
	}

	@Test
	void esEstableDuranteElDia() {
		List<String> primera = muestraDelLunes();
		reloj.fijar(Instant.parse("2026-10-05T22:00:00Z"));
		como(PROMOTORIA);
		List<String> segunda = alertas.alertas().stream().map(AlertaRevision::texto)
				.filter(t -> t.startsWith("Revisa al azar")).toList();

		assertThat(segunda).containsExactlyElementsOf(primera);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM semilla_muestreo", Long.class)).isEqualTo(1);
		long guardada = jdbc.queryForObject("SELECT semilla FROM semilla_muestreo", Long.class);
		assertThat(pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillaMuestreo.Ambito.CAJA).isNotNull();
		// Sprint 7, tanda 2 (H8): la semilla efectiva se deriva de la guardada con la clave HMAC: la guardada sola (la que
		// lee cualquiera con SELECT) no reproduce la muestra.
		long efectiva = semillas.de(pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillaMuestreo.Ambito.CAJA,
				java.time.LocalDate.of(2026, 10, 5));
		assertThat(efectiva).isNotEqualTo(guardada).isEqualTo(semillas.de(
				pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillaMuestreo.Ambito.CAJA, java.time.LocalDate.of(2026, 10, 5)));
	}
}
