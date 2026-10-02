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

	@Autowired
	private javax.sql.DataSource fuenteDatos;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura estructura;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos servicioAlumnos;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioFamilias servicioFamilias;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioMatriculas servicioMatriculas;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ServicioImportacionAlumnos importacion;

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
		assertThatCode(() -> new VerificadorPermisosBaseDatos(jdbc, fuenteDatos).afterPropertiesSet())
				.doesNotThrowAnyException();
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
	void tildesYEmojiSeGuardanEnUtf8mb4YLaCadenaSigueIntegra() {
		String texto = "Pagó S/ 1,250.00 ✅🎉 · ñandú «Año» " + sufijo;
		EventoAuditoria evento = auditoria.registrar(Actor.sistema(1L), AccionAuditoria.USUARIO_CREADO, "usuario", "1",
				null, texto, texto);

		assertThat(jdbc.queryForObject("SELECT detalle FROM evento_auditoria WHERE secuencia = ?", String.class,
				evento.getSecuencia())).isEqualTo(texto);
		UsuariosDePrueba.iniciarSesion(guardar("verificadora." + sufijo, Rol.PROMOTOR));
		assertThat(verificador.verificar().integra()).isTrue();
	}

	@Test
	void elConteoDePromotoresConBloqueoFuncionaEnMySql() {
		Usuario primera = guardar("promo1." + sufijo, Rol.PROMOTOR);
		Usuario segunda = guardar("promo2." + sufijo, Rol.PROMOTOR);
		UsuariosDePrueba.iniciarSesion(primera);

		servicioUsuarios.desactivar(segunda.getId(), "Prueba de bloqueo en MySQL");

		assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?", Boolean.class, segunda.getId()))
				.isFalse();
	}

	@Test
	void lasFechasSeGuardanTalCualEnHoraDeLima() {
		EventoAuditoria evento = auditoria.registrar(Actor.sistema(1L), AccionAuditoria.SESION_CERRADA, null, null,
				null, null, "prueba de fechas " + sufijo);

		LocalDateTime enBase = jdbc.queryForObject("SELECT ocurrido_en FROM evento_auditoria WHERE secuencia = ?",
				LocalDateTime.class, evento.getSecuencia());
		assertThat(enBase).isEqualTo(evento.getOcurridoEn());
	}

	@Test
	void deleteSobreTablasEscolaresFallaCon1142() {
		for (String tabla : new String[] { "anio_escolar", "seccion", "familia", "apoderado", "alumno", "matricula" }) {
			assertThatThrownBy(() -> jdbc.update("DELETE FROM " + tabla + " WHERE 1 = 0"))
					.isInstanceOf(DataAccessException.class)
					.satisfies(e -> assertThat(codigoMySql(e)).as(tabla).isEqualTo(1142));
		}
	}

	/**
	 * Sprint 2, tanda 1: año, secciones, hermanos con un apoderado, matrícula, cambio de sección, corrección del
	 * celular, búsqueda (LIKE con escape) y retiro, todo con los permisos mínimos de cc_app. No limpia: usa un año
	 * libre y documentos únicos.
	 */
	@Test
	void estructuraYAlumnosFuncionanConLosPermisosMinimos() {
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		java.util.Set<Integer> usados = new java.util.HashSet<>(
				jdbc.queryForList("SELECT anio FROM anio_escolar WHERE colegio_id = 1", Integer.class));
		int anio = java.util.stream.IntStream.iterate(2025, a -> a >= 2000, a -> a - 1).filter(a -> !usados.contains(a))
				.findFirst().orElseThrow();
		Long anioId = estructura.crearAnio(new pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest(anio,
				java.time.LocalDate.of(anio, 3, 2), java.time.LocalDate.of(anio, 12, 18), false));
		Long seccionA = estructura.crearSeccion(anioId, new pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest(
				pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado.PRIMARIA_5, "A"));
		Long seccionB = estructura.crearSeccion(anioId, new pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest(
				pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado.PRIMARIA_5, "B"));
		// En MySQL «a» y «A» son iguales para el índice único: el servicio lo avisa antes.
		assertThatThrownBy(() -> estructura.crearSeccion(anioId, new pe.edu.virgenmaria.cuentasclaras.colegio.dto
				.CrearSeccionRequest(pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado.PRIMARIA_5, "a")))
				.hasMessageContaining("Ya existe la sección");

		String base = String.format("%07d", Math.floorMod(System.nanoTime(), 10_000_000L));
		String dniApoderado = "4" + base;
		var mateo = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
				.conApoderadoNuevo("7" + base, "Quispe", "Huamán", "Mateo", java.time.LocalDate.of(anio - 10, 6, 14),
						dniApoderado, "Huamán", "Ccori", "Rosa", "987654321", "rosa@example.com", seccionA));
		var valeria = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
				.conApoderadoRegistrado("8" + base, "Quispe", "Huamán", "Valeria", java.time.LocalDate.of(anio - 10, 1, 3),
						dniApoderado, seccionA));
		assertThat(valeria.familiaId()).isEqualTo(mateo.familiaId());

		Long matricula = jdbc.queryForObject("SELECT id FROM matricula WHERE alumno_id = ?", Long.class, mateo.alumnoId());
		servicioMatriculas.cambiarSeccion(matricula, new pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CambiarSeccionRequest(
				seccionB, "Prueba en MySQL real"));
		Long rosa = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				mateo.alumnoId());
		servicioFamilias.actualizarApoderado(rosa, new pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoRequest(
				pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento.DNI, dniApoderado, "Huamán", "Ccori", "Rosa",
				pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco.MADRE, "999888777", "rosa@example.com",
				"Prueba en MySQL real"));
		assertThat(servicioAlumnos.buscar(new pe.edu.virgenmaria.cuentasclaras.alumnos.dto.BusquedaAlumnos("7" + base,
				null, null, null), 0).getContent()).hasSize(1);
		assertThat(servicioAlumnos.buscar(new pe.edu.virgenmaria.cuentasclaras.alumnos.dto.BusquedaAlumnos("quispe huaman",
				anioId, null, null), 0).getContent()).hasSize(2);
		assertThat(servicioAlumnos.buscar(new pe.edu.virgenmaria.cuentasclaras.alumnos.dto.BusquedaAlumnos("%", anioId,
				null, null), 0).getContent()).isEmpty();
		servicioAlumnos.retirar(valeria.alumnoId(), new pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RetirarAlumnoRequest(
				java.time.LocalDate.of(anio, 12, 1), "Prueba en MySQL real"));

		// La base rechaza un responsable de pago de otra familia y una sección de otro año (FK compuestas).
		Long otraFamilia = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
				.conApoderadoNuevo("6" + base, "Flores", "Rojas", "Sebastián", java.time.LocalDate.of(anio - 12, 8, 21),
						"3" + base, "Flores", "Díaz", "Pedro", "912345678", null, null)).familiaId();
		Long pedro = jdbc.queryForObject("SELECT id FROM apoderado WHERE familia_id = ?", Long.class, otraFamilia);
		assertThatThrownBy(() -> jdbc.update("UPDATE alumno SET responsable_pago_id = ? WHERE id = ?", pedro,
				mateo.alumnoId())).isInstanceOf(DataAccessException.class);
		assertThat(jdbc.queryForObject("SELECT estado FROM alumno WHERE id = ?", String.class, valeria.alumnoId()))
				.isEqualTo("RETIRADO");
		assertThat(jdbc.queryForObject("SELECT seccion_id FROM matricula WHERE id = ?", Long.class, matricula))
				.isEqualTo(seccionB);
		UsuariosDePrueba.iniciarSesion(guardar("verif.alumnos." + sufijo, Rol.PROMOTOR));
		assertThat(verificador.verificar().integra()).isTrue();
	}

	@Test
	void importacionSoloAdmiteInsercion() {
		for (String sentencia : new String[] { "UPDATE importacion_alumnos SET filas = filas WHERE 1 = 0",
				"DELETE FROM importacion_alumnos WHERE 1 = 0" }) {
			assertThatThrownBy(() -> jdbc.update(sentencia))
					.isInstanceOf(DataAccessException.class)
					.satisfies(e -> assertThat(codigoMySql(e)).as(sentencia).isEqualTo(1142));
		}
	}

	/**
	 * Sprint 2, tanda 2: importación desde Excel con los permisos mínimos de cc_app (vista previa, confirmación con el
	 * año bloqueado, registro de la importación y reimportación «sin cambios»). Usa un año libre y documentos únicos.
	 */
	@Test
	void importacionDesdeExcelFuncionaConLosPermisosMinimos() {
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		java.util.Set<Integer> usados = new java.util.HashSet<>(
				jdbc.queryForList("SELECT anio FROM anio_escolar WHERE colegio_id = 1", Integer.class));
		int anio = java.util.stream.IntStream.iterate(2025, a -> a >= 2000, a -> a - 1).filter(a -> !usados.contains(a))
				.findFirst().orElseThrow();
		Long anioId = estructura.crearAnio(new pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest(anio,
				java.time.LocalDate.of(anio, 3, 2), java.time.LocalDate.of(anio, 12, 18), false));
		estructura.crearSeccion(anioId, new pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest(
				pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado.PRIMARIA_5, "A"));
		String base = String.format("%07d", Math.floorMod(System.nanoTime() + 7, 10_000_000L));
		String nacimiento = "14/06/" + (anio - 10);
		byte[] libro = pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.archivo(
				pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.fila("7" + base, "Quispe", "Huamán",
						"Mateo", nacimiento, "Primaria", "5", "A", "4" + base, "Huamán", "Ccori", "Rosa", "Madre",
						"987654321", null),
				pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.fila("8" + base, "Quispe", "Huamán",
						"Valeria", nacimiento, "Primaria", "5", "a", "4" + base, "Huamán", "Ccori", "Rosa", "Madre",
						"987654321", null));

		var previa = importacion.previsualizar(anioId, "alumnos-mysql.xlsx", libro);
		assertThat(previa.resumen().filasConErrores()).isZero();
		var resultado = importacion.confirmar(previa, previa.token());

		assertThat(resultado.resumen().alumnosNuevos()).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT familia_id) FROM alumno WHERE numero_documento IN (?, ?)",
				Long.class, "7" + base, "8" + base)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT alumnos_nuevos FROM importacion_alumnos WHERE id = ?", Integer.class,
				resultado.importacionId())).isEqualTo(2);
		var otraVez = importacion.previsualizar(anioId, "alumnos-mysql.xlsx", libro);
		assertThat(otraVez.resumen().sinCambios()).isTrue();
		assertThat(otraVez.importadoAntesEn()).isNotNull();
		UsuariosDePrueba.iniciarSesion(guardar("verif.importa." + sufijo, Rol.PROMOTOR));
		assertThat(verificador.verificar().integra()).isTrue();
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
