package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.caja.service.CifrasCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AvanceMes;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.DeudaVencida;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
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
 * QA sprint 6: las cifras del panel en los bordes de mes y de año, con la hora de Lima (la suite también corre con
 * {@code -Duser.timezone=America/Los_Angeles}).
 * <ul>
 *   <li>Dado un cobro a las 23:50 del 31/03 y otro a las 00:10 del 01/04 (hora de Lima), cuando se mira el panel del 01/04,
 *       entonces el de las 23:50 es de marzo y el de las 00:10 es de abril.</li>
 *   <li>Dada la pensión de diciembre, cuando llega el día siguiente a su vencimiento (aunque sea de otro año), entonces
 *       es deuda vencida; el mismo día del vencimiento, todavía no.</li>
 *   <li>Dado el primer día hábil de enero, cuando la promotora abre el panel, entonces el mes es enero, sin cobros y sin
 *       avance («—»), y la deuda vencida es la de los libros.</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CifrasPanelFechasTest {

	@Autowired
	private PanelPromotoria panel;

	@Autowired
	private CifrasCaja caja;

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
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioCaja.Familias f;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
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

	@Test
	void debeAsignarCadaCobroAlMesDeLimaAunqueSeHagaCercaDeLaMedianoche() {
		LocalDate marzo31 = LocalDate.of(2027, 3, 31);
		LocalDate abril1 = LocalDate.of(2027, 4, 1);
		a(marzo31, 23, 50);
		como(EscenarioCaja.CAJA);
		Long deMarzo = cobro.cobrar(EscenarioCaja.efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "MAT-2027")),
				"350.00", "350.00"));
		a(abril1, 0, 10);
		como(EscenarioCaja.CAJA_2);
		Long deAbril = cobro.cobrar(EscenarioCaja.efectivo(f.flores(), List.of(cuota(jdbc, f.sebastian(), "MAT-2027")),
				"350.00", "350.00"));

		assertThat(jdbc.queryForObject("SELECT fecha FROM pago WHERE id = ?", LocalDate.class, deMarzo)).isEqualTo(marzo31);
		assertThat(jdbc.queryForObject("SELECT fecha FROM pago WHERE id = ?", LocalDate.class, deAbril)).isEqualTo(abril1);
		a(abril1, 10, 0);
		como(EscenarioCobranza.PROMOTORIA);
		VistaPanel p = panel.ver();
		assertThat(p.mes().nombre()).isEqualTo("abril");
		assertThat(p.hoy().cobrado()).isEqualTo("S/ 350.00");
		assertThat(p.mes().cobrado()).as("el mes empieza hoy: no arrastra el 31/03").isEqualTo("S/ 350.00");
		como(EscenarioCobranza.ADMINISTRACION);
		CobradoPeriodo marzo = caja.cobrado(LocalDate.of(2027, 3, 1), marzo31);
		assertThat(marzo.total()).isEqualByComparingTo("350.00").hasScaleOf(2);
		assertThat(marzo.cantidad()).isEqualTo(1);
	}

	@Test
	void debeVencerLaPensionDeDiciembreRecienAlDiaSiguienteAunqueSeaDeOtroAnio() {
		LocalDate vence = jdbc.queryForObject("SELECT fecha_vencimiento FROM cuota WHERE alumno_id = ? AND obligacion = ?",
				LocalDate.class, f.mateo(), "PEN-2027-12");
		BigDecimal deDiciembre = jdbc.queryForObject("SELECT SUM(monto) FROM cuota WHERE fecha_vencimiento = ? AND estado "
				+ "IN ('PENDIENTE', 'PARCIAL')", BigDecimal.class, vence);

		como(EscenarioCobranza.DIRECCION);
		DeudaVencida elMismoDia = cobranza.deudaVencida(vence);
		DeudaVencida alDiaSiguiente = cobranza.deudaVencida(vence.plusDays(1));
		assertThat(alDiaSiguiente.monto().subtract(elMismoDia.monto())).isEqualByComparingTo(deDiciembre);
		assertThat(alDiaSiguiente.monto()).hasScaleOf(2);
		assertThat(alDiaSiguiente.cuotas() - elMismoDia.cuotas()).isEqualTo(3);
		assertThat(alDiaSiguiente.familias()).isEqualTo(2);
	}

	@Test
	void elPanelDelPrimerDiaHabilDeEneroMuestraEneroVacioYLaDeudaDeLosLibros() {
		LocalDate enero = LocalDate.of(2028, 1, 3);
		a(enero, 8, 0);

		como(EscenarioCobranza.PROMOTORIA);
		VistaPanel p = panel.ver();
		assertThat(p.fecha()).isEqualTo("03/01/2028");
		assertThat(p.mes().nombre()).isEqualTo("enero");
		assertThat(p.mes().cobrado()).isEqualTo("S/ 0.00");
		assertThat(p.mes().avance()).as("en enero no vence nada: no es 0 %").isEqualTo("—");
		assertThat(p.hoy().digital()).isEqualTo("—");
		BigDecimal libros = jdbc.queryForObject("SELECT SUM(monto - monto_pagado - monto_descuento) FROM cuota WHERE "
				+ "estado IN ('PENDIENTE', 'PARCIAL') AND fecha_vencimiento < ?", BigDecimal.class, enero);
		como(EscenarioCobranza.DIRECCION);
		assertThat(cobranza.deudaVencida(enero).monto()).isEqualByComparingTo(libros).hasScaleOf(2);
		AvanceMes eneroAvance = cobranza.avanceDelMes(YearMonth.of(2028, 1));
		assertThat(eneroAvance.vence()).isEqualByComparingTo("0.00").hasScaleOf(2);
		assertThat(eneroAvance.porcentaje()).isEmpty();
	}
}
