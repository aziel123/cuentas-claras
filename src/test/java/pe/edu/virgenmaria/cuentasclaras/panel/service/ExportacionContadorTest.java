package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.PagoExportable;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.CifrasCaja;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.EscritorXlsxSeguro;
import pe.edu.virgenmaria.cuentasclaras.panel.config.PropiedadesPanel;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.ArchivoExportado;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Orden de la exportación: límites, armado, SHA-256 y bitácora ANTES de devolver los bytes (P12). */
class ExportacionContadorTest {

	private static final LocalDate DIA = LocalDate.of(2027, 4, 15);

	private final CifrasCaja caja = mock(CifrasCaja.class);

	private final CifrasCobranza cobranza = mock(CifrasCobranza.class);

	private final AuditoriaService auditoria = mock(AuditoriaService.class);

	private ExportacionContador exportacion;

	/** Los valores por defecto de cuentasclaras.panel (decisión 73). */
	static final PropiedadesPanel PROPIEDADES = new PropiedadesPanel(10, 35, java.time.LocalTime.of(21, 0), 12, 20, 20000,
			5, 3);

	@BeforeEach
	void preparar() {
		Clock reloj = Clock.fixed(Instant.parse("2027-04-15T15:00:00Z"), ZoneId.of("America/Lima"));
		exportacion = new ExportacionContador(caja, cobranza, new EscritorXlsxSeguro(), auditoria, reloj, PROPIEDADES);
		PagoExportable pago = new PagoExportable(DIA, "B001-1", TipoComprobante.BOLETA, MedioPago.EFECTIVO,
				CanalCaja.VENTANILLA, null, new BigDecimal("350.00"), EstadoPago.VIGENTE, null, null, "Matrícula 2027", 7L,
				"caja", null);
		when(caja.pagosEnRango(DIA, DIA)).thenReturn(1L);
		when(caja.pagosParaContador(DIA, DIA)).thenReturn(List.of(pago));
		when(caja.cobrado(DIA, DIA)).thenReturn(new CobradoPeriodo(DIA, DIA, new BigDecimal("350.00"), 1,
				new BigDecimal("350.00"), 1, List.of(new CobradoPeriodo.PorMedio(MedioPago.EFECTIVO, 1,
						new BigDecimal("350.00"))), List.of()));
	}

	@Test
	void sinBitacoraNoHayArchivo() {
		when(auditoria.registrar(eq(AccionAuditoria.REPORTE_EXPORTADO), anyString(), anyString(), isNull(), isNull(),
				anyString())).thenThrow(new IllegalStateException("la bitácora no responde"));
		assertThatThrownBy(() -> exportacion.exportarIngresos(DIA, DIA)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("bitácora");
	}

	@Test
	void laBitacoraLlevaElShaYElCodigoDelArchivo() {
		ArchivoExportado archivo = exportacion.exportarIngresos(DIA, DIA);
		verify(auditoria).registrar(eq(AccionAuditoria.REPORTE_EXPORTADO), eq("reporte"), eq(archivo.codigo()), isNull(),
				isNull(), contains("sha256=" + ExportacionContador.sha256(archivo.contenido())));
		assertThat(archivo.nombre()).isEqualTo("ingresos-2027-04.xlsx");
	}

	@Test
	void rangoDeMasDe12MesesSeRechazaYQuedaEnLaBitacora() {
		assertThatThrownBy(() -> exportacion.exportarIngresos(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 4, 1)))
				.isInstanceOf(ExportacionRechazadaException.class);
		verify(auditoria).registrar(eq(AccionAuditoria.EXPORTACION_RECHAZADA), eq("reporte"), eq("INGRESOS"), isNull(),
				isNull(), contains("rango"));
		verify(caja, never()).pagosParaContador(any(), any());
	}

	@Test
	void topeDiarioDe20() {
		when(auditoria.contarDesdeDelUsuarioActual(eq(AccionAuditoria.REPORTE_EXPORTADO), any()))
				.thenReturn((long) PROPIEDADES.exportacionMaxDiarias());
		assertThatThrownBy(() -> exportacion.exportarIngresos(DIA, DIA)).isInstanceOf(ExportacionRechazadaException.class)
				.hasMessageContaining("20");
		verify(auditoria).registrar(eq(AccionAuditoria.EXPORTACION_RECHAZADA), eq("reporte"), eq("INGRESOS"), isNull(),
				isNull(), contains("tope"));
		verify(caja, never()).pagosParaContador(any(), any());
	}

	/** Desviación 5 de la tanda 1: el tope diario y el rango salen de cuentasclaras.panel.exportacion-*. */
	@Test
	void elTopeDiarioYElRangoSonConfigurables() {
		ExportacionContador conTres = new ExportacionContador(caja, cobranza, new EscritorXlsxSeguro(), auditoria,
				Clock.fixed(Instant.parse("2027-04-15T15:00:00Z"), ZoneId.of("America/Lima")),
				new PropiedadesPanel(10, 35, java.time.LocalTime.of(21, 0), 3, 3, 20000, 5, 3));
		assertThatThrownBy(() -> conTres.exportarIngresos(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 4, 15)))
				.as("rango de 3 meses como máximo").isInstanceOf(ExportacionRechazadaException.class)
				.hasMessageContaining("3 meses");
		when(auditoria.contarDesdeDelUsuarioActual(eq(AccionAuditoria.REPORTE_EXPORTADO), any())).thenReturn(3L);
		assertThatThrownBy(() -> conTres.exportarIngresos(DIA, DIA)).isInstanceOf(ExportacionRechazadaException.class)
				.hasMessageContaining("3 reportes");
	}

	@Test
	void masDe20000FilasSeRechaza() {
		when(caja.pagosEnRango(DIA, DIA)).thenReturn(PROPIEDADES.exportacionMaxFilas() + 1L);
		assertThatThrownBy(() -> exportacion.exportarIngresos(DIA, DIA)).isInstanceOf(ExportacionRechazadaException.class);
		verify(caja, never()).pagosParaContador(any(), any());
	}

	/** Si el detalle no cuadra con lo cobrado (dos lecturas del mismo libro), no se entrega nada. */
	@Test
	void siElDetalleNoCuadraConElLibroNoHayArchivo() {
		when(caja.cobrado(DIA, DIA)).thenReturn(new CobradoPeriodo(DIA, DIA, new BigDecimal("300.00"), 1,
				new BigDecimal("300.00"), 1, List.of(), List.of()));
		assertThatThrownBy(() -> exportacion.exportarIngresos(DIA, DIA)).isInstanceOf(IllegalStateException.class);
		verify(auditoria, never()).registrar(eq(AccionAuditoria.REPORTE_EXPORTADO), anyString(), anyString(), any(),
				any(), anyString());
	}
}
