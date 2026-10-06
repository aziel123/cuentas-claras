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
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.IndicadoresInicio;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.Extracto;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.VistaDiferencias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CategoriaExplicacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ObjetoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.proceso.AplicadorConciliacion;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.partidas;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.verificacionDePago;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/**
 * Sprint 4, tanda 3: lo que resuelve una persona en la conciliación (las sugeridas, el emparejamiento a mano y los
 * movimientos ajenos a la cobranza), siempre con el extracto ya confirmado a ciegas, con nota y en la bitácora; y las
 * alertas de Promotoría que dependen del tiempo.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioPartidasTest {

	static final Instant LUNES = Instant.parse("2026-10-05T14:00:00Z");

	static final String NOTA = "Revisado con el voucher de la familia y la app del banco";

	@Autowired
	private ServicioPartidas servicio;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioExtractos extractos;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private ResumenConciliacion resumen;

	@Autowired
	private AlertasConciliacion alertas;

	@Autowired
	private IndicadoresConciliacion indicadores;

	@Autowired
	private AplicadorConciliacion aplicador;

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

	private Familias f;

	private Long cuentaId;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		cuentaId = EscenarioConciliacion.cuenta(cuentas);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Long yape(String operacion) {
		como(CAJA);
		return cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), MedioPago.YAPE, operacion,
				"450.00"));
	}

	private Long partida() {
		return jdbc.queryForObject("SELECT id FROM partida_conciliacion WHERE estado <> 'DESCARTADA'", Long.class);
	}

	private Long movimiento(String descripcion) {
		return jdbc.queryForObject("SELECT id FROM movimiento_bancario WHERE descripcion = ?", Long.class, descripcion);
	}

	/**
	 * F22: el número del banco difiere en un carácter del registrado (¿el Yape de otra familia?). Se sugiere en rojo; la
	 * confirma Administración (no la cajera) y solo con el extracto confirmado.
	 */
	@Test
	void laSugeridaConNumeroParecidoVaEnRojoYLaConfirmaAdministracion() {
		Long pago = yape("YP123456");
		reloj.fijar(LUNES);
		Extracto banco = extracto("100.00").abono("2026-10-02", "YAPE RECIBIDO", "YP123457", "450.00");
		registrar(extractos, ADMINISTRACION, banco);
		assertThat(partidas(jdbc)).containsExactly("SUGERIDA PROPUESTA PAGO");
		Long sugerida = partida();
		// Sin el extracto confirmado a ciegas, nadie confirma parejas.
		como(ADMINISTRACION);
		assertThatThrownBy(() -> servicio.confirmarSugerida(sugerida)).isInstanceOf(ReglaNegocioException.class);

		confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		assertThat(partidas(jdbc)).containsExactly("SUGERIDA PROPUESTA PAGO");
		VistaDiferencias vista = resumen.diferencias();
		assertThat(vista.sugeridas()).singleElement().satisfies(s -> {
			assertThat(s.enRojo()).isTrue();
			assertThat(s.avisos()).contains(ReglasEmparejamiento.NUMERO_PARECIDO);
			assertThat(s.operacionBanco()).isEqualTo("YP123457");
			assertThat(s.operacionObjeto()).isEqualTo("YP123456");
		});
		// La cajera no resuelve la conciliación; Promotoría y Dirección la miran, pero no la operan.
		como(CAJA);
		assertThatThrownBy(() -> servicio.confirmarSugerida(sugerida)).isInstanceOf(AccessDeniedException.class);
		como(DIRECCION);
		assertThatThrownBy(() -> servicio.confirmarSugerida(sugerida)).isInstanceOf(AccessDeniedException.class);

		como(ADMINISTRACION);
		servicio.confirmarSugerida(sugerida);
		assertThatThrownBy(() -> servicio.confirmarSugerida(sugerida)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya se resolvió");
		assertThat(partidas(jdbc)).containsExactly("SUGERIDA CONFIRMADA PAGO");
		assertThat(verificacionDePago(jdbc, pago)).isEqualTo("AUTOMATICA ENCONTRADO");
		assertThat(ultimoEvento(jdbc, "PARTIDA_SUGERIDA_CONFIRMADA").get("detalle")).asString()
				.contains("YP123457", "YP123456", "NÚMERO PARECIDO");
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.ATENCION
				&& a.texto().startsWith("Esta semana Administración confirmó 1 pareja(s) sugerida(s)"));
	}

	/**
	 * Descartada con nota, la pareja no se vuelve a proponer; se pide emparejar a mano (con nota, mismo monto) y recién
	 * verifica cuando otra persona de Promotoría la aprueba en la bandeja (S4-C1). Mientras tanto el Yape sigue en rojo.
	 */
	@Test
	void descartarYEmparejarAMano() {
		Long pago = yape("YP123456");
		reloj.fijar(LUNES);
		Extracto banco = extracto("100.00").abono("2026-10-02", "YAPE RECIBIDO", "YP654321", "450.00");
		registrar(extractos, ADMINISTRACION, banco);
		confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		Long sugerida = partida();

		como(ADMINISTRACION);
		assertThatThrownBy(() -> servicio.descartar(sugerida, "no")).isInstanceOf(ReglaNegocioException.class);
		servicio.descartar(sugerida, "No es el mismo Yape: el voucher de la familia trae otra hora");
		assertThat(partidas(jdbc)).isEmpty();
		assertThat(aplicador.conciliar(1L).propuestas()).isZero();
		assertThat(partidas(jdbc)).isEmpty();
		como(PROMOTORIA);
		VistaDiferencias vista = resumen.diferencias();
		assertThat(vista.sinPareja()).singleElement().satisfies(s -> assertThat(s.posibles()).isNotEmpty());
		assertThat(vista.faltantes()).singleElement().satisfies(fal -> assertThat(fal.operacion()).isEqualTo("YP123456"));

		como(ADMINISTRACION);
		Long abono = movimiento("YAPE RECIBIDO");
		assertThat(servicio.posibles(abono)).anyMatch(o -> o.tipo() == ObjetoPartida.PAGO && o.id().equals(pago));
		assertThatThrownBy(() -> servicio.emparejarManual(abono, ObjetoPartida.PAGO, pago, ""))
				.isInstanceOf(ReglaNegocioException.class);
		servicio.emparejarManual(abono, ObjetoPartida.PAGO, pago, NOTA);
		assertThat(partidas(jdbc)).containsExactly("MANUAL PROPUESTA PAGO");
		assertThat(verificacionDePago(jdbc, pago)).isNull();
		assertThat(ultimoEvento(jdbc, "PARTIDA_MANUAL_REGISTRADA").get("detalle")).asString()
				.contains("operación distinta", NOTA);
		assertThatThrownBy(() -> servicio.emparejarManual(abono, ObjetoPartida.PAGO, pago, NOTA))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya tiene pareja");
		como(PROMOTORIA);
		assertThat(resumen.diferencias().faltantes()).singleElement()
				.satisfies(fal -> assertThat(fal.operacion()).isEqualTo("YP123456"));
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA && a.texto().contains("YP123456"));

		Long partida = jdbc.queryForObject("SELECT id FROM partida_conciliacion WHERE regla = 'MANUAL'", Long.class);
		assertThatThrownBy(() -> EscenarioAprobaciones.aprueba(PROMOTORIA, bandeja, jdbc, "partida_conciliacion", partida))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("comentario");
		EscenarioAprobaciones.aprueba(PROMOTORIA, bandeja, jdbc, "partida_conciliacion", partida,
				"Lo vi en la app del banco: es el Yape de la familia Quispe");
		assertThat(partidas(jdbc)).containsExactly("MANUAL CONFIRMADA PAGO");
		assertThat(verificacionDePago(jdbc, pago)).isEqualTo("AUTOMATICA ENCONTRADO");
		assertThat(jdbc.queryForObject("SELECT banco_monto FROM verificacion_bancaria WHERE pago_id = ?",
				java.math.BigDecimal.class, pago)).isEqualByComparingTo("450.00");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'PARTIDA_MANUAL_APROBADA'")).isEqualTo(1);
		como(PROMOTORIA);
		assertThat(resumen.diferencias().sinDiferencias()).isTrue();
	}

	@Test
	void unMovimientoAjenoSeExplicaConCategoriaYNota() {
		reloj.fijar(LUNES);
		Extracto banco = extracto("100.00").abono("2026-10-02", "INTERESES GANADOS", "", "1.23")
				.cargo("2026-10-02", "COMISION MANTENIMIENTO", "", "15.00");
		registrar(extractos, ADMINISTRACION, banco);
		confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		Long intereses = movimiento("INTERESES GANADOS");
		como(PROMOTORIA);
		assertThat(resumen.diferencias().sinPareja()).singleElement()
				.satisfies(s -> assertThat(s.movimientoId()).isEqualTo(intereses));

		como(ADMINISTRACION);
		assertThatThrownBy(() -> servicio.explicar(intereses, CategoriaExplicacion.COMISION_BANCARIA, NOTA))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no corresponde");
		assertThatThrownBy(() -> servicio.explicar(intereses, CategoriaExplicacion.INTERESES, ""))
				.isInstanceOf(ReglaNegocioException.class);
		servicio.explicar(intereses, CategoriaExplicacion.INTERESES, "Intereses de la cuenta de ahorros del mes");
		// S4-A2: un CARGO no lo explica quien subió el extracto; lo explica Promotoría mirando su app del banco.
		Long comision = movimiento("COMISION MANTENIMIENTO");
		assertThatThrownBy(() -> servicio.explicar(comision, CategoriaExplicacion.COMISION_BANCARIA,
				"Comisión mensual por mantenimiento de la cuenta")).isInstanceOf(AutoaprobacionException.class);
		como(PROMOTORIA);
		servicio.explicar(comision, CategoriaExplicacion.COMISION_BANCARIA,
				"Comisión mensual por mantenimiento de la cuenta, la vi en la app");
		assertThat(partidas(jdbc)).containsExactly("EXPLICADA CONFIRMADA EXPLICACION",
				"EXPLICADA CONFIRMADA EXPLICACION");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'MOVIMIENTO_EXPLICADO'")).isEqualTo(2);
		como(PROMOTORIA);
		assertThat(resumen.diferencias().sinDiferencias()).isTrue();
		assertThat(indicadores.indicadores()).extracting(IndicadoresInicio.Indicador::etiqueta,
				IndicadoresInicio.Indicador::valor).contains(org.assertj.core.groups.Tuple.tuple(
						"Conciliado (30 días)", "100 %"));
	}

	/**
	 * Un abono del banco que nadie registró: ATENCIÓN el día hábil siguiente y CRÍTICO pasados 2 días hábiles (dinero
	 * que entró y nadie registró).
	 */
	@Test
	void unAbonoSinParejaEscalaACritico() {
		reloj.fijar(LUNES);
		Extracto banco = extracto("100.00").abono("2026-10-02", "YAPE RECIBIDO", "YP777000", "450.00");
		registrar(extractos, ADMINISTRACION, banco);
		confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.ATENCION
				&& a.texto().startsWith("1 abono(s) en el banco por S/ 450.00 sin pareja"));

		reloj.fijar(LUNES.plusSeconds(3 * 24 * 3600));
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().startsWith("1 abono(s) en el banco por S/ 450.00 llevan más de 2 días hábiles sin pareja"));
	}

	/** El Yape del viernes que la cajera registra recién el lunes: la pasada siguiente del sistema lo propone. */
	@Test
	void elPagoRegistradoTardeSeEmparejaEnLaPasadaSiguiente() {
		reloj.fijar(LUNES);
		Extracto banco = extracto("100.00").abono("2026-10-02", "YAPE RECIBIDO", "YP777000", "450.00");
		registrar(extractos, ADMINISTRACION, banco);
		confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		assertThat(partidas(jdbc)).isEmpty();

		Long pago = yape("YP777000");
		assertThat(aplicador.conciliar(1L).propuestas()).isEqualTo(1);
		assertThat(partidas(jdbc)).containsExactly("SUGERIDA PROPUESTA PAGO");
		assertThat(verificacionDePago(jdbc, pago)).isNull();
	}

	@Test
	void unaParejaExactaNoSeDescartaNiLaConfirmaUnaPersona() {
		yape("YP123456");
		reloj.fijar(LUNES);
		Extracto banco = extracto("100.00").abono("2026-10-02", "YAPE RECIBIDO", "YP123456", "450.00");
		registrar(extractos, ADMINISTRACION, banco);
		Long exacta = partida();
		como(ADMINISTRACION);
		assertThatThrownBy(() -> servicio.descartar(exacta, "Quiero emparejarlo con otro pago de la familia"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no se descarta");
		assertThatThrownBy(() -> servicio.confirmarSugerida(exacta)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no es una sugerida");
		assertThat(partidas(jdbc)).containsExactly("EXACTA PROPUESTA PAGO");
	}
}
