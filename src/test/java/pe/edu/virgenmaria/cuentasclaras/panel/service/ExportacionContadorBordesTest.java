package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
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
import pe.edu.virgenmaria.cuentasclaras.panel.dto.ArchivoExportado;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * QA sprint 6: el Excel para el contador con la base real (topes, rango y cuadre entre meses).
 * <ul>
 *   <li>Dadas 20 descargas hoy, cuando Administración pide la 21.ª, entonces se rechaza, queda en la bitácora y no hay
 *       archivo; pasada la medianoche de Lima, vuelve a poder.</li>
 *   <li>Dado un rango de exactamente 12 meses, cuando se exporta, entonces sale; con un día más, se rechaza.</li>
 *   <li>Dado un pago de abril anulado en mayo, cuando el contador baja abril y mayo, entonces abril lo trae como anulado
 *       con su nota de crédito de mayo (vigente + anulado = emitido) y mayo no lo trae.</li>
 *   <li>Dirección ve los reportes pero no exporta; Caja, nada.</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ExportacionContadorBordesTest {

	private static final LocalDate HOY = LocalDate.of(2027, 4, 15);

	@Autowired
	private ExportacionContador exportacion;

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

	private long eventos(String accion) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = ?", Long.class, accion);
	}

	private static Map<String, String> control(XSSFWorkbook libro) {
		Map<String, String> valores = new HashMap<>();
		Sheet hoja = libro.getSheet("Control");
		for (int i = 1; i <= hoja.getLastRowNum(); i++) {
			valores.put(hoja.getRow(i).getCell(0).getStringCellValue(), hoja.getRow(i).getCell(1).getStringCellValue());
		}
		return valores;
	}

	@Test
	void debeRechazarLaDescargaVeintiunoDelDiaYPermitirlaPasadaLaMedianocheDeLima() {
		a(HOY, 9, 0);
		como(EscenarioCobranza.ADMINISTRACION);
		for (int i = 0; i < 20; i++) {
			exportacion.exportarMorosidad(datos.f().anio2027());
		}
		a(HOY, 23, 59);
		assertThatThrownBy(() -> exportacion.exportarIngresos(HOY, HOY))
				.isInstanceOf(ExportacionRechazadaException.class).hasMessageContaining("20");
		assertThat(eventos("REPORTE_EXPORTADO")).isEqualTo(20);
		assertThat(eventos("EXPORTACION_RECHAZADA")).isEqualTo(1);

		a(HOY.plusDays(1), 0, 1);
		assertThat(exportacion.exportarIngresos(HOY, HOY).contenido()).isNotEmpty();
		assertThat(eventos("REPORTE_EXPORTADO")).isEqualTo(21);
	}

	@Test
	void debeExportarDoceMesesExactosYRechazarUnDiaMas() {
		como(EscenarioCobranza.ADMINISTRACION);
		ArchivoExportado anio = exportacion.exportarIngresos(LocalDate.of(2026, 4, 16), HOY);
		assertThat(anio.nombre()).isEqualTo("ingresos-2026-04-a-2027-04.xlsx");
		assertThatThrownBy(() -> exportacion.exportarIngresos(LocalDate.of(2026, 4, 15), HOY))
				.isInstanceOf(ExportacionRechazadaException.class).hasMessageContaining("12 meses");
		assertThat(eventos("EXPORTACION_RECHAZADA")).isEqualTo(1);
	}

	@Test
	void elPagoDeAbrilAnuladoEnMayoCuadraEnElExcelDeAbrilYNoApareceEnElDeMayo() throws IOException {
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(EscenarioCaja.efectivo(datos.f().flores(), List.of(cuota(jdbc, datos.f().sebastian(),
				"MAT-2027")), "350.00", "350.00"));
		LocalDate mayo = LocalDate.of(2027, 5, 3);
		a(mayo, 10, 0);
		anulaciones.solicitarDevolucion(pago, EscenarioAprobaciones.MOTIVO_ANULACION);
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "pago", pago);

		como(EscenarioCobranza.ADMINISTRACION);
		ArchivoExportado abril = exportacion.exportarIngresos(LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 30));
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(abril.contenido()))) {
			Map<String, String> c = control(libro);
			assertThat(c.get("Total emitido (vigente y anulado)")).isEqualTo("S/ 1,500.00");
			assertThat(c.get("Total vigente")).isEqualTo("S/ 800.00");
			assertThat(c.get("Total anulado")).isEqualTo("S/ 700.00");
			Sheet ingresos = libro.getSheet("Ingresos");
			BigDecimal vigente = BigDecimal.ZERO;
			Row deFlores = null;
			for (int i = 1; i <= ingresos.getLastRowNum(); i++) {
				Row fila = ingresos.getRow(i);
				if ("Vigente".equals(fila.getCell(7).getStringCellValue())) {
					vigente = vigente.add(BigDecimal.valueOf(fila.getCell(6).getNumericCellValue()));
				}
				if ((long) fila.getCell(11).getNumericCellValue() == datos.f().flores()) {
					deFlores = fila;
				}
			}
			assertThat(vigente).isEqualByComparingTo("800.00");
			assertThat(deFlores).isNotNull();
			assertThat(deFlores.getCell(7).getStringCellValue()).isEqualTo("Anulado");
			assertThat(deFlores.getCell(8).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(mayo);
			assertThat(deFlores.getCell(9).getStringCellValue()).isNotBlank();
		}
		ArchivoExportado deMayo = exportacion.exportarIngresos(LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 31));
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(deMayo.contenido()))) {
			assertThat(libro.getSheet("Ingresos").getLastRowNum()).as("solo el encabezado").isZero();
			assertThat(control(libro).get("Total anulado")).isEqualTo("S/ 0.00");
		}
	}

	@Test
	void direccionYCajaNoExportanElExcelDelContador() {
		for (var quien : List.of(EscenarioCobranza.DIRECCION, EscenarioCaja.CAJA)) {
			como(quien);
			assertThatThrownBy(() -> exportacion.exportarIngresos(HOY, HOY)).as(quien.getUsername())
					.isInstanceOf(AuthorizationDeniedException.class);
			assertThatThrownBy(() -> exportacion.exportarMorosidad(null)).as(quien.getUsername())
					.isInstanceOf(AuthorizationDeniedException.class);
		}
		assertThat(eventos("REPORTE_EXPORTADO")).isZero();
	}
}
