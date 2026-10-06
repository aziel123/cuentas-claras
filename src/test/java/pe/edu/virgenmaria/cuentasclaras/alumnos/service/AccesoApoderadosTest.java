package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 4: el acceso en línea del apoderado. Lo da Promotoría o Administración desde la ficha del apoderado (nunca
 * desde Usuarios), queda enlazado a ESE apoderado, con clave temporal, y se quita con motivo.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AccesoApoderadosTest {

	@Autowired
	private ServicioAccesoApoderados acceso;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void administracionDaElAccesoEnlazadoYConClaveTemporal() throws Exception {
		como(EscenarioCaja.CAJA);
		assertThatThrownBy(() -> acceso.darAcceso(f.rosa())).isInstanceOfAny(AccessDeniedException.class,
				AuthorizationDeniedException.class);

		como(EscenarioCobranza.ADMINISTRACION);
		var creado = acceso.darAcceso(f.rosa());

		Usuario usuario = ContextoColegio.en(1L, () -> usuarios.findById(creado.id()).orElseThrow());
		assertThat(usuario.getNombreUsuario()).isEqualTo("45678912");
		assertThat(usuario.getRoles()).containsExactly(Rol.APODERADO);
		assertThat(usuario.getApoderadoId()).isEqualTo(f.rosa());
		assertThat(usuario.isDebeCambiarClave()).isTrue();
		assertThat(acceso.cuentaDe(f.rosa())).contains("45678912");
		assertThatThrownBy(() -> acceso.darAcceso(f.rosa())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya tiene");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'ACCESO_APODERADO_CREADO'")).isEqualTo(1);

		// Con su clave ya cambiada, entra a SU familia.
		Usuario conClave = ContextoColegio.en(1L, () -> {
			Usuario u = usuarios.findById(creado.id()).orElseThrow();
			u.cambiarClave(u.getClaveHash(), java.time.LocalDateTime.now(), false);
			return usuarios.save(u);
		});
		mvc.perform(get("/familia").with(UsuariosDePrueba.como(conClave)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Mateo")));

		como(EscenarioCobranza.ADMINISTRACION);
		acceso.quitarAcceso(f.rosa(), "La familia pidió cerrar la cuenta en línea");
		assertThat(ContextoColegio.en(1L, () -> usuarios.findById(creado.id()).orElseThrow().isActivo())).isFalse();
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'ACCESO_APODERADO_QUITADO'")).isEqualTo(1);
	}

	@Test
	void desdeLaFichaDelApoderadoSeDaElAccesoYLaClaveSeMuestraUnaVez() throws Exception {
		mvc.perform(get("/alumnos/apoderados/" + f.pedro()).with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Acceso en línea")));
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.post("/alumnos/apoderados/" + f.pedro() + "/acceso")
				.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
				.with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)))
				.andExpect(status().isOk()).andExpect(content().string(containsString(EscenarioCaja.DNI_PEDRO)));
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.post("/alumnos/apoderados/" + f.rosa() + "/acceso")
				.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
				.with(UsuariosDePrueba.como(EscenarioCaja.CAJA)))
				.andExpect(status().isForbidden());
		assertThat(contar(jdbc, "usuario WHERE apoderado_id IS NOT NULL")).isEqualTo(1);
	}
}
