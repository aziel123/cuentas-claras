package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.AutoaprobacionSolicitudException;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 6, tanda 2 (hallazgo 5, P6, decisión 78): el celular o el correo del personal (por ahí llegan la huella, el
 * resumen y las alertas) cambian solo con una solicitud que aprueba OTRA persona de Promotoría o Dirección (nunca el
 * titular ni quien la pidió), nunca a un contacto de un apoderado o de otra persona del personal, y se avisa al contacto
 * anterior. En MySQL lo exige además trg_usuario_contacto (PermisosMySqlTest).
 */
@PruebaIntegracion
class ContactoPersonalTest {

	@Autowired
	private ServicioContactoPersonal servicio;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario lucia;

	private Usuario promotora;

	private Usuario promotora2;

	private Usuario directora;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		lucia = guardar("lucia.caja", Rol.CAJA);
		promotora = guardar("promotora", Rol.PROMOTOR);
		promotora2 = guardar("promotora2", Rol.PROMOTOR);
		directora = guardar("directora", Rol.DIRECTOR);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Usuario guardar(String nombre, Rol rol) {
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, rol);
	}

	private Long pide(Usuario quien, Usuario para, String telefono, String correo) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return servicio.solicitar(para.getId(), telefono, correo, "Cambió de número de celular este mes");
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private void aprueba(Usuario quien, Long solicitud) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			bandeja.aprobar(solicitud, null);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	/** P6: el titular lo pide, otra persona lo aprueba, el cambio queda con su solicitud y se avisa al número anterior. */
	@Test
	void elTitularPideOtraPersonaApruebaYSeAvisaAlContactoAnterior() {
		String anterior = lucia.getTelefonoWhatsapp();
		Long solicitud = pide(lucia, lucia, "987 111 222", "lucia@colegio.pe");
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class, lucia.getId()))
				.as("hasta que otra persona apruebe, nada cambia").isEqualTo(anterior);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = "
				+ "'CONTACTO_PERSONAL_SOLICITADO'", Long.class)).isEqualTo(1);

		aprueba(directora, solicitud);

		assertThat(jdbc.queryForMap("SELECT telefono_whatsapp, correo, contacto_solicitud_id FROM usuario WHERE id = ?",
				lucia.getId())).containsEntry("telefono_whatsapp", "+51987111222")
				.containsEntry("correo", "lucia@colegio.pe").containsEntry("contacto_solicitud_id", solicitud);
		Map<String, Object> aviso = jdbc.queryForMap("SELECT destinatario_tipo, usuario_id, canal, destino, plantilla, "
				+ "entidad, entidad_id FROM mensaje WHERE tipo = 'CONTACTO_CAMBIADO'");
		assertThat(aviso).containsEntry("destinatario_tipo", "USUARIO").containsEntry("usuario_id", lucia.getId())
				.containsEntry("canal", "WHATSAPP").containsEntry("destino", anterior)
				.containsEntry("plantilla", "CONTACTO_PERSONAL_CAMBIADO").containsEntry("entidad", "solicitud_cambio")
				.containsEntry("entidad_id", solicitud);
		Map<String, Object> evento = jdbc.queryForMap("SELECT valor_anterior, valor_nuevo, detalle FROM evento_auditoria "
				+ "WHERE accion = 'CONTACTO_PERSONAL_CAMBIADO'");
		assertThat((String) evento.get("detalle")).contains("pedida por lucia.caja", "aprobada por directora");
		assertThat((String) evento.get("valor_nuevo")).doesNotContain("987111222").contains("WhatsApp");
	}

	/** P6: el titular no aprueba su propio cambio, ni cuando lo pidió otra persona de Promotoría por él. */
	@Test
	void elTitularNoApruebaSuPropioCambio() {
		Long propia = pide(promotora, promotora, "987 333 444", null);
		assertThatThrownBy(() -> aprueba(promotora, propia)).isInstanceOf(AutoaprobacionSolicitudException.class);
		jdbc.update("UPDATE solicitud_cambio SET estado = 'RECHAZADA', pendiente = NULL, resuelto_por = 'directora', "
				+ "resuelto_en = CURRENT_TIMESTAMP, comentario = 'otra' WHERE id = ?", propia);

		Long porOtra = pide(promotora2, promotora, "987 333 444", null);
		assertThatThrownBy(() -> aprueba(promotora, porOtra)).as("el titular")
				.isInstanceOf(AutoaprobacionSolicitudException.class);
		assertThatThrownBy(() -> aprueba(promotora2, porOtra)).as("quien la pidió")
				.isInstanceOf(AutoaprobacionSolicitudException.class);
		aprueba(directora, porOtra);
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class,
				promotora.getId())).isEqualTo("+51987333444");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'AUTOAPROBACION_RECHAZADA'",
				Long.class)).isEqualTo(3);
	}

	/** El contacto nuevo no es de un apoderado (comparado normalizado) ni de otra persona del personal. */
	@Test
	void elContactoNuevoNoEsDeUnApoderadoNiDeOtraPersona() {
		EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		assertThatThrownBy(() -> pide(lucia, lucia, "+51 912 345 678", null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("apoderado");
		assertThatThrownBy(() -> pide(lucia, lucia, promotora.getTelefonoWhatsapp().substring(3), null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("otra persona del personal");
		assertThatThrownBy(() -> pide(lucia, lucia, null, null)).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> pide(lucia, lucia, lucia.getTelefonoWhatsapp(), null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("nada que cambiar");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_cambio", Long.class)).isZero();
	}

	/** Decisión 78: solo Promotoría pide el cambio de OTRA persona; Dirección y Caja, solo el suyo. */
	@Test
	void soloPromotoriaPideElDeOtraPersona() {
		assertThatThrownBy(() -> pide(directora, lucia, "987 555 666", null)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> pide(lucia, directora, "987 555 666", null)).isInstanceOf(AccessDeniedException.class);
		assertThat(pide(promotora, lucia, "987 555 666", null)).isNotNull();
	}

	/** Un rechazo no cambia nada; y la base no deja que dos personas usen la misma solicitud (H2: la UNIQUE). */
	@Test
	void unRechazoNoCambiaNadaYLaSolicitudNoSeReusa() {
		String anterior = lucia.getTelefonoWhatsapp();
		Long solicitud = pide(lucia, lucia, "987 777 888", null);
		UsuariosDePrueba.iniciarSesion(directora);
		bandeja.rechazar(solicitud, "No lo confirmó en persona");
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class, lucia.getId()))
				.isEqualTo(anterior);
		jdbc.update("UPDATE usuario SET contacto_solicitud_id = ? WHERE id = ?", solicitud, lucia.getId());
		assertThatThrownBy(() -> jdbc.update("UPDATE usuario SET contacto_solicitud_id = ? WHERE id = ?", solicitud,
				promotora.getId())).isInstanceOf(DataIntegrityViolationException.class);
	}

	/** Aislamiento: el personal de otro colegio no existe para Promotoría (404, sin pistas). */
	@Test
	void elPersonalDeOtroColegioNoExiste() {
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		Usuario deB = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "cajera.b", UsuariosDePrueba.CLAVE, false,
				Rol.CAJA);
		assertThatThrownBy(() -> pide(promotora, deB, "987 999 000", null)).isInstanceOf(RecursoNoEncontradoException.class);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(colegioB, 991L, "promotor.b", "Promotor B", false,
				EnumSet.of(Rol.PROMOTOR)));
		assertThatThrownBy(() -> servicio.actual(lucia.getId())).isInstanceOf(RecursoNoEncontradoException.class);
	}

	/** Web: «Mi celular y correo» muestra el contacto enmascarado y crea la solicitud; Promotoría lo pide para otra. */
	@Test
	void pantallasDelContacto() throws Exception {
		mvc.perform(get("/cuenta/contacto").with(UsuariosDePrueba.como(lucia))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Mi celular y correo")))
				.andExpect(content().string(not(containsString(lucia.getTelefonoWhatsapp()))));
		mvc.perform(post("/cuenta/contacto").with(csrf()).with(UsuariosDePrueba.como(lucia)).param("telefono", "987123456")
				.param("correo", "").param("motivo", "Cambié de número de celular")).andExpect(redirectedUrl("/cuenta/contacto"));
		mvc.perform(get("/cuenta/contacto").with(UsuariosDePrueba.como(lucia))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Ya pediste un cambio")));
		mvc.perform(post("/usuarios/" + directora.getId() + "/contacto").with(csrf())
				.with(UsuariosDePrueba.como(promotora)).param("telefono", "987654000").param("motivo", "Cambió de número"))
				.andExpect(redirectedUrl("/usuarios/" + directora.getId()));
		mvc.perform(post("/usuarios/" + lucia.getId() + "/contacto").with(csrf()).with(UsuariosDePrueba.como(directora))
				.param("telefono", "987654111").param("motivo", "Cambió de número")).andExpect(status().isForbidden());
		mvc.perform(get("/cuenta/contacto").with(UsuariosDePrueba.como(Rol.APODERADO))).andExpect(status().isForbidden());
		assertThat(jdbc.queryForList("SELECT entidad_id FROM solicitud_cambio WHERE tipo = 'CAMBIO_CONTACTO_PERSONAL'",
				Long.class)).containsExactlyInAnyOrder(lucia.getId(), directora.getId());
	}
}
