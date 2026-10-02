package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.DepositoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.EstadoCierreVista;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ReconteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Denominacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoConteo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Cierre ciego de caja (sprint 3, tanda 3): el esperado lo calcula el sistema y la cajera no lo ve hasta contar; un solo
 * reconteo con explicación, que siempre cierra; todo cierre lo aprueba otra persona; depósito y reapertura.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioCierreCajaTest {

	static final String EXPLICACION = "Conté mal los billetes de veinte la primera vez";

	@Autowired
	private ServicioCierreCaja cierre;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private BandejaAprobaciones bandeja;

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

	private Familias f;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(CAJA);
		cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00", "500.00"));
	}

	@AfterEach
	void limpiar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void conteoQueCoincideCierraSinMostrarElEsperadoAntes() {
		EstadoCierreVista antes = cierre.estado();
		assertThat(antes.porCerrar()).isNotNull();
		assertThat(antes.porCerrar().reconteo()).isFalse();
		// Antes de contar, ningún monto de la caja (solo el fondo fijo, que la cajera ya conoce).
		assertThat(antes.ultimoCierre()).isNull();
		assertThat(antes.toString()).doesNotContain("450");

		assertThat(cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null))).isEqualTo(ResultadoConteo.COINCIDE);

		assertThat(jdbc.queryForMap("SELECT estado, cierres, conteos, primer_conteo FROM caja_diaria"))
				.containsEntry("estado", "CERRADA").containsEntry("cierres", 1).containsEntry("conteos", 1);
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cierre_caja");
		assertThat(fila).containsEntry("numero", 1).containsEntry("estado", "POR_REVISAR").containsEntry("creado_por", "caja");
		assertThat((BigDecimal) fila.get("diferencia")).isEqualByComparingTo("0.00");
		EstadoCierreVista despues = cierre.estado();
		assertThat(despues.porCerrar()).isNull();
		assertThat(despues.ultimoCierre().esperado()).isEqualByComparingTo("450.00");
		assertThat(despues.ultimoCierre().diferenciaTexto()).isEqualTo("cuadró");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "CAJA_CERRADA")).containsEntry("nombre_usuario", "caja");
	}

	@Test
	void conteoQueNoCoincidePideReconteoSinMontos() {
		assertThat(cierre.contar(new ConteoRequest(new BigDecimal("400.00"), null))).isEqualTo(ResultadoConteo.RECONTAR);

		// La caja sigue abierta, el primer conteo quedó guardado y no hay cierre todavía.
		assertThat(jdbc.queryForMap("SELECT estado, conteos, primer_conteo FROM caja_diaria"))
				.containsEntry("estado", "ABIERTA").containsEntry("conteos", 1);
		assertThat(contar(jdbc, "cierre_caja")).isZero();
		EstadoCierreVista estado = cierre.estado();
		assertThat(estado.porCerrar().reconteo()).isTrue();
		assertThat(estado.ultimoCierre()).isNull();
		assertThat(estado.toString()).doesNotContain("450").doesNotContain("400");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "CAJA_CONTEO_NO_COINCIDE").get("detalle")).asString()
				.contains("S/ 400.00", "S/ 450.00");
		// No se puede volver a «primer conteo» para tantear.
		assertThatThrownBy(() -> cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Ya registraste tu primer conteo");
	}

	@Test
	void reconteoExigeExplicacionYSiempreCierra() {
		cierre.contar(new ConteoRequest(new BigDecimal("400.00"), null));
		assertThatThrownBy(() -> cierre.recontar(new ReconteoRequest(new BigDecimal("450.00"), null, "corto")))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(contar(jdbc, "cierre_caja")).isZero();

		// Al reconteo cuadra: igual cierra y queda el primer conteo y la explicación.
		assertThat(cierre.recontar(new ReconteoRequest(new BigDecimal("450.00"), null, EXPLICACION)))
				.isEqualTo(ResultadoConteo.FINAL);
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cierre_caja");
		assertThat((BigDecimal) fila.get("primer_conteo")).isEqualByComparingTo("400.00");
		assertThat((BigDecimal) fila.get("contado")).isEqualByComparingTo("450.00");
		assertThat(fila).containsEntry("explicacion", EXPLICACION);
		assertThat(jdbc.queryForObject("SELECT estado FROM caja_diaria", String.class)).isEqualTo("CERRADA");
		assertThat(cierre.estado().ultimoCierre().reconteo()).isTrue();
		// Y no hay un tercer conteo.
		assertThatThrownBy(() -> cierre.recontar(new ReconteoRequest(new BigDecimal("450.00"), null, EXPLICACION)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("No tienes una caja abierta");
	}

	/** El conteo NUNCA lleva el esperado: lo calcula el libro (fondo + efectivo VIGENTE de la caja). */
	@Test
	void esperadoLoCalculaElSistemaYNoLlegaDelFormulario() {
		assertThat(ConteoRequest.class.getRecordComponents()).extracting(c -> c.getName())
				.containsExactly("contado", "denominaciones");
		assertThat(ReconteoRequest.class.getRecordComponents()).extracting(c -> c.getName())
				.containsExactly("contado", "denominaciones", "explicacion");
		cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null));
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cierre_caja");
		assertThat((BigDecimal) fila.get("efectivo_cobrado")).isEqualByComparingTo(jdbc.queryForObject(
				"SELECT SUM(total) FROM pago WHERE medio = 'EFECTIVO' AND estado = 'VIGENTE'", BigDecimal.class));
		assertThat((BigDecimal) fila.get("esperado")).isEqualByComparingTo("450.00");
	}

	@Test
	void pagosDigitalesNoEntranAlEsperado() {
		cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03")), MedioPago.YAPE, "YP123456",
				"450.00"));

		assertThat(cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null))).isEqualTo(ResultadoConteo.COINCIDE);
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cierre_caja");
		assertThat((BigDecimal) fila.get("esperado")).isEqualByComparingTo("450.00");
		assertThat((BigDecimal) fila.get("total_digital")).isEqualByComparingTo("450.00");
		assertThat(fila).containsEntry("pagos_efectivo", 1).containsEntry("pagos_digitales", 1);
	}

	/** Se puede contar por billetes y monedas: el servidor suma (y avisa si el total escrito no cuadra). */
	@Test
	void denominacionesSumanEnElServidor() {
		Map<Denominacion, Integer> billetes = new EnumMap<>(Denominacion.class);
		billetes.put(Denominacion.B200, 2);
		billetes.put(Denominacion.B50, 1);
		assertThatThrownBy(() -> cierre.contar(new ConteoRequest(new BigDecimal("400.00"), billetes)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es la suma");
		assertThat(jdbc.queryForObject("SELECT conteos FROM caja_diaria", Integer.class)).isZero();

		assertThat(cierre.contar(new ConteoRequest(null, billetes))).isEqualTo(ResultadoConteo.COINCIDE);
		assertThat(jdbc.queryForObject("SELECT denominaciones FROM cierre_caja", String.class)).isEqualTo("B200×2, B50×1");
	}

	@Test
	void cierreCreaSolicitudQueApruebaOtraPersona() {
		cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null));
		Long cierreId = jdbc.queryForObject("SELECT id FROM cierre_caja", Long.class);
		assertThat(jdbc.queryForMap("SELECT tipo, solicitado_por FROM solicitud_cambio WHERE entidad = 'cierre_caja'"))
				.containsEntry("tipo", "CIERRE_CAJA").containsEntry("solicitado_por", "caja");

		como(DIRECCION);
		SolicitudVista tarjeta = bandeja.bandeja().pendientes().getFirst();
		assertThat(tarjeta.tipo()).isEqualTo("CIERRE_CAJA");
		assertThat(tarjeta.pideComentario()).isFalse();
		assertThat(tarjeta.accionRechazo()).isEqualTo("Observar");
		assertThat(tarjeta.detalle()).anyMatch(l -> l.startsWith("Esperado S/ 450.00"))
				.anyMatch(l -> l.equals("Diferencia: cuadró"));
		// Un clic: sin comentario.
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "cierre_caja", cierreId);

		assertThat(jdbc.queryForMap("SELECT estado, revisado_por FROM cierre_caja"))
				.containsEntry("estado", "APROBADO").containsEntry("revisado_por", "director");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "CAJA_CIERRE_APROBADO")).containsEntry("nombre_usuario", "director");
	}

	@Test
	void observarUnCierreLoDejaObservadoConComentario() {
		cierre.contar(new ConteoRequest(new BigDecimal("400.00"), null));
		cierre.recontar(new ReconteoRequest(new BigDecimal("400.00"), null, EXPLICACION));
		Long cierreId = jdbc.queryForObject("SELECT id FROM cierre_caja", Long.class);

		como(PROMOTORIA);
		bandeja.rechazar(EscenarioAprobaciones.pendiente(jdbc, "cierre_caja", cierreId), "Falta explicar los S/ 50 a "
				+ "Dirección mañana");
		assertThat(jdbc.queryForMap("SELECT estado, revisado_por, comentario_revision FROM cierre_caja"))
				.containsEntry("estado", "OBSERVADO").containsEntry("revisado_por", "promotor")
				.containsEntry("comentario_revision", "Falta explicar los S/ 50 a Dirección mañana");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "CAJA_CIERRE_OBSERVADO")).containsEntry("nombre_usuario",
				"promotor");
	}

	@Test
	void depositoDistintoExigeExplicacionYAlerta() {
		cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null));
		EstadoCierreVista estado = cierre.estado();
		Long caja = estado.porDepositar().getFirst().cajaId();
		assertThat(estado.porDepositar().getFirst().esperado()).isEqualByComparingTo("450.00");
		String cuenta = estado.cuentas().getFirst();
		LocalDate hoy = estado.hoy();

		assertThatThrownBy(() -> cierre.registrarDeposito(new DepositoRequest(caja, cuenta, "OP-77881", hoy,
				new BigDecimal("400.00"), null))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no es igual a lo contado");
		assertThatThrownBy(() -> cierre.registrarDeposito(new DepositoRequest(caja, "Otra cuenta", "OP-77881", hoy,
				new BigDecimal("450.00"), null))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("cuentas del colegio");
		cierre.registrarDeposito(new DepositoRequest(caja, cuenta, "op 77881", hoy, new BigDecimal("400.00"),
				"Me quedé S/ 50 para el sencillo de mañana por indicación de Administración"));

		Map<String, Object> deposito = jdbc.queryForMap("SELECT * FROM deposito_caja");
		assertThat(deposito).containsEntry("numero_operacion", "OP77881").containsEntry("creado_por", "caja");
		assertThat((BigDecimal) deposito.get("esperado")).isEqualByComparingTo("450.00");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "DEPOSITO_DIFERENTE").get("detalle")).asString()
				.contains("S/ 400.00", "S/ 450.00");
		assertThat(cierre.estado().porDepositar()).isEmpty();
		assertThatThrownBy(() -> cierre.registrarDeposito(new DepositoRequest(caja, cuenta, "OP-77882", hoy,
				new BigDecimal("450.00"), null))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya está registrado");
	}

	@Test
	void reaperturaSoloAprobadaMismoDiaYSinDeposito() {
		assertThatThrownBy(() -> cierre.solicitarReapertura("Llegó una familia a pagar en efectivo"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("está abierta");
		cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null));
		assertThat(cierre.estado().reapertura().puedePedir()).isTrue();
		cierre.solicitarReapertura("Llegó una familia a pagar en efectivo");
		assertThat(cierre.estado().reapertura().pendiente()).isTrue();
		Long caja = jdbc.queryForObject("SELECT id FROM caja_diaria", Long.class);

		// Al día siguiente ya no se reabre (y se rechaza con motivo).
		reloj.avanzar(Duration.ofDays(1));
		assertThatThrownBy(() -> EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "caja_diaria", caja))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Solo se reabre la caja del mismo día");
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);

		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "caja_diaria", caja);
		assertThat(jdbc.queryForMap("SELECT estado, cierres, conteos, primer_conteo FROM caja_diaria"))
				.containsEntry("estado", "ABIERTA").containsEntry("cierres", 1).containsEntry("conteos", 0)
				.containsEntry("primer_conteo", null);
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "CAJA_REABIERTA")).containsEntry("nombre_usuario", "director");
		// El cierre anterior sigue ahí; el siguiente es el N.° 2.
		como(CAJA);
		cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03")), "450.00", "450.00"));
		cierre.contar(new ConteoRequest(new BigDecimal("900.00"), null));
		assertThat(jdbc.queryForList("SELECT numero FROM cierre_caja ORDER BY numero", Integer.class)).containsExactly(1, 2);

		// Con depósito, ya no se reabre.
		EstadoCierreVista estado = cierre.estado();
		cierre.registrarDeposito(new DepositoRequest(caja, estado.cuentas().getFirst(), "OP-1001", estado.hoy(),
				new BigDecimal("900.00"), null));
		assertThat(cierre.estado().reapertura().puedePedir()).isFalse();
		assertThatThrownBy(() -> cierre.solicitarReapertura("Llegó otra familia a pagar en efectivo"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Ya depositaste");
	}

	/**
	 * La caja de ayer quedó abierta: hoy no se cobra hasta cerrarla. La salida es /caja/cierre, que muestra primero la
	 * caja de ayer (a ciegas); al cerrarla, se cobra hoy con normalidad.
	 */
	@Test
	void cajaDeAyerAbiertaSeCierraPrimeroYDespuesSeCobraHoy() {
		reloj.avanzar(Duration.ofDays(1));
		Long marzoValeria = cuota(jdbc, f.valeria(), "PEN-2027-03");
		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), List.of(marzoValeria), "450.00", "450.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Primero cierra tu caja del 02/10/2026");

		EstadoCierreVista estado = cierre.estado();
		assertThat(estado.porCerrar().anterior()).isTrue();
		assertThat(estado.porCerrar().fecha()).isEqualTo(LocalDate.of(2026, 10, 2));
		cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null));
		assertThat(jdbc.queryForObject("SELECT estado FROM caja_diaria WHERE fecha = '2026-10-02'", String.class))
				.isEqualTo("CERRADA");

		cobro.cobrar(efectivo(f.quispe(), List.of(marzoValeria), "450.00", "450.00"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE fecha = '2026-10-03'", Long.class)).isEqualTo(1);
		// Y ahora la caja por cerrar es la de hoy.
		assertThat(cierre.estado().porCerrar().anterior()).isFalse();
	}
}
