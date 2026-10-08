package pe.edu.virgenmaria.cuentasclaras.alumnos.web;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion;
import pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.VistaPreviaImportacion;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Asistente de importación desde la web: subir → revisar → confirmar, permisos, CSRF y archivos grandes. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ImportacionControllerTest {

	private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private JdbcTemplate jdbc;

	private Long anio2026;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		anio2026 = EscenarioEscolar.crearEstructura(estructura).anio2026();
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void plantillaSeDescargaComoXlsxSinDatosPersonales() throws Exception {
		MvcResult resultado = mvc.perform(get("/alumnos/importar/plantilla").with(admin()))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", XLSX))
				.andExpect(header().string("Content-Disposition", containsString("plantilla-alumnos.xlsx")))
				.andReturn();
		byte[] plantilla = resultado.getResponse().getContentAsByteArray();
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(plantilla))) {
			assertThat(libro.getSheet("Alumnos").getLastRowNum()).as("solo encabezados").isZero();
			assertThat(libro.getSheet("Alumnos").getRow(0).getCell(1).getStringCellValue())
					.isEqualTo("N.° de documento del alumno");
			assertThat(libro.isSheetHidden(libro.getSheetIndex("Listas"))).isTrue();
			assertThat(libro.getSheet("Alumnos").getColumnStyle(1).getDataFormatString()).isEqualTo("@");
			assertThat(libro.getSheet("Instrucciones").getRow(0).getCell(0).getStringCellValue()).contains("Alumnos");
		}
	}

	@Test
	void flujoSubirRevisarConfirmar() throws Exception {
		MockHttpSession sesion = new MockHttpSession();
		byte[] libro = ArchivoImportacion.archivo(ArchivoImportacion.mateo(), ArchivoImportacion.valeria());

		mvc.perform(get("/alumnos/importar").with(admin()).session(sesion)).andExpect(status().isOk())
				.andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
				.andExpect(content().string(containsString("name=\"_csrf\"")));
		mvc.perform(subir(libro, sesion)).andExpect(redirectedUrl("/alumnos/importar/revision"));
		VistaPreviaImportacion previa = (VistaPreviaImportacion) sesion.getAttribute(ImportacionController.CLAVE_SESION);
		assertThat(previa).isNotNull();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alumno", Long.class)).isZero();

		mvc.perform(get("/alumnos/importar/revision").with(admin()).session(sesion)).andExpect(status().isOk())
				.andExpect(view().name("alumnos/importar-revisar"))
				.andExpect(content().string(containsString("2 filas leídas")))
				.andExpect(content().string(containsString("Confirmar e importar")))
				.andExpect(content().string(containsString("Mateo Quispe Huamán")));
		// Un token que no es el de la revisión: no importa nada.
		mvc.perform(post("/alumnos/importar/confirmar").with(admin()).with(csrf()).session(sesion)
				.param("token", "00000000-0000-0000-0000-000000000000")).andExpect(redirectedUrl("/alumnos/importar"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alumno", Long.class)).isZero();

		mvc.perform(subir(libro, sesion)).andExpect(status().is3xxRedirection());
		previa = (VistaPreviaImportacion) sesion.getAttribute(ImportacionController.CLAVE_SESION);
		mvc.perform(post("/alumnos/importar/confirmar").with(admin()).with(csrf()).session(sesion)
						.param("token", previa.token().toString()))
				.andExpect(status().isOk()).andExpect(view().name("alumnos/importar-resultado"))
				.andExpect(content().string(containsString("Importación terminada")));
		assertThat(sesion.getAttribute(ImportacionController.CLAVE_SESION)).as("se borra de la sesión").isNull();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alumno", Long.class)).isEqualTo(2);

		mvc.perform(get("/alumnos/importaciones").with(admin())).andExpect(status().isOk())
				.andExpect(content().string(containsString("alumnos.xlsx")))
				.andExpect(content().string(containsString("2 nuevos")));
		// Reimportar: "sin cambios" y sin botón de confirmar.
		mvc.perform(subir(libro, sesion)).andExpect(status().is3xxRedirection());
		mvc.perform(get("/alumnos/importar/revision").with(admin()).session(sesion))
				.andExpect(content().string(containsString("no hay nada que importar")))
				.andExpect(content().string(containsString("Este mismo archivo ya se importó")))
				.andExpect(content().string(not(containsString("Confirmar e importar"))));
		mvc.perform(post("/alumnos/importar/cancelar").with(admin()).with(csrf()).session(sesion))
				.andExpect(redirectedUrl("/alumnos/importar"));
		assertThat(sesion.getAttribute(ImportacionController.CLAVE_SESION)).isNull();
	}

	@Test
	void erroresDelArchivoSeMuestranPorFilaYNoSePuedeConfirmar() throws Exception {
		MockHttpSession sesion = new MockHttpSession();
		mvc.perform(subir(ArchivoImportacion.archivo(ArchivoImportacion.con(ArchivoImportacion.mateo(), 1, "1234567")),
				sesion)).andExpect(status().is3xxRedirection());
		mvc.perform(get("/alumnos/importar/revision").with(admin()).session(sesion))
				.andExpect(content().string(containsString("Errores por corregir")))
				.andExpect(content().string(containsString("El DNI debe tener 8 dígitos; escribiste «1234567».")))
				.andExpect(content().string(not(containsString("Confirmar e importar"))));
		// Un PDF renombrado: se queda en el paso 1 con el mensaje.
		mvc.perform(multipart("/alumnos/importar").file(new MockMultipartFile("archivo", "alumnos.xlsx", XLSX,
								"%PDF-1.4".getBytes())).param("anioId", anio2026.toString()).with(admin()).with(csrf()))
				.andExpect(status().isOk()).andExpect(view().name("alumnos/importar-subir"))
				.andExpect(content().string(containsString("no es un Excel .xlsx válido")));
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "CAJA", "PROMOTOR", "DOCENTE", "APODERADO" })
	void cajaYPromotorReciben403AlImportar(Rol rol) throws Exception {
		RequestPostProcessor usuario = UsuariosDePrueba.como(rol);
		for (String ruta : new String[] { "/alumnos/importar", "/alumnos/importar/plantilla", "/alumnos/importaciones" }) {
			mvc.perform(get(ruta).with(usuario)).andExpect(status().isForbidden());
		}
		mvc.perform(multipart("/alumnos/importar").file(new MockMultipartFile("archivo", "alumnos.xlsx", XLSX,
				ArchivoImportacion.archivo(ArchivoImportacion.mateo()))).param("anioId", anio2026.toString())
				.with(usuario).with(csrf())).andExpect(status().isForbidden());
		mvc.perform(post("/alumnos/importar/confirmar").with(usuario).with(csrf())).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alumno", Long.class)).isZero();
	}

	@Test
	void subirSinTokenCsrfEsRechazado() throws Exception {
		MockHttpSession sesion = new MockHttpSession();
		mvc.perform(multipart("/alumnos/importar").file(new MockMultipartFile("archivo", "alumnos.xlsx", XLSX,
								ArchivoImportacion.archivo(ArchivoImportacion.mateo()))).param("anioId", anio2026.toString())
						.with(admin()).session(sesion))
				.andExpect(status().isForbidden());
		assertThat(sesion.getAttribute(ImportacionController.CLAVE_SESION)).isNull();
		mvc.perform(post("/alumnos/importar/confirmar").with(admin())).andExpect(status().isForbidden());
	}

	@Test
	void archivoDe3MbMuestraMensajeClaro() throws Exception {
		byte[] libro = ArchivoImportacion.archivo(ArchivoImportacion.mateo());
		byte[] grande = Arrays.copyOf(libro, 3 * 1024 * 1024);
		mvc.perform(multipart("/alumnos/importar").file(new MockMultipartFile("archivo", "alumnos.xlsx", XLSX, grande))
						.param("anioId", anio2026.toString()).with(admin()).with(csrf()))
				.andExpect(status().isOk()).andExpect(view().name("alumnos/importar-subir"))
				.andExpect(content().string(containsString("El archivo pesa 3 MB y el máximo es 2 MB")));
	}

	private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder subir(byte[] libro,
			MockHttpSession sesion) {
		return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder) multipart(
				"/alumnos/importar").file(new MockMultipartFile("archivo", "alumnos.xlsx", XLSX, libro))
				.param("anioId", anio2026.toString()).with(admin()).with(csrf()).session(sesion);
	}

	private static RequestPostProcessor admin() {
		return UsuariosDePrueba.como(Rol.ADMINISTRACION);
	}
}
