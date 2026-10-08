package pe.edu.virgenmaria.cuentasclaras.panel.web;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Panel y reportes por la web (sprint 6, tanda 1): permisos (P14), exportación solo por POST con CSRF (P13), bitácora
 * con el código y el SHA-256 del archivo (P12), datos mínimos (P11), totales que cuadran (P15) y pantallas de celular.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ReportesWebTest {

	private static final List<String> PANTALLAS = List.of("/panel/morosos", "/panel/reportes",
			"/panel/reportes/ingresos", "/panel/reportes/morosidad");

	@Autowired
	private MockMvc mvc;

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

	/**
	 * Sin navegador, el «snapshot de 360 px» se comprueba por construcción (como en el portal): viewport de celular, sin
	 * tablas y una hoja de estilos sin anchos fijos mayores a 360 px en el bloque del panel.
	 */
	@Test
	void elPanelYLosReportesCabenEnElCelular() throws Exception {
		String panel = mvc.perform(get("/panel").with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(panel).contains("name=\"viewport\" content=\"width=device-width, initial-scale=1\"")
				.doesNotContain("<table").contains("S/ 800.00", "S/ 1,600.00", "50 %", "Anulado en el mes: S/ 350.00");
		for (String pantalla : PANTALLAS) {
			String html = mvc.perform(get(pantalla).with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			assertThat(html).as(pantalla).contains("name=\"viewport\"").doesNotContain("<table");
		}
		String css = Files.readString(Path.of("src/main/resources/static/css/app.css"));
		String bloque = css.substring(css.indexOf("/* Sprint 6 · tanda 1: panel de Promotoría"));
		assertThat(Pattern.compile("(min-)?width:\\s*(3[6-9]\\d|[4-9]\\d\\d|\\d{4,})px").matcher(bloque).find())
				.as("ancho fijo mayor a 360 px en el panel").isFalse();
	}

	/** P14: Caja, Docente y Apoderado no ven nada del panel; Dirección ve reportes pero no el panel ni exporta. */
	@Test
	void cajaDocenteYApoderadoNoEntranAlPanel() throws Exception {
		for (Rol rol : List.of(Rol.CAJA, Rol.DOCENTE, Rol.APODERADO)) {
			for (String ruta : List.of("/panel", "/panel/morosos", "/panel/reportes/morosidad", "/panel/reportes/ingresos")) {
				mvc.perform(get(ruta).with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
			}
			mvc.perform(post("/panel/reportes/morosidad.xlsx").with(csrf()).with(UsuariosDePrueba.como(rol)))
					.andExpect(status().isForbidden());
		}
		mvc.perform(get("/panel").with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION))).andExpect(status().isForbidden());
		mvc.perform(get("/panel/morosos").with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION)))
				.andExpect(status().isOk());
		String morosidad = mvc.perform(get("/panel/reportes/morosidad").with(UsuariosDePrueba.como(EscenarioCobranza
				.DIRECCION))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(morosidad).as("Dirección no ve el botón de descarga").doesNotContain("data-exportar");
		mvc.perform(post("/panel/reportes/morosidad.xlsx").with(csrf())
				.with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION))).andExpect(status().isForbidden());
		assertThat(contarEventos("REPORTE_EXPORTADO")).isZero();
	}

	/** P13: exportar es solo POST con CSRF. */
	@Test
	void exportarPorGetResponde405YSinCsrf403() throws Exception {
		mvc.perform(get("/panel/reportes/ingresos.xlsx").param("desde", "2027-04-01").param("hasta", "2027-04-30")
				.with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION))).andExpect(status().isMethodNotAllowed());
		mvc.perform(post("/panel/reportes/ingresos.xlsx").param("desde", "2027-04-01").param("hasta", "2027-04-30")
				.with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION))).andExpect(status().isForbidden());
		mvc.perform(post("/panel/reportes/morosidad.xlsx").with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
				.andExpect(status().isForbidden());
		assertThat(contarEventos("REPORTE_EXPORTADO")).isZero();
	}

	/** P12, P11 y P15 con el Excel real que baja Administración (el contador). */
	@Test
	void elExcelDeIngresosQuedaEnLaBitacoraConSuCodigoYSuSha() throws Exception {
		MvcResult respuesta = mvc.perform(post("/panel/reportes/ingresos.xlsx").param("desde", "2027-04-01")
				.param("hasta", "2027-04-30").with(csrf()).with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type",
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"ingresos-2027-04.xlsx\""))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn();
		byte[] archivo = respuesta.getResponse().getContentAsByteArray();

		Map<String, Object> evento = jdbc.queryForMap("SELECT nombre_usuario, entidad_id, detalle FROM evento_auditoria "
				+ "WHERE accion = 'REPORTE_EXPORTADO'");
		String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archivo));
		assertThat(evento.get("nombre_usuario")).isEqualTo("administracion");
		assertThat((String) evento.get("detalle")).contains("tipo=INGRESOS", "filas=3", "sha256=" + sha);

		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
			assertThat(libro.getNumberOfSheets()).isEqualTo(3);
			Map<String, String> control = control(libro.getSheet("Control"));
			assertThat(control.get("Código de exportación")).isEqualTo(evento.get("entidad_id"));
			assertThat(control.get("Exportado por")).isEqualTo("administracion");
			// P15: vigente + anulado = emitido, y vigente es lo cobrado del libro.
			assertThat(control.get("Total emitido (vigente y anulado)")).isEqualTo("S/ 1,150.00");
			assertThat(control.get("Total vigente")).isEqualTo("S/ 800.00");
			assertThat(control.get("Total anulado")).isEqualTo("S/ 350.00");
			assertThat(control.get("Aviso")).contains("no lo reenvíe");

			Sheet ingresos = libro.getSheet("Ingresos");
			assertThat(ingresos.getLastRowNum()).isEqualTo(3);
			BigDecimal suma = BigDecimal.ZERO;
			Row anulada = null;
			for (int i = 1; i <= ingresos.getLastRowNum(); i++) {
				Row fila = ingresos.getRow(i);
				assertThat(fila.getCell(6).getCellType()).isEqualTo(CellType.NUMERIC);
				suma = suma.add(BigDecimal.valueOf(fila.getCell(6).getNumericCellValue()));
				if ("Anulado".equals(fila.getCell(7).getStringCellValue())) {
					anulada = fila;
				}
				assertThat((long) fila.getCell(11).getNumericCellValue()).as("familia por código")
						.isEqualTo(datos.f().quispe());
			}
			assertThat(suma).isEqualByComparingTo("1150.00");
			assertThat(anulada).as("el pago anulado aparece con su nota de crédito").isNotNull();
			assertThat(anulada.getCell(9).getStringCellValue()).isNotBlank();
			assertThat(anulada.getCell(10).getStringCellValue()).isEqualTo("Matrícula 2027");
			sinDatosPersonales(libro);
		}
	}

	@Test
	void elExcelDeMorosidadVaPorGradoYSinNombres() throws Exception {
		byte[] archivo = mvc.perform(post("/panel/reportes/morosidad.xlsx").param("anio", datos.f().anio2027().toString())
				.with(csrf()).with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"morosidad-2027.xlsx\""))
				.andReturn().getResponse().getContentAsByteArray();
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
			Sheet hoja = libro.getSheet("Morosidad por grado");
			assertThat(hoja.getLastRowNum()).isEqualTo(1);
			assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).isEqualTo("6.° Primaria");
			assertThat(BigDecimal.valueOf(hoja.getRow(1).getCell(3).getNumericCellValue())).isEqualByComparingTo("1600.00");
			assertThat(celdas(libro)).noneMatch(t -> t.contains("Sección") || t.endsWith("Primaria A"));
			sinDatosPersonales(libro);
		}
		assertThat(contarEventos("REPORTE_EXPORTADO")).isEqualTo(1);
	}

	/** Un rango de más de 12 meses no descarga nada y queda en la bitácora como rechazo. */
	@Test
	void rangoDeMasDe12MesesVuelveConErrorYQuedaEnLaBitacora() throws Exception {
		mvc.perform(post("/panel/reportes/ingresos.xlsx").param("desde", "2026-01-01").param("hasta", "2027-04-30")
				.with(csrf()).with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)))
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/panel/reportes/ingresos"))
				.andExpect(flash().attributeExists("error"));
		assertThat(contarEventos("REPORTE_EXPORTADO")).isZero();
		assertThat(contarEventos("EXPORTACION_RECHAZADA")).isEqualTo(1);
	}

	/** Decisión 73: 20 descargas por persona y día; la 21.ª se rechaza y queda en la bitácora. Otra persona sí puede. */
	@Test
	void topeDe20DescargasPorPersonaYDia() throws Exception {
		for (int i = 0; i < 20; i++) {
			mvc.perform(post("/panel/reportes/morosidad.xlsx").with(csrf())
					.with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION))).andExpect(status().isOk());
		}
		mvc.perform(post("/panel/reportes/morosidad.xlsx").with(csrf())
				.with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)))
				.andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
		assertThat(contarEventos("EXPORTACION_RECHAZADA")).isEqualTo(1);
		mvc.perform(post("/panel/reportes/morosidad.xlsx").with(csrf())
				.with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA))).andExpect(status().isOk());
		assertThat(contarEventos("REPORTE_EXPORTADO")).isEqualTo(21);
	}

	private long contarEventos(String accion) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = ?", Long.class, accion);
	}

	private static Map<String, String> control(Sheet hoja) {
		Map<String, String> valores = new java.util.HashMap<>();
		for (int i = 1; i <= hoja.getLastRowNum(); i++) {
			valores.put(hoja.getRow(i).getCell(0).getStringCellValue(), hoja.getRow(i).getCell(1).getStringCellValue());
		}
		return valores;
	}

	private static List<String> celdas(XSSFWorkbook libro) {
		DataFormatter formato = new DataFormatter();
		List<String> textos = new ArrayList<>();
		libro.forEach(hoja -> hoja.forEach(fila -> fila.forEach((Cell c) -> textos.add(formato.formatCellValue(c)))));
		return textos;
	}

	/** P11: ni documentos, ni nombres de alumnos o apoderados, ni celulares, ni correos, en ninguna celda. */
	private static void sinDatosPersonales(XSSFWorkbook libro) {
		List<String> prohibidos = List.of(EscenarioEscolar.DNI_MATEO, EscenarioEscolar.DNI_VALERIA,
				EscenarioEscolar.DNI_ROSA, EscenarioCaja.DNI_SEBASTIAN, EscenarioCaja.DNI_PEDRO,
				EscenarioEscolar.CELULAR_ROSA, EscenarioEscolar.CORREO_ROSA, "912345678", "Mateo", "Valeria", "Sebastián",
				"Rosa", "Pedro", "Quispe", "Flores", "Huamán");
		Pattern patrones = Pattern.compile("\\b\\d{8}\\b|\\b9\\d{8}\\b|@");
		Pattern codigo = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
		for (String texto : celdas(libro)) {
			if (codigo.matcher(texto).matches()) {
				continue; // el código de exportación (UUID) puede empezar con 8 dígitos
			}
			assertThat(prohibidos).as("celda «%s»", texto).noneMatch(texto::contains);
			assertThat(patrones.matcher(texto).find()).as("DNI, celular o correo en «%s»", texto).isFalse();
		}
	}
}
