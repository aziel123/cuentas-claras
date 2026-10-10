package pe.edu.virgenmaria.cuentasclaras.comun.archivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Sprint 7, tanda 3 (auditoría web, «Subida de archivos»; E25): el catálogo de archivos hostiles de
 * {@code src/test/resources/hostiles/} contra las TRES subidas de archivos (importación de alumnos, extracto del banco y
 * recaudación bancaria). Ninguno rompe la aplicación (nunca un 500 ni una traza), todos se rechazan con un mensaje claro,
 * ninguno deja una vista previa en la sesión ni guarda un archivo, ninguno expande una entidad XML ni ejecuta su HTML, y un
 * nombre con «../» no escribe nada en el disco.
 * <ul>
 *   <li>{@code bomba-zip.xlsx}: 47 KB que al abrirse son 30 MB (el límite es 20 MB).</li>
 *   <li>{@code xxe.xlsx}: una hoja con DOCTYPE y entidades externas (leer {@code /etc/hostname}).</li>
 *   <li>{@code html-disfrazado.xlsx} y {@code .csv}: HTML con un script, con la extensión de una planilla.</li>
 *   <li>{@code formulas.csv}: fórmulas en todas las columnas (inyección en hojas de cálculo).</li>
 *   <li>{@code nombre-con-ruta.csv}: contenido inofensivo subido con el nombre {@code ../../../../tmp/cc-hostil.csv}.</li>
 *   <li>{@code vacio.xlsx}: 0 bytes.</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ArchivosHostilesTest {

	private static final String NOMBRE_CON_RUTA = "../../../../tmp/cc-hostil.csv";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private JdbcTemplate jdbc;

	private Long anio;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		anio = EscenarioEscolar.crearEstructura(estructura).anio2027();
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	static Stream<Arguments> casos() {
		List<String> archivos = List.of("bomba-zip.xlsx", "xxe.xlsx", "html-disfrazado.xlsx", "html-disfrazado.csv",
				"formulas.csv", "nombre-con-ruta.csv", "vacio.xlsx");
		List<String> subidas = List.of("/alumnos/importar", "/conciliacion/extractos/vista-previa",
				"/recaudacion/vista-previa");
		return subidas.stream().flatMap(s -> archivos.stream().map(a -> Arguments.of(s, a)));
	}

	@ParameterizedTest(name = "{1} en {0}")
	@MethodSource("casos")
	void ningunArchivoHostilPasaNiRompeLaAplicacion(String subida, String archivo) throws Exception {
		byte[] contenido = new ClassPathResource("hostiles/" + archivo).getContentAsByteArray();
		String nombre = archivo.equals("nombre-con-ruta.csv") ? NOMBRE_CON_RUTA : archivo;
		long archivosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM archivo_cargado", Long.class);
		MockHttpSession sesion = new MockHttpSession();

		MvcResult resultado = mvc.perform(multipart(subida)
				.file(new MockMultipartFile("archivo", nombre, "application/octet-stream", contenido))
				.param("anioId", anio.toString()).session(sesion).with(csrf())
				.with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION))).andReturn();

		int estado = resultado.getResponse().getStatus();
		String cuerpo = resultado.getResponse().getContentAsString();
		Object error = resultado.getFlashMap().get("error");
		assertThat(estado).as("nunca un error del servidor").isLessThan(500);
		assertThat(cuerpo.contains("role=\"alert\"") || error != null).as("se rechaza con un mensaje claro").isTrue();
		assertThat(cuerpo + error).doesNotContain("Exception", "org.apache", "SAXParse", "java.", "aaaaaaaaaa",
				"<script>alert(\"cc-hostil\")", "cc-hostil.invalid");
		assertThat(Collections.list(sesion.getAttributeNames())).as("no queda ninguna vista previa en la sesión")
				.noneMatch(n -> n.toLowerCase().contains("previa") || n.toLowerCase().contains("importacion")
						|| n.toLowerCase().contains("extracto") || n.toLowerCase().contains("recaudacion"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM archivo_cargado", Long.class)).isEqualTo(archivosAntes);
		assertThat(Files.exists(Path.of("/tmp/cc-hostil.csv"))).isFalse();
		assertThat(Files.exists(Path.of(System.getProperty("java.io.tmpdir"), "cc-hostil.csv"))).isFalse();
		assertThat(Files.exists(Path.of("cc-hostil.csv"))).isFalse();
	}
}
