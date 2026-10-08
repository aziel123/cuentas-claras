package pe.edu.virgenmaria.cuentasclaras.panel.service;

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
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.CifrasCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AvanceMes;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.DeudaVencida;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.FamiliaMorosa;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.MorosidadGrado;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.Tramos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.CifrasResumen;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.FamiliaMorosaVista;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.ReporteMorosidad;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.VistaPanel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Las cifras del panel salen de los libros (P3) con una semilla conocida ({@link EscenarioPanel}): efectivo, Yape, un
 * pago anulado, un descuento aprobado y cuotas vencidas de dos familias. Montos exactos con escala 2.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class PanelPromotoriaTest {

	private static final LocalDate HOY = LocalDate.of(2027, 4, 15);

	@Autowired
	private PanelPromotoria panel;

	@Autowired
	private CifrasDelDia cifras;

	@Autowired
	private CifrasCaja caja;

	@Autowired
	private CifrasCobranza cobranza;

	@Autowired
	private ReportesCobranza reportes;

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

	/** P3: cada cifra del panel es la suma del libro de pagos y del de cuotas en ese momento. */
	@Test
	void lasCifrasSalenDeLosLibros() {
		como(EscenarioCobranza.PROMOTORIA);
		VistaPanel p = panel.ver();

		assertThat(p.fecha()).isEqualTo("15/04/2027");
		assertThat(p.hoy().cobrado()).isEqualTo("S/ 800.00");
		assertThat(p.hoy().pagos()).isEqualTo(2);
		assertThat(p.hoy().digital()).isEqualTo("50 %");
		assertThat(p.hoy().digitalMonto()).isEqualTo("56 %");
		assertThat(p.hoy().efectivo()).isEqualTo("S/ 350.00");
		assertThat(p.hoy().pagosEfectivo()).isEqualTo(1);
		assertThat(p.hoy().cajasAbiertas()).isEqualTo(1);
		assertThat(p.mes().nombre()).isEqualTo("abril");
		assertThat(p.mes().cobrado()).isEqualTo("S/ 800.00");
		assertThat(p.mes().anulado()).isEqualTo("S/ 350.00");
		assertThat(p.mes().anulados()).isEqualTo(1);
		assertThat(p.deuda().monto()).isEqualTo("S/ 1,600.00");
		assertThat(p.deuda().familias()).isEqualTo(2);
		assertThat(p.deuda().hasta60()).isEqualTo(2);
		assertThat(p.deuda().hasta30() + p.deuda().hasta90() + p.deuda().masDe90()).isZero();

		// Lo mismo, directo de las tablas.
		assertThat(jdbc.queryForObject("SELECT SUM(total) FROM pago WHERE estado = 'VIGENTE' AND fecha = ?",
				BigDecimal.class, HOY)).isEqualByComparingTo("800.00");
		assertThat(jdbc.queryForObject("SELECT SUM(monto - monto_pagado - monto_descuento) FROM cuota WHERE estado IN "
				+ "('PENDIENTE', 'PARCIAL') AND fecha_vencimiento < ?", BigDecimal.class, HOY))
				.isEqualByComparingTo("1600.00");
	}

	/** Un pago nuevo cambia la cifra al instante: no hay foto ni caché que maquillar. */
	@Test
	void unPagoNuevoSeVeAlInstante() {
		como(EscenarioCaja.CAJA);
		cobro.cobrar(EscenarioCaja.efectivo(datos.f().flores(), List.of(EscenarioCaja.cuota(jdbc, datos.f().sebastian(),
				"MAT-2027")), "350.00", "350.00"));
		como(EscenarioCobranza.PROMOTORIA);
		VistaPanel p = panel.ver();
		assertThat(p.hoy().cobrado()).isEqualTo("S/ 1,150.00");
		assertThat(p.deuda().monto()).isEqualTo("S/ 1,250.00");
		assertThat(p.deuda().familias()).isEqualTo(2);
	}

	/** P16: las rebajas del mes dicen cuánto se descontó y quién lo aprobó. */
	@Test
	void lasRebajasDelMesMuestranQuienAprobo() {
		como(EscenarioCobranza.PROMOTORIA);
		VistaPanel.Rebajas r = panel.ver().rebajas();
		assertThat(r.descuentos()).isEqualTo("S/ 45.00");
		assertThat(r.cantidadDescuentos()).isEqualTo(1);
		assertThat(r.aprobaronDescuentos()).containsExactly("director: 1 por S/ 45.00");
		assertThat(r.cuotasAnuladas()).isEqualTo("S/ 0.00");
		assertThat(r.cantidadCuotasAnuladas()).isZero();
	}

	@Test
	void cifrasDeCajaPorMedioYPorOrigen() {
		como(EscenarioCobranza.ADMINISTRACION);
		CobradoPeriodo c = caja.cobrado(HOY, HOY);
		assertThat(c.total()).isEqualByComparingTo("800.00").hasScaleOf(2);
		assertThat(c.porMedio()).extracting(CobradoPeriodo.PorMedio::medio, CobradoPeriodo.PorMedio::cantidad)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(MedioPago.EFECTIVO, 1L),
						org.assertj.core.groups.Tuple.tuple(MedioPago.YAPE, 1L));
		assertThat(c.porCanal()).singleElement().satisfies(o -> {
			assertThat(o.canal()).isEqualTo(CanalCaja.VENTANILLA);
			assertThat(o.total()).isEqualByComparingTo("800.00");
		});
		assertThat(caja.anulado(HOY, HOY).total()).isEqualByComparingTo("350.00").hasScaleOf(2);
		// Ningún pago en marzo.
		CobradoPeriodo marzo = caja.cobrado(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31));
		assertThat(marzo.total()).isEqualByComparingTo("0.00").hasScaleOf(2);
		assertThat(marzo.porcentajeDigital()).isEmpty();
	}

	@Test
	void cifrasDeCobranzaDeudaAvanceYMorosos() {
		como(EscenarioCobranza.DIRECCION);
		DeudaVencida deuda = cobranza.deudaVencida(HOY);
		assertThat(deuda.monto()).isEqualByComparingTo("1600.00").hasScaleOf(2);
		assertThat(deuda.cuotas()).isEqualTo(4);
		assertThat(deuda.tramos()).isEqualTo(new Tramos(0, 2, 0, 0));
		// El día del vencimiento aún no está vencido: al 28/02 no hay deuda vencida; al 01/03, solo las matrículas.
		assertThat(cobranza.deudaVencida(LocalDate.of(2027, 2, 28)).monto()).isEqualByComparingTo("0.00");
		assertThat(cobranza.deudaVencida(LocalDate.of(2027, 3, 1)).tramos()).isEqualTo(new Tramos(2, 0, 0, 0));

		// Abril: tres pensiones de S/ 450 menos el descuento de S/ 45; nada pagado aún.
		AvanceMes abril = cobranza.avanceDelMes(YearMonth.of(2027, 4));
		assertThat(abril.vence()).isEqualByComparingTo("1305.00");
		assertThat(abril.pagado()).isEqualByComparingTo("0.00");
		assertThat(abril.porcentaje()).hasValue(0);

		List<FamiliaMorosa> morosas = cobranza.familiasMorosas(HOY);
		assertThat(morosas).extracting(FamiliaMorosa::familiaId).containsExactlyInAnyOrder(datos.f().quispe(),
				datos.f().flores());
		assertThat(morosas).allSatisfy(m -> {
			assertThat(m.monto()).isEqualByComparingTo("800.00");
			assertThat(m.alumnos()).isEqualTo(1);
			assertThat(m.dias()).isEqualTo(46);
		});
	}

	/** Morosidad por grado del año 2027: una sola fila (6.° de Primaria), sin secciones. */
	@Test
	void laMorosidadSeAgrupaPorGradoYNoPorSeccion() {
		como(EscenarioCobranza.ADMINISTRACION);
		List<MorosidadGrado> filas = cobranza.morosidadPorGrado(datos.f().anio2027(), HOY);
		assertThat(filas).singleElement().satisfies(g -> {
			assertThat(g.grado()).isEqualTo(Grado.PRIMARIA_6);
			assertThat(g.matriculados()).isEqualTo(3);
			assertThat(g.conDeuda()).isEqualTo(2);
			assertThat(g.monto()).isEqualByComparingTo("1600.00");
			assertThat(g.tramos()).isEqualTo(new Tramos(0, 2, 0, 0));
		});
		ReporteMorosidad reporte = reportes.morosidadPorGrado(datos.f().anio2027());
		assertThat(reporte.filas()).extracting(ReporteMorosidad.Fila::grado).containsExactly(Grado.PRIMARIA_6.etiqueta());
		assertThat(reporte.filas().get(0).grado()).doesNotContain(" A");
		assertThat(reporte.puedeExportar()).isTrue();
	}

	@Test
	void laListaDeMorososNoLlevaContactosYTraeElUltimoAviso() {
		como(EscenarioCobranza.PROMOTORIA);
		List<FamiliaMorosaVista> lista = reportes.familiasMorosas();
		assertThat(lista).hasSize(2);
		assertThat(lista).allSatisfy(f -> {
			assertThat(f.monto()).isEqualTo("S/ 800.00");
			assertThat(f.ultimoAviso()).isEqualTo("Sin avisos entregados");
			assertThat(f.toString()).doesNotContain(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
					.CELULAR_ROSA, EscenarioCaja.DNI_SEBASTIAN, "912345678");
		});
	}

	/** El panel y el resumen (tanda 2) usan las mismas cifras del día. */
	@Test
	void cifrasDelDiaCoincidenConElPanel() {
		como(EscenarioCobranza.PROMOTORIA);
		CifrasResumen c = cifras.calcular(HOY);
		VistaPanel p = panel.ver();
		assertThat(pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero.formatear(c.dia().total())).isEqualTo(p.hoy().cobrado());
		assertThat(pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero.formatear(c.deuda().monto())).isEqualTo(p.deuda().monto());
		assertThat(c.cajas().abiertas()).isEqualTo(p.hoy().cajasAbiertas());
	}

	/** P14 en el servicio (segunda capa): Dirección no ve el panel y Caja no ve cifras ni morosidad. */
	@Test
	void soloPromotoriaVeElPanelYCajaNoVeLaMorosidad() {
		como(EscenarioCobranza.DIRECCION);
		assertThatThrownBy(panel::ver).isInstanceOf(AccessDeniedException.class);
		como(EscenarioCaja.CAJA);
		assertThatThrownBy(() -> cobranza.deudaVencida(HOY)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> caja.cobrado(HOY, HOY)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(reportes::familiasMorosas).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> reportes.morosidadPorGrado(null)).isInstanceOf(AccessDeniedException.class);
	}
}
