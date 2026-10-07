package pe.edu.virgenmaria.cuentasclaras.comunicacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAccesoApoderados;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EnlacesDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor.BuzonSimulado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CrearUsuarioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnlacesActivacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioActivacionCuenta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 5 (A2, S4-M2, G7): quien da el acceso nunca recibe ni ve el enlace; el token se genera en el envío, solo existe
 * en lo que recibió el titular y en la base queda solo su SHA-256 (nunca en el mensaje ni en la bitácora).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AccesoDirectoTest {

	private static final String CELULAR_ROSA = "+51" + EscenarioEscolar.CELULAR_ROSA;

	@Autowired
	private ServicioAccesoApoderados acceso;

	@Autowired
	private ServicioUsuarios usuarios;

	@Autowired
	private ServicioActivacionCuenta activacion;

	@Autowired
	private DespachoMensajes despacho;

	@Autowired
	private BuzonSimulado buzon;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		buzon.vaciar();
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		buzon.vaciar();
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void quienDaElAccesoNoRecibeElEnlace() throws Exception {
		String pagina = mvc.perform(post("/alumnos/apoderados/" + f.rosa() + "/acceso").with(csrf())
				.with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Enlace enviado a WhatsApp +51 *** *** 321")))
				.andExpect(content().string(not(containsString("/activar/"))))
				.andReturn().getResponse().getContentAsString();
		assertThat(pagina).doesNotContain("Clave temporal");

		String ruta = EnlacesDePrueba.recibido(despacho, buzon, 1L, CELULAR_ROSA);
		// El enlace solo llegó al contacto registrado de Rosa: a ningún otro destino.
		assertThat(buzon.entregas()).allSatisfy(e -> assertThat(e.destino()).isEqualTo(CELULAR_ROSA));
		assertThat(pagina).doesNotContain(EnlacesDePrueba.token(ruta));
	}

	@Test
	void elTokenNoSeGuardaEnElMensaje() {
		como(EscenarioCobranza.ADMINISTRACION);
		acceso.darAcceso(f.rosa());
		SecurityContextHolder.clearContext();
		String token = EnlacesDePrueba.token(EnlacesDePrueba.recibido(despacho, buzon, 1L, CELULAR_ROSA));

		assertThat(contar(jdbc, "mensaje WHERE parametros LIKE '%" + token + "%' OR destino LIKE '%" + token + "%'"))
				.isZero();
		assertThat(contar(jdbc, "mensaje WHERE parametros LIKE '%/activar/%'")).isZero();
		assertThat(contar(jdbc, "evento_auditoria WHERE detalle LIKE '%" + token + "%' OR valor_nuevo LIKE '%" + token
				+ "%'")).isZero();
		assertThat(contar(jdbc, "enlace_activacion WHERE hash_token = '" + EnlacesActivacion.hash(token) + "'"))
				.isEqualTo(1);
	}

	@Test
	void restablecerAnulaLosEnlacesAnteriores() {
		como(EscenarioCobranza.ADMINISTRACION);
		acceso.darAcceso(f.rosa());
		SecurityContextHolder.clearContext();
		String primero = EnlacesDePrueba.token(EnlacesDePrueba.recibido(despacho, buzon, 1L, CELULAR_ROSA));

		como(EscenarioCobranza.PROMOTORIA);
		acceso.restablecerAcceso(f.rosa());
		SecurityContextHolder.clearContext();
		assertThat(activacion.vista(1L, primero)).as("se anula al restablecer, antes del envío").isEmpty();
		String segundo = EnlacesDePrueba.token(EnlacesDePrueba.recibido(despacho, buzon, 1L, CELULAR_ROSA));

		assertThat(activacion.vista(1L, segundo)).isPresent();
		assertThat(contar(jdbc, "enlace_activacion WHERE anulado_en IS NOT NULL")).isEqualTo(1);
		assertThat(contar(jdbc, "enlace_activacion WHERE anulado_en IS NULL AND usado_en IS NULL")).isEqualTo(1);
	}

	@Test
	void personalSinCelularNiCorreoNoSeCrea() {
		como(EscenarioCobranza.PROMOTORIA);
		assertThatThrownBy(() -> usuarios.crear(new CrearUsuarioRequest("Carla Caja", "caja3", "", "", Set.of(Rol.CAJA))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("celular");
		SecurityContextHolder.clearContext();
		assertThat(contar(jdbc, "usuario WHERE nombre_usuario = 'caja3'")).isZero();
		assertThat(contar(jdbc, "mensaje")).isZero();
	}

	@Test
	void unApoderadoConElCelularDelPersonalNoRecibeElEnlaceSinAprobacion() {
		// G6: Rosa quedó registrada con un celular que también es de alguien del personal (y nadie más lo aprobó).
		var cajera = UsuariosDePrueba.guardar(usuariosRepo, codificador, 1L, "cajera.g6", UsuariosDePrueba.CLAVE, false,
				Rol.CAJA);
		jdbc.update("UPDATE usuario SET telefono_whatsapp = ? WHERE id = ?", CELULAR_ROSA, cajera.getId());
		jdbc.update("UPDATE apoderado SET correo = NULL WHERE id = ?", f.rosa());

		como(EscenarioCobranza.ADMINISTRACION);
		assertThatThrownBy(() -> acceso.darAcceso(f.rosa())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("personal");
		SecurityContextHolder.clearContext();
		assertThat(contar(jdbc, "usuario WHERE apoderado_id = " + f.rosa())).isZero();
		assertThat(contar(jdbc, "mensaje")).isZero();
	}

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository usuariosRepo;

	@Autowired
	private org.springframework.security.crypto.password.PasswordEncoder codificador;
}
