package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.LogoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ImagenesDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 8, tanda 1 (E17, sección 3.7): el logo del colegio. Los archivos hostiles de {@code hostiles/logos} (SVG con
 * script, PNG políglota, bomba de 30000 × 30000, imagen de 4001 × 4001 y HTML con extensión .png) se rechazan sin
 * decodificar; una imagen válida se reescribe como PNG de 400 px como máximo, sin metadatos, con su SHA-256 y como único
 * vigente; y {@code /marca/logo} responde solo el logo del colegio de la sesión, con {@code nosniff} y CSP.
 */
@PruebaIntegracion
class LogoColegioTest {

	private static final String HTML_HOSTIL = "<html><body><script>alert('logo hostil')</script></body></html>";

	@Autowired
	private LogosColegio logos;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private TransactionTemplate transaccion;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MockMvc mvc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	// ------------------------------------------------------------------ E17: archivos hostiles

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "script.svg", "poliglota.png", "bomba.png", "enorme.png", "falsa-extension.png" })
	void unArchivoHostilSeRechazaSinGuardarNada(String archivo) throws IOException {
		byte[] contenido = new ClassPathResource("hostiles/logos/" + archivo).getContentAsByteArray();
		assertThatThrownBy(() -> guardarEn(1L, contenido, archivo)).isInstanceOf(ReglaNegocioException.class);
		assertThat(contar()).as("nada quedó guardado").isZero();
	}

	@Test
	void laBombaYLaImagenEnormeSeRechazanPorLaCabeceraSinDecodificar() throws IOException {
		// Las dos declaran sus dimensiones en el IHDR y no traen ni un píxel: si se intentara decodificar, fallaría por
		// «dañada»; el mensaje de las medidas demuestra que se rechazó antes.
		byte[] bomba = new ClassPathResource("hostiles/logos/bomba.png").getContentAsByteArray();
		assertThatThrownBy(() -> guardarEn(1L, bomba, "bomba.png")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("30000 × 30000 px").hasMessageContaining("4000 × 4000");
		byte[] enorme = new ClassPathResource("hostiles/logos/enorme.png").getContentAsByteArray();
		assertThatThrownBy(() -> guardarEn(1L, enorme, "enorme.png")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("4001 × 4001 px");
	}

	@Test
	void elTipoSeDecidePorLosBytesNoPorLaExtension() {
		assertThatThrownBy(() -> guardarEn(1L, HTML_HOSTIL.getBytes(StandardCharsets.UTF_8), "logo.png"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("PNG o JPEG");
		assertThatThrownBy(() -> guardarEn(1L, "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8),
				"logo.jpg")).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("SVG");
		// Un JPEG con extensión .txt sí entra: lo que importa son los bytes.
		LogoColegio guardado = guardarEn(1L, ImagenesDePrueba.jpeg(120, 80), "logo.txt");
		assertThat(guardado.getAncho()).isEqualTo(120);
		assertThat(guardado.getAlto()).isEqualTo(80);
	}

	@Test
	void masDeUnMegabyteSeRechaza() {
		byte[] grande = new byte[1_048_577];
		System.arraycopy(ImagenesDePrueba.FIRMA_PNG, 0, grande, 0, ImagenesDePrueba.FIRMA_PNG.length);
		assertThatThrownBy(() -> guardarEn(1L, grande, "grande.png")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("máximo");
		assertThat(contar()).isZero();
	}

	// ------------------------------------------------------------------ reescritura

	@Test
	void unPngValidoSeReescribeA400PxSinMetadatosConSuSha256YComoUnicoVigente() throws Exception {
		byte[] original = ImagenesDePrueba.pngConTexto(1200, 800, HTML_HOSTIL);
		assertThat(new String(original, StandardCharsets.ISO_8859_1)).as("el original sí trae el chunk tEXt")
				.contains("tEXt").contains("<script>");

		LogoColegio primero = guardarEn(1L, original, "logo.png");

		assertThat(primero.getAncho()).isEqualTo(400);
		assertThat(primero.getAlto()).isEqualTo(267);
		byte[] png = primero.getContenido();
		assertThat(png.length).isEqualTo(primero.getBytes()).isLessThanOrEqualTo(LogoColegio.MAX_BYTES);
		assertThat(java.util.Arrays.copyOf(png, 8)).isEqualTo(ImagenesDePrueba.FIRMA_PNG);
		String texto = new String(png, StandardCharsets.ISO_8859_1);
		assertThat(texto).as("sin el chunk de texto ni el script").doesNotContain("tEXt").doesNotContain("<script>");
		BufferedImage leida = ImageIO.read(new ByteArrayInputStream(png));
		assertThat(leida.getWidth()).isEqualTo(400);
		assertThat(leida.getHeight()).isEqualTo(267);
		assertThat(primero.getSha256()).isEqualTo(sha256(png));
		assertThat(primero.esVigente()).isTrue();
		assertThat(jdbc.queryForObject("SELECT sha256 FROM colegio_logo WHERE id = ?", String.class, primero.getId()))
				.isEqualTo(primero.getSha256());

		// Un segundo logo (JPEG chico, no se agranda) reemplaza al primero: queda uno solo vigente y ninguno se borra.
		LogoColegio segundo = guardarEn(1L, ImagenesDePrueba.jpeg(100, 60), "logo.jpg");
		assertThat(segundo.getAncho()).isEqualTo(100);
		assertThat(segundo.getAlto()).isEqualTo(60);
		assertThat(contar()).isEqualTo(2);
		List<Map<String, Object>> filas = jdbc.queryForList("SELECT id, vigente FROM colegio_logo ORDER BY id");
		assertThat(filas.get(0).get("vigente")).as("el primero quedó reemplazado").isNull();
		assertThat(filas.get(1).get("vigente")).isEqualTo(true);
		assertThat(ContextoColegio.en(1L, () -> logos.vigente().map(LogoColegio::getId).orElse(null)))
				.isEqualTo(segundo.getId());
	}

	@Test
	void unJpegSeConvierteEnPng() {
		LogoColegio logo = guardarEn(1L, ImagenesDePrueba.jpeg(900, 900), "foto.jpeg");
		assertThat(logo.getAncho()).isEqualTo(400);
		assertThat(logo.getAlto()).isEqualTo(400);
		assertThat(java.util.Arrays.copyOf(logo.getContenido(), 8)).isEqualTo(ImagenesDePrueba.FIRMA_PNG);
	}

	// ------------------------------------------------------------------ GET /marca/logo

	@Test
	void sinLogoVigenteRespondeNoEncontrado() throws Exception {
		mvc.perform(get("/marca/logo").with(UsuariosDePrueba.como(Rol.CAJA))).andExpect(status().isNotFound());
	}

	@Test
	void sirveElPngConNosniffYCspACualquierPersonaConSesion() throws Exception {
		LogoColegio logo = guardarEn(1L, ImagenesDePrueba.jpeg(300, 200), "logo.jpg");
		for (Rol rol : List.of(Rol.CAJA, Rol.DOCENTE, Rol.ADMINISTRACION, Rol.DIRECTOR, Rol.PROMOTOR)) {
			MvcResult respuesta = mvc.perform(get("/marca/logo").with(UsuariosDePrueba.como(rol)))
					.andExpect(status().isOk())
					.andExpect(header().string("Content-Type", "image/png"))
					.andExpect(header().string("X-Content-Type-Options", "nosniff"))
					.andExpect(header().string("Content-Security-Policy", "default-src 'none'"))
					.andExpect(header().string("Cache-Control", "private, max-age=86400"))
					.andExpect(header().longValue("Content-Length", logo.getBytes()))
					.andReturn();
			assertThat(respuesta.getResponse().getContentAsByteArray()).isEqualTo(logo.getContenido());
		}
		mvc.perform(get("/marca/logo")).andExpect(status().is3xxRedirection());
	}

	@Test
	void nuncaSirveElLogoDeOtroColegio() throws Exception {
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		LogoColegio deB = guardarEn(colegioB, ImagenesDePrueba.jpeg(64, 64), "b.jpg");
		UsuarioAutenticado cajaB = UsuariosDePrueba.autenticado(colegioB, 93L, "caja", "Caja B", false,
				EnumSet.of(Rol.CAJA));

		// El colegio A no tiene logo: 404, nunca el de B.
		mvc.perform(get("/marca/logo").with(UsuariosDePrueba.como(Rol.CAJA))).andExpect(status().isNotFound());
		// B ve el suyo.
		MvcResult respuestaB = mvc.perform(get("/marca/logo").with(UsuariosDePrueba.como(cajaB)))
				.andExpect(status().isOk()).andReturn();
		assertThat(respuestaB.getResponse().getContentAsByteArray()).isEqualTo(deB.getContenido());

		// Ahora A sube el suyo: cada uno recibe el propio y B sigue vigente (el UNIQUE es por colegio).
		LogoColegio deA = guardarEn(1L, ImagenesDePrueba.jpeg(80, 40), "a.jpg");
		assertThat(deA.getSha256()).isNotEqualTo(deB.getSha256());
		MvcResult respuestaA = mvc.perform(get("/marca/logo").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(status().isOk()).andReturn();
		assertThat(respuestaA.getResponse().getContentAsByteArray()).isEqualTo(deA.getContenido());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM colegio_logo WHERE vigente", Long.class)).isEqualTo(2);
	}

	// ------------------------------------------------------------------ ayudas

	/** {@code guardar} exige una transacción abierta y el colegio en contexto (como el alta o la pantalla de marca). */
	private LogoColegio guardarEn(long colegioId, byte[] contenido, String nombre) {
		return ContextoColegio.en(colegioId, () -> transaccion.execute(estado -> logos.guardar(contenido, nombre)));
	}

	private long contar() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM colegio_logo", Long.class);
	}

	static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}
