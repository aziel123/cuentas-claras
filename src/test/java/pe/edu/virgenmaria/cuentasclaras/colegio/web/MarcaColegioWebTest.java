package pe.edu.virgenmaria.cuentasclaras.colegio.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ImagenesDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/**
 * Sprint 8, tanda 1 (sección 3.7 y H8): la pantalla de marca del colegio. Promotoría corrige la razón social y sube un
 * logo (multipart), todo queda en la bitácora con su valor anterior y el nuevo; Dirección recibe 403; el RUC no se cambia
 * por esta pantalla; y la boleta muestra la razón social, el RUC, la dirección y el logo del emisor.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class MarcaColegioWebTest {

	private static final String RUC = EscenarioCaja.RUC_EJEMPLO;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioCobro cobro;

	private String nombreOriginal;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		SecurityContextHolder.clearContext();
		nombreOriginal = jdbc.queryForObject("SELECT nombre FROM colegio WHERE id = 1", String.class);
		jdbc.update("UPDATE colegio SET ruc = ? WHERE id = 1", RUC);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
		jdbc.update("UPDATE colegio SET nombre = ? WHERE id = 1", nombreOriginal);
	}

	@Test
	void promotoriaVeElFormularioSinCampoParaElRuc() throws Exception {
		mvc.perform(get("/colegio/marca").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(status().isOk()).andExpect(view().name("colegio/marca"))
				.andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("name=\"logo\"")))
				.andExpect(content().string(containsString("accept=\"image/png,image/jpeg\"")))
				.andExpect(content().string(containsString(RUC)))
				.andExpect(content().string(not(containsString("name=\"ruc\""))))
				.andExpect(content().string(containsString("Todavía sin logo")));
	}

	@Test
	void promotoriaCambiaLaRazonSocialYSubeUnLogoYQuedaAuditado() throws Exception {
		mvc.perform(ficha("Colegio Virgen María S.A.C.").file(logo(ImagenesDePrueba.jpeg(600, 300))))
				.andExpect(redirectedUrl("/colegio/marca"))
				.andExpect(flash().attribute("exito", containsString("logo nuevo")));

		Map<String, Object> colegio = jdbc.queryForMap(
				"SELECT nombre, razon_social, direccion, correo_institucional, ruc FROM colegio WHERE id = 1");
		assertThat(colegio).containsEntry("razon_social", "Colegio Virgen María S.A.C.")
				.containsEntry("direccion", "Av. Los Próceres 123, Santiago de Surco, Lima")
				.containsEntry("correo_institucional", "secretaria@colegio.edu.pe").containsEntry("ruc", RUC);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM colegio_logo WHERE vigente", Long.class)).isEqualTo(1);

		Map<String, Object> marca = ultimoEvento(jdbc, "MARCA_COLEGIO_CAMBIADA");
		assertThat(marca).containsEntry("nombre_usuario", "usuario.prueba").containsEntry("entidad", "colegio")
				.containsEntry("entidad_id", "1");
		assertThat(marca.get("valor_anterior")).asString().contains("razonSocial=(vacío)").contains("direccion=(vacío)")
				.doesNotContain("nombre=");
		assertThat(marca.get("valor_nuevo")).asString().contains("razonSocial=Colegio Virgen María S.A.C.")
				.contains("correoInstitucional=secretaria@colegio.edu.pe");

		Map<String, Object> logo = ultimoEvento(jdbc, "LOGO_COLEGIO_CAMBIADO");
		String sha = jdbc.queryForObject("SELECT sha256 FROM colegio_logo WHERE vigente", String.class);
		assertThat(logo).containsEntry("entidad", "colegio_logo").containsEntry("valor_nuevo", sha);
		assertThat(logo.get("valor_anterior")).as("el primero del colegio no reemplaza a ninguno").isNull();
		assertThat(logo.get("detalle")).asString().contains("400 × 200 px");

		// Segundo logo: la bitácora guarda el SHA-256 anterior y el nuevo.
		mvc.perform(ficha("Colegio Virgen María S.A.C.").file(logo(ImagenesDePrueba.jpeg(50, 50))))
				.andExpect(redirectedUrl("/colegio/marca")).andExpect(flash().attributeExists("exito"));
		Map<String, Object> cambio = ultimoEvento(jdbc, "LOGO_COLEGIO_CAMBIADO");
		assertThat(cambio).containsEntry("valor_anterior", sha);
		assertThat(cambio.get("valor_nuevo")).isNotEqualTo(sha);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM colegio_logo", Long.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM colegio_logo WHERE vigente", Long.class)).isEqualTo(1);

		// La pantalla muestra el logo vigente y sus medidas.
		mvc.perform(get("/colegio/marca").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(content().string(containsString("src=\"/marca/logo\"")))
				.andExpect(content().string(containsString("PNG de 50 × 50 px")))
				// En un atributo value, Thymeleaf escribe la í como entidad.
				.andExpect(content().string(containsString("value=\"Colegio Virgen Mar&iacute;a S.A.C.\"")));
	}

	@Test
	void unLogoHostilNoGuardaNiLaFichaNiElLogo() throws Exception {
		byte[] html = "<html><script>alert(1)</script></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		mvc.perform(ficha("Otra razón social").file(logo(html)))
				.andExpect(redirectedUrl("/colegio/marca"))
				.andExpect(flash().attribute("error", containsString("PNG o JPEG")));
		assertThat(jdbc.queryForObject("SELECT razon_social FROM colegio WHERE id = 1", String.class)).isNull();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM colegio_logo", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion IN "
				+ "('MARCA_COLEGIO_CAMBIADA', 'LOGO_COLEGIO_CAMBIADO')", Long.class)).isZero();
	}

	@Test
	void losErroresDelFormularioVuelvenConElMensaje() throws Exception {
		mvc.perform(multipart("/colegio/marca").with(csrf()).with(UsuariosDePrueba.como(Rol.PROMOTOR))
						.param("nombre", nombreOriginal).param("razonSocial", "").param("direccion", "Av. Sur 1")
						.param("correoInstitucional", "secretaria@colegio.edu.pe"))
				.andExpect(redirectedUrl("/colegio/marca"))
				.andExpect(flash().attribute("error", "Escribe la razón social (la que figura en SUNAT)."));
		mvc.perform(multipart("/colegio/marca").with(csrf()).with(UsuariosDePrueba.como(Rol.PROMOTOR))
						.param("nombre", nombreOriginal).param("razonSocial", "Razón").param("direccion", "Av. Sur 1")
						.param("correoInstitucional", "no-es-un-correo"))
				.andExpect(flash().attribute("error", containsString("correo institucional")));
		assertThat(jdbc.queryForObject("SELECT razon_social FROM colegio WHERE id = 1", String.class)).isNull();
	}

	@Test
	void elRucNoSeCambiaPorEstaPantalla() throws Exception {
		mvc.perform(ficha("Colegio Virgen María S.A.C.").param("ruc", "20999999999"))
				.andExpect(redirectedUrl("/colegio/marca")).andExpect(flash().attributeExists("exito"));
		assertThat(jdbc.queryForObject("SELECT ruc FROM colegio WHERE id = 1", String.class)).isEqualTo(RUC);
		assertThat(ultimoEvento(jdbc, "MARCA_COLEGIO_CAMBIADA").get("valor_nuevo")).asString().doesNotContain("ruc");
	}

	@Test
	void direccionYLosDemasRolesReciben403() throws Exception {
		for (Rol rol : List.of(Rol.DIRECTOR, Rol.ADMINISTRACION, Rol.CAJA, Rol.DOCENTE)) {
			mvc.perform(get("/colegio/marca").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
			mvc.perform(multipart("/colegio/marca").with(csrf()).with(UsuariosDePrueba.como(rol))
							.param("nombre", nombreOriginal).param("razonSocial", "Intento").param("direccion", "Av. Sur 1")
							.param("correoInstitucional", "secretaria@colegio.edu.pe"))
					.andExpect(status().isForbidden());
		}
		assertThat(jdbc.queryForObject("SELECT razon_social FROM colegio WHERE id = 1", String.class)).isNull();
	}

	@Test
	void laBoletaMuestraLaRazonSocialElRucLaDireccionYElLogoDelEmisor() throws Exception {
		mvc.perform(ficha("Colegio Virgen María S.A.C.").file(logo(ImagenesDePrueba.jpeg(200, 100))))
				.andExpect(flash().attributeExists("exito"));
		EscenarioCaja.Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		EscenarioCobranza.como(EscenarioCaja.CAJA);
		Long pagoId = cobro.cobrar(EscenarioCaja.efectivo(f.quispe(), List.of(EscenarioCaja.cuota(jdbc, f.mateo(),
				"PEN-2027-03")), "450.00", "450.00"));
		SecurityContextHolder.clearContext();

		mvc.perform(get("/caja/pagos/{id}/comprobante", pagoId).with(UsuariosDePrueba.como(EscenarioCaja.CAJA)))
				.andExpect(status().isOk()).andExpect(view().name("caja/comprobante"))
				.andExpect(content().string(containsString("Colegio Virgen María S.A.C.")))
				.andExpect(content().string(containsString("RUC " + RUC)))
				.andExpect(content().string(containsString("Av. Los Próceres 123, Santiago de Surco, Lima")))
				.andExpect(content().string(containsString("class=\"comprobante-logo\"")))
				.andExpect(content().string(containsString("src=\"/marca/logo\"")))
				.andExpect(content().string(containsString("B001-00000001")));
	}

	@Test
	void sinRucNiDireccionLaBoletaSigueSaliendoConElNombre() throws Exception {
		jdbc.update("UPDATE colegio SET ruc = NULL WHERE id = 1");
		EscenarioCaja.Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		EscenarioCobranza.como(EscenarioCaja.CAJA);
		Long pagoId = cobro.cobrar(EscenarioCaja.efectivo(f.quispe(), List.of(EscenarioCaja.cuota(jdbc, f.mateo(),
				"PEN-2027-03")), "450.00", "450.00"));
		SecurityContextHolder.clearContext();

		mvc.perform(get("/caja/pagos/{id}/comprobante", pagoId).with(UsuariosDePrueba.como(EscenarioCaja.CAJA)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(nombreOriginal)))
				.andExpect(content().string(not(containsString("RUC "))))
				.andExpect(content().string(not(containsString("comprobante-logo"))))
				.andExpect(content().string(containsString("B001-00000001")));
	}

	// ------------------------------------------------------------------ ayudas

	private MockMultipartHttpServletRequestBuilder ficha(String razonSocial) {
		return multipart("/colegio/marca").with(csrf()).with(UsuariosDePrueba.como(Rol.PROMOTOR))
				.param("nombre", nombreOriginal).param("razonSocial", razonSocial)
				.param("direccion", "Av. Los Próceres 123, Santiago de Surco, Lima")
				.param("correoInstitucional", "secretaria@colegio.edu.pe");
	}

	private static MockMultipartFile logo(byte[] contenido) {
		return new MockMultipartFile("logo", "logo.jpg", "image/jpeg", contenido);
	}
}
