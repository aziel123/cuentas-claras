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
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.OtraPersona;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificadorIntegridadAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificadorPermisosBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
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
 * los permisos mínimos de cc_app; MySQL rechaza editar o borrar la bitácora y borrar datos financieros con el error
 * 1142, y cambiar el monto, la fecha o el alumno de una cuota con el error 1143 (GRANT por columna).
 * No limpia la base: cc_app no puede borrar eventos (la base del CI es desechable). Usa nombres únicos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class PermisosMySqlTest {

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private BandejaAprobaciones bandeja;

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

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension planes;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioSaldoInicial saldoInicial;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioAnulacionCuotas anulaciones;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository cuotas;

	@Autowired
	private org.springframework.transaction.PlatformTransactionManager transacciones;

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
		// Retiro y cambio de celular: los aprueba otra persona (INSERT y UPDATE por columna en solicitud_cambio).
		OtraPersona.apruebaLaDe(bandeja, jdbc, "alumno", valeria.alumnoId());
		OtraPersona.apruebaLaDe(bandeja, jdbc, "apoderado", rosa);
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM apoderado WHERE id = ?", String.class, rosa))
				.isEqualTo("+51999888777");

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

	@Test
	void deleteSobreTablasFinancierasFallaCon1142() {
		for (String tabla : new String[] { "cuota", "plan_pension", "lote_saldo_inicial", "linea_saldo_inicial",
				"solicitud_cambio" }) {
			assertThatThrownBy(() -> jdbc.update("DELETE FROM " + tabla + " WHERE 1 = 0"))
					.isInstanceOf(DataAccessException.class)
					.satisfies(e -> assertThat(codigoMySql(e)).as(tabla).isEqualTo(1142));
		}
	}

	/** GRANT UPDATE por columna: el monto, la fecha, el alumno, el origen y la clave de una cuota no se cambian. */
	@Test
	void updateDeMontoFechaYAlumnoDeUnaCuotaFallaCon1143() {
		for (String columna : new String[] { "monto", "fecha_vencimiento", "alumno_id", "clave", "matricula_id",
				"plan_pension_id", "descripcion", "tipo", "colegio_id" }) {
			assertThatThrownBy(() -> jdbc.update("UPDATE cuota SET " + columna + " = " + columna + " WHERE 1 = 0"))
					.isInstanceOf(DataAccessException.class)
					.satisfies(e -> assertThat(codigoMySql(e)).as(columna).isEqualTo(1143));
		}
		// Las columnas del estado de pago y de la anulación sí (las que la entidad Cuota puede actualizar).
		assertThatCode(() -> jdbc.update("UPDATE cuota SET estado = estado, monto_pagado = monto_pagado, "
				+ "obligacion = obligacion, anulacion_motivo = anulacion_motivo, actualizado_en = actualizado_en, "
				+ "version = version WHERE 1 = 0")).doesNotThrowAnyException();
	}

	/**
	 * Sprint 2, tanda 3, con los permisos mínimos de cc_app: plan (propuesta, aprobación y versión nueva), matrícula
	 * con cronograma, lote de saldo inicial (envío y confirmación de otra persona) y el gancho de anulación (solicitud
	 * y aprobación). Usa un año libre y documentos únicos.
	 */
	@Test
	void flujoCompletoConPermisosMinimos() {
		java.util.Set<Integer> usados = new java.util.HashSet<>(
				jdbc.queryForList("SELECT anio FROM anio_escolar WHERE colegio_id = 1", Integer.class));
		int anio = java.util.stream.IntStream.iterate(2025, a -> a >= 2000, a -> a - 1).filter(a -> !usados.contains(a))
				.findFirst().orElseThrow();
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long anioId = estructura.crearAnio(new pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest(anio,
				java.time.LocalDate.of(anio, 3, 2), java.time.LocalDate.of(anio, 12, 18), false));
		Long seccion = estructura.crearSeccion(anioId, new pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest(
				pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado.PRIMARIA_5, "A"));
		String base = String.format("%07d", Math.floorMod(System.nanoTime() + 13, 10_000_000L));

		// Plan: lo propone Administración y lo aprueba Dirección (UPDATE de plan_pension).
		Long plan = EscenarioCobranza.planAprobado(planes, anioId, anio, Nivel.PRIMARIA, "450", "350", null);
		// Matrícula: el cronograma se genera en la misma transacción (INSERT en cuota).
		Long mateo = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
				.conApoderadoNuevo("7" + base, "Quispe", "Huamán", "Mateo", java.time.LocalDate.of(anio - 10, 6, 14),
						"4" + base, "Huamán", "Ccori", "Rosa", "987654321", null, seccion)).alumnoId();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cuota WHERE alumno_id = ?", Long.class, mateo)).isEqualTo(11);
		// Versión nueva aprobada por Promotoría: reemplaza la anterior (UPDATE con vigente = NULL) sin tocar cuotas.
		Long v2 = planes.nuevaVersion(plan, "Reajuste aprobado por la asamblea de padres");
		planes.editarBorrador(v2, EscenarioCobranza.plan(anio, "465", "350", null));
		EscenarioCobranza.como(EscenarioCobranza.PROMOTORIA);
		EscenarioCobranza.aprobar(planes, v2);
		assertThat(jdbc.queryForObject("SELECT estado FROM plan_pension WHERE id = ?", String.class, plan))
				.isEqualTo("REEMPLAZADO");

		// Saldo inicial: lo arma y envía Administración, lo confirma Dirección.
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long lote = saldoInicial.crearLote(new pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest(anioId,
				java.time.LocalDate.of(anio, 9, 30), "Informe MySQL " + sufijo, new java.math.BigDecimal("80.00")));
		Long linea = saldoInicial.agregarLinea(lote, new pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest(
				"7" + base, pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo.OTRO, null, null, "Taller de verano",
				new java.math.BigDecimal("30.00"), java.time.LocalDate.of(anio, 2, 15)));
		saldoInicial.quitarLinea(lote, linea, "Se cargó con otro monto");
		saldoInicial.agregarLinea(lote, new pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest("7" + base,
				pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo.OTRO, null, null, "Taller de verano",
				new java.math.BigDecimal("80.00"), java.time.LocalDate.of(anio, 2, 15)));
		saldoInicial.enviar(lote);
		EscenarioCobranza.como(EscenarioCobranza.DIRECCION);
		assertThat(EscenarioCobranza.confirmar(saldoInicial, lote, "80.00")).isEqualTo(1);

		// Gancho de anulación: la solicitud (UPDATE de columnas permitidas) y la aprobación de otra persona.
		Long setiembre = jdbc.queryForObject("SELECT id FROM cuota WHERE alumno_id = ? AND numero = 9", Long.class, mateo);
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		anulaciones.solicitar(setiembre, "Prueba de anulación en MySQL real");
		OtraPersona.apruebaLaDe(bandeja, jdbc, "cuota", setiembre);
		assertThat(jdbc.queryForMap("SELECT estado, obligacion, monto FROM cuota WHERE id = ?", setiembre))
				.containsEntry("estado", "ANULADA").containsEntry("obligacion", null);
		// La base sigue rechazando la autoaprobación (CHECK) aunque cc_app pueda actualizar esas columnas.
		Long octubre = jdbc.queryForObject("SELECT id FROM cuota WHERE alumno_id = ? AND numero = 10", Long.class, mateo);
		assertThatThrownBy(() -> jdbc.update("UPDATE cuota SET estado = 'ANULADA', obligacion = NULL, "
				+ "anulada_en = NOW(), anulacion_motivo = 'Autoaprobación por SQL', anulacion_solicitada_por = 'x', "
				+ "anulacion_aprobada_por = 'x' WHERE id = ?", octubre)).isInstanceOf(DataAccessException.class);

		UsuariosDePrueba.iniciarSesion(guardar("verif.cobranza." + sufijo, Rol.PROMOTOR));
		assertThat(verificador.verificar().integra()).isTrue();
	}

	/** Correcciones del sprint 2 (M2): GRANT por columna también en planes, lotes, líneas y solicitudes. */
	@Test
	void columnasInmutablesDePlanesLotesLineasYSolicitudesFallanCon1143() {
		for (String sentencia : new String[] {
				"UPDATE plan_pension SET nivel = nivel WHERE 1 = 0",
				"UPDATE plan_pension SET numero_version = numero_version WHERE 1 = 0",
				"UPDATE plan_pension SET anio_escolar_id = anio_escolar_id WHERE 1 = 0",
				"UPDATE lote_saldo_inicial SET total_declarado = total_declarado WHERE 1 = 0",
				"UPDATE lote_saldo_inicial SET fecha_corte = fecha_corte WHERE 1 = 0",
				"UPDATE linea_saldo_inicial SET monto = monto WHERE 1 = 0",
				"UPDATE linea_saldo_inicial SET alumno_id = alumno_id WHERE 1 = 0",
				"UPDATE linea_saldo_inicial SET anio_deuda = anio_deuda WHERE 1 = 0",
				"UPDATE solicitud_cambio SET datos = datos WHERE 1 = 0",
				"UPDATE solicitud_cambio SET solicitado_por = solicitado_por WHERE 1 = 0" }) {
			assertThatThrownBy(() -> jdbc.update(sentencia))
					.isInstanceOf(DataAccessException.class)
					.satisfies(e -> assertThat(codigoMySql(e)).as(sentencia).isEqualTo(1143));
		}
	}

	/**
	 * Triggers de scripts/mysql/03-triggers.sql (el CI los aplica con cc_migrador antes de esta fase): un plan aprobado
	 * no cambia de montos, una línea de un lote enviado no se quita, y nada nace ya aprobado o confirmado.
	 */
	@Test
	void triggersImpidenCambiarPlanesAprobadosYLotesEnviados() {
		int anio = anioLibre();
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long anioId = estructura.crearAnio(new pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest(anio,
				java.time.LocalDate.of(anio, 3, 2), java.time.LocalDate.of(anio, 12, 18), false));
		estructura.crearSeccion(anioId, new pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest(
				pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado.PRIMARIA_5, "A"));
		Long seccion = jdbc.queryForObject("SELECT id FROM seccion WHERE anio_escolar_id = ?", Long.class, anioId);
		String base = String.format("%07d", Math.floorMod(System.nanoTime() + 29, 10_000_000L));
		servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.conApoderadoNuevo(
				"7" + base, "Rojas", "Paz", "Iris", java.time.LocalDate.of(anio - 10, 4, 4), "4" + base, "Paz", "Lima",
				"Ana", "987000111", null, seccion));
		Long plan = EscenarioCobranza.planAprobado(planes, anioId, anio, Nivel.PRIMARIA, "450", "350", null);

		assertThat(codigoAl(() -> jdbc.update("UPDATE plan_pension SET monto_pension = monto_pension - 100 WHERE id = ?",
				plan))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE plan_pension SET estado = 'BORRADOR', vigente = NULL WHERE id = ?",
				plan))).isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT monto_pension FROM plan_pension WHERE id = ?", java.math.BigDecimal.class,
				plan)).isEqualByComparingTo("450.00");

		Long lote = saldoInicial.crearLote(new pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest(anioId,
				java.time.LocalDate.of(anio, 9, 30), "Informe triggers " + sufijo, new java.math.BigDecimal("80.00")));
		Long linea = saldoInicial.agregarLinea(lote, new pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest(
				"7" + base, pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo.OTRO, null, null, "Excursión",
				new java.math.BigDecimal("80.00"), java.time.LocalDate.of(anio, 5, 10)));
		saldoInicial.enviar(lote);
		assertThat(codigoAl(() -> jdbc.update("UPDATE linea_saldo_inicial SET quitada = TRUE WHERE id = ?", linea)))
				.isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT quitada FROM linea_saldo_inicial WHERE id = ?", Boolean.class, linea))
				.isFalse();
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO lote_saldo_inicial (colegio_id, anio_escolar_id, fecha_corte, "
				+ "documento_referencia, total_declarado, estado, enviado_por, enviado_en, confirmado_por, confirmado_en, "
				+ "total_confirmado, creado_en, creado_por, actualizado_en) VALUES (1, ?, '2000-01-01', 'x', 1, 'CONFIRMADO', "
				+ "'a', NOW(6), 'b', NOW(6), 1, NOW(6), 'c', NOW(6))", anioId))).isEqualTo(1644);
	}

	// ================================================================== Sprint 3 · caja (tanda 1)

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro cobro;

	/** Familia con dos hermanos (Rosa) y otra familia (Pedro), con cuotas de un año libre (todas ya vencidas). */
	private record FamiliasCaja(Long familia, Long hermano1, Long hermano2, Long otraFamilia, Long otroAlumno) {
	}

	/**
	 * Un solo escenario para todas las pruebas de caja (cada una usa cuotas distintas): cada escenario gasta un año libre
	 * y la regla de edad del alumno deja pocos años válidos en una base que no se limpia.
	 */
	private static FamiliasCaja familiasCompartidas;

	private synchronized FamiliasCaja familiasDeCaja() {
		if (familiasCompartidas == null) {
			familiasCompartidas = crearFamiliasDeCaja();
		}
		return familiasCompartidas;
	}

	private FamiliasCaja crearFamiliasDeCaja() {
		int anio = anioLibre();
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long anioId = estructura.crearAnio(new pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest(anio,
				java.time.LocalDate.of(anio, 3, 2), java.time.LocalDate.of(anio, 12, 18), false));
		Long seccion = estructura.crearSeccion(anioId, new pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest(
				pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado.PRIMARIA_5, "A"));
		String base = String.format("%07d", Math.floorMod(System.nanoTime() + 41, 10_000_000L));
		var uno = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
				.conApoderadoNuevo("7" + base, "Quispe", "Huamán", "Mateo", java.time.LocalDate.of(anio - 10, 6, 14),
						"4" + base, "Huamán", "Ccori", "Rosa", "987654321", null, seccion));
		var dos = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
				.conApoderadoRegistrado("8" + base, "Quispe", "Huamán", "Valeria", java.time.LocalDate.of(anio - 10, 1, 3),
						"4" + base, seccion));
		var otro = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
				.conApoderadoNuevo("6" + base, "Flores", "Rojas", "Sebastián", java.time.LocalDate.of(anio - 11, 8, 21),
						"3" + base, "Flores", "Díaz", "Pedro", "912345678", null, seccion));
		EscenarioCobranza.planAprobado(planes, anioId, anio, Nivel.PRIMARIA, "450", "350", null);
		SecurityContextHolder.clearContext();
		return new FamiliasCaja(uno.familiaId(), uno.alumnoId(), dos.alumnoId(), otro.familiaId(), otro.alumnoId());
	}

	private Long cuotaDe(Long alumno, int numero) {
		return jdbc.queryForObject("SELECT id FROM cuota WHERE alumno_id = ? AND tipo = 'PENSION' AND numero = ?", Long.class,
				alumno, numero);
	}

	private pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado cajera(String nombre) {
		return UsuariosDePrueba.autenticado(1L, 300L, nombre + "." + sufijo, "Cajera " + nombre, false,
				EnumSet.of(Rol.CAJA));
	}

	private Long cobrarEfectivo(Long familia, java.util.List<Long> cuotas, String total) {
		return cobro.cobrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo(familia, cuotas, total,
				total));
	}

	@Test
	void deleteSobreTablasDeCajaFallaCon1142() {
		for (String tabla : new String[] { "serie_comprobante", "comprobante", "comprobante_linea", "caja_diaria", "pago",
				"aplicacion_pago" }) {
			assertThat(codigoAl(() -> jdbc.update("DELETE FROM " + tabla + " WHERE 1 = 0"))).as(tabla).isEqualTo(1142);
		}
	}

	@Test
	void updateDeTablasDeSoloInsercionFallaCon1142() {
		for (String tabla : new String[] { "comprobante_linea", "aplicacion_pago" }) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE " + tabla + " SET version = version WHERE 1 = 0"))).as(tabla)
					.isEqualTo(1142);
		}
	}

	@Test
	void columnasInmutablesDePagoComprobanteYCajaFallanCon1143() {
		for (String sentencia : new String[] { "UPDATE pago SET total = total WHERE 1 = 0",
				"UPDATE pago SET familia_id = familia_id WHERE 1 = 0", "UPDATE pago SET comprobante_id = comprobante_id WHERE 1 = 0",
				"UPDATE pago SET medio = medio WHERE 1 = 0", "UPDATE pago SET vuelto = vuelto WHERE 1 = 0",
				"UPDATE pago SET caja_diaria_id = caja_diaria_id WHERE 1 = 0",
				"UPDATE pago SET clave_idempotencia = clave_idempotencia WHERE 1 = 0",
				"UPDATE comprobante SET numero = numero WHERE 1 = 0", "UPDATE comprobante SET total = total WHERE 1 = 0",
				"UPDATE comprobante SET receptor_numero_documento = receptor_numero_documento WHERE 1 = 0",
				"UPDATE serie_comprobante SET serie = serie WHERE 1 = 0", "UPDATE caja_diaria SET fecha = fecha WHERE 1 = 0",
				"UPDATE caja_diaria SET cajero = cajero WHERE 1 = 0", "UPDATE caja_diaria SET fondo_fijo = fondo_fijo WHERE 1 = 0",
				"UPDATE cuota SET monto = monto WHERE 1 = 0" }) {
			assertThat(codigoAl(() -> jdbc.update(sentencia))).as(sentencia).isEqualTo(1143);
		}
		// Lo que sí cambia (envío al OSE, anulación, número de la serie, estado de la caja y lo pagado de la cuota).
		assertThatCode(() -> {
			jdbc.update("UPDATE comprobante SET estado_envio = estado_envio, intentos = intentos, respuesta = respuesta "
					+ "WHERE 1 = 0");
			jdbc.update("UPDATE pago SET estado = estado, operacion_vigente = operacion_vigente WHERE 1 = 0");
			jdbc.update("UPDATE serie_comprobante SET ultimo_numero = ultimo_numero WHERE 1 = 0");
			jdbc.update("UPDATE caja_diaria SET estado = estado WHERE 1 = 0");
			jdbc.update("UPDATE cuota SET monto_pagado = monto_pagado, monto_descuento = monto_descuento WHERE 1 = 0");
		}).doesNotThrowAnyException();
	}

	/** El pendiente del sprint 2: ni con SQL directo una cuota queda PAGADA (o PARCIAL) sin su pago en el libro. */
	@Test
	void cuotaPagadaSinAplicacionFallaCon1644() {
		FamiliasCaja familias = familiasDeCaja();
		Long cuota = cuotaDe(familias.hermano1(), 4);

		assertThat(codigoAl(() -> jdbc.update("UPDATE cuota SET estado = 'PAGADA', monto_pagado = monto WHERE id = ?",
				cuota))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE cuota SET estado = 'PARCIAL', monto_pagado = 100 WHERE id = ?",
				cuota))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE cuota SET estado = 'EXONERADA', monto_descuento = monto WHERE id = ?",
				cuota))).isEqualTo(1644);
		// Tampoco nace pagada.
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO cuota (colegio_id, alumno_id, anio_escolar_id, matricula_id, "
				+ "plan_pension_id, tipo, numero, descripcion, monto, monto_pagado, monto_descuento, fecha_vencimiento, estado, "
				+ "clave, obligacion, creado_en, creado_por, actualizado_en) SELECT colegio_id, alumno_id, anio_escolar_id, "
				+ "matricula_id, plan_pension_id, tipo, numero, descripcion, monto, monto, 0, fecha_vencimiento, 'PAGADA', "
				+ "CONCAT(clave, 'x'), NULL, creado_en, creado_por, actualizado_en FROM cuota WHERE id = ?", cuota)))
				.isEqualTo(1644);
		assertThat(jdbc.queryForMap("SELECT estado, monto_pagado FROM cuota WHERE id = ?", cuota))
				.containsEntry("estado", "PENDIENTE");
		// Cobrada de verdad, sí.
		UsuariosDePrueba.iniciarSesion(cajera("caja.libro"));
		cobrarEfectivo(familias.familia(), java.util.List.of(cuota), "450.00");
		assertThat(jdbc.queryForObject("SELECT estado FROM cuota WHERE id = ?", String.class, cuota)).isEqualTo("PAGADA");
	}

	@Test
	void aplicacionACuotaDeOtraFamiliaFallaCon1644() {
		FamiliasCaja familias = familiasDeCaja();
		UsuariosDePrueba.iniciarSesion(cajera("caja.familia"));
		Long pago = cobrarEfectivo(familias.familia(), java.util.List.of(cuotaDe(familias.hermano1(), 3)), "450.00");

		String aplicar = "INSERT INTO aplicacion_pago (colegio_id, pago_id, cuota_id, tipo, monto, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, ?, ?, 'APLICACION', ?, NOW(6), 'x', NOW(6))";
		// A la cuota de otra familia.
		assertThat(codigoAl(() -> jdbc.update(aplicar, pago, cuotaDe(familias.otroAlumno(), 3), 1))).isEqualTo(1644);
		// Más de lo que se pagó (a otra cuota de la misma familia).
		assertThat(codigoAl(() -> jdbc.update(aplicar, pago, cuotaDe(familias.hermano2(), 3), 1))).isEqualTo(1644);
		// Una reversión sin anulación registrada.
		Long original = jdbc.queryForObject("SELECT id FROM aplicacion_pago WHERE pago_id = ?", Long.class, pago);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO aplicacion_pago (colegio_id, pago_id, cuota_id, tipo, monto, "
				+ "revierte_id, creado_en, creado_por, actualizado_en) SELECT colegio_id, pago_id, cuota_id, 'REVERSION', "
				+ "-monto, id, NOW(6), 'x', NOW(6) FROM aplicacion_pago WHERE id = ?", original))).isEqualTo(1644);
		// Ni un pago que cambia de estado sin su anulación.
		assertThat(codigoAl(() -> jdbc.update("UPDATE pago SET estado = 'ANULADO', operacion_vigente = NULL WHERE id = ?",
				pago))).isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aplicacion_pago WHERE pago_id = ?", Long.class, pago))
				.isEqualTo(1);
	}

	@Test
	void pagoConTotalDistintoDeSuBoletaFallaCon1644() {
		FamiliasCaja familias = familiasDeCaja();
		UsuariosDePrueba.iniciarSesion(cajera("caja.total"));
		Long pago = cobrarEfectivo(familias.familia(), java.util.List.of(cuotaDe(familias.hermano1(), 5)), "450.00");

		// Un pago «de menos» que reusa la boleta de otro: el trigger lo rechaza antes que el UNIQUE.
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO pago (colegio_id, familia_id, caja_diaria_id, cajero, fecha, "
				+ "comprobante_id, medio, total, recibido, vuelto, origen, clave_idempotencia, estado, creado_en, creado_por, "
				+ "actualizado_en) SELECT colegio_id, familia_id, caja_diaria_id, cajero, fecha, comprobante_id, medio, 100, "
				+ "100, 0, origen, ?, estado, NOW(6), creado_por, NOW(6) FROM pago WHERE id = ?", "menos-" + sufijo, pago)))
				.isEqualTo(1644);
		// Ni nace anulado.
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO pago (colegio_id, familia_id, caja_diaria_id, cajero, fecha, "
				+ "comprobante_id, medio, total, recibido, vuelto, origen, clave_idempotencia, estado, creado_en, creado_por, "
				+ "actualizado_en) SELECT colegio_id, familia_id, caja_diaria_id, cajero, fecha, comprobante_id, medio, total, "
				+ "recibido, vuelto, origen, ?, 'ANULADO', NOW(6), creado_por, NOW(6) FROM pago WHERE id = ?",
				"anulado-" + sufijo, pago))).isEqualTo(1644);
	}

	/**
	 * La caja no se cierra sin su cierre registrado, el conteo a ciegas no se reescribe (no se tantea el esperado) y no
	 * se reabre sin una reapertura aprobada (trigger trg_caja_diaria_estado en su versión final).
	 */
	@Test
	void cajaNoSeCierraSinCierreRegistradoFallaCon1644() {
		FamiliasCaja familias = familiasDeCaja();
		UsuariosDePrueba.iniciarSesion(cajera("caja.cierre"));
		Long pago = cobrarEfectivo(familias.familia(), java.util.List.of(cuotaDe(familias.hermano1(), 6)), "450.00");
		Long caja = jdbc.queryForObject("SELECT caja_diaria_id FROM pago WHERE id = ?", Long.class, pago);

		assertThat(codigoAl(() -> jdbc.update("UPDATE caja_diaria SET estado = 'CERRADA', cierres = 1 WHERE id = ?", caja)))
				.isEqualTo(1644);
		// Dos conteos de golpe, tampoco.
		assertThat(codigoAl(() -> jdbc.update("UPDATE caja_diaria SET conteos = 2, primer_conteo = 10 WHERE id = ?", caja)))
				.isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT estado FROM caja_diaria WHERE id = ?", String.class, caja))
				.isEqualTo("ABIERTA");
	}

	@Test
	void comprobanteQueSaltaUnNumeroFallaCon1644() {
		FamiliasCaja familias = familiasDeCaja();
		UsuariosDePrueba.iniciarSesion(cajera("caja.numero"));
		Long pago = cobrarEfectivo(familias.familia(), java.util.List.of(cuotaDe(familias.hermano1(), 7)), "450.00");
		Long comprobante = jdbc.queryForObject("SELECT comprobante_id FROM pago WHERE id = ?", Long.class, pago);
		Long serie = jdbc.queryForObject("SELECT serie_id FROM comprobante WHERE id = ?", Long.class, comprobante);

		String copia = "INSERT INTO comprobante (colegio_id, serie_id, tipo, serie, numero, fecha_emision, "
				+ "receptor_tipo_documento, receptor_numero_documento, receptor_nombre, moneda, total, afectacion_igv, proveedor, "
				+ "estado_envio, creado_en, creado_por, actualizado_en) SELECT colegio_id, serie_id, tipo, serie, numero + %d, "
				+ "fecha_emision, receptor_tipo_documento, receptor_numero_documento, receptor_nombre, moneda, total, "
				+ "afectacion_igv, proveedor, 'PENDIENTE', NOW(6), creado_por, NOW(6) FROM comprobante WHERE id = ?";
		// Saltar un número (deja un hueco) o reusar uno: el trigger lo rechaza.
		assertThat(codigoAl(() -> jdbc.update(String.format(copia, 2), comprobante))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(String.format(copia, -1), comprobante))).isEqualTo(1644);
		// La serie solo avanza de uno en uno.
		assertThat(codigoAl(() -> jdbc.update("UPDATE serie_comprobante SET ultimo_numero = ultimo_numero + 2 WHERE id = ?",
				serie))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE serie_comprobante SET ultimo_numero = ultimo_numero - 1 WHERE id = ?",
				serie))).isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante WHERE serie_id = ?", Long.class, serie))
				.isEqualTo(jdbc.queryForObject("SELECT ultimo_numero FROM serie_comprobante WHERE id = ?", Long.class, serie));
	}

	/** Dos cajeras cobran la misma cuota a la vez en MySQL real (SELECT ... FOR UPDATE): solo una lo logra. */
	@Test
	void dobleCobroConcurrenteSoloUnoLoLograEnMySql() throws Exception {
		FamiliasCaja familias = familiasDeCaja();
		Long cuota = cuotaDe(familias.hermano2(), 8);
		java.util.concurrent.CountDownLatch largada = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.ExecutorService hilos = java.util.concurrent.Executors.newFixedThreadPool(2);
		java.util.List<Object> resultados = new java.util.ArrayList<>();
		try {
			java.util.List<java.util.concurrent.Future<Long>> futuros = new java.util.ArrayList<>();
			for (String nombre : new String[] { "caja.a", "caja.b" }) {
				var cajera = cajera(nombre);
				futuros.add(hilos.submit(() -> {
					largada.await();
					UsuariosDePrueba.iniciarSesion(cajera);
					try {
						return cobrarEfectivo(familias.familia(), java.util.List.of(cuota), "450.00");
					}
					finally {
						SecurityContextHolder.clearContext();
					}
				}));
			}
			largada.countDown();
			for (var futuro : futuros) {
				try {
					resultados.add(futuro.get(60, java.util.concurrent.TimeUnit.SECONDS));
				}
				catch (java.util.concurrent.ExecutionException e) {
					resultados.add(e.getCause());
				}
			}
		}
		finally {
			hilos.shutdownNow();
		}
		assertThat(resultados).filteredOn(Long.class::isInstance).hasSize(1);
		assertThat(resultados).filteredOn(pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException.class::isInstance)
				.hasSize(1);
		assertThat(jdbc.queryForMap("SELECT estado, monto_pagado FROM cuota WHERE id = ?", cuota))
				.containsEntry("estado", "PAGADA");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aplicacion_pago WHERE cuota_id = ?", Long.class, cuota))
				.isEqualTo(1);
	}

	/**
	 * Tanda 1 con los permisos mínimos de cc_app y los triggers: cobro en efectivo de dos hermanos (bloqueos FOR UPDATE
	 * de caja, cuotas y serie; UPDATE por columna de cuota y serie), doble clic, Yape, factura y envío al OSE después del
	 * commit. Es la prueba que detecta un saveAndFlush faltante (en H2 no hay triggers).
	 */
	@Test
	void flujoCompletoDeCajaConPermisosMinimos() throws Exception {
		FamiliasCaja familias = familiasDeCaja();
		UsuariosDePrueba.iniciarSesion(cajera("caja.flujo"));
		var revision = cobro.revisar(familias.familia(), new pe.edu.virgenmaria.cuentasclaras.caja.dto.SeleccionCobroRequest(
				java.util.List.of(cuotaDe(familias.hermano1(), 9), cuotaDe(familias.hermano2(), 9)),
				pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.EFECTIVO), null);
		var solicitud = new pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest(revision.clave(), familias.familia(),
				revision.cuotaIds(), pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.EFECTIVO, null,
				new java.math.BigDecimal("1000"), null, revision.total(),
				pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante.BOLETA, revision.receptorPorDefecto(),
				null, null);
		Long pago = cobro.cobrar(solicitud);
		assertThat(cobro.cobrar(solicitud)).isEqualTo(pago);
		assertThat(jdbc.queryForMap("SELECT total, vuelto, estado FROM pago WHERE id = ?", pago))
				.containsEntry("estado", "VIGENTE")
				.satisfies(p -> assertThat((java.math.BigDecimal) p.get("vuelto")).isEqualByComparingTo("100.00"));
		assertThat(jdbc.queryForList("SELECT estado FROM cuota WHERE id IN (?, ?)", String.class,
				revision.cuotaIds().get(0), revision.cuotaIds().get(1))).containsOnly("PAGADA");

		// Yape y factura (serie F001 nueva) en la misma caja.
		cobro.cobrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital(familias.familia(),
				java.util.List.of(cuotaDe(familias.hermano1(), 10)), pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.YAPE,
				"Y" + sufijo, "450.00"));
		Long factura = cobro.cobrar(new pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest(java.util.UUID.randomUUID(),
				familias.otraFamilia(), java.util.List.of(cuotaDe(familias.otroAlumno(), 9)),
				pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.TRANSFERENCIA, "T" + sufijo, null, null,
				new java.math.BigDecimal("450.00"), pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante.FACTURA,
				null, "20131312955", "Comercial Flores S.A.C."));
		assertThat(cobro.confirmacion(factura).comprobante()).startsWith("F001-");

		// El envío al OSE (simulado) termina después del commit, en otro hilo.
		Long comprobante = jdbc.queryForObject("SELECT comprobante_id FROM pago WHERE id = ?", Long.class, pago);
		long limite = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
		while (!"ACEPTADO".equals(jdbc.queryForObject("SELECT estado_envio FROM comprobante WHERE id = ?", String.class,
				comprobante)) && System.nanoTime() < limite) {
			Thread.sleep(100);
		}
		assertThat(jdbc.queryForObject("SELECT estado_envio FROM comprobante WHERE id = ?", String.class, comprobante))
				.isEqualTo("ACEPTADO");
		// Ninguna serie tiene huecos.
		assertThat(jdbc.queryForList("SELECT s.serie FROM serie_comprobante s WHERE s.colegio_id = 1 AND s.ultimo_numero <> "
				+ "(SELECT COUNT(*) FROM comprobante c WHERE c.serie_id = s.id)", String.class)).isEmpty();
		UsuariosDePrueba.iniciarSesion(guardar("verif.caja." + sufijo, Rol.PROMOTOR));
		assertThat(verificador.verificar().integra()).isTrue();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sprint 3, tanda 2: anulaciones de pago y descuentos (V10 y la versión final de 03-triggers.sql).
	// Cuotas libres del escenario compartido (la tanda 1 usa hermano1: 3-7, 9, 10; hermano2: 8, 9; otro: 9).
	// ---------------------------------------------------------------------------------------------------------------

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos anulacionesPago;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos descuentos;

	private static final String MOTIVO = "Se cobró a la familia equivocada en ventanilla";

	@Test
	void anulacionesYDescuentosNoSeBorranNiCambianLoPedido() {
		for (String tabla : new String[] { "anulacion_pago", "descuento", "ajuste_cuota" }) {
			assertThat(codigoAl(() -> jdbc.update("DELETE FROM " + tabla + " WHERE 1 = 0"))).as(tabla).isEqualTo(1142);
		}
		for (String tabla : new String[] { "anulacion_pago", "ajuste_cuota" }) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE " + tabla + " SET version = version WHERE 1 = 0"))).as(tabla)
					.isEqualTo(1142);
		}
		for (String columna : new String[] { "valor", "cuotas", "total_estimado", "alumno_id", "tipo", "modalidad",
				"motivo", "sustento", "creado_por" }) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE descuento SET " + columna + " = " + columna + " WHERE 1 = 0")))
					.as(columna).isEqualTo(1143);
		}
		assertThatCode(() -> jdbc.update("UPDATE descuento SET estado = estado, resuelto_por = resuelto_por, "
				+ "resuelto_en = resuelto_en WHERE 1 = 0")).doesNotThrowAnyException();
	}

	@Test
	void triggersDeAnulacionesYDescuentosFallanCon1644() {
		FamiliasCaja familias = familiasDeCaja();
		var cajera = cajera("caja.t2");
		UsuariosDePrueba.iniciarSesion(cajera);
		Long pago = cobrarEfectivo(familias.familia(), java.util.List.of(cuotaDe(familias.hermano2(), 4)), "450.00");
		Long boleta = jdbc.queryForObject("SELECT comprobante_id FROM pago WHERE id = ?", Long.class, pago);

		// Una anulación que apunta a la boleta (no a una nota de crédito que la anule): rechazada.
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO anulacion_pago (colegio_id, pago_id, solicitud_id, "
				+ "nota_credito_id, tipo, motivo, monto, cajero_pago, solicitado_por, aprobado_por, posterior_al_cierre, "
				+ "creado_en, creado_por, actualizado_en) VALUES (1, ?, 0, ?, 'DEVOLUCION', 'motivo de prueba', 450, ?, "
				+ "'a', 'b', FALSE, NOW(6), 'b', NOW(6))", pago, boleta, cajera.getUsername()))).isEqualTo(1644);
		// Un descuento que nace aprobado y un ajuste sin descuento aprobado: rechazados.
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO descuento (colegio_id, alumno_id, anio_escolar_id, tipo, "
				+ "modalidad, valor, cuotas, total_estimado, motivo, sustento, estado, resuelto_por, resuelto_en, creado_en, "
				+ "creado_por, actualizado_en) SELECT colegio_id, alumno_id, anio_escolar_id, 'BECA', 'PORCENTAJE', 100, "
				+ "CONCAT(',', id, ','), monto, 'beca que nace aprobada', 'x', 'APROBADO', 'b', NOW(6), NOW(6), 'a', NOW(6) "
				+ "FROM cuota WHERE id = ?", cuotaDe(familias.hermano2(), 5)))).isEqualTo(1644);

		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long cuota = cuotaDe(familias.hermano2(), 5);
		Long descuento = descuentos.solicitar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.descuento(
				familias.hermano2(), pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento.HERMANOS, "10",
				java.util.List.of(cuota)));
		String ajuste = "INSERT INTO ajuste_cuota (colegio_id, cuota_id, descuento_id, monto, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, ?, ?, 45, NOW(6), 'x', NOW(6))";
		assertThat(codigoAl(() -> jdbc.update(ajuste, cuota, descuento))).as("descuento sin aprobar").isEqualTo(1644);
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja,
				jdbc, "descuento", descuento);
		assertThat(jdbc.queryForObject("SELECT monto_descuento FROM cuota WHERE id = ?", java.math.BigDecimal.class, cuota))
				.isEqualByComparingTo("45.00");
		// Aprobado: no cambia de estado, no se le agregan ajustes a otra cuota y la cuota no cambia su descuento.
		assertThat(codigoAl(() -> jdbc.update("UPDATE descuento SET estado = 'RECHAZADO' WHERE id = ?", descuento)))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(ajuste, cuotaDe(familias.hermano2(), 6), descuento))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE cuota SET monto_descuento = 0 WHERE id = ?", cuota)))
				.isEqualTo(1644);

		// Un pago anulado de verdad no vuelve a estar vigente.
		UsuariosDePrueba.iniciarSesion(cajera);
		anulacionesPago.solicitarDevolucion(pago, MOTIVO);
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja,
				jdbc, "pago", pago);
		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("ANULADO");
		assertThat(codigoAl(() -> jdbc.update("UPDATE pago SET estado = 'VIGENTE' WHERE id = ?", pago))).isEqualTo(1644);
		// Ni se anula dos veces.
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO anulacion_pago (colegio_id, pago_id, solicitud_id, "
				+ "nota_credito_id, tipo, motivo, monto, cajero_pago, solicitado_por, aprobado_por, posterior_al_cierre, "
				+ "creado_en, creado_por, actualizado_en) SELECT colegio_id, pago_id, solicitud_id, nota_credito_id, tipo, "
				+ "motivo, monto, cajero_pago, solicitado_por, aprobado_por, posterior_al_cierre, NOW(6), creado_por, NOW(6) "
				+ "FROM anulacion_pago WHERE pago_id = ?", pago))).isEqualTo(1644);
	}

	/**
	 * Tanda 2 con los permisos mínimos y los triggers finales: corrección con nota de crédito BC01 y boleta nueva,
	 * devolución de un Yape (libera el número de operación), descuento por hermanos aprobado, cobro con descuento y beca
	 * del 100 % (cuota EXONERADA). Detecta un saveAndFlush faltante o un orden de inserción que los triggers rechazan.
	 */
	@Test
	void flujoCompletoDeAnulacionesYDescuentosConPermisosMinimos() throws Exception {
		FamiliasCaja familias = familiasDeCaja();
		var cajera = cajera("caja.t2f");
		UsuariosDePrueba.iniciarSesion(cajera);
		Long pago = cobro.cobrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo(familias.familia(),
				java.util.List.of(cuotaDe(familias.hermano1(), 8)), "450.00", "500.00"));
		Long destino = cuotaDe(familias.hermano1(), 11);
		anulacionesPago.solicitarCorreccion(pago, pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones
				.correccion(familias.familia(), java.util.List.of(destino)));
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja,
				jdbc, "pago", pago);

		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("ANULADO");
		assertThat(jdbc.queryForObject("SELECT estado FROM cuota WHERE id = ?", String.class, cuotaDe(familias.hermano1(), 8)))
				.isEqualTo("PENDIENTE");
		assertThat(jdbc.queryForObject("SELECT estado FROM cuota WHERE id = ?", String.class, destino)).isEqualTo("PAGADA");
		java.util.Map<String, Object> anulacion = jdbc.queryForMap("SELECT * FROM anulacion_pago WHERE pago_id = ?", pago);
		assertThat(anulacion).containsEntry("tipo", "CORRECCION").containsEntry("aprobado_por", "director");
		Long nota = ((Number) anulacion.get("nota_credito_id")).longValue();
		assertThat(jdbc.queryForObject("SELECT serie FROM comprobante WHERE id = ?", String.class, nota)).isEqualTo("BC01");
		java.util.Map<String, Object> reemplazo = jdbc.queryForMap("SELECT p.*, c.serie FROM pago p JOIN comprobante c "
				+ "ON c.id = p.comprobante_id WHERE p.reemplaza_pago_id = ?", pago);
		assertThat(reemplazo).containsEntry("serie", "B001").containsEntry("estado", "VIGENTE")
				.containsEntry("origen", "REEMPLAZO");

		// Devolución de un Yape: libera el número de operación.
		UsuariosDePrueba.iniciarSesion(cajera);
		Long yape = cobro.cobrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital(familias.otraFamilia(),
				java.util.List.of(cuotaDe(familias.otroAlumno(), 4)), pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.YAPE,
				"Y2" + sufijo, "450.00"));
		anulacionesPago.solicitarDevolucion(yape, MOTIVO);
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja,
				jdbc, "pago", yape);
		assertThat(jdbc.queryForMap("SELECT estado, operacion_vigente FROM pago WHERE id = ?", yape))
				.containsEntry("estado", "ANULADO").containsEntry("operacion_vigente", null);

		// Descuento por hermanos (10 %) y beca del 100 %.
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long junio = cuotaDe(familias.hermano2(), 6);
		Long julio = cuotaDe(familias.hermano2(), 7);
		Long octubre = cuotaDe(familias.hermano2(), 10);
		Long hermanos = descuentos.solicitar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.descuento(
				familias.hermano2(), pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento.HERMANOS, "10",
				java.util.List.of(junio, julio)));
		Long beca = descuentos.solicitar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.descuento(
				familias.hermano2(), pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento.BECA, "100",
				java.util.List.of(octubre)));
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja,
				jdbc, "descuento", hermanos);
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja,
				jdbc, "descuento", beca);
		assertThat(jdbc.queryForObject("SELECT estado FROM cuota WHERE id = ?", String.class, octubre))
				.isEqualTo("EXONERADA");
		assertThat(jdbc.queryForObject("SELECT SUM(monto) FROM ajuste_cuota WHERE descuento_id = ?",
				java.math.BigDecimal.class, hermanos)).isEqualByComparingTo("90.00");
		// La caja ya cobra el monto con descuento.
		UsuariosDePrueba.iniciarSesion(cajera);
		cobrarEfectivo(familias.familia(), java.util.List.of(junio), "405.00");
		assertThat(jdbc.queryForMap("SELECT estado, monto_pagado FROM cuota WHERE id = ?", junio))
				.containsEntry("estado", "PAGADA");

		// Las notas de crédito también se envían al OSE después del commit y ninguna serie tiene huecos.
		long limite = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
		while (!"ACEPTADO".equals(jdbc.queryForObject("SELECT estado_envio FROM comprobante WHERE id = ?", String.class,
				nota)) && System.nanoTime() < limite) {
			Thread.sleep(100);
		}
		assertThat(jdbc.queryForObject("SELECT estado_envio FROM comprobante WHERE id = ?", String.class, nota))
				.isEqualTo("ACEPTADO");
		assertThat(jdbc.queryForList("SELECT s.serie FROM serie_comprobante s WHERE s.colegio_id = 1 AND s.ultimo_numero <> "
				+ "(SELECT COUNT(*) FROM comprobante c WHERE c.serie_id = s.id)", String.class)).isEmpty();
		UsuariosDePrueba.iniciarSesion(guardar("verif.t2." + sufijo, Rol.PROMOTOR));
		assertThat(verificador.verificar().integra()).isTrue();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sprint 3, tanda 3: cierre ciego, depósito y verificación bancaria (V11 y el 03-triggers.sql final del sprint).
	// Cuotas libres del escenario compartido: otroAlumno 5-8 y 10-12; hermano2 11 y 12; hermano1 12.
	// ---------------------------------------------------------------------------------------------------------------

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCierreCaja cierres;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioVerificacionBancaria verificacionBancaria;

	private static final String EXPLICACION = "Volví a contar y faltan cincuenta soles";

	private static pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest conteo(String monto) {
		return new pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest(new java.math.BigDecimal(monto), null);
	}

	private static pe.edu.virgenmaria.cuentasclaras.caja.dto.ReconteoRequest reconteo(String monto) {
		return new pe.edu.virgenmaria.cuentasclaras.caja.dto.ReconteoRequest(new java.math.BigDecimal(monto), null,
				EXPLICACION);
	}

	@Test
	void cierreCajaDepositoYVerificacionNoSeBorranNiCambianElConteo() {
		for (String columna : new String[] { "contado", "primer_conteo", "esperado", "efectivo_cobrado", "diferencia",
				"explicacion", "caja_diaria_id", "numero" }) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE cierre_caja SET " + columna + " = " + columna + " WHERE 1 = 0")))
					.as(columna).isEqualTo(1143);
		}
		assertThatCode(() -> jdbc.update("UPDATE cierre_caja SET estado = estado, revisado_por = revisado_por, "
				+ "revisado_en = revisado_en, comentario_revision = comentario_revision WHERE 1 = 0"))
				.doesNotThrowAnyException();
	}

	@Test
	void triggersDelCierreFallanCon1644() {
		FamiliasCaja familias = familiasDeCaja();
		var cajera = cajera("caja.t3");
		UsuariosDePrueba.iniciarSesion(cajera);
		Long pago = cobrarEfectivo(familias.otraFamilia(), java.util.List.of(cuotaDe(familias.otroAlumno(), 5)), "450.00");
		Long caja = jdbc.queryForObject("SELECT caja_diaria_id FROM pago WHERE id = ?", Long.class, pago);
		// Primer conteo que no coincide: queda guardado y la caja sigue abierta.
		cierres.contar(conteo("400.00"));
		assertThat(jdbc.queryForMap("SELECT estado, conteos, primer_conteo FROM caja_diaria WHERE id = ?", caja))
				.containsEntry("estado", "ABIERTA").containsEntry("conteos", 1);

		// No se reescribe el primer conteo ni se reinician los intentos (no se tantea el esperado).
		assertThat(codigoAl(() -> jdbc.update("UPDATE caja_diaria SET primer_conteo = 450 WHERE id = ?", caja)))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE caja_diaria SET conteos = 0 WHERE id = ?", caja))).isEqualTo(1644);
		// Un cierre con el esperado «acomodado» (sin el pago) o registrado por otra persona: rechazado.
		String cierre = "INSERT INTO cierre_caja (colegio_id, caja_diaria_id, numero, fondo_fijo, efectivo_cobrado, "
				+ "esperado, primer_conteo, contado, diferencia, explicacion, pagos_efectivo, pagos_digitales, total_digital, "
				+ "estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, 1, 0, ?, ?, 400, 400, ?, 'acomodado a mano', "
				+ "0, 0, 0, 'POR_REVISAR', NOW(6), ?, NOW(6))";
		assertThat(codigoAl(() -> jdbc.update(cierre, caja, 400, 400, 0, cajera.getUsername()))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(cierre, caja, 450, 450, -50, "otra.persona"))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE caja_diaria SET estado = 'CERRADA', cierres = cierres + 1 "
				+ "WHERE id = ?", caja))).isEqualTo(1644);

		// Cerrada de verdad (reconteo con explicación): no recibe efectivo ni se reabre sin una reapertura aprobada.
		cierres.recontar(reconteo("400.00"));
		assertThat(jdbc.queryForObject("SELECT estado FROM caja_diaria WHERE id = ?", String.class, caja))
				.isEqualTo("CERRADA");
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO pago (colegio_id, familia_id, caja_diaria_id, cajero, fecha, "
				+ "comprobante_id, medio, total, recibido, vuelto, origen, clave_idempotencia, estado, creado_en, creado_por, "
				+ "actualizado_en) SELECT colegio_id, familia_id, caja_diaria_id, cajero, fecha, comprobante_id, medio, "
				+ "total, recibido, vuelto, origen, ?, estado, NOW(6), creado_por, NOW(6) FROM pago WHERE id = ?",
				"cerrada-" + sufijo, pago))).isEqualTo(1644);
		assertThatThrownBy(() -> cobrarEfectivo(familias.otraFamilia(), java.util.List.of(cuotaDe(familias.otroAlumno(),
				6)), "450.00")).isInstanceOf(pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException.class)
				.hasMessageContaining("ya se cerró");
		assertThat(codigoAl(() -> jdbc.update("UPDATE caja_diaria SET estado = 'ABIERTA', conteos = 0, "
				+ "primer_conteo = NULL WHERE id = ?", caja))).isEqualTo(1644);
		// El cierre revisado no cambia.
		Long cierreId = jdbc.queryForObject("SELECT id FROM cierre_caja WHERE caja_diaria_id = ?", Long.class, caja);
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.DIRECCION);
		bandeja.aprobar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.pendiente(jdbc, "cierre_caja",
				cierreId), "La cajera repuso los cincuenta soles");
		assertThat(codigoAl(() -> jdbc.update("UPDATE cierre_caja SET estado = 'OBSERVADO', comentario_revision = "
				+ "'cambio de opinión posterior' WHERE id = ?", cierreId))).isEqualTo(1644);
		// Quien cobró no verifica su propio Yape (ni un pago en efectivo como si fuera digital).
		UsuariosDePrueba.iniciarSesion(cajera);
		Long yape = cobro.cobrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital(familias.otraFamilia(),
				java.util.List.of(cuotaDe(familias.otroAlumno(), 7)), pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.YAPE,
				"Y3" + sufijo, "450.00"));
		String verificar = "INSERT INTO verificacion_bancaria (colegio_id, pago_id, resultado, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, ?, 'ENCONTRADO', NOW(6), ?, NOW(6))";
		assertThat(codigoAl(() -> jdbc.update(verificar, yape, cajera.getUsername()))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(verificar, pago, "administracion"))).isEqualTo(1644);
	}

	/**
	 * Tanda 3 con los permisos mínimos de cc_app y los triggers finales: cobro en efectivo y Yape, primer conteo que no
	 * coincide, reconteo y cierre con faltante, aprobación con comentario, depósito, verificación del Yape y del depósito
	 * por Administración; y en otra caja, cierre cuadrado, reapertura aprobada el mismo día y segundo cierre. Detecta un
	 * saveAndFlush faltante (en H2 no hay triggers).
	 */
	@Test
	void flujoCompletoDeCierreConPermisosMinimos() {
		FamiliasCaja familias = familiasDeCaja();
		var cajera = cajera("caja.t3f");
		UsuariosDePrueba.iniciarSesion(cajera);
		cobrarEfectivo(familias.otraFamilia(), java.util.List.of(cuotaDe(familias.otroAlumno(), 8)), "450.00");
		Long yape = cobro.cobrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital(familias.otraFamilia(),
				java.util.List.of(cuotaDe(familias.otroAlumno(), 10)), pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.YAPE,
				"Y4" + sufijo, "450.00"));
		assertThat(cierres.contar(conteo("400.00"))).isEqualTo(pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoConteo.RECONTAR);
		cierres.recontar(reconteo("400.00"));
		Long caja = jdbc.queryForObject("SELECT caja_diaria_id FROM pago WHERE id = ?", Long.class, yape);
		java.util.Map<String, Object> cierre = jdbc.queryForMap("SELECT * FROM cierre_caja WHERE caja_diaria_id = ?", caja);
		assertThat(cierre).containsEntry("estado", "POR_REVISAR").containsEntry("pagos_digitales", 1);
		assertThat((java.math.BigDecimal) cierre.get("diferencia")).isEqualByComparingTo("-50.00");
		assertThat((java.math.BigDecimal) cierre.get("total_digital")).isEqualByComparingTo("450.00");

		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.DIRECCION);
		bandeja.aprobar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.pendiente(jdbc, "cierre_caja",
				((Number) cierre.get("id")).longValue()), "Conversado con la cajera: repone mañana");
		assertThat(jdbc.queryForObject("SELECT estado FROM cierre_caja WHERE caja_diaria_id = ?", String.class, caja))
				.isEqualTo("APROBADO");

		UsuariosDePrueba.iniciarSesion(cajera);
		var estado = cierres.estado();
		cierres.registrarDeposito(new pe.edu.virgenmaria.cuentasclaras.caja.dto.DepositoRequest(caja,
				estado.cuentas().getFirst(), "D" + sufijo, estado.hoy(), new java.math.BigDecimal("400.00"), null));
		Long deposito = jdbc.queryForObject("SELECT id FROM deposito_caja WHERE caja_diaria_id = ?", Long.class, caja);

		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.ADMINISTRACION);
		var verificado = new pe.edu.virgenmaria.cuentasclaras.caja.dto.VerificacionRequest(
				pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoVerificacion.ENCONTRADO, null);
		verificacionBancaria.verificarPago(yape, verificado);
		verificacionBancaria.verificarDeposito(deposito, verificado);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verificacion_bancaria WHERE pago_id = ? OR deposito_id = ?",
				Long.class, yape, deposito)).isEqualTo(2);

		// Otra caja: cierra cuadrada, pide reabrir, se aprueba el mismo día y cierra de nuevo (cierre N.° 2).
		var otra = cajera("caja.t3r");
		UsuariosDePrueba.iniciarSesion(otra);
		cobrarEfectivo(familias.familia(), java.util.List.of(cuotaDe(familias.hermano2(), 11)), "450.00");
		cierres.contar(conteo("450.00"));
		cierres.solicitarReapertura("Llegó una familia a pagar en efectivo tarde");
		Long cajaOtra = jdbc.queryForObject("SELECT id FROM caja_diaria WHERE cajero = ?", Long.class, otra.getUsername());
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		bandeja.aprobar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.pendiente(jdbc, "caja_diaria",
				cajaOtra), null);
		assertThat(jdbc.queryForMap("SELECT estado, cierres, conteos FROM caja_diaria WHERE id = ?", cajaOtra))
				.containsEntry("estado", "ABIERTA").containsEntry("cierres", 1).containsEntry("conteos", 0);
		UsuariosDePrueba.iniciarSesion(otra);
		cobrarEfectivo(familias.familia(), java.util.List.of(cuotaDe(familias.hermano2(), 12)), "450.00");
		cierres.contar(conteo("900.00"));
		assertThat(jdbc.queryForList("SELECT CONCAT(numero, ' ', diferencia) FROM cierre_caja WHERE caja_diaria_id = ? "
				+ "ORDER BY numero", String.class, cajaOtra)).containsExactly("1 0.00", "2 0.00");
		UsuariosDePrueba.iniciarSesion(guardar("verif.t3." + sufijo, Rol.PROMOTOR));
		assertThat(verificador.verificar().integra()).isTrue();
	}

	private Integer codigoAl(Runnable sentencia) {
		try {
			sentencia.run();
			return null;
		}
		catch (DataAccessException e) {
			return codigoMySql(e);
		}
	}

	private int anioLibre() {
		java.util.Set<Integer> usados = new java.util.HashSet<>(
				jdbc.queryForList("SELECT anio FROM anio_escolar WHERE colegio_id = 1", Integer.class));
		return java.util.stream.IntStream.iterate(2025, a -> a >= 2000, a -> a - 1).filter(a -> !usados.contains(a))
				.findFirst().orElseThrow();
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
