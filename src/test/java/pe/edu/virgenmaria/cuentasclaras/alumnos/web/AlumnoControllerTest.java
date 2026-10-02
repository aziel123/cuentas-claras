package pe.edu.virgenmaria.cuentasclaras.alumnos.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.OtraPersona;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Alumnos desde la web (aplicación completa con MockMvc): registrar hermanos con un apoderado, matricularlos, ver la
 * ficha y la familia, buscar, y los errores junto a su campo.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AlumnoControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private JdbcTemplate jdbc;

	private Estructura escuela;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		escuela = EscenarioEscolar.crearEstructura(estructura);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void registrarHermanosConUnApoderadoYVerSuFicha() throws Exception {
		String fichaMateo = registrar(mateo("seccionId", escuela.primaria5A2026().toString()));
		mvc.perform(get(fichaMateo).with(admin())).andExpect(status().isOk())
				.andExpect(view().name("alumnos/ficha"))
				.andExpect(content().string(containsString("Mateo Quispe Huamán")))
				.andExpect(content().string(containsString("Rosa Huamán Ccori")))
				.andExpect(content().string(containsString("5.° Primaria A · 2026")))
				.andExpect(content().string(containsString("Cambiar responsable de pago")))
				.andExpect(content().string(containsString("2027 · 6.° Primaria A")));

		Long familia = jdbc.queryForObject("SELECT id FROM familia", Long.class);
		Long rosa = jdbc.queryForObject("SELECT id FROM apoderado", Long.class);
		mvc.perform(get("/alumnos/nuevo").param("familia", familia.toString()).with(admin()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Registrar hermano")))
				.andExpect(content().string(containsString("Rosa Huamán Ccori · Madre")));
		String fichaValeria = registrar(post("/alumnos/nuevo").with(admin()).with(csrf())
				.param("familia", familia.toString()).param("tipoDocumento", "DNI").param("numeroDocumento", "80127745")
				.param("apellidoPaterno", "Quispe").param("apellidoMaterno", "Huamán").param("nombres", "Valeria")
				.param("fechaNacimiento", "2018-09-03").param("apoderadoExistenteId", rosa.toString())
				.param("seccionId", escuela.primaria2B2026().toString()));

		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT familia_id) FROM alumno", Long.class)).isEqualTo(1);
		mvc.perform(get("/alumnos/familias/" + familia).with(admin())).andExpect(status().isOk())
				.andExpect(content().string(containsString("Paga por: Mateo Quispe Huamán, Valeria Quispe Huamán")))
				.andExpect(content().string(containsString("2.° Primaria B · 2026")));
		mvc.perform(get(fichaValeria).with(admin()))
				.andExpect(content().string(containsString("Hermanos en el colegio")))
				.andExpect(content().string(containsString("Mateo Quispe Huamán")));
	}

	@Test
	void errorDeUnDatoSeMuestraJuntoASuCampo() throws Exception {
		mvc.perform(mateo("numeroDocumento", "1234567"))
				.andExpect(status().isOk()).andExpect(view().name("alumnos/formulario"))
				.andExpect(content().string(containsString("El DNI debe tener 8 dígitos; escribiste «1234567».")))
				.andExpect(content().string(containsString("input-con-error")));
		mvc.perform(mateo("apoderadoTelefonoWhatsapp", "014567890"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("un teléfono fijo no recibe WhatsApp")));
		mvc.perform(mateo("fechaNacimiento", ""))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Elige la fecha de nacimiento.")));
		mvc.perform(mateo("documentoApoderadoExistente", "45678912"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Elige una sola opción")));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alumno", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM familia", Long.class)).isZero();
	}

	@Test
	void listaBuscaPorNombreODniYPagina() throws Exception {
		registrar(mateo("seccionId", escuela.primaria5A2026().toString()));

		mvc.perform(get("/alumnos").param("q", "7845").with(admin())).andExpect(status().isOk())
				.andExpect(content().string(containsString("Mateo Quispe Huamán")))
				.andExpect(content().string(containsString("DNI 78451236")))
				.andExpect(content().string(containsString("Sección 2026")));
		mvc.perform(get("/alumnos").param("q", "flores").with(admin())).andExpect(status().isOk())
				.andExpect(content().string(containsString("No encontramos alumnos")));
		mvc.perform(get("/alumnos").param("anio", "abc").with(admin())).andExpect(status().isOk())
				.andExpect(content().string(containsString("Revisa los filtros")));
		mvc.perform(get("/alumnos").with(UsuariosDePrueba.como(Rol.PROMOTOR))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Mateo Quispe Huamán")))
				.andExpect(content().string(not(containsString("Registrar alumno"))));
	}

	@Test
	void matricularCambiarSeccionCambiarResponsableYRetirarDesdeLaFicha() throws Exception {
		String ficha = registrar(mateo());
		Long mateo = Long.valueOf(ficha.substring(ficha.lastIndexOf('/') + 1));

		mvc.perform(post(ficha + "/matricula").with(admin()).with(csrf())
						.param("seccionId", escuela.primaria5A2026().toString()))
				.andExpect(flash().attribute("exito", "Listo: el alumno quedó matriculado."));
		Long matricula = jdbc.queryForObject("SELECT id FROM matricula WHERE alumno_id = ?", Long.class, mateo);
		mvc.perform(post("/alumnos/matriculas/" + matricula + "/seccion").with(admin()).with(csrf())
						.param("alumnoId", mateo.toString()).param("seccionId", escuela.primaria5B2026().toString())
						.param("motivo", "corto"))
				.andExpect(flash().attributeExists("error"));
		mvc.perform(post("/alumnos/matriculas/" + matricula + "/seccion").with(admin()).with(csrf())
						.param("alumnoId", mateo.toString()).param("seccionId", escuela.primaria5B2026().toString())
						.param("motivo", EscenarioEscolar.MOTIVO))
				.andExpect(flash().attribute("exito", "Listo: el alumno cambió de sección."));

		Long familia = jdbc.queryForObject("SELECT familia_id FROM alumno WHERE id = ?", Long.class, mateo);
		mvc.perform(post("/alumnos/familias/" + familia + "/apoderados").with(admin()).with(csrf())
						.param("tipoDocumento", "DNI").param("numeroDocumento", "40112233").param("apellidoPaterno", "Quispe")
						.param("nombres", "Juan").param("parentesco", "PADRE").param("correo", "Juan.Quispe@Gmail.com"))
				.andExpect(flash().attribute("exito", "Listo: agregaste a Juan Quispe como apoderado."));
		Long juan = jdbc.queryForObject("SELECT id FROM apoderado WHERE numero_documento = '40112233'", Long.class);
		mvc.perform(post(ficha + "/responsable").with(admin()).with(csrf()).param("apoderadoId", juan.toString())
						.param("motivo", "La madre viajó; paga el padre desde octubre"))
				.andExpect(flash().attribute("exito", containsString("se pidió el cambio de responsable de pago")));
		Long rosa = jdbc.queryForObject("SELECT id FROM apoderado WHERE numero_documento = ?", Long.class,
				EscenarioEscolar.DNI_ROSA);
		// Auditoría A4: hasta que otra persona lo apruebe, se sigue cobrando a Rosa.
		assertThat(jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class, mateo))
				.isEqualTo(rosa);
		mvc.perform(get(ficha).with(admin()))
				.andExpect(content().string(containsString("Esperando aprobación")))
				.andExpect(content().string(containsString("Cambio de responsable de pago")));
		aprobarComoOtraPersona();
		assertThat(jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class, mateo))
				.isEqualTo(juan);

		mvc.perform(post(ficha + "/retirar").with(admin()).with(csrf()).param("fecha", "2026-10-01")
						.param("motivo", "Se mudó a Arequipa con su familia"))
				.andExpect(flash().attribute("exito", containsString("se pidió el retiro")));
		assertThat(jdbc.queryForObject("SELECT estado FROM alumno WHERE id = ?", String.class, mateo))
				.isEqualTo("ACTIVO");
		aprobarComoOtraPersona();
		mvc.perform(get(ficha).with(admin()))
				.andExpect(content().string(containsString("Retirado el 01/10/2026")))
				.andExpect(content().string(not(containsString("Cambiar responsable de pago"))));
	}

	@Test
	void corregirDatosDelAlumnoYDelApoderadoExigeMotivo() throws Exception {
		String ficha = registrar(mateo());
		mvc.perform(get(ficha + "/editar").with(admin())).andExpect(status().isOk())
				.andExpect(content().string(containsString("value=\"78451236\"")))
				.andExpect(content().string(containsString("value=\"2015-06-14\"")));
		mvc.perform(post(ficha + "/editar").with(admin()).with(csrf()).param("tipoDocumento", "DNI")
						.param("numeroDocumento", "78451236").param("apellidoPaterno", "Quispe")
						.param("apellidoMaterno", "Huamán").param("nombres", "Mateo Alonso")
						.param("fechaNacimiento", "2015-06-14").param("motivo", ""))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Escribe el motivo.")));
		mvc.perform(post(ficha + "/editar").with(admin()).with(csrf()).param("tipoDocumento", "DNI")
						.param("numeroDocumento", "78451236").param("apellidoPaterno", "Quispe")
						.param("apellidoMaterno", "Huamán").param("nombres", "Mateo Alonso")
						.param("fechaNacimiento", "2015-06-14").param("motivo", "Faltaba su segundo nombre"))
				.andExpect(status().is3xxRedirection());

		Long rosa = jdbc.queryForObject("SELECT id FROM apoderado", Long.class);
		mvc.perform(get("/alumnos/apoderados/" + rosa).with(admin())).andExpect(status().isOk())
				.andExpect(content().string(containsString("value=\"+51987654321\"")));
		mvc.perform(post("/alumnos/apoderados/" + rosa).with(admin()).with(csrf()).param("tipoDocumento", "DNI")
						.param("numeroDocumento", "45678912").param("apellidoPaterno", "Huamán")
						.param("apellidoMaterno", "Ccori").param("nombres", "Rosa").param("parentesco", "MADRE")
						.param("telefonoWhatsapp", "999888777").param("correo", "").param("motivo", "Cambió de número"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("exito", containsString("quedó pedido")));
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM apoderado WHERE id = ?", String.class, rosa))
				.isEqualTo("+51987654321");
		aprobarComoOtraPersona();
		assertThat(jdbc.queryForMap("SELECT telefono_whatsapp, correo FROM apoderado WHERE id = ?", rosa))
				.containsEntry("telefono_whatsapp", "+51999888777").containsEntry("correo", null);
		assertThat(jdbc.queryForObject("SELECT nombres FROM alumno", String.class)).isEqualTo("Mateo Alonso");
	}

	/** Formulario de Mateo con Rosa como apoderada nueva; {@code cambios}: pares campo, valor que reemplazan. */
	private MockHttpServletRequestBuilder mateo(String... cambios) {
		java.util.Map<String, String> campos = new java.util.LinkedHashMap<>();
		campos.put("tipoDocumento", "DNI");
		campos.put("numeroDocumento", EscenarioEscolar.DNI_MATEO);
		campos.put("apellidoPaterno", "Quispe");
		campos.put("apellidoMaterno", "Huamán");
		campos.put("nombres", "Mateo");
		campos.put("fechaNacimiento", "2015-06-14");
		campos.put("apoderadoTipoDocumento", "DNI");
		campos.put("apoderadoNumeroDocumento", EscenarioEscolar.DNI_ROSA);
		campos.put("apoderadoApellidoPaterno", "Huamán");
		campos.put("apoderadoApellidoMaterno", "Ccori");
		campos.put("apoderadoNombres", "Rosa");
		campos.put("apoderadoParentesco", "MADRE");
		campos.put("apoderadoTelefonoWhatsapp", "987 654 321");
		campos.put("apoderadoCorreo", "");
		for (int i = 0; i + 1 < cambios.length; i += 2) {
			campos.put(cambios[i], cambios[i + 1]);
		}
		MockHttpServletRequestBuilder solicitud = post("/alumnos/nuevo").with(admin()).with(csrf());
		campos.forEach(solicitud::param);
		return solicitud;
	}

	/** Envía el formulario de registro y devuelve la ficha a la que redirige. */
	private String registrar(MockHttpServletRequestBuilder solicitud) throws Exception {
		MvcResult resultado = mvc.perform(solicitud).andExpect(status().is3xxRedirection()).andReturn();
		String ruta = resultado.getResponse().getRedirectedUrl();
		assertThat(ruta).matches("/alumnos/\\d+");
		return ruta;
	}

	/** Otra persona aprueba en /aprobaciones todas las solicitudes pendientes. */
	private void aprobarComoOtraPersona() throws Exception {
		for (Long id : jdbc.queryForList("SELECT id FROM solicitud_cambio WHERE estado = 'PENDIENTE'", Long.class)) {
			mvc.perform(post("/aprobaciones/" + id + "/aprobar").with(UsuariosDePrueba.como(OtraPersona.APROBADOR))
							.with(csrf()))
					.andExpect(flash().attribute("exito", "Listo: aprobaste la solicitud y el cambio se aplicó."));
		}
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
		return UsuariosDePrueba.como(Rol.ADMINISTRACION);
	}
}
