package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RetirarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.DeudaVencida;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.FamiliaMorosa;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.MorosidadGrado;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.VistaPanel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * QA sprint 6: bordes de las cifras del panel sobre la semilla conocida de {@link EscenarioPanel} (jueves 15/04/2027:
 * S/ 800.00 vigentes, S/ 350.00 anulados, descuento de S/ 45.00 y S/ 1,600.00 vencidos de 2 familias).
 * <p>
 * Escenarios (Dado / Cuando / Entonces):
 * <ul>
 *   <li>Dado un pago adelantado de mayo, cuando la promotora abre el panel en abril, entonces suma en lo cobrado de hoy
 *       y en el avance de mayo, no en el de abril.</li>
 *   <li>Dado un doble clic en «Cobrar» (misma clave), cuando se mira el panel, entonces el pago cuenta una sola vez.</li>
 *   <li>Dada una beca del 100 % sobre una cuota vencida, cuando se mira la deuda, entonces esa cuota ya no suma.</li>
 *   <li>Dado un descuento de hermanos del 12.5 % (redondeo a décimos), cuando se miran las rebajas, entonces suman
 *       exacto con escala 2.</li>
 *   <li>Dado un día en el que el único pago se anuló, cuando se mira el panel, entonces el % digital es «—» y no 0 %.</li>
 *   <li>Dado un alumno retirado a mitad de año, cuando pasan los meses, entonces sus pensiones posteriores al retiro no
 *       son deuda vencida ni lo vuelven moroso (QA-S6-3), y la morosidad por grado no cuenta más alumnos con deuda que
 *       matriculados (QA-S6-4).</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CifrasPanelBordesTest {

	private static final LocalDate HOY = LocalDate.of(2027, 4, 15);

	private static final String MOTIVO_RETIRO = "La familia se mudó a Arequipa y lo retiró del colegio";

	@Autowired
	private PanelPromotoria panel;

	@Autowired
	private CifrasCobranza cobranza;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioPanel.Datos datos;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		datos = EscenarioPanel.preparar(estructura, alumnos, planes, cobro, anulaciones, descuentos, bandeja, reloj, jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private void a(LocalDate dia, int hora, int minuto) {
		reloj.fijar(dia.atTime(hora, minuto).atZone(reloj.getZone()).toInstant());
	}

	private void retirar(Long alumnoId, LocalDate fecha) {
		como(EscenarioCobranza.ADMINISTRACION);
		alumnos.retirar(alumnoId, new RetirarAlumnoRequest(fecha, MOTIVO_RETIRO));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "alumno", alumnoId);
		SecurityContextHolder.clearContext();
	}

	@Test
	void debeSumarElPagoAdelantadoEnLoCobradoDeHoyYEnElAvanceDelMesEnQueVence() {
		como(EscenarioCaja.CAJA);
		cobro.cobrar(EscenarioCaja.digital(datos.f().quispe(), List.of(cuota(jdbc, datos.f().mateo(), "PEN-2027-05")),
				MedioPago.YAPE, "YP550099", "450.00"));

		como(EscenarioCobranza.PROMOTORIA);
		VistaPanel p = panel.ver();
		assertThat(p.hoy().cobrado()).isEqualTo("S/ 1,250.00");
		assertThat(p.hoy().pagos()).isEqualTo(3);
		assertThat(p.hoy().digital()).isEqualTo("67 %");
		assertThat(p.mes().pagadoDeLoQueVence()).as("abril no cambia: lo de mayo no vence en abril").isEqualTo("S/ 0.00");
		assertThat(cobranza.avanceDelMes(YearMonth.of(2027, 5)).pagado()).isEqualByComparingTo("450.00").hasScaleOf(2);
		assertThat(p.deuda().monto()).as("adelantar mayo no baja lo vencido").isEqualTo("S/ 1,600.00");
	}

	@Test
	void debeContarUnaSolaVezUnCobroRepetidoConLaMismaClave() {
		como(EscenarioCaja.CAJA);
		CobroRequest dobleClic = EscenarioCaja.efectivo(datos.f().flores(), List.of(cuota(jdbc, datos.f().sebastian(),
				"MAT-2027")), "350.00", "350.00");
		Long primero = cobro.cobrar(dobleClic);
		Long segundo = cobro.cobrar(dobleClic);

		assertThat(segundo).isEqualTo(primero);
		como(EscenarioCobranza.PROMOTORIA);
		VistaPanel p = panel.ver();
		assertThat(p.hoy().pagos()).isEqualTo(3);
		assertThat(p.hoy().cobrado()).isEqualTo("S/ 1,150.00");
		assertThat(p.hoy().efectivo()).isEqualTo("S/ 700.00");
		assertThat(jdbc.queryForObject("SELECT SUM(total) FROM pago WHERE estado = 'VIGENTE' AND fecha = ?",
				BigDecimal.class, HOY)).isEqualByComparingTo("1150.00");
	}

	@Test
	void debeSacarDeLaDeudaVencidaUnaCuotaVencidaConBecaDelCienPorCiento() {
		como(EscenarioCobranza.ADMINISTRACION);
		Long beca = descuentos.solicitar(EscenarioAprobaciones.descuento(datos.f().sebastian(), TipoDescuento.BECA, "100",
				List.of(cuota(jdbc, datos.f().sebastian(), "PEN-2027-03"))));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "descuento", beca);

		como(EscenarioCobranza.PROMOTORIA);
		VistaPanel p = panel.ver();
		assertThat(p.deuda().monto()).isEqualTo("S/ 1,150.00");
		assertThat(p.deuda().familias()).as("a Sebastián aún le falta su matrícula").isEqualTo(2);
		assertThat(p.rebajas().descuentos()).isEqualTo("S/ 495.00");
		assertThat(p.rebajas().cantidadDescuentos()).isEqualTo(2);
		assertThat(EscenarioCaja.estado(jdbc, cuota(jdbc, datos.f().sebastian(), "PEN-2027-03"))).isEqualTo("EXONERADA");
		assertThat(jdbc.queryForObject("SELECT SUM(monto - monto_pagado - monto_descuento) FROM cuota WHERE estado IN "
				+ "('PENDIENTE', 'PARCIAL') AND fecha_vencimiento < ?", BigDecimal.class, HOY))
				.isEqualByComparingTo("1150.00");
	}

	@Test
	void debeSumarLasRebajasConEscalaDosCuandoElDescuentoDeHermanosRedondeaADecimos() {
		como(EscenarioCobranza.ADMINISTRACION);
		Long hermanos = descuentos.solicitar(EscenarioAprobaciones.descuento(datos.f().mateo(), TipoDescuento.HERMANOS,
				"12.5", List.of(cuota(jdbc, datos.f().mateo(), "PEN-2027-05"))));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "descuento", hermanos);

		// 450 × 87.5 % = 393.75: la familia paga 393.70 (décimos a su favor) y el descuento es 56.30.
		assertThat(jdbc.queryForObject("SELECT monto_descuento FROM cuota WHERE id = ?", BigDecimal.class,
				cuota(jdbc, datos.f().mateo(), "PEN-2027-05"))).isEqualByComparingTo("56.30");
		como(EscenarioCobranza.PROMOTORIA);
		VistaPanel.Rebajas r = panel.ver().rebajas();
		assertThat(r.descuentos()).isEqualTo("S/ 101.30");
		assertThat(r.aprobaronDescuentos()).containsExactly("director: 2 por S/ 101.30");
		assertThat(cobranza.rebajas(HOY.withDayOfMonth(1), HOY).descuentos()).isEqualByComparingTo("101.30").hasScaleOf(2);
	}

	@Test
	void debeMostrarGuionYNoCeroPorCientoCuandoElUnicoPagoDelDiaSeAnulo() {
		LocalDate viernes = HOY.plusDays(1);
		a(viernes, 9, 0);
		como(EscenarioCaja.CAJA_2);
		Long pago = cobro.cobrar(EscenarioCaja.efectivo(datos.f().flores(), List.of(cuota(jdbc, datos.f().sebastian(),
				"MAT-2027")), "350.00", "350.00"));
		anulaciones.solicitarDevolucion(pago, EscenarioAprobaciones.MOTIVO_ANULACION);
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "pago", pago);

		como(EscenarioCobranza.PROMOTORIA);
		VistaPanel p = panel.ver();
		assertThat(p.hoy().cobrado()).isEqualTo("S/ 0.00");
		assertThat(p.hoy().pagos()).isZero();
		assertThat(p.hoy().digital()).isEqualTo("—");
		assertThat(p.hoy().digitalMonto()).isEqualTo("—");
		assertThat(p.mes().cobrado()).isEqualTo("S/ 800.00");
		assertThat(p.mes().anulado()).isEqualTo("S/ 700.00");
		assertThat(p.mes().anulados()).isEqualTo(2);
	}

	/**
	 * QA-S6-3. Dado Sebastián retirado (con aprobación) el 15/04/2027, cuando la promotora mira el panel el 15/06/2027,
	 * entonces la pensión de mayo (que vence el 31/05, todo el periodo después del retiro) no es deuda vencida de la
	 * familia Flores. Hoy suma S/ 450.00 por mes después del retiro: la familia aparece morosa por pensiones que no
	 * corresponden y la deuda vencida del panel y del resumen crece sola.
	 */
	@Test
	void debeExcluirDeLaDeudaVencidaLasPensionesQueVencenDespuesDelRetiro() {
		retirar(datos.f().sebastian(), HOY);
		LocalDate junio = LocalDate.of(2027, 6, 15);
		a(junio, 9, 0);

		como(EscenarioCobranza.PROMOTORIA);
		FamiliaMorosa flores = cobranza.familiasMorosas(junio).stream()
				.filter(f -> f.familiaId().equals(datos.f().flores())).findFirst().orElseThrow();
		// Matrícula (28/02), marzo (31/03) y abril (30/04, mes del retiro) como mucho: S/ 1,250.00. Mayo no.
		assertThat(flores.monto()).isLessThanOrEqualTo(new BigDecimal("1250.00"));
		DeudaVencida deuda = cobranza.deudaVencida(junio);
		BigDecimal mayoDeSebastian = jdbc.queryForObject("SELECT monto FROM cuota WHERE id = ?", BigDecimal.class,
				cuota(jdbc, datos.f().sebastian(), "PEN-2027-05"));
		BigDecimal todoLoPendiente = jdbc.queryForObject("SELECT SUM(monto - monto_pagado - monto_descuento) FROM cuota "
				+ "WHERE estado IN ('PENDIENTE', 'PARCIAL') AND fecha_vencimiento < ?", BigDecimal.class, junio);
		assertThat(deuda.monto()).isLessThanOrEqualTo(todoLoPendiente.subtract(mayoDeSebastian));
	}

	/**
	 * QA-S6-4. Dados Valeria y Sebastián retirados con deuda vencida y Mateo como único matriculado ACTIVO de 6.° de
	 * Primaria, cuando Administración mira la morosidad por grado, entonces «alumnos con deuda» no puede ser mayor que
	 * «matriculados» (hoy la pantalla dice «2 con deuda vencida de 1 matriculado(s)» y el Excel lo repite).
	 */
	@Test
	void laMorosidadPorGradoNoDebeTenerMasAlumnosConDeudaQueMatriculados() {
		retirar(datos.f().valeria(), HOY);
		retirar(datos.f().sebastian(), HOY);

		como(EscenarioCobranza.ADMINISTRACION);
		List<MorosidadGrado> filas = cobranza.morosidadPorGrado(datos.f().anio2027(), HOY);
		assertThat(filas).isNotEmpty().allSatisfy(g -> assertThat(g.conDeuda()).as(g.etiqueta())
				.isLessThanOrEqualTo(g.matriculados()));
	}
}
