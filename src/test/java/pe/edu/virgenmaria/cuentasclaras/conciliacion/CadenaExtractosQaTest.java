package pe.edu.virgenmaria.cuentasclaras.conciliacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
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
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.VistaPreviaExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.AlertasConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ExtractoDiscontinuoException;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.estadoExtracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.partidas;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * QA del sprint 4 (extracto): la cadena de saldos con huecos y superposiciones, y un fraude que la confirmación a
 * ciegas del saldo final no puede ver.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CadenaExtractosQaTest {

	/** Martes 6 de octubre de 2026, 09:00 en Lima. */
	static final Instant MARTES = Instant.parse("2026-10-06T14:00:00Z");

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

	private Long subidoYConfirmado(Extracto banco) {
		Long id = registrar(extractos, ADMINISTRACION, banco);
		confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		assertThat(estadoExtracto(jdbc, id)).isEqualTo("CONFIRMADO");
		return id;
	}

	@Test
	void debeRechazarUnExtractoQueRepiteSoloParteDeUnDiaYaCargado() {
		reloj.fijar(MARTES);
		subidoYConfirmado(extracto("10000.00").abono("2026-10-01", "ABONO A", "", "100.00")
				.abono("2026-10-01", "ABONO B", "", "200.00"));
		// El segundo archivo empieza a mitad del 01/10: falta el «ABONO A» que ya está guardado.
		Extracto parcial = extracto("10100.00").abono("2026-10-01", "ABONO B", "", "200.00")
				.abono("2026-10-02", "ABONO C", "", "50.00");

		assertThatThrownBy(() -> EscenarioConciliacion.previa(extractos, ADMINISTRACION, parcial))
				.isInstanceOf(ExtractoDiscontinuoException.class);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'EXTRACTO_DISCONTINUO'")).isEqualTo(1);
		assertThat(contar(jdbc, "extracto_bancario")).isEqualTo(1);
	}

	@Test
	void debeAceptarUnExtractoQueContinuaTrasUnDiaHabilSinMovimientos() {
		reloj.fijar(MARTES);
		subidoYConfirmado(extracto("10000.00").abono("2026-10-01", "ABONO A", "", "100.00"));

		Long segundo = registrar(extractos, ADMINISTRACION, extracto("10100.00").abono("2026-10-05", "ABONO B", "",
				"50.00"));

		assertThat(jdbc.queryForObject("SELECT desde FROM extracto_bancario WHERE id = ?", LocalDate.class, segundo))
				.isEqualTo(LocalDate.of(2026, 10, 2));
		assertThat(jdbc.queryForObject("SELECT hasta FROM extracto_bancario WHERE id = ?", LocalDate.class, segundo))
				.isEqualTo(LocalDate.of(2026, 10, 5));
	}

	@Test
	void debeRechazarUnExtractoQueOmiteUnDiaConMovimientos() {
		reloj.fijar(MARTES);
		subidoYConfirmado(extracto("10000.00").abono("2026-10-01", "ABONO A", "", "100.00"));
		// Falta el 02/10 (un abono de 50.00): el primer saldo del archivo no continúa al del extracto anterior.
		Extracto conHueco = extracto("10150.00").abono("2026-10-05", "ABONO C", "", "80.00");

		VistaPreviaExtracto previa = EscenarioConciliacion.previa(extractos, ADMINISTRACION, conHueco);

		assertThat(previa.problema()).contains("no continúa");
		assertThatThrownBy(() -> extractos.registrar(previa, previa.token())).isInstanceOf(ReglaNegocioException.class);
		assertThat(contar(jdbc, "extracto_bancario")).isEqualTo(1);
	}

	@Test
	void debeOmitirLosDiasRepetidosIdenticosYCargarSoloLosNuevos() {
		reloj.fijar(MARTES);
		subidoYConfirmado(extracto("10000.00").abono("2026-10-01", "ABONO A", "OPA001", "100.00"));

		Long segundo = registrar(extractos, ADMINISTRACION, extracto("10000.00").abono("2026-10-01", "ABONO A", "OPA001",
				"100.00").abono("2026-10-02", "ABONO B", "OPB001", "0.01"));

		assertThat(contar(jdbc, "movimiento_bancario WHERE extracto_id = " + segundo)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT saldo_final FROM extracto_bancario WHERE id = ?", java.math.BigDecimal.class,
				segundo)).isEqualByComparingTo("10100.01");
	}

	@Test
	void debeEscalarElAbonoSinParejaACriticoSoloDespuesDeDosDiasHabiles() {
		reloj.fijar(Instant.parse("2026-10-05T14:00:00Z"));
		subidoYConfirmado(extracto("10000.00").abono("2026-10-02", "ABONO DESCONOCIDO", "", "77.00"));

		// Martes 06/10: 2 días hábiles después del viernes (lunes y martes); todavía es ATENCIÓN.
		reloj.fijar(Instant.parse("2026-10-06T14:00:00Z"));
		como(PROMOTORIA);
		assertThat(alertas.alertas()).filteredOn(a -> a.texto().contains("abono(s) en el banco"))
				.extracting(AlertaRevision::gravedad).containsExactly(Gravedad.ATENCION);

		// Miércoles 07/10: 3 días hábiles; ya es CRÍTICA.
		reloj.fijar(Instant.parse("2026-10-07T14:00:00Z"));
		assertThat(alertas.alertas()).filteredOn(a -> a.texto().contains("abono(s) en el banco"))
				.extracting(AlertaRevision::gravedad).containsExactly(Gravedad.CRITICA);
	}

	/**
	 * F14 con un par compensado: Administración agrega al extracto el abono del Yape inventado y, junto a él, un cargo
	 * del mismo monto con una glosa cualquiera. Los saldos corren bien, el saldo final es EL DEL BANCO (Promotoría lo
	 * confirma a ciegas sin problema), el Yape inventado queda conciliado EXACTO y el cargo inventado no alerta porque
	 * «un cargo sin pareja no se alerta» (sección 10.4). El control del caso original queda burlado sin colusión.
	 */
	@Test
	void debeAlertarUnAbonoYUnCargoInventadosQueSeCompensanEnElExtracto() {
		como(CAJA);
		cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), MedioPago.YAPE, "YP999888",
				"450.00"));
		reloj.fijar(Instant.parse("2026-10-05T14:00:00Z"));
		Extracto delBanco = extracto("10000.00").cargo("2026-10-02", "MANTENIMIENTO DE CUENTA", "", "15.00");
		Extracto editado = extracto("10000.00").abono("2026-10-02", "YAPE DE ROSA QUISPE", "YP999888", "450.00")
				.cargo("2026-10-02", "PAGO SERVICIOS", "", "450.00")
				.cargo("2026-10-02", "MANTENIMIENTO DE CUENTA", "", "15.00");
		assertThat(editado.saldoFinal()).isEqualByComparingTo(delBanco.saldoFinal());

		Long id = registrar(extractos, ADMINISTRACION, editado);
		confirmar(extractos, PROMOTORIA, cuentaId, delBanco.saldoFinal().toPlainString());
		assertThat(estadoExtracto(jdbc, id)).isEqualTo("CONFIRMADO");
		assertThat(partidas(jdbc)).containsExactly("EXACTA CONFIRMADA PAGO");

		como(PROMOTORIA);
		List<AlertaRevision> lista = alertas.alertas();
		assertThat(lista).anyMatch(a -> a.gravedad() == Gravedad.CRITICA);
	}
}
