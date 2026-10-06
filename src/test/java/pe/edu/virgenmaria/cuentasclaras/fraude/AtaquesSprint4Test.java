package pe.edu.virgenmaria.cuentasclaras.fraude;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
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
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ObjetoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.AlertasConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioPartidas;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.partidas;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.verificacionDePago;

/**
 * Correcciones del sprint 4: los 7 ataques de la auditoría antifraude ({@code AtaquesSprint4Test} de la evidencia), que
 * antes FUNCIONABAN, ahora FALLAN. Cada prueba reproduce el ataque paso a paso y comprueba que el sistema lo impide o lo
 * deja en rojo para Promotoría (docs/arquitectura/sprint-4-correcciones.md).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AtaquesSprint4Test {

	static final Instant LUNES = Instant.parse("2026-10-05T14:00:00Z");

	@Autowired
	private ServicioExtractos extractos;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private AlertasConciliacion alertas;

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
	private ServicioPartidas servicioPartidas;

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

	private Long yapeInventado(String operacion) {
		como(CAJA);
		return cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), MedioPago.YAPE, operacion,
				"450.00"));
	}

	private List<AlertaRevision> alertasPromotoria() {
		como(PROMOTORIA);
		return alertas.alertas();
	}

	/**
	 * ATAQUE 6 (S4-C1): el Yape inventado por la cajera queda CRÍTICO; Administración lo empareja «a mano» con un abono de
	 * otro monto (intereses de S/ 0.35). Ahora se rechaza por el monto, no queda ninguna partida ni verificación y la
	 * alerta crítica sigue.
	 */
	@Test
	void ataque6EmparejarAManoConOtroMontoYaNoBorraLaAlertaCritica() {
		Long inventado = yapeInventado("YP999888");
		reloj.fijar(LUNES);
		Extracto banco = extracto("10000.00").abono("2026-10-02", "INTERESES", "", "0.35");
		Long id = registrar(extractos, ADMINISTRACION, banco);
		EscenarioConciliacion.confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		assertThat(alertasPromotoria()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA && a.texto().contains("YP999888"));
		Long movimiento = jdbc.queryForObject("SELECT id FROM movimiento_bancario WHERE extracto_id = ?", Long.class, id);

		como(ADMINISTRACION);
		assertThat(servicioPartidas.posibles(movimiento)).noneMatch(o -> o.id().equals(inventado));
		assertThatThrownBy(() -> servicioPartidas.emparejarManual(movimiento, ObjetoPartida.PAGO, inventado,
				"Yape agrupado por el banco")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("MISMO monto");

		assertThat(partidas(jdbc)).isEmpty();
		assertThat(verificacionDePago(jdbc, inventado)).isNull();
		assertThat(alertasPromotoria()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA && a.texto().contains("YP999888"));
	}

	/**
	 * S4-C1, variante con el MISMO monto (F16): Administración empareja el Yape inventado con un abono real ajeno de
	 * S/ 450. La pareja queda por aprobar, no verifica nada y la alerta sigue hasta que Promotoría la apruebe mirando su
	 * app del banco; si la rechaza, todo sigue en rojo.
	 */
	@Test
	void parejaManualDelMismoMontoEsperaLaBandejaYSiSeRechazaSigueEnRojo() {
		Long inventado = yapeInventado("YP999888");
		reloj.fijar(LUNES);
		Extracto banco = extracto("10000.00").abono("2026-10-02", "TRANSFERENCIA DE UN TERCERO", "TR55501", "450.00")
				.abono("2026-10-02", "OTRA TRANSFERENCIA", "TR55502", "450.00");
		Long id = registrar(extractos, ADMINISTRACION, banco);
		EscenarioConciliacion.confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		Long movimiento = jdbc.queryForObject("SELECT id FROM movimiento_bancario WHERE extracto_id = ? AND numero = 1",
				Long.class, id);

		como(ADMINISTRACION);
		servicioPartidas.emparejarManual(movimiento, ObjetoPartida.PAGO, inventado, "Yape que el banco registró como "
				+ "transferencia");
		assertThat(partidas(jdbc)).containsExactly("MANUAL PROPUESTA PAGO");
		assertThat(verificacionDePago(jdbc, inventado)).isNull();
		assertThat(alertasPromotoria()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA && a.texto().contains("YP999888"));

		// La cajera que cobró no puede aprobarla; Promotoría la rechaza porque en su app es la transferencia de un tercero.
		Long solicitud = jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'PARTIDA_MANUAL'", Long.class);
		como(PROMOTORIA);
		bandeja.rechazar(solicitud, "En la app del banco es una transferencia de un tercero, no el Yape de la familia");
		assertThat(partidas(jdbc)).isEmpty();
		assertThat(verificacionDePago(jdbc, inventado)).isNull();
		assertThat(alertasPromotoria()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA && a.texto().contains("YP999888"));
	}
}
