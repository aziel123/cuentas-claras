package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificadorIntegridadAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificadorPermisosBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * FASE 2 del job "mysql" del CI (después de scripts/mysql/02-permisos-tablas.sql): la aplicación funciona con
 * los permisos mínimos de cc_app y MySQL rechaza editar o borrar la bitácora con el error 1142.
 * No limpia la base: cc_app no puede borrar eventos (la base del CI es desechable). Usa nombres únicos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class PermisosMySqlTest {

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private AuditoriaService auditoria;

	@Autowired
	private ServicioUsuarios servicioUsuarios;

	@Autowired
	private VerificadorIntegridadAuditoria verificador;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void updateYDeleteSobreLaBitacoraFallanCon1142() {
		for (String sentencia : new String[] { "UPDATE evento_auditoria SET ip = ip WHERE 1 = 0",
				"DELETE FROM evento_auditoria WHERE 1 = 0" }) {
			assertThatThrownBy(() -> jdbc.update(sentencia))
					.isInstanceOf(DataAccessException.class)
					.satisfies(e -> assertThat(codigoMySql(e)).isEqualTo(1142));
		}
		assertThatCode(() -> new VerificadorPermisosBaseDatos(jdbc, true).verificar()).doesNotThrowAnyException();
	}

	@Test
	void laAplicacionFuncionaConLosPermisosMinimos() throws Exception {
		Usuario promotora = guardar("promotora." + sufijo, Rol.PROMOTOR);
		Usuario caja = guardar("caja." + sufijo, Rol.CAJA);

		// Login: SELECT ... FOR UPDATE y UPDATE en usuario; INSERT en evento_auditoria; UPDATE en auditoria_cadena.
		mvc.perform(post("/login").with(csrf()).param("usuario", caja.getNombreUsuario()).param("clave", "equivocada!"))
				.andExpect(redirectedUrl("/login?error"));
		mvc.perform(post("/login").with(csrf()).param("usuario", caja.getNombreUsuario())
				.param("clave", UsuariosDePrueba.CLAVE)).andExpect(redirectedUrl("/inicio"));

		// Gestión: reescribe usuario_rol (DELETE e INSERT) y audita.
		UsuariosDePrueba.iniciarSesion(promotora);
		servicioUsuarios.cambiarRoles(caja.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE), "Prueba en MySQL"));
		servicioUsuarios.desactivar(caja.getId(), "Prueba en MySQL real");

		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, caja.getId()))
				.containsExactly("DOCENTE");
		assertThat(verificador.verificar().integra()).isTrue();
	}

	@Test
	void lasFechasSeGuardanTalCualEnHoraDeLima() {
		EventoAuditoria evento = auditoria.registrar(Actor.sistema(1L), AccionAuditoria.SESION_CERRADA, null, null,
				null, null, "prueba de fechas " + sufijo);

		LocalDateTime enBase = jdbc.queryForObject("SELECT ocurrido_en FROM evento_auditoria WHERE secuencia = ?",
				LocalDateTime.class, evento.getSecuencia());
		assertThat(enBase).isEqualTo(evento.getOcurridoEn());
	}

	private Usuario guardar(String nombre, Rol rol) {
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, rol);
	}

	private static Integer codigoMySql(Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause()) {
			if (t instanceof SQLException sql) {
				return sql.getErrorCode();
			}
		}
		return null;
	}
}
