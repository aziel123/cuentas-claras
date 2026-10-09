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
// Sprint 5, tanda 2 (G19): el pago a cuenta deja la cuota de matrícula PARCIAL (sin activar la matrícula reservada).
@org.springframework.test.context.TestPropertySource(properties = "cuentasclaras.caja.permitir-pago-a-cuenta=true")
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

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAccesoApoderados accesoApoderados;

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
		var cajeraFlujo = cajera("caja.flujo");
		UsuariosDePrueba.iniciarSesion(cajeraFlujo);
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
		// B2: la factura solo con el RUC registrado de la familia (la solicitud aprobada la exige un trigger).
		Long apoderadoOtra = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				familias.otroAlumno());
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.rucRegistrado(servicioFamilias, bandeja, jdbc,
				apoderadoOtra, "20131312955", "Comercial Flores S.A.C.");
		UsuariosDePrueba.iniciarSesion(cajeraFlujo);
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
		// A1: un pago digital se anula solo si Administración ya lo encontró en el banco.
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.verificadoEnBanco(verificacionBancaria, jdbc, yape);
		UsuariosDePrueba.iniciarSesion(cajera);
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
		// A ciegas (C1): lo que se ve en el banco; el trigger exige que coincida.
		java.time.LocalDate fechaYape = jdbc.queryForObject("SELECT fecha FROM pago WHERE id = ?", java.time.LocalDate.class,
				yape);
		verificacionBancaria.verificarPago(yape, pe.edu.virgenmaria.cuentasclaras.caja.dto.VerificacionRequest.delBanco(
				"Y4" + sufijo, fechaYape, new java.math.BigDecimal("450.00")));
		verificacionBancaria.verificarDeposito(deposito, pe.edu.virgenmaria.cuentasclaras.caja.dto.VerificacionRequest
				.delBanco("D" + sufijo, estado.hoy(), new java.math.BigDecimal("400.00")));
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

	/**
	 * Correcciones del sprint 3: los ataques de la auditoría reproducidos COMO cc_app (con sus permisos mínimos) ahora
	 * fallan con 1644 (los triggers de 03-triggers.sql), y los flujos legítimos siguen funcionando.
	 * <ul>
	 *   <li>A3: un «reemplazo» por SQL de un pago anulado por DEVOLUCIÓN;</li>
	 *   <li>M1: reabrir una caja con una solicitud solo pendiente (o sin solicitud); anular una cuota sin solicitud
	 *       aprobada; cambiar la resolución de una solicitud, de un cierre y de un descuento; el resultado del envío de un
	 *       comprobante aceptado;</li>
	 *   <li>A1/A2/C1/B2: el reembolso registrado por la cajera; una verificación «encontrada» con datos del banco que no
	 *       coinciden; el RUC de un apoderado sin su solicitud aprobada.</li>
	 * </ul>
	 */
	@Test
	void ataquesDeLaAuditoriaComoCcAppFallanConLosTriggers() {
		FamiliasCaja familias = familiasDeCaja();
		var cajera = cajera("caja.m1");
		UsuariosDePrueba.iniciarSesion(cajera);
		Long pago = cobrarEfectivo(familias.otraFamilia(), java.util.List.of(cuotaDe(familias.otroAlumno(), 6)), "450.00");
		anulacionesPago.solicitarDevolucion(pago, "Se cobró a la familia equivocada en ventanilla");
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja,
				jdbc, "pago", pago);
		java.util.Map<String, Object> p = jdbc.queryForMap("SELECT * FROM pago WHERE id = ?", pago);
		assertThat(p).containsEntry("estado", "ANULADO");

		// A3: el reemplazo de un pago anulado por DEVOLUCIÓN (el trigger antes solo miraba caja, medio y total).
		String reemplazo = "INSERT INTO pago (colegio_id, familia_id, caja_diaria_id, cajero, fecha, comprobante_id, medio, "
				+ "total, recibido, vuelto, origen, reemplaza_pago_id, clave_idempotencia, estado, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, ?, ?, ?, ?, ?, 'EFECTIVO', 450.00, 450.00, 0.00, 'REEMPLAZO', ?, ?, 'VIGENTE', "
				+ "NOW(6), ?, NOW(6))";
		assertThat(codigoAl(() -> jdbc.update(reemplazo, p.get("familia_id"), p.get("caja_diaria_id"), p.get("cajero"),
				p.get("fecha"), p.get("comprobante_id"), pago, "a3-" + sufijo, p.get("cajero")))).isEqualTo(1644);

		// A1/A2: el reembolso no lo registra la cajera del pago.
		Long anulacion = jdbc.queryForObject("SELECT id FROM anulacion_pago WHERE pago_id = ?", Long.class, pago);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO reembolso (colegio_id, anulacion_pago_id, medio, "
				+ "recibido_por_nombre, recibido_por_documento, monto, fecha, cajero_pago, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, ?, 'EFECTIVO', 'Rosa Huamán', '45678912', 450.00, CURDATE(), ?, NOW(6), ?, "
				+ "NOW(6))", anulacion, cajera.getUsername(), cajera.getUsername()))).isEqualTo(1644);
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.ADMINISTRACION);
		verificacionBancaria.registrarReembolso(anulacion, new pe.edu.virgenmaria.cuentasclaras.caja.dto.ReembolsoRequest(
				null, false, "Pedro Flores Díaz", "41234567"));
		assertThat(jdbc.queryForObject("SELECT creado_por FROM reembolso WHERE anulacion_pago_id = ?", String.class,
				anulacion)).isEqualTo("administracion");

		// M1: cierre de esa caja (S/ 0 en efectivo tras la devolución) y su aprobación.
		UsuariosDePrueba.iniciarSesion(cajera);
		assertThat(cierres.contar(conteo("0.00"))).isEqualTo(pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoConteo.COINCIDE);
		Long caja = (Long) p.get("caja_diaria_id");
		Long cierre = jdbc.queryForObject("SELECT id FROM cierre_caja WHERE caja_diaria_id = ?", Long.class, caja);
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.DIRECCION);
		bandeja.aprobar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.pendiente(jdbc, "cierre_caja",
				cierre), null);
		assertThat(codigoAl(() -> jdbc.update("UPDATE cierre_caja SET comentario_revision = 'Cambiado por SQL' "
				+ "WHERE id = ?", cierre))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE cierre_caja SET revisado_por = 'otro' WHERE id = ?", cierre)))
				.isEqualTo(1644);

		// M1: reabrir con una solicitud solo PENDIENTE (antes bastaba) o sin solicitud.
		UsuariosDePrueba.iniciarSesion(cajera);
		cierres.solicitarReapertura("Llegó una familia a pagar en efectivo tarde");
		Long reapertura = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.pendiente(jdbc, "caja_diaria",
				caja);
		assertThat(codigoAl(() -> jdbc.update("UPDATE caja_diaria SET estado = 'ABIERTA', conteos = 0, primer_conteo = NULL "
				+ "WHERE id = ?", caja))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE caja_diaria SET estado = 'ABIERTA', conteos = 0, primer_conteo = NULL, "
				+ "reapertura_solicitud_id = ? WHERE id = ?", reapertura, caja))).isEqualTo(1644);
		// La vía legítima: la bandeja aprueba la solicitud y después la aplica (el trigger exige que esté APROBADA).
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja,
				jdbc, "caja_diaria", caja);
		assertThat(jdbc.queryForMap("SELECT estado, reapertura_solicitud_id FROM caja_diaria WHERE id = ?", caja))
				.containsEntry("estado", "ABIERTA").containsEntry("reapertura_solicitud_id", reapertura);
		assertThat(codigoAl(() -> jdbc.update("UPDATE solicitud_cambio SET comentario = 'Cambiado por SQL' WHERE id = ?",
				reapertura))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE solicitud_cambio SET resuelto_por = 'otro' WHERE id = ?",
				reapertura))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE caja_diaria SET reapertura_solicitud_id = NULL WHERE id = ?",
				caja))).isEqualTo(1644);

		// M1: anular una cuota sin su solicitud aprobada.
		Long cuota = cuotaDe(familias.otroAlumno(), 11);
		assertThat(codigoAl(() -> jdbc.update("UPDATE cuota SET estado = 'ANULADA', obligacion = NULL, anulacion_motivo = "
				+ "'Anulación por SQL sin aprobación', anulacion_solicitada_por = 'a', anulacion_aprobada_por = 'b', "
				+ "anulada_en = NOW(6) WHERE id = ?", cuota))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE cuota SET estado = 'ANULADA', obligacion = NULL, anulacion_motivo = "
				+ "'Anulación por SQL sin aprobación', anulacion_solicitada_por = 'a', anulacion_aprobada_por = 'b', "
				+ "anulada_en = NOW(6), anulacion_solicitud_id = ? WHERE id = ?", reapertura, cuota))).isEqualTo(1644);

		// M1: un descuento resuelto no cambia quién ni cuándo lo resolvió.
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.ADMINISTRACION);
		Long descuento = descuentos.solicitar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.descuento(
				familias.hermano1(), pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento.OTRO, "10",
				java.util.List.of(cuotaDe(familias.hermano1(), 12))));
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja,
				jdbc, "descuento", descuento);
		assertThat(codigoAl(() -> jdbc.update("UPDATE descuento SET resuelto_por = 'otro' WHERE id = ?", descuento)))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE descuento SET resuelto_en = NOW(6) WHERE id = ?", descuento)))
				.isEqualTo(1644);

		// M1: el resultado del envío de un comprobante aceptado no cambia.
		Long aceptado = jdbc.queryForObject("SELECT id FROM comprobante WHERE id = ?", Long.class, p.get("comprobante_id"));
		assertThat(jdbc.queryForObject("SELECT estado_envio FROM comprobante WHERE id = ?", String.class, aceptado))
				.isEqualTo("ACEPTADO");
		assertThat(codigoAl(() -> jdbc.update("UPDATE comprobante SET codigo_hash = 'manipulado' WHERE id = ?", aceptado)))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE comprobante SET estado_envio = 'RECHAZADO' WHERE id = ?", aceptado)))
				.isEqualTo(1644);

		// C1: una verificación «encontrada» con un monto que no es el del pago.
		UsuariosDePrueba.iniciarSesion(cajera);
		Long yape = cobro.cobrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital(familias.otraFamilia(),
				java.util.List.of(cuotaDe(familias.otroAlumno(), 12)), pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.YAPE,
				"YC1" + sufijo, "450.00"));
		java.util.Map<String, Object> y = jdbc.queryForMap("SELECT numero_operacion, fecha FROM pago WHERE id = ?", yape);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO verificacion_bancaria (colegio_id, pago_id, resultado, "
				+ "banco_operacion, banco_fecha, banco_monto, creado_en, creado_por, actualizado_en) VALUES (1, ?, "
				+ "'ENCONTRADO', ?, ?, 45.00, NOW(6), 'administracion', NOW(6))", yape, y.get("numero_operacion"),
				y.get("fecha")))).isEqualTo(1644);

		// B2: el RUC de un apoderado sin su solicitud aprobada.
		Long apoderado = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				familias.hermano1());
		assertThat(codigoAl(() -> jdbc.update("UPDATE apoderado SET ruc = '20131312955', razon_social = 'Empresa Ajena SAC', "
				+ "facturacion_solicitud_id = ? WHERE id = ?", reapertura, apoderado))).isEqualTo(1644);
		SecurityContextHolder.clearContext();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sprint 4, tanda 1: pagos en línea (V13) y outbox del OSE. Usa las MATRÍCULAS del escenario compartido (las
	// pensiones ya las usan las tandas anteriores).
	// ---------------------------------------------------------------------------------------------------------------

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea pagoEnLinea;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.SimuladorPagos simuladorPagos;

	@Test
	void tablasDePagosEnLineaNoSeBorranNiCambianLoPedido() {
		for (String tabla : new String[] { "orden_pago", "orden_pago_cuota", "evento_pasarela", "configuracion_bd" }) {
			assertThat(codigoAl(() -> jdbc.update("DELETE FROM " + tabla + " WHERE 1 = 0"))).as(tabla).isEqualTo(1142);
		}
		assertThat(codigoAl(() -> jdbc.update("UPDATE orden_pago_cuota SET version = version WHERE 1 = 0"))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO configuracion_bd VALUES ('pasarela_simulada', 'PERMITIDA', "
				+ "NOW(6))"))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("UPDATE configuracion_bd SET valor = valor WHERE 1 = 0"))).isEqualTo(1142);
		for (String sentencia : new String[] { "UPDATE orden_pago SET monto = monto WHERE 1 = 0",
				"UPDATE orden_pago SET familia_id = familia_id WHERE 1 = 0",
				"UPDATE orden_pago SET referencia = referencia WHERE 1 = 0",
				"UPDATE evento_pasarela SET orden_pago_id = orden_pago_id WHERE 1 = 0",
				"UPDATE caja_diaria SET canal = canal WHERE 1 = 0", "UPDATE pago SET orden_pago_id = orden_pago_id WHERE 1 = 0",
				"UPDATE comprobante SET reemplaza_id = reemplaza_id WHERE 1 = 0" }) {
			assertThat(codigoAl(() -> jdbc.update(sentencia))).as(sentencia).isEqualTo(1143);
		}
		// Triggers: una orden que nace pagada, una cuota de una orden que no existe y (sin la fila del DBA) una orden
		// de la pasarela simulada.
		for (String sentencia : new String[] {
				"INSERT INTO orden_pago (colegio_id, referencia, familia_id, apoderado_id, proveedor, monto, moneda, "
						+ "comprobante_tipo, clave_idempotencia, vence_en, estado, creado_en, creado_por, actualizado_en) VALUES "
						+ "(0, 'verificador', 0, 0, 'CULQI', 1, 'PEN', 'BOLETA', 'verificador', NOW(6), 'PAGADA', NOW(6), "
						+ "'verificador', NOW(6))",
				"INSERT INTO orden_pago (colegio_id, referencia, familia_id, apoderado_id, proveedor, monto, moneda, "
						+ "comprobante_tipo, clave_idempotencia, vence_en, estado, creado_en, creado_por, actualizado_en) VALUES "
						+ "(0, 'verificador', 0, 0, 'SIMULADA', 1, 'PEN', 'BOLETA', 'verificador', NOW(6), 'CREADA', NOW(6), "
						+ "'verificador', NOW(6))",
				"INSERT INTO orden_pago_cuota (colegio_id, orden_pago_id, cuota_id, monto, creado_en, creado_por, "
						+ "actualizado_en) VALUES (0, 0, 0, 1, NOW(6), 'verificador', NOW(6))" }) {
			assertThat(codigoAl(() -> jdbc.update(sentencia))).as(sentencia).isEqualTo(1644);
		}
	}

	/**
	 * Orden → cuotas → enlace → confirmación → pago → aplicaciones → PAGADA → boleta ACEPTADA, con los permisos mínimos y
	 * los triggers (H2 no los tiene: aquí se ve un saveAndFlush faltante). La fila 'pasarela_simulada' la pone y la quita
	 * cc_migrador (como el DBA del piloto): en prod no existe.
	 */
	@Test
	void flujoPagoEnLineaConPermisosMinimos() {
		String claveMigrador = System.getenv("CC_MYSQL_CLAVE_MIGRADOR");
		org.junit.jupiter.api.Assumptions.assumeTrue(claveMigrador != null && !claveMigrador.isBlank(),
				"Falta CC_MYSQL_CLAVE_MIGRADOR");
		JdbcTemplate migrador = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
				System.getenv().getOrDefault("CC_MYSQL_URL",
						"jdbc:mysql://127.0.0.1:3306/cuentasclaras?allowPublicKeyRetrieval=true&useSSL=false"),
				"cc_migrador", claveMigrador));
		FamiliasCaja familias = familiasDeCaja();
		Long rosa = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				familias.hermano1());
		Long matricula1 = jdbc.queryForObject("SELECT id FROM cuota WHERE alumno_id = ? AND tipo = 'MATRICULA'", Long.class,
				familias.hermano1());
		Long matricula2 = jdbc.queryForObject("SELECT id FROM cuota WHERE alumno_id = ? AND tipo = 'MATRICULA'", Long.class,
				familias.hermano2());
		migrador.update("INSERT INTO configuracion_bd (clave, valor, creado_en) VALUES ('pasarela_simulada', "
				+ "'PERMITIDA', NOW(6))");
		try {
			UsuariosDePrueba.iniciarSesion(new pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado(
					400L, 1L, "rosa.linea." + sufijo, "Rosa en línea", null, true, false, false, EnumSet.of(Rol.APODERADO),
					rosa));
			String pagada = iniciarPagoEnLinea(matricula1);
			assertThat(simuladorPagos.simular(pagada, pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada
					.Accion.YAPE)).isEqualTo(pe.edu.virgenmaria.cuentasclaras.pasarela.proceso.RecepcionAvisos.Resultado.ACEPTADO);
			java.util.Map<String, Object> orden = jdbc.queryForMap("SELECT id, estado FROM orden_pago WHERE referencia = ?",
					pagada);
			assertThat(orden).containsEntry("estado", "PAGADA");
			java.util.Map<String, Object> pago = jdbc.queryForMap("SELECT p.id, p.origen, p.cajero, p.comprobante_id, c.canal "
					+ "FROM pago p JOIN caja_diaria c ON c.id = p.caja_diaria_id WHERE p.orden_pago_id = ?", orden.get("id"));
			assertThat(pago).containsEntry("origen", "PASARELA").containsEntry("cajero", "sistema.pasarela")
					.containsEntry("canal", "PASARELA");
			assertThat(jdbc.queryForObject("SELECT estado FROM cuota WHERE id = ?", String.class, matricula1))
					.isEqualTo("PAGADA");
			assertThat(jdbc.queryForObject("SELECT estado_envio FROM comprobante WHERE id = ?", String.class,
					pago.get("comprobante_id"))).isEqualTo("ACEPTADO");
			// Una orden pagada no vuelve atrás ni cambia lo confirmado por SQL.
			assertThat(codigoAl(() -> jdbc.update("UPDATE orden_pago SET estado = 'CREADA' WHERE id = ?", orden.get("id"))))
					.isEqualTo(1644);
			assertThat(codigoAl(() -> jdbc.update("UPDATE orden_pago SET monto_confirmado = 1 WHERE id = ?",
					orden.get("id")))).isEqualTo(1644);

			// La pasarela confirma menos de lo pedido: queda por revisar, sin pago.
			String porRevisar = iniciarPagoEnLinea(matricula2);
			simuladorPagos.simular(porRevisar,
					pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada.Accion.MONTO_MENOR);
			assertThat(jdbc.queryForObject("SELECT estado FROM orden_pago WHERE referencia = ?", String.class, porRevisar))
					.isEqualTo("POR_REVISAR");
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago p JOIN orden_pago o ON o.id = p.orden_pago_id "
					+ "WHERE o.referencia = ?", Integer.class, porRevisar)).isZero();
			// QA-S4-2: con contracargo, el ingreso por revisar ya no se aplica ni se devuelve (ni por SQL).
			simuladorPagos.simular(porRevisar, pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada.Accion
					.CONTRACARGO);
			assertThat(jdbc.queryForObject("SELECT contracargo_origen FROM orden_pago WHERE referencia = ?", String.class,
					porRevisar)).isEqualTo("AVISO");
			assertThat(codigoAl(() -> jdbc.update("UPDATE orden_pago SET estado = 'DEVUELTA', devolucion_operacion = 'X1', "
					+ "devuelto_por = 'adm.cc', devuelto_en = NOW(6) WHERE referencia = ?", porRevisar))).isEqualTo(1644);
			// S4-A3: el contracargo se registra una vez, pide una anulación SIN reembolso y nadie reembolsa otra vez.
			simuladorPagos.simular(pagada, pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada.Accion
					.CONTRACARGO);
			assertThat(jdbc.queryForObject("SELECT contracargo_origen FROM orden_pago WHERE id = ?", String.class,
					orden.get("id"))).isEqualTo("AVISO");
			assertThat(codigoAl(() -> jdbc.update("UPDATE orden_pago SET contracargo_en = NULL, contracargo_origen = NULL "
					+ "WHERE id = ?", orden.get("id")))).isIn(1644, 3819);
			Long pagoId = ((Number) pago.get("id")).longValue();
			pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(persona(415, "promotora.cc",
					Rol.PROMOTOR), bandeja, jdbc, "pago", pagoId);
			java.util.Map<String, Object> anulacion = jdbc.queryForMap("SELECT id, tipo, monto, cajero_pago, solicitado_por, "
					+ "aprobado_por FROM anulacion_pago WHERE pago_id = ?", pagoId);
			assertThat(anulacion).containsEntry("tipo", "CONTRACARGO");
			assertThat(codigoAl(() -> jdbc.update("INSERT INTO reembolso (colegio_id, anulacion_pago_id, medio, "
					+ "numero_operacion, monto, fecha, cajero_pago, creado_en, creado_por, actualizado_en) VALUES (1, ?, "
					+ "'YAPE', 'TRFMIA0002', ?, CURDATE(), ?, NOW(6), 'adm.cc', NOW(6))", anulacion.get("id"),
					anulacion.get("monto"), anulacion.get("cajero_pago")))).isEqualTo(1644);
			assertThat(codigoAl(() -> jdbc.update("INSERT INTO reembolso_pasarela (colegio_id, anulacion_pago_id, pago_id, "
					+ "cargo_id, reembolso_id, monto, fecha, creado_en, creado_por, actualizado_en) SELECT 1, ?, ?, cargo_id, "
					+ "'SIMREF1', ?, CURDATE(), NOW(6), 'adm.cc', NOW(6) FROM orden_pago WHERE id = ?", anulacion.get("id"),
					pagoId, anulacion.get("monto"), orden.get("id")))).isEqualTo(1644);

		}
		finally {
			SecurityContextHolder.clearContext();
			migrador.update("DELETE FROM configuracion_bd WHERE clave = 'pasarela_simulada'");
		}
		// Sin la fila, como en producción, el verificador de arranque vuelve a pasar.
		assertThatCode(() -> new VerificadorPermisosBaseDatos(jdbc, fuenteDatos).afterPropertiesSet())
				.doesNotThrowAnyException();
	}

	// Sprint 4, tanda 2: recaudación bancaria (V14). Usa la matrícula y marzo del otro alumno y marzo del segundo hermano
	// del escenario compartido (las demás pruebas no los cobran).

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion recaudacion;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioExcepcionesRecaudacion excepciones;

	private pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado persona(long id, String nombre, Rol rol) {
		return UsuariosDePrueba.autenticado(1L, id, nombre + "." + sufijo, "Persona " + nombre, false, EnumSet.of(rol));
	}

	/** Un número de operación canónico y único en esta base (que no se limpia). */
	private String operacion(int n) {
		return "R" + sufijo.toUpperCase(java.util.Locale.ROOT) + n;
	}

	/** Registra un archivo del banco como Administración (queda CARGADO). */
	private Long loteCargado(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.Archivo archivo) {
		Long lote = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.registrar(recaudacion,
				persona(410, "adm.banco", Rol.ADMINISTRACION), "banco-" + sufijo + System.nanoTime() + ".csv",
				archivo.csv());
		SecurityContextHolder.clearContext();
		return lote;
	}

	private pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.Archivo archivoDeAyer() {
		return pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.archivo();
	}

	private String ayer() {
		return java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).minusDays(1).toString();
	}

	@Test
	void tablasDeRecaudacionNoSeBorranNiCambianLoPedido() {
		for (String tabla : new String[] { "archivo_cargado", "lote_recaudacion", "linea_recaudacion" }) {
			assertThat(codigoAl(() -> jdbc.update("DELETE FROM " + tabla + " WHERE 1 = 0"))).as(tabla).isEqualTo(1142);
		}
		// archivoNoSeEditaFallaCon1142: el archivo original del banco es de solo inserción.
		assertThat(codigoAl(() -> jdbc.update("UPDATE archivo_cargado SET version = version WHERE 1 = 0"))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("UPDATE archivo_cargado SET contenido = contenido WHERE 1 = 0")))
				.isEqualTo(1142);
		for (String sentencia : new String[] { "UPDATE lote_recaudacion SET total = total WHERE 1 = 0",
				"UPDATE lote_recaudacion SET archivo_sha256 = archivo_sha256 WHERE 1 = 0",
				"UPDATE lote_recaudacion SET desde = desde WHERE 1 = 0",
				"UPDATE linea_recaudacion SET monto = monto WHERE 1 = 0",
				"UPDATE linea_recaudacion SET codigo = codigo WHERE 1 = 0",
				"UPDATE linea_recaudacion SET numero_operacion = numero_operacion WHERE 1 = 0",
				"UPDATE linea_recaudacion SET alumno_id = alumno_id WHERE 1 = 0",
				"UPDATE pago SET linea_recaudacion_id = linea_recaudacion_id WHERE 1 = 0" }) {
			assertThat(codigoAl(() -> jdbc.update(sentencia))).as(sentencia).isEqualTo(1143);
		}
		for (String sentencia : new String[] {
				"INSERT INTO lote_recaudacion (colegio_id, archivo_id, archivo_sha256, sha_vigente, banco, formato, "
						+ "fecha_proceso, desde, hasta, lineas, total, estado, creado_en, creado_por, actualizado_en) VALUES "
						+ "(0, 0, REPEAT('0', 64), REPEAT('0', 64), 'BCP', 'verificador', '2000-01-01', '2000-01-01', "
						+ "'2000-01-01', 1, 1, 'APLICADO', NOW(6), 'verificador', NOW(6))",
				"INSERT INTO linea_recaudacion (colegio_id, lote_id, numero, fecha_pago, codigo, monto, moneda, "
						+ "numero_operacion, estado, creado_en, creado_por, actualizado_en) VALUES (0, 0, 1, '2000-01-01', "
						+ "'0', 1, 'PEN', '1234', 'PENDIENTE', NOW(6), 'verificador', NOW(6))" }) {
			assertThat(codigoAl(() -> jdbc.update(sentencia))).as(sentencia).isEqualTo(1644);
		}
	}

	/**
	 * Subir → registrar → confirmar a ciegas (otra persona) → el sistema aplica los pagos en la caja RECAUDACION con su
	 * boleta, con los permisos mínimos y los triggers (H2 no los tiene: aquí se ve un saveAndFlush faltante). Después,
	 * nada de lo resuelto se reescribe por SQL.
	 */
	@Test
	void flujoRecaudacionConPermisosMinimos() {
		FamiliasCaja familias = familiasDeCaja();
		Long matricula = jdbc.queryForObject("SELECT id FROM cuota WHERE alumno_id = ? AND tipo = 'MATRICULA'", Long.class,
				familias.otroAlumno());
		Long marzo = cuotaDe(familias.hermano2(), 3);
		var archivo = archivoDeAyer()
				.linea(ayer(), pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago.deAlumno(familias.otroAlumno()),
						pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago.deCuota(matricula), "350.00", "PEN",
						operacion(1))
				.linea(ayer(), pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago.deAlumno(familias.hermano2()),
						pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago.deCuota(marzo), "450.00", "PEN", operacion(2))
				.linea(ayer(), pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.codigoErrado(
						familias.hermano1()), "", "120.00", "PEN", operacion(3));
		Long lote = loteCargado(archivo);
		var promotora = persona(411, "promotora.banco", Rol.PROMOTOR);
		try {
			// Primero un total que no coincide (suma un intento) y luego el correcto.
			UsuariosDePrueba.iniciarSesion(promotora);
			assertThatThrownBy(() -> recaudacion.confirmar(lote, pe.edu.virgenmaria.cuentasclaras.comun.prueba
					.EscenarioRecaudacion.version(jdbc, lote), new java.math.BigDecimal("919.00")))
					.isInstanceOf(pe.edu.virgenmaria.cuentasclaras.cobranza.model.TotalNoCoincideException.class);
			recaudacion.confirmar(lote, pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.version(jdbc,
					lote), archivo.total());
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		java.util.Map<String, Object> fila = jdbc.queryForMap("SELECT estado, intentos_confirmacion, lineas_aplicadas, "
				+ "lineas_excepcion, confirmado_por FROM lote_recaudacion WHERE id = ?", lote);
		assertThat(fila).containsEntry("estado", "APLICADO").containsEntry("intentos_confirmacion", 1)
				.containsEntry("lineas_aplicadas", 2).containsEntry("lineas_excepcion", 1)
				.containsEntry("confirmado_por", promotora.getUsername());
		assertThat(jdbc.queryForObject("SELECT estado FROM cuota WHERE id = ?", String.class, matricula)).isEqualTo("PAGADA");
		assertThat(jdbc.queryForObject("SELECT estado FROM cuota WHERE id = ?", String.class, marzo)).isEqualTo("PAGADA");
		java.util.List<java.util.Map<String, Object>> pagos = jdbc.queryForList("SELECT p.origen, p.medio, p.cajero, "
				+ "c.canal, b.estado_envio FROM pago p JOIN caja_diaria c ON c.id = p.caja_diaria_id JOIN comprobante b "
				+ "ON b.id = p.comprobante_id JOIN linea_recaudacion l ON l.id = p.linea_recaudacion_id WHERE l.lote_id = ?",
				lote);
		assertThat(pagos).hasSize(2).allSatisfy(p -> assertThat(p).containsEntry("origen", "RECAUDACION")
				.containsEntry("medio", "RECAUDACION_BANCARIA").containsEntry("cajero", "sistema.recaudacion")
				.containsEntry("canal", "RECAUDACION").containsEntry("estado_envio", "ACEPTADO"));
		Long excepcion = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.idLinea(jdbc, lote, 3);
		Long aplicada = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.idLinea(jdbc, lote, 1);
		// Nada de lo resuelto se reescribe por SQL.
		assertThat(codigoAl(() -> jdbc.update("UPDATE lote_recaudacion SET estado = 'CARGADO' WHERE id = ?", lote)))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE lote_recaudacion SET total_ciego = total_ciego + 1 WHERE id = ?",
				lote))).isEqualTo(1644);
		// S4-A1: la muestra fija del lote no se reescribe.
		assertThat(codigoAl(() -> jdbc.update("UPDATE lote_recaudacion SET muestra = '1' WHERE id = ?", lote)))
				.isEqualTo(1143);
		assertThat(codigoAl(() -> jdbc.update("UPDATE linea_recaudacion SET estado = 'PENDIENTE' WHERE id = ?", aplicada)))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE linea_recaudacion SET motivo_excepcion = 'EXCESO' WHERE id = ?",
				excepcion))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE linea_recaudacion SET estado = 'APLICADA_REVISION' WHERE id = ?",
				excepcion))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE linea_recaudacion SET estado = 'DEVUELTA', devolucion_operacion = "
				+ "'X1', devuelto_por = 'x', devuelto_en = NOW(6) WHERE id = ?", excepcion))).isEqualTo(1644);
		// S4-A4: la devolución lleva su cuenta de destino y no la ejecuta quien la pidió (ni por SQL).
		var pide = persona(416, "adm.pide", Rol.ADMINISTRACION);
		UsuariosDePrueba.iniciarSesion(pide);
		try {
			excepciones.solicitarDevolucion(excepcion, new pe.edu.virgenmaria.cuentasclaras.recaudacion.dto
					.DevolucionLineaRequest("BCP", "191-7654321-0-55", "Rosa Quispe Huamán", "Código errado: se devuelve "
							+ "a quien pagó"));
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(promotora, bandeja, jdbc,
				"linea_recaudacion", excepcion);
		SecurityContextHolder.clearContext();
		assertThat(codigoAl(() -> jdbc.update("UPDATE linea_recaudacion SET estado = 'DEVUELTA', devolucion_operacion = "
				+ "'X1', devuelto_por = ?, devuelto_en = NOW(6), devolucion_banco = 'BCP', devolucion_cuenta = '1', "
				+ "devolucion_titular = 'x' WHERE id = ?", pide.getUsername(), excepcion))).isEqualTo(1644);
		UsuariosDePrueba.iniciarSesion(persona(417, "adm.ejecuta", Rol.ADMINISTRACION));
		try {
			excepciones.registrarDevolucion(excepcion, operacion(9));
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		assertThat(jdbc.queryForObject("SELECT CONCAT(estado, ' ', devolucion_cuenta) FROM linea_recaudacion WHERE id = ?",
				String.class, excepcion)).isEqualTo("DEVUELTA 191-7654321-0-55");
		assertThat(codigoAl(() -> jdbc.update("UPDATE linea_recaudacion SET devolucion_cuenta = '999' WHERE id = ?",
				excepcion))).isEqualTo(1644);
		// El mismo archivo no se vuelve a cargar mientras su lote está vigente (UNIQUE sobre su SHA-256).
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO lote_recaudacion (colegio_id, archivo_id, archivo_sha256, "
				+ "sha_vigente, banco, formato, fecha_proceso, desde, hasta, lineas, total, estado, intentos_confirmacion, "
				+ "lineas_aplicadas, lineas_excepcion, monto_aplicado, monto_excepcion, muestra, creado_en, creado_por, "
				+ "actualizado_en) SELECT colegio_id, archivo_id, archivo_sha256, sha_vigente, banco, formato, fecha_proceso, "
				+ "desde, hasta, lineas, total, 'CARGADO', 0, 0, 0, 0, 0, muestra, NOW(6), 'otra', NOW(6) FROM lote_recaudacion "
				+ "WHERE id = ?", lote))).isEqualTo(1062);
	}

	/** El pago de recaudación solo nace de un lote confirmado a ciegas: con el lote CARGADO, el trigger lo rechaza. */
	@Test
	void pagoDeRecaudacionConLoteSinConfirmarFallaCon1644() {
		FamiliasCaja familias = familiasDeCaja();
		java.util.Map<String, Object> boleta = jdbc.queryForMap("SELECT id, total FROM comprobante WHERE tipo = 'BOLETA' "
				+ "ORDER BY id LIMIT 1");
		String total = ((java.math.BigDecimal) boleta.get("total")).toPlainString();
		Long lote = loteCargado(archivoDeAyer().linea(ayer(), pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago
				.deAlumno(familias.otroAlumno()), "", total, "PEN", operacion(11)));
		Long linea = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.idLinea(jdbc, lote, 1);
		Long caja = cajaRecaudacionDeAyer();

		Integer codigo = insertarPagoRecaudacion("RECAUDACION_BANCARIA", familias.otraFamilia(), caja, boleta.get("id"),
				total, operacion(11), "clave-" + sufijo + "-11", linea);

		assertThat(codigo).isEqualTo(1644);
	}

	/**
	 * confirmaQuienSubioFallaCon3819 y reiniciarIntentosFallaCon1644: aunque cc_app escriba la confirmación por SQL, la
	 * base exige otra persona y el total a ciegas igual; y los intentos fallidos no se reinician.
	 */
	@Test
	void confirmaQuienSubioFallaCon3819YReiniciarIntentosFallaCon1644() {
		FamiliasCaja familias = familiasDeCaja();
		Long lote = loteCargado(archivoDeAyer().linea(ayer(), pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago
				.deAlumno(familias.otroAlumno()), "", "450.00", "PEN", operacion(21)));

		assertThat(codigoAl(() -> jdbc.update("UPDATE lote_recaudacion SET estado = 'CONFIRMADO', confirmado_por = "
				+ "creado_por, confirmado_en = NOW(6), total_ciego = total WHERE id = ?", lote))).isEqualTo(3819);
		assertThat(codigoAl(() -> jdbc.update("UPDATE lote_recaudacion SET estado = 'CONFIRMADO', confirmado_por = "
				+ "'otra.persona', confirmado_en = NOW(6), total_ciego = total - 1 WHERE id = ?", lote))).isEqualTo(3819);
		UsuariosDePrueba.iniciarSesion(persona(412, "director.banco", Rol.DIRECTOR));
		try {
			assertThatThrownBy(() -> recaudacion.confirmar(lote, pe.edu.virgenmaria.cuentasclaras.comun.prueba
					.EscenarioRecaudacion.version(jdbc, lote), new java.math.BigDecimal("449.00")))
					.isInstanceOf(pe.edu.virgenmaria.cuentasclaras.cobranza.model.TotalNoCoincideException.class);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		assertThat(codigoAl(() -> jdbc.update("UPDATE lote_recaudacion SET intentos_confirmacion = 0 WHERE id = ?", lote)))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE lote_recaudacion SET estado = 'APLICADO', aplicado_en = NOW(6) "
				+ "WHERE id = ?", lote))).isEqualTo(1644);
	}

	/** Las líneas entran solo a un lote CARGADO y dentro de sus fechas. */
	@Test
	void lineaFueraDeFechasFallaCon1644() {
		FamiliasCaja familias = familiasDeCaja();
		Long lote = loteCargado(archivoDeAyer().linea(ayer(), pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago
				.deAlumno(familias.otroAlumno()), "", "450.00", "PEN", operacion(31)));
		String insertar = "INSERT INTO linea_recaudacion (colegio_id, lote_id, numero, fecha_pago, codigo, monto, moneda, "
				+ "numero_operacion, estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, ?, '00000000', 450, "
				+ "'PEN', ?, ?, NOW(6), 'x', NOW(6))";
		String antes = java.time.LocalDate.parse(ayer()).minusDays(1).toString();

		assertThat(codigoAl(() -> jdbc.update(insertar, lote, 2, antes, operacion(32), "PENDIENTE"))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(insertar, lote, 2, ayer(), operacion(33), "APLICADA"))).isEqualTo(1644);
	}

	/**
	 * F20: un pago CAJA con medio «recaudación bancaria», y un pago de recaudación en efectivo (aunque cc_app escribiera
	 * por SQL la confirmación del lote), los rechaza el CHECK {@code ck_pago_origen}.
	 */
	@Test
	void pagoDeRecaudacionEnEfectivoFallaCon3819() {
		FamiliasCaja familias = familiasDeCaja();
		java.util.Map<String, Object> boleta = jdbc.queryForMap("SELECT id, total FROM comprobante WHERE tipo = 'BOLETA' "
				+ "ORDER BY id LIMIT 1");
		String total = ((java.math.BigDecimal) boleta.get("total")).toPlainString();
		Long lote = loteCargado(archivoDeAyer().linea(ayer(), pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago
				.deAlumno(familias.otroAlumno()), "", total, "PEN", operacion(41)));
		Long linea = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.idLinea(jdbc, lote, 1);
		Long caja = cajaRecaudacionDeAyer();
		// Riesgo aceptado (M1): cc_app puede escribir la confirmación con otro nombre; el resto lo frena la base.
		jdbc.update("UPDATE lote_recaudacion SET estado = 'CONFIRMADO', confirmado_por = 'otra.persona', confirmado_en = "
				+ "NOW(6), total_ciego = total WHERE id = ?", lote);

		assertThat(insertarPagoRecaudacion("EFECTIVO", familias.otraFamilia(), caja, boleta.get("id"), total,
				operacion(41), "clave-" + sufijo + "-41", linea)).isEqualTo(3819);
		// De otra familia que no es la del código: el trigger.
		assertThat(insertarPagoRecaudacion("RECAUDACION_BANCARIA", familias.familia(), caja, boleta.get("id"), total,
				operacion(41), "clave-" + sufijo + "-42", linea)).isEqualTo(1644);
		// Un pago de caja «por banco».
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO pago (colegio_id, familia_id, caja_diaria_id, cajero, fecha, "
				+ "comprobante_id, medio, numero_operacion, operacion_vigente, total, a_cuenta, origen, clave_idempotencia, "
				+ "estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, 'cajera.x', ?, ?, 'RECAUDACION_BANCARIA', "
				+ "?, ?, ?, FALSE, 'CAJA', ?, 'VIGENTE', NOW(6), 'cajera.x', NOW(6))", familias.otraFamilia(), caja, ayer(),
				boleta.get("id"), operacion(43), operacion(43), total, "clave-" + sufijo + "-43"))).isEqualTo(3819);
	}

	// --- Sprint 4, tanda 3: extracto bancario y conciliación automática ---

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias cuentasBancarias;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos servicioExtractos;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioPartidas servicioPartidas;

	@Test
	void tablasDeConciliacionNoSeBorranNiCambianLoPedido() {
		for (String tabla : new String[] { "cuenta_bancaria", "extracto_bancario", "movimiento_bancario",
				"partida_conciliacion", "liquidacion_pasarela", "liquidacion_linea" }) {
			assertThat(codigoAl(() -> jdbc.update("DELETE FROM " + tabla + " WHERE 1 = 0"))).as(tabla).isEqualTo(1142);
		}
		// movimientoNoSeEditaFallaCon1142: los movimientos del banco y las liquidaciones son de solo inserción.
		for (String tabla : new String[] { "movimiento_bancario", "liquidacion_pasarela", "liquidacion_linea",
				"verificacion_bancaria" }) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE " + tabla + " SET version = version WHERE 1 = 0"))).as(tabla)
					.isEqualTo(1142);
		}
		for (String sentencia : new String[] { "UPDATE extracto_bancario SET saldo_final = saldo_final WHERE 1 = 0",
				"UPDATE extracto_bancario SET saldo_inicial = saldo_inicial WHERE 1 = 0",
				"UPDATE extracto_bancario SET desde = desde WHERE 1 = 0",
				"UPDATE extracto_bancario SET cuenta_id = cuenta_id WHERE 1 = 0",
				"UPDATE extracto_bancario SET archivo_sha256 = archivo_sha256 WHERE 1 = 0",
				"UPDATE partida_conciliacion SET monto_movimiento = monto_movimiento WHERE 1 = 0",
				"UPDATE partida_conciliacion SET pago_id = pago_id WHERE 1 = 0",
				"UPDATE partida_conciliacion SET regla = regla WHERE 1 = 0",
				"UPDATE cuenta_bancaria SET numero = numero WHERE 1 = 0" }) {
			assertThat(codigoAl(() -> jdbc.update(sentencia))).as(sentencia).isEqualTo(1143);
		}
	}

	/**
	 * Promotoría registra la cuenta, Administración sube el extracto de ayer y Dirección lo confirma a ciegas; el sistema
	 * (sistema.conciliacion, con los permisos mínimos de cc_app) propone las parejas; Administración confirma la del lote
	 * de recaudación (no quien lo subió) y la verificación AUTOMATICA de su pago la deja el sistema; la del Yape (de hoy,
	 * sugerida) no la confirma la cajera ni por SQL. Después, nada de lo resuelto se reescribe por SQL.
	 */
	@Test
	void flujoExtractoYConciliacionConPermisosMinimos() {
		AlumnoNuevo nuevo = alumnoNuevoConCuotas();
		Long matricula = jdbc.queryForObject("SELECT id FROM cuota WHERE alumno_id = ? AND tipo = 'MATRICULA'", Long.class,
				nuevo.alumno());
		// Un lote del banco de ayer: la matrícula del alumno nuevo y una línea con el código errado (total único).
		String centimos = String.format("%02d", Math.floorMod(System.nanoTime(), 100));
		String errada = "1" + Math.floorMod(System.nanoTime() / 100, 90) + "." + centimos;
		var archivo = archivoDeAyer()
				.linea(ayer(), pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago.deAlumno(nuevo.alumno()),
						pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago.deCuota(matricula), "350.00", "PEN",
						operacion(51))
				.linea(ayer(), pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.codigoErrado(
						nuevo.alumno()), "", errada, "PEN", operacion(52));
		Long lote = loteCargado(archivo);
		UsuariosDePrueba.iniciarSesion(persona(511, "promotora.conc", Rol.PROMOTOR));
		try {
			recaudacion.confirmar(lote, pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.version(jdbc,
					lote), archivo.total());
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		Long pagoBanco = jdbc.queryForObject("SELECT p.id FROM pago p JOIN linea_recaudacion l ON l.id = "
				+ "p.linea_recaudacion_id WHERE l.lote_id = ?", Long.class, lote);
		// Hoy, un Yape de tres pensiones (total único en la base).
		var cajera = cajera("caja.t4c");
		UsuariosDePrueba.iniciarSesion(cajera);
		Long yape;
		try {
			yape = cobro.cobrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital(nuevo.familia(),
					java.util.List.of(cuotaDe(nuevo.alumno(), 3), cuotaDe(nuevo.alumno(), 4), cuotaDe(nuevo.alumno(), 5)),
					pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.YAPE, "Y5" + sufijo, "1350.00"));
		}
		finally {
			SecurityContextHolder.clearContext();
		}

		String numeroCuenta = cuentaUnica();
		Long cuenta = registrarCuenta(numeroCuenta);
		var extracto = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extractoDe(numeroCuenta,
				"25000.00").abono(ayer(), "ABONO RECAUDACION CODIGO ALUMNO", "", archivo.total().toPlainString())
				.abono(ayer(), "YAPE RECIBIDO", "Y5" + sufijo + "X", "1350.00")
				.abono(ayer(), "INTERESES GANADOS", "", "1.23").cargo(ayer(), "IMPUESTO ITF", "", "0.45");
		var administracion = persona(512, "adm.conc", Rol.ADMINISTRACION);
		Long id = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar(servicioExtractos,
				administracion, extracto);
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForList("SELECT CONCAT(p.regla, ' ', p.estado, ' ', p.objeto_tipo) FROM partida_conciliacion p "
				+ "JOIN movimiento_bancario m ON m.id = p.movimiento_id WHERE m.extracto_id = ? ORDER BY p.id", String.class,
				id)).containsExactly("SUGERIDA PROPUESTA LOTE_RECAUDACION", "SUGERIDA PROPUESTA PAGO");

		// Quien subió no confirma; Dirección escribe a ciegas el saldo (primero uno que no coincide).
		UsuariosDePrueba.iniciarSesion(persona(513, "director.conc", Rol.DIRECTOR));
		try {
			var vista = servicioExtractos.paraConfirmar(cuenta);
			assertThatThrownBy(() -> servicioExtractos.confirmar(cuenta, vista.extractoId(), vista.version(),
					extracto.saldoFinal().add(java.math.BigDecimal.ONE)))
					.isInstanceOf(pe.edu.virgenmaria.cuentasclaras.conciliacion.service.SaldoNoCoincideException.class);
			var otraVez = servicioExtractos.paraConfirmar(cuenta);
			servicioExtractos.confirmar(cuenta, otraVez.extractoId(), otraVez.version(), extracto.saldoFinal());
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		java.util.Map<String, Object> fila = jdbc.queryForMap("SELECT estado, intentos_confirmacion, saldo_final_ciego, "
				+ "confirmacion_extracto_id FROM extracto_bancario WHERE id = ?", id);
		assertThat(fila).containsEntry("estado", "CONFIRMADO").containsEntry("intentos_confirmacion", 1);
		assertThat(((Number) fila.get("confirmacion_extracto_id")).longValue()).isEqualTo(id);

		Long partidaLote = jdbc.queryForObject("SELECT id FROM partida_conciliacion WHERE lote_recaudacion_id = ?",
				Long.class, lote);
		Long partidaYape = jdbc.queryForObject("SELECT id FROM partida_conciliacion WHERE pago_id = ?", Long.class, yape);
		// sugeridaConfirmadaPorLaCajeraFallaCon1644: aunque cc_app lo escriba por SQL, la cajera no confirma su Yape.
		assertThat(codigoAl(() -> jdbc.update("UPDATE partida_conciliacion SET estado = 'CONFIRMADA', resuelto_por = ?, "
				+ "resuelto_en = NOW(6) WHERE id = ?", cajera.getUsername(), partidaYape))).isEqualTo(1644);
		// S4-B2: quien subió el lote tampoco confirma su pareja, aunque lo escriba por SQL.
		assertThat(codigoAl(() -> jdbc.update("UPDATE partida_conciliacion SET estado = 'CONFIRMADA', resuelto_por = "
				+ "(SELECT creado_por FROM lote_recaudacion WHERE id = ?), resuelto_en = NOW(6) WHERE id = ?", lote,
				partidaLote))).isEqualTo(1644);
		// S4-B1: la clave vigente solo puede ser la de su propio objeto.
		assertThat(codigoAl(() -> jdbc.update("UPDATE partida_conciliacion SET objeto_vigente = 'PAGO:1' WHERE id = ?",
				partidaYape))).isEqualTo(1644);
		// Administración (que no subió el lote ni cobró) confirma las dos; el sistema deja las verificaciones.
		UsuariosDePrueba.iniciarSesion(administracion);
		try {
			servicioPartidas.confirmarSugerida(partidaLote);
			servicioPartidas.confirmarSugerida(partidaYape);
			servicioPartidas.explicar(jdbc.queryForObject("SELECT id FROM movimiento_bancario WHERE extracto_id = ? AND "
					+ "descripcion = 'INTERESES GANADOS'", Long.class, id),
					pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CategoriaExplicacion.INTERESES,
					"Intereses mensuales de la cuenta de cobranza");
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		// S4-A2: el cargo (ITF) no lo explica quien subió el extracto, ni por la aplicación ni por SQL.
		Long itf = jdbc.queryForObject("SELECT id FROM movimiento_bancario WHERE extracto_id = ? AND descripcion = "
				+ "'IMPUESTO ITF'", Long.class, id);
		UsuariosDePrueba.iniciarSesion(administracion);
		try {
			assertThatThrownBy(() -> servicioPartidas.explicar(itf,
					pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CategoriaExplicacion.IMPUESTO_ITF,
					"Impuesto a las transacciones financieras")).isInstanceOf(
							pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException.class);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO partida_conciliacion (colegio_id, movimiento_id, "
				+ "movimiento_vigente, objeto_tipo, regla, monto_movimiento, monto_objeto, diferencia, estado, categoria, "
				+ "nota, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, 'EXPLICACION', 'EXPLICADA', 0.45, 0.45, 0, "
				+ "'PROPUESTA', 'IMPUESTO_ITF', 'Impuesto ITF del día', NOW(6), ?, NOW(6))", itf, itf,
				administracion.getUsername()))).isNull();
		Long explicacion = jdbc.queryForObject("SELECT id FROM partida_conciliacion WHERE movimiento_vigente = ?",
				Long.class, itf);
		assertThat(codigoAl(() -> jdbc.update("UPDATE partida_conciliacion SET estado = 'CONFIRMADA', resuelto_por = ?, "
				+ "resuelto_en = NOW(6) WHERE id = ?", administracion.getUsername(), explicacion))).isEqualTo(1644);
		// Promotoría (que no lo subió) lo explica mirando su app del banco.
		assertThat(codigoAl(() -> jdbc.update("UPDATE partida_conciliacion SET estado = 'CONFIRMADA', resuelto_por = ?, "
				+ "resuelto_en = NOW(6) WHERE id = ?", "promotora.conc." + sufijo, explicacion))).isNull();
		assertThat(jdbc.queryForList("SELECT CONCAT(origen, ' ', resultado, ' ', creado_por) FROM verificacion_bancaria "
				+ "WHERE pago_id IN (?, ?) ORDER BY pago_id", String.class, pagoBanco, yape))
				.containsExactly("AUTOMATICA ENCONTRADO sistema.conciliacion", "AUTOMATICA ENCONTRADO sistema.conciliacion");

		// S4-B1: una partida CONFIRMADA no cambia su clave vigente (antes cc_app podía liberar el objeto).
		assertThat(codigoAl(() -> jdbc.update("UPDATE partida_conciliacion SET objeto_vigente = NULL WHERE id = ?",
				partidaYape))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE partida_conciliacion SET movimiento_vigente = NULL WHERE id = ?",
				partidaLote))).isEqualTo(1644);
		// partidaConfirmadaNoSeDescartaFallaCon1644 y lo demás resuelto no se reescribe.
		assertThat(codigoAl(() -> jdbc.update("UPDATE partida_conciliacion SET estado = 'DESCARTADA', movimiento_vigente = "
				+ "NULL, objeto_vigente = NULL WHERE id = ?", partidaLote))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE partida_conciliacion SET resuelto_por = 'otra' WHERE id = ?",
				partidaYape))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE extracto_bancario SET estado = 'CARGADO', confirmado_por = NULL, "
				+ "confirmado_en = NULL, confirmacion_extracto_id = NULL WHERE id = ?", id))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE extracto_bancario SET intentos_confirmacion = 0 WHERE id = ?", id)))
				.isEqualTo(1644);
		// Un movimiento más en un extracto ya confirmado.
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO movimiento_bancario (colegio_id, extracto_id, cuenta_id, numero, "
				+ "fecha, tipo, monto, descripcion, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, 99, ?, 'ABONO', "
				+ "1350.00, 'YAPE AGREGADO', NOW(6), 'x', NOW(6))", id, cuenta, ayer()))).isEqualTo(1644);
		// verificacionAutomaticaSinPartidaFallaCon1644: ni sin partida, ni con otra, ni escrita por una persona.
		Long otroYape = jdbc.queryForObject("SELECT MAX(id) FROM pago WHERE medio = 'YAPE' AND id <> ? AND estado = "
				+ "'VIGENTE' AND id NOT IN (SELECT pago_id FROM verificacion_bancaria WHERE pago_id IS NOT NULL)", Long.class,
				yape);
		if (otroYape != null) {
			assertThat(codigoAl(() -> jdbc.update("INSERT INTO verificacion_bancaria (colegio_id, pago_id, resultado, "
					+ "origen, partida_id, banco_fecha, banco_monto, creado_en, creado_por, actualizado_en) VALUES (1, ?, "
					+ "'ENCONTRADO', 'AUTOMATICA', ?, ?, 1350.00, NOW(6), 'sistema.conciliacion', NOW(6))", otroYape,
					partidaYape, ayer()))).isEqualTo(1644);
			assertThat(codigoAl(() -> jdbc.update("INSERT INTO verificacion_bancaria (colegio_id, pago_id, resultado, "
					+ "origen, banco_fecha, banco_monto, creado_en, creado_por, actualizado_en) VALUES (1, ?, 'ENCONTRADO', "
					+ "'AUTOMATICA', ?, 1350.00, NOW(6), 'sistema.conciliacion', NOW(6))", otroYape, ayer())))
					.isEqualTo(1644);
		}
		UsuariosDePrueba.iniciarSesion(guardar("verif.t4c." + sufijo, Rol.PROMOTOR));
		try {
			assertThat(verificador.verificar().integra()).isTrue();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	/**
	 * extractoQueNoContinuaFallaCon1644, extractoSuperpuestoFallaCon1644, exactaConOtraOperacionFallaCon1644 y
	 * descartarExtractoConSiguienteFallaCon1644: aunque cc_app escriba por SQL, la cadena de saldos y las parejas exactas
	 * las cuida la base.
	 */
	@Test
	void laCadenaDeSaldosYLasParejasExactasLasCuidaLaBase() {
		String numeroCuenta = cuentaUnica();
		Long cuenta = registrarCuenta(numeroCuenta);
		String anteayer = java.time.LocalDate.parse(ayer()).minusDays(1).toString();
		var primero = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extractoDe(numeroCuenta,
				"900.00").abono(anteayer, "ABONO VARIOS", "", "100.00");
		var administracion = persona(521, "adm.cadena", Rol.ADMINISTRACION);
		Long id = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar(servicioExtractos,
				administracion, primero);
		SecurityContextHolder.clearContext();
		String insertar = "INSERT INTO extracto_bancario (colegio_id, cuenta_id, secuencia, secuencia_vigente, anterior_id, "
				+ "archivo_id, archivo_sha256, formato, desde, hasta, saldo_inicial, total_abonos, total_cargos, saldo_final, "
				+ "movimientos, muestra, semilla_muestreo, estado, creado_en, creado_por, actualizado_en) SELECT colegio_id, "
				+ "cuenta_id, 2, 2, id, archivo_id, archivo_sha256, formato, ?, ?, ?, 0, 0, ?, 0, '', 7, 'CARGADO', NOW(6), "
				+ "'x', NOW(6) FROM extracto_bancario WHERE id = ?";
		// S4-A1 y S4-A2: sin muestra fija ni semilla secreta, un extracto no nace.
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO extracto_bancario (colegio_id, cuenta_id, secuencia, "
				+ "secuencia_vigente, archivo_id, archivo_sha256, formato, desde, hasta, saldo_inicial, total_abonos, "
				+ "total_cargos, saldo_final, movimientos, estado, creado_en, creado_por, actualizado_en) SELECT colegio_id, "
				+ "cuenta_id, 9, 9, archivo_id, archivo_sha256, formato, desde, hasta, 0, 0, 0, 0, 0, 'CARGADO', NOW(6), "
				+ "'x', NOW(6) FROM extracto_bancario WHERE id = ?", id))).isEqualTo(1644);
		// La muestra y la semilla no se reescriben.
		assertThat(codigoAl(() -> jdbc.update("UPDATE extracto_bancario SET muestra = '' WHERE id = ?", id)))
				.isEqualTo(1143);
		assertThat(codigoAl(() -> jdbc.update("UPDATE extracto_bancario SET semilla_muestreo = 1 WHERE id = ?", id)))
				.isEqualTo(1143);
		// No continúa el saldo (falta un movimiento) o se superpone con el día ya cargado.
		assertThat(codigoAl(() -> jdbc.update(insertar, ayer(), ayer(), "999.00", "999.00", id))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(insertar, anteayer, ayer(), "1000.00", "1000.00", id))).isEqualTo(1644);
		// Confirmarlo sin el saldo a ciegas, o quien lo subió.
		assertThat(codigoAl(() -> jdbc.update("UPDATE extracto_bancario SET estado = 'CONFIRMADO', confirmado_por = 'otra', "
				+ "confirmado_en = NOW(6), confirmacion_extracto_id = id WHERE id = ?", id))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE extracto_bancario SET estado = 'CONFIRMADO', confirmado_por = "
				+ "creado_por, confirmado_en = NOW(6), confirmacion_extracto_id = id, saldo_final_ciego = saldo_final "
				+ "WHERE id = ?", id))).isIn(1644, 3819);

		// El siguiente (legítimo, por la aplicación) continúa al primero; el primero ya no se descarta.
		var segundo = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extractoDe(numeroCuenta,
				primero.saldoFinal().toPlainString()).abono(ayer(), "YAPE RECIBIDO", "Z9" + sufijo, "77.70");
		Long id2 = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar(servicioExtractos,
				administracion, segundo);
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForObject("SELECT secuencia FROM extracto_bancario WHERE id = ?", Integer.class, id2))
				.isEqualTo(2);
		assertThat(codigoAl(() -> jdbc.update("UPDATE extracto_bancario SET estado = 'DESCARTADO', secuencia_vigente = NULL, "
				+ "rechazado_por = creado_por, rechazado_en = NOW(6), motivo_rechazo = 'Descarte por SQL' WHERE id = ?",
				id))).isEqualTo(1644);
		// confirmarFueraDeOrdenFallaCon1644: el segundo no se confirma antes que el primero.
		assertThat(codigoAl(() -> jdbc.update("UPDATE extracto_bancario SET saldo_final_ciego = saldo_final WHERE id = ?",
				id2))).isNull();
		assertThat(codigoAl(() -> jdbc.update("UPDATE extracto_bancario SET estado = 'CONFIRMADO', confirmado_por = 'otra', "
				+ "confirmado_en = NOW(6), confirmacion_extracto_id = id WHERE id = ?", id2))).isEqualTo(1644);
		// S4-A2: el primero ya no se confirma con el saldo a ciegas del segundo (la «cadena» sellada por el último).
		assertThat(codigoAl(() -> jdbc.update("UPDATE extracto_bancario SET estado = 'CONFIRMADO', confirmado_por = 'otra', "
				+ "confirmado_en = NOW(6), confirmacion_extracto_id = ? WHERE id = ?", id2, id))).isEqualTo(1644);
		// Por la aplicación se confirma de uno en uno: primero el más antiguo, con su propio saldo.
		UsuariosDePrueba.iniciarSesion(persona(522, "director.cadena", Rol.DIRECTOR));
		try {
			var vista = servicioExtractos.paraConfirmar(cuenta);
			assertThat(vista.pendientes()).hasSize(2);
			assertThat(vista.extractoId()).isEqualTo(id);
			servicioExtractos.confirmar(cuenta, vista.extractoId(), vista.version(), primero.saldoFinal());
			assertThat(servicioExtractos.paraConfirmar(cuenta).extractoId()).isEqualTo(id2);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		assertThat(jdbc.queryForObject("SELECT CONCAT(estado, ' ', confirmacion_extracto_id = id) FROM extracto_bancario "
				+ "WHERE id = ?", String.class, id)).isEqualTo("CONFIRMADO 1");
		// exactaConOtraOperacionFallaCon1644: una pareja EXACTA con un Yape de otra operación.
		Long movimiento = jdbc.queryForObject("SELECT id FROM movimiento_bancario WHERE extracto_id = ?", Long.class, id2);
		java.util.Map<String, Object> otroPago = jdbc.queryForMap("SELECT id, total FROM pago WHERE medio <> 'EFECTIVO' "
				+ "AND estado = 'VIGENTE' ORDER BY id LIMIT 1");
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO partida_conciliacion (colegio_id, movimiento_id, "
				+ "movimiento_vigente, objeto_tipo, pago_id, objeto_vigente, regla, monto_movimiento, monto_objeto, "
				+ "diferencia, estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, 'PAGO', ?, ?, 'EXACTA', "
				+ "77.70, ?, ?, 'PROPUESTA', NOW(6), 'sistema.conciliacion', NOW(6))", movimiento, movimiento,
				otroPago.get("id"), "PAGO:" + otroPago.get("id"), otroPago.get("total"), new java.math.BigDecimal("77.70")
						.subtract((java.math.BigDecimal) otroPago.get("total"))))).isEqualTo(1644);
	}

	/**
	 * Ataque 6 de la auditoría del sprint 4 (S4-C1), en MySQL real: el Yape inventado de la cajera ya NO se verifica
	 * emparejándolo a mano con un abono de otro monto. La aplicación lo rechaza y, aunque cc_app escriba la partida por
	 * SQL, la base la rechaza (CHECK de monto exacto); una MANUAL del mismo monto no se confirma sin su aprobación en la
	 * bandeja, y una verificación automática no guarda otro monto que el del movimiento.
	 */
	@Test
	void ataque6EmparejarAManoConOtroMontoYaNoVerificaElYapeInventado() {
		AlumnoNuevo nuevo = alumnoNuevoConCuotas();
		var cajera = cajera("caja.aud6");
		UsuariosDePrueba.iniciarSesion(cajera);
		Long yape;
		try {
			yape = cobro.cobrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital(nuevo.familia(),
					java.util.List.of(cuotaDe(nuevo.alumno(), 3)), pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.YAPE,
					"Z9" + sufijo, "450.00"));
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		String numeroCuenta = cuentaUnica();
		Long cuenta = registrarCuenta(numeroCuenta);
		// Dos abonos de S/ 450 (no hay pareja sugerida única) y unos intereses de S/ 0.35.
		var extracto = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extractoDe(numeroCuenta,
				"25000.00").abono(ayer(), "INTERESES GANADOS", "", "0.35")
				.abono(ayer(), "TRANSFERENCIA DE TERCERO", "T1" + sufijo, "450.00")
				.abono(ayer(), "TRANSFERENCIA DE OTRO", "T2" + sufijo, "450.00");
		var administracion = persona(611, "adm.aud6", Rol.ADMINISTRACION);
		Long id = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar(servicioExtractos,
				administracion, extracto);
		SecurityContextHolder.clearContext();
		UsuariosDePrueba.iniciarSesion(persona(613, "director.aud6", Rol.DIRECTOR));
		try {
			var vista = servicioExtractos.paraConfirmar(cuenta);
			servicioExtractos.confirmar(cuenta, vista.extractoId(), vista.version(), extracto.saldoFinal());
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		Long intereses = jdbc.queryForObject("SELECT id FROM movimiento_bancario WHERE extracto_id = ? AND numero = 1",
				Long.class, id);
		Long tercero = jdbc.queryForObject("SELECT id FROM movimiento_bancario WHERE extracto_id = ? AND numero = 2",
				Long.class, id);
		UsuariosDePrueba.iniciarSesion(administracion);
		try {
			assertThatThrownBy(() -> servicioPartidas.emparejarManual(intereses,
					pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ObjetoPartida.PAGO, yape,
					"Yape agrupado por el banco con otros")).isInstanceOf(
					pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException.class);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM partida_conciliacion WHERE pago_id = ?", Integer.class, yape))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verificacion_bancaria WHERE pago_id = ?", Integer.class, yape))
				.isZero();
		String insertar = "INSERT INTO partida_conciliacion (colegio_id, movimiento_id, movimiento_vigente, objeto_tipo, "
				+ "pago_id, objeto_vigente, regla, monto_movimiento, monto_objeto, diferencia, estado, nota, creado_en, "
				+ "creado_por, actualizado_en) VALUES (1, ?, ?, 'PAGO', ?, ?, 'MANUAL', ?, 450.00, ?, 'PROPUESTA', "
				+ "'Yape agrupado por el banco', NOW(6), ?, NOW(6))";
		// Por SQL, con otro monto: el CHECK de monto exacto la rechaza.
		assertThat(codigoAl(() -> jdbc.update(insertar, intereses, intereses, yape, "PAGO:" + yape,
				new java.math.BigDecimal("0.35"), new java.math.BigDecimal("-449.65"), administracion.getUsername())))
				.isEqualTo(3819);
		// Por SQL, con el mismo monto: se puede proponer, pero no confirmarla sin la aprobación de otra persona.
		assertThat(codigoAl(() -> jdbc.update(insertar, tercero, tercero, yape, "PAGO:" + yape,
				new java.math.BigDecimal("450.00"), java.math.BigDecimal.ZERO.setScale(2), administracion.getUsername())))
				.isNull();
		Long partida = jdbc.queryForObject("SELECT id FROM partida_conciliacion WHERE pago_id = ?", Long.class, yape);
		assertThat(codigoAl(() -> jdbc.update("UPDATE partida_conciliacion SET estado = 'CONFIRMADA', resuelto_por = ?, "
				+ "resuelto_en = NOW(6) WHERE id = ?", "otra.persona." + sufijo, partida))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO verificacion_bancaria (colegio_id, pago_id, resultado, origen, "
				+ "partida_id, banco_fecha, banco_monto, creado_en, creado_por, actualizado_en) VALUES (1, ?, 'ENCONTRADO', "
				+ "'AUTOMATICA', ?, ?, 450.00, NOW(6), 'sistema.conciliacion', NOW(6))", yape, partida, ayer())))
				.isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verificacion_bancaria WHERE pago_id = ?", Integer.class, yape))
				.isZero();
	}

	private record AlumnoNuevo(Long familia, Long alumno) {
	}

	/** Un alumno nuevo en la sección del escenario compartido: su cronograma se genera al matricularlo. */
	private AlumnoNuevo alumnoNuevoConCuotas() {
		FamiliasCaja familias = familiasDeCaja();
		java.util.Map<String, Object> matricula = jdbc.queryForMap("SELECT m.seccion_id, a.anio FROM matricula m JOIN "
				+ "anio_escolar a ON a.id = m.anio_escolar_id WHERE m.alumno_id = ?", familias.hermano1());
		int anio = ((Number) matricula.get("anio")).intValue();
		String base = String.format("%07d", Math.floorMod(System.nanoTime() + 59, 10_000_000L));
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		try {
			var nuevo = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
					.conApoderadoNuevo("5" + base, "Gutiérrez", "Salas", "Ariana", java.time.LocalDate.of(anio - 10, 5, 9),
							"2" + base, "Salas", "Paredes", "Carmen", "934567812", null,
							((Number) matricula.get("seccion_id")).longValue()));
			return new AlumnoNuevo(nuevo.familiaId(), nuevo.alumnoId());
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	/** Un número de cuenta único en esta base (que no se limpia). */
	private String cuentaUnica() {
		String digitos = String.format("%09d", Math.floorMod(System.nanoTime(), 1_000_000_000L));
		return "191-" + digitos.substring(0, 7) + "-0-" + digitos.substring(7);
	}

	/** Promotoría registra la cuenta. */
	private Long registrarCuenta(String numero) {
		UsuariosDePrueba.iniciarSesion(persona(520, "promotor.cuenta", Rol.PROMOTOR));
		try {
			return cuentasBancarias.registrar(new pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.CuentaRequest(
					pe.edu.virgenmaria.cuentasclaras.conciliacion.model.BancoCuenta.BCP, numero, "BCP " + sufijo));
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	/** La caja del canal RECAUDACION de ayer (la abre el sistema; aquí, si falta, la abre una tanda real). */
	private Long cajaRecaudacionDeAyer() {
		java.util.List<Long> cajas = jdbc.queryForList("SELECT id FROM caja_diaria WHERE canal = 'RECAUDACION' AND fecha = ?",
				Long.class, ayer());
		if (!cajas.isEmpty()) {
			return cajas.getFirst();
		}
		assertThat(jdbc.update("INSERT INTO caja_diaria (colegio_id, cajero, fecha, fondo_fijo, estado, cierres, conteos, "
				+ "canal, creado_en, creado_por, actualizado_en) VALUES (1, 'sistema.recaudacion', ?, 0, 'ABIERTA', 0, 0, "
				+ "'RECAUDACION', NOW(6), 'sistema.recaudacion', NOW(6))", ayer())).isEqualTo(1);
		return jdbc.queryForObject("SELECT id FROM caja_diaria WHERE canal = 'RECAUDACION' AND fecha = ?", Long.class,
				ayer());
	}

	/** INSERT directo de un pago de recaudación como cc_app (lo que haría alguien con sus credenciales). */
	private Integer insertarPagoRecaudacion(String medio, Long familia, Long caja, Object comprobante, String total,
			String operacion, String clave, Long linea) {
		return codigoAl(() -> jdbc.update("INSERT INTO pago (colegio_id, familia_id, caja_diaria_id, cajero, fecha, "
				+ "comprobante_id, medio, total, numero_operacion, operacion_vigente, a_cuenta, origen, clave_idempotencia, "
				+ "linea_recaudacion_id, estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, "
				+ "'sistema.recaudacion', ?, ?, ?, ?, ?, ?, FALSE, 'RECAUDACION', ?, ?, 'VIGENTE', NOW(6), "
				+ "'sistema.recaudacion', NOW(6))", familia, caja, ayer(), comprobante, medio, total, operacion, operacion,
				clave, linea));
	}

	private String iniciarPagoEnLinea(Long cuota) {
		var revision = pagoEnLinea.revisar(new pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest(
				java.util.List.of(cuota)));
		return pagoEnLinea.crearOrden(new pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest(
				java.util.UUID.randomUUID(), java.util.List.of(cuota), revision.total(), false));
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

	// ---------------------------------------------------------------------------------------------------------------
	// Sprint 5, tanda 1 (V17): mensajes, acceso directo al titular y huella. La fase 2 corre SIN la fila
	// 'mensajeria_simulada' (como prod: el verificador de prod la exige ausente). Después el job la inserta como
	// administrador y vuelve a correr solo los flujos que envían con la mensajería simulada (fase 2b), y la borra.
	// ---------------------------------------------------------------------------------------------------------------

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes despacho;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor.BuzonSimulado buzon;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.auditoria.proceso.HuellaDiaria huellaDiaria;

	private boolean mensajeriaSimuladaHabilitada() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM configuracion_bd WHERE clave = 'mensajeria_simulada'",
				Integer.class) > 0;
	}

	/** Rosa (apoderada nueva con celular) de una familia nueva: [familia, apoderado, celular guardado]. */
	private Object[] familiaConCelular(String celular) {
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		String base = String.format("%07d", Math.floorMod(System.nanoTime(), 10_000_000L));
		Long familia = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
				.conApoderadoNuevo("5" + base, "Ramos", "Vega", "Lucía", java.time.LocalDate.of(2016, 4, 9), "4" + base,
						"Vega", "Soto", "Ana", celular, null, null)).familiaId();
		SecurityContextHolder.clearContext();
		Long apoderado = jdbc.queryForObject("SELECT id FROM apoderado WHERE familia_id = ?", Long.class, familia);
		String guardado = jdbc.queryForObject("SELECT telefono_whatsapp FROM apoderado WHERE id = ?", String.class,
				apoderado);
		return new Object[] { familia, apoderado, guardado, "4" + base };
	}

	/**
	 * Correcciones del sprint 5 (S5-A1): el titular confirma su contacto con el enlace de verificación que le llegó (fase
	 * 2b: con la mensajería simulada). Pasa por la página pública, con los permisos mínimos de cc_app y los triggers.
	 */
	private void confirmarContacto(String destino, String documento) throws Exception {
		String ruta = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EnlacesDePrueba.verificacionRecibida(despacho, buzon, 1L,
				destino);
		mvc.perform(post(ruta).with(csrf()).param("documento", documento))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM apoderado WHERE (telefono_verificado = ? OR correo_verificado = ?)"
				+ " AND numero_documento = ?", Long.class, destino, destino, documento)).isEqualTo(1);
	}

	/**
	 * Sprint 5 (A2, S4-M2, G7, G9): dar y restablecer el acceso del apoderado envía el enlace DIRECTO a su celular; el
	 * enlace nace con su mensaje (trigger) en el proceso de envío; se activa con los permisos mínimos; el uso no se
	 * reescribe (1644) y el enlace no se borra ni cambia su hash, su usuario, su vencimiento ni su mensaje (1142/1143).
	 */
	@Test
	void flujoActivacionDirectaConPermisosMinimos() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(mensajeriaSimuladaHabilitada(), "falta la fila mensajeria_simulada");
		Object[] f = familiaConCelular("955444333");
		Long apoderado = (Long) f[1];
		String celular = (String) f[2];
		String dni = (String) f[3];
		confirmarContacto(celular, dni);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		assertThat(accesoApoderados.darAcceso(apoderado).enviadoA()).doesNotContain("/activar/");
		SecurityContextHolder.clearContext();
		String primero = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EnlacesDePrueba.recibido(despacho, buzon, 1L,
				celular);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.PROMOTOR));
		accesoApoderados.restablecerAcceso(apoderado);
		SecurityContextHolder.clearContext();
		String ruta = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EnlacesDePrueba.recibido(despacho, buzon, 1L, celular);
		assertThat(ruta).isNotEqualTo(primero);

		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(primero))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
						.string(org.hamcrest.Matchers.containsString("ya no sirve")));
		String clave = "una clave elegida en mysql " + sufijo;
		mvc.perform(post(ruta).with(csrf()).param("documento", dni).param("clave", clave).param("confirmacion", clave))
				.andExpect(redirectedUrl("/login?cuenta-activada"));
		mvc.perform(post("/login").with(csrf()).param("usuario", dni).param("clave", clave))
				.andExpect(redirectedUrl("/inicio"));
		Long usuario = jdbc.queryForObject("SELECT id FROM usuario WHERE apoderado_id = ?", Long.class, apoderado);
		assertThat(jdbc.queryForObject("SELECT CONCAT(COUNT(*), ' ', SUM(usado_en IS NOT NULL), ' ', "
				+ "SUM(anulado_en IS NOT NULL), ' ', SUM(mensaje_id IS NOT NULL)) FROM enlace_activacion WHERE usuario_id = ?",
				String.class, usuario)).isEqualTo("2 1 1 2");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'ACTIVACION_CUENTA' AND entidad_id = ? "
				+ "AND estado = 'ENVIADO' AND proveedor = 'SIMULADO'", Long.class, usuario)).isEqualTo(2);
		for (String[] caso : new String[][] { { "DELETE FROM enlace_activacion WHERE usuario_id = ?", "1142" },
				{ "UPDATE enlace_activacion SET hash_token = REPEAT('0', 64) WHERE usuario_id = ?", "1143" },
				{ "UPDATE enlace_activacion SET vence_en = NOW(6) WHERE usuario_id = ?", "1143" },
				{ "UPDATE enlace_activacion SET usuario_id = usuario_id WHERE usuario_id = ?", "1143" },
				{ "UPDATE enlace_activacion SET mensaje_id = mensaje_id WHERE usuario_id = ?", "1143" } }) {
			assertThatThrownBy(() -> jdbc.update(caso[0], usuario)).as(caso[0]).isInstanceOf(DataAccessException.class)
					.satisfies(e -> assertThat(codigoMySql(e)).as(caso[0]).isEqualTo(Integer.parseInt(caso[1])));
		}
	}

	/** G9: el uso de un enlace se escribe una vez; uno anulado o vencido no se usa (trg_enlace_activacion_uso). */
	@Test
	void reescribirElUsoDelEnlaceFallaCon1644() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(mensajeriaSimuladaHabilitada(), "falta la fila mensajeria_simulada");
		Object[] f = familiaConCelular("955201775");
		confirmarContacto((String) f[2], (String) f[3]);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		accesoApoderados.darAcceso((Long) f[1]);
		SecurityContextHolder.clearContext();
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EnlacesDePrueba.recibido(despacho, buzon, 1L, (String) f[2]);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.PROMOTOR));
		accesoApoderados.restablecerAcceso((Long) f[1]);
		SecurityContextHolder.clearContext();
		String ruta = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EnlacesDePrueba.recibido(despacho, buzon, 1L,
				(String) f[2]);
		String clave = "otra clave elegida en mysql " + sufijo;
		mvc.perform(post(ruta).with(csrf()).param("documento", (String) f[3]).param("clave", clave)
				.param("confirmacion", clave)).andExpect(redirectedUrl("/login?cuenta-activada"));
		Long usuario = jdbc.queryForObject("SELECT id FROM usuario WHERE apoderado_id = ?", Long.class, f[1]);
		Long usado = jdbc.queryForObject("SELECT id FROM enlace_activacion WHERE usuario_id = ? AND usado_en IS NOT NULL",
				Long.class, usuario);
		Long anulado = jdbc.queryForObject("SELECT id FROM enlace_activacion WHERE usuario_id = ? AND anulado_en IS NOT NULL",
				Long.class, usuario);
		assertThat(codigoAl(() -> jdbc.update("UPDATE enlace_activacion SET usado_en = NOW(6), usado_ip = '1.2.3.4' "
				+ "WHERE id = ?", usado))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE enlace_activacion SET anulado_en = NULL WHERE id = ?", anulado)))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE enlace_activacion SET usado_en = NOW(6), usado_ip = '1.2.3.4' "
				+ "WHERE id = ?", anulado))).isEqualTo(1644);
	}

	/** S4-M2 + A2: un enlace sin su mensaje de activación al titular no existe. */
	@Test
	void enlaceSinMensajeFallaCon1644() {
		Usuario titular = guardar("titular.enlace." + sufijo, Rol.CAJA);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO enlace_activacion (colegio_id, usuario_id, hash_token, vence_en, "
				+ "proposito, creado_en, creado_por, actualizado_en) VALUES (1, ?, REPEAT('a', 64), NOW(6) + INTERVAL 1 HOUR, "
				+ "'PERSONAL', NOW(6), 'promotor', NOW(6))", titular.getId()))).isEqualTo(1644);
	}

	/**
	 * G2 y G10 con los permisos mínimos: el cobro crea su aviso en la misma transacción; sale (ENVIADO con su proveedor,
	 * id y fecha), se marca ENTREGADO y nunca vuelve atrás ni reescribe su envío.
	 */
	@Test
	void flujoMensajeDePagoConPermisosMinimos() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(mensajeriaSimuladaHabilitada(), "falta la fila mensajeria_simulada");
		// Un alumno nuevo en la sección del escenario de caja (sus cuotas no las toca ninguna otra prueba).
		FamiliasCaja familias = familiasDeCaja();
		Long seccion = jdbc.queryForObject("SELECT seccion_id FROM matricula WHERE alumno_id = ?", Long.class,
				familias.hermano1());
		int anio = jdbc.queryForObject("SELECT a.anio FROM seccion s JOIN anio_escolar a ON a.id = s.anio_escolar_id "
				+ "WHERE s.id = ?", Integer.class, seccion);
		String base = String.format("%07d", Math.floorMod(System.nanoTime() + 77, 10_000_000L));
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		var nuevo = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
				.conApoderadoNuevo("7" + base, "Ríos", "Paz", "Camila", java.time.LocalDate.of(anio - 10, 5, 2), "4" + base,
						"Paz", "León", "Elena", "955000222", null, seccion));
		SecurityContextHolder.clearContext();
		confirmarContacto("+51955000222", "4" + base);
		UsuariosDePrueba.iniciarSesion(cajera("caja.mensaje"));
		Long pago = cobrarEfectivo(nuevo.familiaId(), java.util.List.of(cuotaDe(nuevo.alumnoId(), 3)), "450.00");
		SecurityContextHolder.clearContext();
		Long mensaje = jdbc.queryForObject("SELECT id FROM mensaje WHERE tipo = 'PAGO_REGISTRADO' AND entidad = 'pago' "
				+ "AND entidad_id = ?", Long.class, pago);
		assertThat(jdbc.queryForObject("SELECT parametros FROM mensaje WHERE id = ?", String.class, mensaje))
				.contains("S/ 450.00").contains("B001-");
		for (int pasada = 0; pasada < 50 && despacho.despacharColegio(1L) > 0; pasada++) {
			// de a 20, el más antiguo primero
		}
		assertThat(jdbc.queryForMap("SELECT estado, proveedor FROM mensaje WHERE id = ?", mensaje))
				.containsEntry("estado", "ENVIADO").containsEntry("proveedor", "SIMULADO");
		assertThat(jdbc.update("UPDATE mensaje SET estado = 'ENTREGADO', entregado_en = NOW(6), version = version + 1 "
				+ "WHERE id = ?", mensaje)).isEqualTo(1);
		assertThat(codigoAl(() -> jdbc.update("UPDATE mensaje SET estado = 'PENDIENTE' WHERE id = ?", mensaje)))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE mensaje SET proveedor_mensaje_id = 'otro' WHERE id = ?", mensaje)))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE mensaje SET intentos = intentos + 5 WHERE id = ?", mensaje)))
				.isEqualTo(1644);
	}

	/** G12: el texto, el destino y el destinatario de un mensaje no cambian (1143) y no se borra (1142). */
	@Test
	void mensajeNoSeEditaNiSeBorra() {
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM mensaje WHERE 1 = 0"))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM huella_bitacora WHERE 1 = 0"))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("UPDATE huella_bitacora SET version = version WHERE 1 = 0")))
				.isEqualTo(1142);
		for (String columna : new String[] { "destino", "parametros", "apoderado_id", "usuario_id", "plantilla", "tipo",
				"clave" }) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE mensaje SET " + columna + " = " + columna + " WHERE 1 = 0")))
					.as(columna).isEqualTo(1143);
		}
	}

	/** G6: a un apoderado no se le escribe a un celular del personal si nadie más aprobó ese contacto. */
	@Test
	void mensajeAContactoDelPersonalFallaCon1644() {
		Usuario personal = guardar("personal.g6." + sufijo, Rol.CAJA);
		String suyo = jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class,
				personal.getId());
		Object[] f = familiaConCelular(suyo.substring(3));
		assertThat(f[2]).isEqualTo(suyo);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, "
				+ "apoderado_id, familia_id, destino, plantilla, parametros, entidad, entidad_id, estado, creado_en, "
				+ "creado_por, actualizado_en) VALUES (1, ?, 'PAGO_REGISTRADO', 'WHATSAPP', 'APODERADO', ?, ?, ?, "
				+ "'PAGO_REGISTRADO', 'x', 'pago', 0, 'PENDIENTE', NOW(6), 'caja', NOW(6))", "g6-" + sufijo, f[1], f[0], suyo)))
				.isEqualTo(1644);
	}

	/** G5: el celular del apoderado no cambia sin SU solicitud CAMBIO_CONTACTO_APODERADO aprobada. */
	@Test
	void cambiarElCelularSinSolicitudFallaCon1644() {
		Object[] f = familiaConCelular("955201774");
		assertThat(codigoAl(() -> jdbc.update("UPDATE apoderado SET telefono_whatsapp = '+51955000111' WHERE id = ?",
				f[1]))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE apoderado SET correo = 'otro@correo.pe' WHERE id = ?", f[1])))
				.isEqualTo(1644);
	}

	/**
	 * Un mensaje PENDIENTE válido (va al celular registrado de una persona del personal: desde V20 el de un apoderado
	 * exige su contacto verificado) que el despacho no toma (2100).
	 */
	private Long mensajePendiente(String prefijoClave) {
		Usuario titular = guardar("pendiente." + prefijoClave + sufijo, Rol.PROMOTOR);
		jdbc.update("INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, usuario_id, destino, plantilla, "
				+ "parametros, entidad, entidad_id, estado, proximo_intento_en, creado_en, creado_por, actualizado_en) "
				+ "VALUES (1, ?, 'HUELLA_BITACORA', 'WHATSAPP', 'USUARIO', ?, ?, 'HUELLA', '01/01/2000', 'huella_bitacora', 0, "
				+ "'PENDIENTE', '2100-01-01', NOW(6), 'sistema.auditoria', NOW(6))", prefijoClave + sufijo, titular.getId(),
				titular.getTelefonoWhatsapp());
		return jdbc.queryForObject("SELECT id FROM mensaje WHERE clave = ?", Long.class, prefijoClave + sufijo);
	}

	/** G7: los parámetros de un mensaje nunca llevan el enlace de activación (CHECK ck_mensaje_sin_token). */
	@Test
	void mensajeConTokenFallaCon3819() {
		Usuario titular = guardar("token." + sufijo, Rol.PROMOTOR);
		for (String enlace : new String[] { "https://colegio.pe/activar/1/abc", "https://colegio.pe/verificar/1/abc" }) {
			assertThat(codigoAl(() -> jdbc.update("INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, "
					+ "usuario_id, destino, plantilla, parametros, estado, creado_en, creado_por, actualizado_en) "
					+ "VALUES (1, ?, 'HUELLA_BITACORA', 'WHATSAPP', 'USUARIO', ?, ?, 'HUELLA', ?, 'PENDIENTE', NOW(6), "
					+ "'sistema.auditoria', NOW(6))", "token-" + enlace.length() + sufijo, titular.getId(),
					titular.getTelefonoWhatsapp(), enlace))).as(enlace).isEqualTo(3819);
		}
	}

	/** G10: ENVIADO exige el proveedor, su id y la fecha (CHECK ck_mensaje_envio). */
	@Test
	void enviadoSinIdDelProveedorFallaCon3819() {
		Long mensaje = mensajePendiente("sin-id-");
		assertThat(codigoAl(() -> jdbc.update("UPDATE mensaje SET estado = 'ENVIADO' WHERE id = ?", mensaje)))
				.isEqualTo(3819);
	}

	/**
	 * G10 y G11: sin la fila 'mensajeria_simulada' del DBA, la base no acepta un mensaje simulado. Deja un mensaje
	 * PENDIENTE con la clave «pendiente-para-ci-…»: el paso «comprobar» del job (ya sin la fila) lo intenta y espera 1644.
	 */
	@Test
	void simuladoSinPermisoDeLaBaseFallaCon1644() {
		Long mensaje = mensajePendiente("pendiente-para-ci-");
		String simulado = "UPDATE mensaje SET proveedor = 'SIMULADO', proveedor_mensaje_id = CONCAT('SIM-', id), "
				+ "estado = 'ENVIADO', enviado_en = NOW(6), intentos = intentos + 1 WHERE id = ?";
		if (!mensajeriaSimuladaHabilitada()) {
			assertThat(codigoAl(() -> jdbc.update(simulado, mensaje))).isEqualTo(1644);
		}
		assertThat(jdbc.queryForObject("SELECT estado FROM mensaje WHERE id = ?", String.class, mensaje))
				.isEqualTo("PENDIENTE");
	}

	/** G13: la huella diaria se guarda con los permisos mínimos y una que no coincide con la bitácora se rechaza. */
	@Test
	void huellaQueNoCoincideFallaCon1644() {
		java.time.LocalDate manana = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).plusDays(1);
		huellaDiaria.enColegio(1L, manana);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM huella_bitacora WHERE colegio_id = 1 AND fecha = ?",
				Long.class, manana.minusDays(1))).isEqualTo(1);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO huella_bitacora (colegio_id, fecha, secuencia, codigo, "
				+ "eventos_del_dia, creado_en, creado_por, actualizado_en) VALUES (1, '1999-01-01', 1, "
				+ "'ffffffffffffffff', 0, NOW(6), 'sistema.auditoria', NOW(6))"))).isEqualTo(1644);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sprint 5, tanda 2 (V18): renovación de matrícula, matrícula reservada y avisos de las familias. La renovación
	// necesita un año EN CURSO y uno PLANIFICADO; el colegio 1 de esta base no tiene año en curso (sus años son
	// planificados y matriculan ACTIVA, como antes del sprint), así que estas pruebas usan un segundo colegio que el job
	// crea como administrador («Colegio de renovacion CI»; cc_app no puede crear colegios).
	// ---------------------------------------------------------------------------------------------------------------

	static final String COLEGIO_RENOVACION = "Colegio de renovacion CI";

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioCampanaRenovacion campanaRenovacion;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioRenovacionFamilia renovacionFamilia;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.familias.service.ServicioAvisosFamilia avisosFamilia;

	/** El colegio de renovación: año actual EN CURSO con 5.° A y el siguiente PLANIFICADO con 6.° A y su plan aprobado. */
	private record ColegioRenovacion(long id, int anio, Long anioActual, Long anioSiguiente, Long quintoA, Long sextoA) {
	}

	private static ColegioRenovacion colegioRenovacion;

	private pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado personalRenovacion(long colegio, long id,
			String nombre, Rol rol) {
		return UsuariosDePrueba.autenticado(colegio, id, nombre, "Nombre de " + nombre, false, EnumSet.of(rol));
	}

	private synchronized ColegioRenovacion colegioDeRenovacion() {
		java.util.List<Long> ids = jdbc.queryForList("SELECT id FROM colegio WHERE nombre = ?", Long.class,
				COLEGIO_RENOVACION);
		org.junit.jupiter.api.Assumptions.assumeFalse(ids.isEmpty(), "falta el colegio de renovación (lo crea el job)");
		if (colegioRenovacion != null) {
			return colegioRenovacion;
		}
		long colegio = ids.getFirst();
		int anio = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).getYear();
		UsuariosDePrueba.iniciarSesion(personalRenovacion(colegio, 2101L, "ren.administracion", Rol.ADMINISTRACION));
		Long actual = anioDe(colegio, anio, true);
		Long siguiente = anioDe(colegio, anio + 1, false);
		Long quinto = seccionDe(colegio, actual, pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado.PRIMARIA_5);
		Long sexto = seccionDe(colegio, siguiente, pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado.PRIMARIA_6);
		if (jdbc.queryForObject("SELECT COUNT(*) FROM plan_pension WHERE anio_escolar_id = ? AND nivel = 'PRIMARIA' "
				+ "AND estado = 'APROBADO' AND vigente = TRUE", Long.class, siguiente) == 0) {
			Long plan = planes.crearBorrador(siguiente, Nivel.PRIMARIA, EscenarioCobranza.plan(anio + 1, "450", "300", null));
			UsuariosDePrueba.iniciarSesion(personalRenovacion(colegio, 2102L, "ren.administracion2", Rol.ADMINISTRACION));
			planes.enviar(plan);
			UsuariosDePrueba.iniciarSesion(personalRenovacion(colegio, 2103L, "ren.director", Rol.DIRECTOR));
			planes.aprobar(plan, planes.obtener(plan).version());
		}
		SecurityContextHolder.clearContext();
		colegioRenovacion = new ColegioRenovacion(colegio, anio, actual, siguiente, quinto, sexto);
		return colegioRenovacion;
	}

	private Long anioDe(long colegio, int anio, boolean enCurso) {
		java.util.List<Long> existente = jdbc.queryForList("SELECT id FROM anio_escolar WHERE colegio_id = ? AND anio = ?",
				Long.class, colegio, anio);
		return existente.isEmpty() ? estructura.crearAnio(new pe.edu.virgenmaria.cuentasclaras.colegio.dto
				.CrearAnioEscolarRequest(anio, java.time.LocalDate.of(anio, 3, 2), java.time.LocalDate.of(anio, 12, 18),
						enCurso)) : existente.getFirst();
	}

	private Long seccionDe(long colegio, Long anio, pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado grado) {
		java.util.List<Long> existente = jdbc.queryForList("SELECT id FROM seccion WHERE colegio_id = ? AND "
				+ "anio_escolar_id = ? AND grado = ? AND nombre = 'A'", Long.class, colegio, anio, grado.name());
		return existente.isEmpty() ? estructura.crearSeccion(anio, new pe.edu.virgenmaria.cuentasclaras.colegio.dto
				.CrearSeccionRequest(grado, "A")) : existente.getFirst();
	}

	/** Un alumno nuevo en 5.° A del año en curso, con su apoderada (celular único): [familia, alumno, apoderado]. */
	private Long[] alumnoDeRenovacion(ColegioRenovacion r) {
		UsuariosDePrueba.iniciarSesion(personalRenovacion(r.id(), 2101L, "ren.administracion", Rol.ADMINISTRACION));
		long n = Math.floorMod(System.nanoTime(), 10_000_000L);
		String base = String.format("%07d", n);
		var registro = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
				.conApoderadoNuevo("7" + base, "Quispe", "Huamán", "Mateo", java.time.LocalDate.of(r.anio() - 10, 6, 14),
						"4" + base, "Huamán", "Ccori", "Rosa", "95" + base, null, r.quintoA()));
		SecurityContextHolder.clearContext();
		Long apoderado = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				registro.alumnoId());
		return new Long[] { registro.familiaId(), registro.alumnoId(), apoderado };
	}

	/**
	 * S5-A1: el apoderado recibe mensajes solo con su contacto confirmado por él mismo (enlace de verificación, fase 2b).
	 */
	private void confirmarContactoDe(ColegioRenovacion r, Long apoderado) throws Exception {
		var a = jdbc.queryForMap("SELECT telefono_whatsapp, numero_documento FROM apoderado WHERE id = ?", apoderado);
		String ruta = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EnlacesDePrueba.verificacionRecibida(despacho, buzon,
				r.id(), (String) a.get("telefono_whatsapp"));
		mvc.perform(post(ruta).with(csrf()).param("documento", (String) a.get("numero_documento")))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM apoderado WHERE id = ? AND telefono_verificado IS NOT NULL",
				Long.class, apoderado)).isEqualTo(1);
	}

	/** La cuenta en línea del apoderado (fila de usuario enlazada: el trigger de la respuesta por portal la exige). */
	private pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado enLinea(ColegioRenovacion r,
			Long apoderado) {
		String nombre = "ren.familia." + apoderado + "." + sufijo;
		// La cuenta de un apoderado solo nace desde su ficha (con el enlace al titular, fase 2b): aquí se enlaza por SQL
		// con los permisos de cc_app, como quedaría después de activarla.
		Usuario usuario = UsuariosDePrueba.guardar(usuarios, codificador, r.id(), nombre, UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE);
		// Sprint 6 (trg_usuario_contacto): el contacto del PERSONAL no cambia por SQL; la cuenta pasa a ser del apoderado y
		// después pierde el celular (la regla del apoderado no cambió). Correcciones del sprint 6 (S6-A1): una cuenta con
		// roles del personal no se enlaza a un apoderado, así que primero cambia el rol y después se enlaza.
		jdbc.update("UPDATE usuario_rol SET rol = 'APODERADO' WHERE usuario_id = ?", usuario.getId());
		jdbc.update("UPDATE usuario SET apoderado_id = ? WHERE id = ?", apoderado, usuario.getId());
		jdbc.update("UPDATE usuario SET telefono_whatsapp = NULL WHERE id = ?", usuario.getId());
		return new pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado(usuario.getId(), r.id(), nombre,
				nombre, null, true, false, false, EnumSet.of(Rol.APODERADO), apoderado);
	}

	/** Abre (o completa) la campaña y devuelve la renovación del alumno. */
	private Long propuestaDe(ColegioRenovacion r, Long alumno) {
		UsuariosDePrueba.iniciarSesion(personalRenovacion(r.id(), 2101L, "ren.administracion", Rol.ADMINISTRACION));
		campanaRenovacion.abrir(r.anioSiguiente(), java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).plusDays(1));
		SecurityContextHolder.clearContext();
		return jdbc.queryForObject("SELECT id FROM renovacion_matricula WHERE alumno_id = ?", Long.class, alumno);
	}

	/** La familia confirma en el portal; después del commit, sistema.matricula reserva. Devuelve la matrícula. */
	private Long reservada(ColegioRenovacion r, Long[] familia) {
		Long renovacion = propuestaDe(r, familia[1]);
		UsuariosDePrueba.iniciarSesion(enLinea(r, familia[2]));
		renovacionFamilia.responder(renovacion, true);
		SecurityContextHolder.clearContext();
		return jdbc.queryForObject("SELECT matricula_id FROM renovacion_matricula WHERE id = ?", Long.class, renovacion);
	}

	private Long cobrarEnRenovacion(ColegioRenovacion r, Long familia, Long cuota, String monto, boolean aCuenta) {
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(r.id(), 2104L, "ren.caja." + sufijo, "Cajera",
				false, EnumSet.of(Rol.CAJA)));
		Long pago = cobro.cobrar(new pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest(java.util.UUID.randomUUID(),
				familia, java.util.List.of(cuota), pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.EFECTIVO, null,
				new java.math.BigDecimal(monto), aCuenta ? new java.math.BigDecimal(monto) : null,
				new java.math.BigDecimal("300.00"), pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante.BOLETA,
				null, null, null));
		SecurityContextHolder.clearContext();
		return pago;
	}

	/**
	 * G18 con los permisos mínimos: propuesta → la familia confirma en el portal → sistema.matricula reserva (con la
	 * cuota del plan) → la cajera cobra la matrícula → sistema.matricula activa → las 10 pensiones. Detecta un
	 * saveAndFlush faltante: cada trigger valida contra la fila recién escrita.
	 */
	@Test
	void flujoRenovacionYMatriculaConPermisosMinimos() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(mensajeriaSimuladaHabilitada(), "falta la fila mensajeria_simulada");
		ColegioRenovacion r = colegioDeRenovacion();
		Long[] familia = alumnoDeRenovacion(r);
		confirmarContactoDe(r, familia[2]);
		Long matricula = reservada(r, familia);
		assertThat(jdbc.queryForMap("SELECT estado, seccion_id, creado_por FROM matricula WHERE id = ?", matricula))
				.containsEntry("estado", "RESERVADA").containsEntry("seccion_id", r.sextoA())
				.containsEntry("creado_por", "sistema.matricula");
		assertThat(jdbc.queryForObject("SELECT estado FROM renovacion_matricula WHERE matricula_id = ?", String.class,
				matricula)).isEqualTo("MATRICULADA");
		Long cuota = jdbc.queryForObject("SELECT id FROM cuota WHERE matricula_id = ? AND tipo = 'MATRICULA'", Long.class,
				matricula);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cuota WHERE matricula_id = ? AND tipo = 'PENSION'", Long.class,
				matricula)).isZero();

		cobrarEnRenovacion(r, familia[0], cuota, "300.00", false);
		assertThat(jdbc.queryForMap("SELECT estado, activada_por FROM matricula WHERE id = ?", matricula))
				.containsEntry("estado", "ACTIVA").containsEntry("activada_por", "sistema.matricula");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cuota WHERE matricula_id = ? AND tipo = 'PENSION'", Long.class,
				matricula)).isEqualTo(10);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'RENOVACION_MATRICULA' AND "
				+ "apoderado_id = ?", Long.class, familia[2])).isEqualTo(1);
		// La activación no se reescribe ni vuelve atrás; la renovación no se borra ni cambia de alumno.
		assertThat(codigoAl(() -> jdbc.update("UPDATE matricula SET activada_por = 'otra.persona' WHERE id = ?",
				matricula))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE matricula SET estado = 'RESERVADA', activada_en = NULL, "
				+ "activada_por = NULL WHERE id = ?", matricula))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM renovacion_matricula WHERE matricula_id = ?", matricula)))
				.isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("UPDATE renovacion_matricula SET alumno_id = alumno_id WHERE "
				+ "matricula_id = ?", matricula))).isEqualTo(1143);
		assertThat(codigoAl(() -> jdbc.update("UPDATE renovacion_matricula SET respondido_por = 'otra.persona' WHERE "
				+ "matricula_id = ?", matricula))).isEqualTo(1644);
	}

	/** G18: nadie activa una matrícula reservada sin la matrícula pagada, ni firma como sistema.matricula sin pagar. */
	@Test
	void activarSinPagarFallaCon1644() {
		ColegioRenovacion r = colegioDeRenovacion();
		Long matricula = reservada(r, alumnoDeRenovacion(r));
		assertThat(codigoAl(() -> jdbc.update("UPDATE matricula SET estado = 'ACTIVA', activada_en = NOW(6), "
				+ "activada_por = 'sistema.matricula' WHERE id = ?", matricula))).isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT estado FROM matricula WHERE id = ?", String.class, matricula))
				.isEqualTo("RESERVADA");
	}

	/** Con un año en curso, nadie inserta una matrícula ACTIVA en el año planificado (se saltaría la reserva). */
	@Test
	void matriculaActivaEnAnioPlanificadoFallaCon1644() {
		ColegioRenovacion r = colegioDeRenovacion();
		Long[] familia = alumnoDeRenovacion(r);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO matricula (colegio_id, alumno_id, anio_escolar_id, seccion_id, "
				+ "fecha_matricula, estado, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, CURRENT_DATE, "
				+ "'ACTIVA', NOW(6), 'administracion', NOW(6))", r.id(), familia[1], r.anioSiguiente(), r.sextoA())))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO matricula (colegio_id, alumno_id, anio_escolar_id, seccion_id, "
				+ "fecha_matricula, estado, activada_en, activada_por, creado_en, creado_por, actualizado_en) VALUES (?, ?, "
				+ "?, ?, CURRENT_DATE, 'RESERVADA', NOW(6), 'sistema.matricula', NOW(6), 'administracion', NOW(6))", r.id(),
				familia[1], r.anioSiguiente(), r.sextoA()))).isEqualTo(1644);
	}

	/** G19: una reservada con la matrícula pagada (o en pago parcial) no se retira para quedarse con el dinero. */
	@Test
	void retirarReservadaPagadaFallaCon1644() {
		ColegioRenovacion r = colegioDeRenovacion();
		Long[] familia = alumnoDeRenovacion(r);
		Long matricula = reservada(r, familia);
		Long cuota = jdbc.queryForObject("SELECT id FROM cuota WHERE matricula_id = ? AND tipo = 'MATRICULA'", Long.class,
				matricula);
		cobrarEnRenovacion(r, familia[0], cuota, "100.00", true);
		assertThat(jdbc.queryForObject("SELECT estado FROM cuota WHERE id = ?", String.class, cuota)).isEqualTo("PARCIAL");
		assertThat(jdbc.queryForObject("SELECT estado FROM matricula WHERE id = ?", String.class, matricula))
				.isEqualTo("RESERVADA");
		assertThat(codigoAl(() -> jdbc.update("UPDATE matricula SET estado = 'RETIRADA', retirada_en = CURRENT_DATE "
				+ "WHERE id = ?", matricula))).isEqualTo(1644);
	}

	/** G17: en el portal responde un apoderado de ESA familia (la cuenta de otra familia no confirma la renovación). */
	@Test
	void respuestaPorPortalDeOtraFamiliaFallaCon1644() {
		ColegioRenovacion r = colegioDeRenovacion();
		Long[] suya = alumnoDeRenovacion(r);
		Long[] otra = alumnoDeRenovacion(r);
		Long renovacion = propuestaDe(r, suya[1]);
		String deOtra = enLinea(r, otra[2]).getUsername();
		assertThat(codigoAl(() -> jdbc.update("UPDATE renovacion_matricula SET estado = 'CONFIRMADA', "
				+ "canal_respuesta = 'PORTAL', respondido_por = ?, respondido_en = NOW(6) WHERE id = ?", deOtra,
				renovacion))).isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT estado FROM renovacion_matricula WHERE id = ?", String.class, renovacion))
				.isEqualTo("PROPUESTA");
	}

	/** El aviso de una familia se atiende una vez: su respuesta no cambia (1644), su texto tampoco (1143) y no se borra. */
	@Test
	void avisoAtendidoNoCambiaFallaCon1644() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(mensajeriaSimuladaHabilitada(), "falta la fila mensajeria_simulada");
		ColegioRenovacion r = colegioDeRenovacion();
		Long[] familia = alumnoDeRenovacion(r);
		confirmarContactoDe(r, familia[2]);
		UsuariosDePrueba.iniciarSesion(enLinea(r, familia[2]));
		Long aviso = avisosFamilia.enviar(new pe.edu.virgenmaria.cuentasclaras.familias.dto.AvisoRequest(
				pe.edu.virgenmaria.cuentasclaras.familias.model.TipoAvisoFamilia.PAGUE_Y_NO_APARECE, null, null,
				"Pagué en caja y no aparece " + sufijo));
		UsuariosDePrueba.iniciarSesion(personalRenovacion(r.id(), 2105L, "ren.promotor", Rol.PROMOTOR));
		avisosFamilia.atender(aviso, "Lo revisamos con caja y ya aparece en tu estado de cuenta.");
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForObject("SELECT estado FROM aviso_familia WHERE id = ?", String.class, aviso))
				.isEqualTo("ATENDIDO");
		assertThat(codigoAl(() -> jdbc.update("UPDATE aviso_familia SET respuesta = 'Otra respuesta' WHERE id = ?",
				aviso))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE aviso_familia SET estado = 'ABIERTO', atendido_por = NULL, "
				+ "atendido_en = NULL, respuesta = NULL WHERE id = ?", aviso))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE aviso_familia SET texto = 'otro texto' WHERE id = ?", aviso)))
				.isEqualTo(1143);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM aviso_familia WHERE id = ?", aviso))).isEqualTo(1142);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'AVISO_ATENDIDO' AND entidad_id = ?",
				Long.class, aviso)).isEqualTo(1);
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

	/** Una persona del personal con correo desde su alta (un INSERT: trg_usuario_contacto vigila los UPDATE). */
	private Usuario guardarConCorreo(String nombre, Rol rol, String correo) {
		return pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio.en(1L, () -> {
			Usuario usuario = Usuario.nuevo(nombre, "Nombre de " + nombre, correo, codificador.encode(UsuariosDePrueba.CLAVE),
					EnumSet.of(rol));
			usuario.asignarTelefonoWhatsapp(UsuariosDePrueba.celular(nombre));
			return usuarios.save(usuario);
		});
	}

	private static Integer codigoMySql(Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause()) {
			if (t instanceof SQLException sql) {
				return sql.getErrorCode();
			}
		}
		return null;
	}
	// --- Sprint 5, tanda 3 (V19): feriados, semilla del muestreo y cierre bancario mensual ---

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCierreMensual servicioCierreMensual;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.conciliacion.proceso.CierresMensuales cierresMensuales;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioFeriados servicioFeriados;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillasMuestreo semillasMuestreo;

	private static final java.time.ZoneId LIMA = java.time.ZoneId.of("America/Lima");

	/** Una cuenta nueva con el extracto CONFIRMADO de todo el mes anterior (real, en Lima). Devuelve la cuenta. */
	private Long cuentaConMesAnteriorConfirmado(String sufijoPersonas) {
		java.time.YearMonth mes = java.time.YearMonth.now(LIMA).minusMonths(1);
		String numeroCuenta = cuentaUnica();
		Long cuenta = registrarCuenta(numeroCuenta);
		var extracto = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extractoDe(numeroCuenta,
				"20000.00").abono(mes.atDay(1).toString(), "YAPE RECIBIDO", "", "700.00")
				.cargo(mes.atDay(15).toString(), "COMISION MANTENIMIENTO", "", "25.00")
				.abono(mes.atEndOfMonth().toString(), "TRANSFERENCIA DE TERCEROS", "", "300.00");
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar(servicioExtractos,
				persona(530, "adm.mes" + sufijoPersonas, Rol.ADMINISTRACION), extracto);
		UsuariosDePrueba.iniciarSesion(persona(531, "promotor.mes" + sufijoPersonas, Rol.PROMOTOR));
		try {
			var vista = servicioExtractos.paraConfirmar(cuenta);
			servicioExtractos.confirmar(cuenta, vista.extractoId(), vista.version(), extracto.saldoFinal());
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		return cuenta;
	}

	/**
	 * Tanda 3 (G22): con los permisos mínimos de cc_app, sistema.conciliacion crea el cierre del mes anterior (el trigger
	 * recalcula los totales y el saldo), Dirección lo cuadra a ciegas tras un intento fallido, y nada de lo resuelto se
	 * reescribe por SQL. Quien confirmó el extracto no puede firmar el cierre ni por SQL.
	 */
	@Test
	void flujoCierreMensualConPermisosMinimos() {
		java.time.YearMonth mes = java.time.YearMonth.now(LIMA).minusMonths(1);
		Long cuenta = cuentaConMesAnteriorConfirmado("a");
		cierresMensuales.enColegio(1L, java.time.LocalDate.now(LIMA));
		java.util.Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cierre_mensual_banco WHERE cuenta_id = ? "
				+ "AND anio = ? AND mes = ?", cuenta, mes.getYear(), mes.getMonthValue());
		Long id = ((Number) fila.get("id")).longValue();
		assertThat(fila).containsEntry("estado", "ABIERTO").containsEntry("creado_por", "sistema.conciliacion");
		assertThat((java.math.BigDecimal) fila.get("total_abonos")).isEqualByComparingTo("1000.00");
		assertThat((java.math.BigDecimal) fila.get("total_cargos")).isEqualByComparingTo("25.00");
		assertThat((java.math.BigDecimal) fila.get("saldo_final")).isEqualByComparingTo("20975.00");

		// Quien confirmó el extracto no firma el cierre, ni siquiera por SQL.
		assertThat(codigoAl(() -> jdbc.update("UPDATE cierre_mensual_banco SET intentos = intentos + 1, registrado_por = "
				+ "?, registrado_en = NOW(6), abonos_ciego = 1000, cargos_ciego = 25, saldo_ciego = 20975 WHERE id = ?",
				"promotor.mesa." + sufijo, id))).isEqualTo(1644);
		// Los intentos no saltan.
		assertThat(codigoAl(() -> jdbc.update("UPDATE cierre_mensual_banco SET intentos = intentos + 2 WHERE id = ?", id)))
				.isEqualTo(1644);

		UsuariosDePrueba.iniciarSesion(persona(532, "director.mes", Rol.DIRECTOR));
		try {
			var vista = servicioCierreMensual.vista(id);
			assertThat(vista.totalAbonos()).isNull();
			assertThatThrownBy(() -> servicioCierreMensual.registrar(id, vista.version(), new java.math.BigDecimal(
					"1000"), new java.math.BigDecimal("25"), new java.math.BigDecimal("20970")))
					.isInstanceOf(pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCierreMensual
							.CierreNoCoincideException.class);
			var otraVez = servicioCierreMensual.vista(id);
			assertThat(servicioCierreMensual.registrar(id, otraVez.version(), new java.math.BigDecimal("1000"),
					new java.math.BigDecimal("25"), new java.math.BigDecimal("20975")))
					.isEqualTo(pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoCierreMensual.CUADRADO);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		assertThat(jdbc.queryForMap("SELECT estado, intentos FROM cierre_mensual_banco WHERE id = ?", id))
				.containsEntry("estado", "CUADRADO").containsEntry("intentos", 2);
		// Resuelto: no vuelve a ABIERTO ni cambia lo escrito; sus totales y su mes no cambian (1143); no se borra (1142).
		assertThat(codigoAl(() -> jdbc.update("UPDATE cierre_mensual_banco SET estado = 'ABIERTO' WHERE id = ?", id)))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE cierre_mensual_banco SET saldo_ciego = 0 WHERE id = ?", id)))
				.isEqualTo(1644);
		for (String columna : new String[] { "total_abonos", "total_cargos", "saldo_final", "cuenta_id", "anio", "mes" }) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE cierre_mensual_banco SET " + columna + " = " + columna
					+ " WHERE id = ?", id))).as(columna).isEqualTo(1143);
		}
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM cierre_mensual_banco WHERE id = ?", id))).isEqualTo(1142);
	}

	/** Tanda 3 (G22): nadie inserta un cierre con totales que no salen de los extractos confirmados, ni ya cuadrado. */
	@Test
	void cierreConTotalesInventadosFallaCon1644() {
		java.time.YearMonth mes = java.time.YearMonth.now(LIMA).minusMonths(1);
		Long cuenta = cuentaConMesAnteriorConfirmado("b");
		String insertar = "INSERT INTO cierre_mensual_banco (colegio_id, cuenta_id, anio, mes, total_abonos, total_cargos, "
				+ "saldo_final, estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, ?, ?, ?, ?, ?, NOW(6), "
				+ "'sistema.conciliacion', NOW(6))";
		// Un abono inventado de 500 compensado con cargos: el saldo cuadra, los totales no.
		assertThat(codigoAl(() -> jdbc.update(insertar, cuenta, mes.getYear(), mes.getMonthValue(), "1500.00", "525.00",
				"20975.00", "ABIERTO"))).isEqualTo(1644);
		// Con los totales verdaderos pero otro saldo.
		assertThat(codigoAl(() -> jdbc.update(insertar, cuenta, mes.getYear(), mes.getMonthValue(), "1000.00", "25.00",
				"20000.00", "ABIERTO"))).isEqualTo(1644);
		// Ya CUADRADO al nacer.
		assertThat(codigoAl(() -> jdbc.update(insertar, cuenta, mes.getYear(), mes.getMonthValue(), "1000.00", "25.00",
				"20975.00", "CUADRADO"))).isEqualTo(1644);
		// Un mes que los extractos no cubren.
		java.time.YearMonth otro = mes.minusMonths(1);
		assertThat(codigoAl(() -> jdbc.update(insertar, cuenta, otro.getYear(), otro.getMonthValue(), "0.00", "0.00",
				"20000.00", "ABIERTO"))).isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cierre_mensual_banco WHERE cuenta_id = ?", Long.class,
				cuenta)).isZero();
	}

	/** Tanda 3 (G20): un feriado se registra solo a futuro y se anula una vez y antes de su fecha (trigger). */
	@Test
	void feriadoEnElPasadoFallaCon1644() {
		java.time.LocalDate hoy = java.time.LocalDate.now(LIMA);
		String insertar = "INSERT INTO feriado (colegio_id, fecha, descripcion, vigente, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, ?, 'Feriado de prueba', TRUE, NOW(6), 'verificador', NOW(6))";
		assertThat(codigoAl(() -> jdbc.update(insertar, hoy.minusDays(3)))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(insertar, hoy))).isEqualTo(1644);

		// A futuro, por la aplicación (Dirección), con los permisos mínimos; se anula y no se vuelve a tocar.
		java.time.LocalDate futuro = hoy.plusDays(40 + Math.floorMod(System.nanoTime(), 300));
		while (futuro.getDayOfWeek() == java.time.DayOfWeek.SUNDAY
				|| pe.edu.virgenmaria.cuentasclaras.comun.fecha.FeriadosNacionales.es(futuro)
				|| jdbc.queryForObject("SELECT COUNT(*) FROM feriado WHERE colegio_id = 1 AND fecha = ? AND vigente",
						Long.class, futuro) > 0) {
			futuro = futuro.plusDays(1);
		}
		java.time.LocalDate fecha = futuro;
		UsuariosDePrueba.iniciarSesion(persona(540, "director.feriado", Rol.DIRECTOR));
		Long id;
		try {
			id = servicioFeriados.registrar(new pe.edu.virgenmaria.cuentasclaras.colegio.dto.FeriadoRequest(fecha,
					"Día no laborable de prueba"));
			servicioFeriados.anular(id, "Prueba de anulación en MySQL");
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		assertThat(codigoAl(() -> jdbc.update("UPDATE feriado SET vigente = TRUE, anulado_por = NULL, anulado_en = NULL, "
				+ "motivo_anulacion = NULL WHERE id = ?", id))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE feriado SET fecha = fecha WHERE id = ?", id))).isEqualTo(1143);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM feriado WHERE id = ?", id))).isEqualTo(1142);
	}

	/** Tanda 3 (G21): la semilla del muestreo nace una vez por día y no se edita ni se borra. */
	@Test
	void semillaNoSeEditaFallaCon1142() {
		java.time.LocalDate dia = java.time.LocalDate.of(2026, 1, 1).plusDays(Math.floorMod(System.nanoTime(), 3000));
		UsuariosDePrueba.iniciarSesion(persona(550, "promotor.semilla", Rol.PROMOTOR));
		long semilla;
		try {
			semilla = semillasMuestreo.de(pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillaMuestreo.Ambito.CAJA, dia);
			assertThat(semillasMuestreo.de(pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillaMuestreo.Ambito.CAJA,
					dia)).isEqualTo(semilla);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		assertThat(codigoAl(() -> jdbc.update("UPDATE semilla_muestreo SET semilla = semilla + 1 WHERE fecha = ?", dia)))
				.isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM semilla_muestreo WHERE fecha = ?", dia))).isEqualTo(1142);
	}
	// ---------------------------------------------------------------------------------------------------------------
	// Correcciones del sprint 5 (V20): verificación de contactos (S5-A1), contacto aprobado por canal (S5-M1), avisos
	// que no cierra quien participó (S5-M2), feriados aprobados por otra persona con topes (S5-M3) y huellas que no
	// retroceden (S5-M4). Las que necesitan enviar (la verificación) corren en la fase 2b.
	// ---------------------------------------------------------------------------------------------------------------

	private String insertarAvisoDePago = "INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, "
			+ "apoderado_id, familia_id, destino, plantilla, parametros, entidad, entidad_id, estado, creado_en, creado_por, "
			+ "actualizado_en) VALUES (1, ?, ?, ?, 'APODERADO', ?, ?, ?, 'PAGO_REGISTRADO', 'x', 'pago', 0, 'PENDIENTE', "
			+ "NOW(6), 'caja', NOW(6))";

	/** S5-A1: un contacto sin verificar no recibe mensajes, y nadie lo marca verificado sin su enlace usado. */
	@Test
	void contactoSinVerificarNoRecibeNiSeVerificaPorSqlFallaCon1644() {
		Object[] f = familiaConCelular("955300" + String.format("%03d", Math.floorMod(System.nanoTime(), 1000)));
		assertThat(codigoAl(() -> jdbc.update(insertarAvisoDePago, "s5a1-" + sufijo, "PAGO_REGISTRADO", "WHATSAPP", f[1],
				f[0], f[2]))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE apoderado SET telefono_verificado = telefono_whatsapp WHERE id = ?",
				f[1]))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO verificacion_contacto (colegio_id, apoderado_id, canal, contacto, "
				+ "hash_token, mensaje_id, vence_en, verificado_en, verificado_ip, creado_en, creado_por, actualizado_en) "
				+ "VALUES (1, ?, 'WHATSAPP', ?, REPEAT('b', 64), 0, NOW(6) + INTERVAL 1 HOUR, NOW(6), '1.2.3.4', NOW(6), "
				+ "'sistema.mensajeria', NOW(6))", f[1], f[2]))).isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'VERIFICACION_CONTACTO' AND apoderado_id = ?",
				Long.class, f[1])).as("el registro pidió verificar ese contacto").isEqualTo(1);
	}

	/**
	 * S5-A1 con los permisos mínimos (fase 2b): el enlace de verificación nace en el envío con su mensaje; confirmarlo
	 * verifica el contacto; el enlace usado no se reescribe (1644) y no cambia su contacto, su hash ni su mensaje (1143).
	 */
	@Test
	void flujoVerificacionDeContactoConPermisosMinimos() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(mensajeriaSimuladaHabilitada(), "falta la fila mensajeria_simulada");
		Object[] f = familiaConCelular("955301" + String.format("%03d", Math.floorMod(System.nanoTime(), 1000)));
		assertThat(codigoAl(() -> jdbc.update(insertarAvisoDePago, "s5a1b-" + sufijo, "PAGO_REGISTRADO", "WHATSAPP", f[1],
				f[0], f[2]))).as("antes de verificar").isEqualTo(1644);
		confirmarContacto((String) f[2], (String) f[3]);
		assertThat(jdbc.update(insertarAvisoDePago, "s5a1c-" + sufijo, "PAGO_REGISTRADO", "WHATSAPP", f[1], f[0], f[2]))
				.as("ya verificado").isEqualTo(1);
		Long verificacion = jdbc.queryForObject("SELECT id FROM verificacion_contacto WHERE apoderado_id = ? AND "
				+ "verificado_en IS NOT NULL", Long.class, f[1]);
		assertThat(codigoAl(() -> jdbc.update("UPDATE verificacion_contacto SET verificado_en = NOW(6), verificado_ip = "
				+ "'9.9.9.9' WHERE id = ?", verificacion))).isEqualTo(1644);
		for (String columna : new String[] { "contacto", "hash_token", "mensaje_id", "apoderado_id" }) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE verificacion_contacto SET " + columna + " = " + columna
					+ " WHERE id = ?", verificacion))).as(columna).isEqualTo(1143);
		}
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM verificacion_contacto WHERE id = ?", verificacion)))
				.isEqualTo(1142);
	}

	/** S5-A1 y S5-M5: la regla del personal compara NORMALIZADO (un alias de Gmail es el mismo buzón). */
	@Test
	void aliasDelCorreoDelPersonalFallaCon1644() {
		String nombre = "lucia.caja" + sufijo.replaceAll("[^a-z0-9]", "");
		// Sprint 6 (trg_usuario_contacto): el correo del personal se registra al crear la cuenta (cambiarlo exige su
		// solicitud aprobada).
		guardarConCorreo("alias." + sufijo, Rol.CAJA, nombre + "@gmail.com");
		String alias = nombre.replace("lucia.", "Lucia.") + "+ramos@googlemail.com";
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		String base = String.format("%07d", Math.floorMod(System.nanoTime() + 13, 10_000_000L));
		Long familia = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
				.conApoderadoNuevo("5" + base, "Ramos", "Vega", "Lucas", java.time.LocalDate.of(2016, 4, 9), "4" + base,
						"Vega", "Soto", "Ana", null, alias, null)).familiaId();
		SecurityContextHolder.clearContext();
		Long apoderado = jdbc.queryForObject("SELECT id FROM apoderado WHERE familia_id = ?", Long.class, familia);
		String guardado = jdbc.queryForObject("SELECT correo FROM apoderado WHERE id = ?", String.class, apoderado);
		// La verificación de ese contacto no la manda la aplicación (G6) y la base tampoco la acepta.
		assertThat(codigoAl(() -> jdbc.update(insertarAvisoDePago, "s5m5-" + sufijo, "VERIFICACION_CONTACTO", "CORREO",
				apoderado, familia, guardado))).isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_cambio WHERE tipo = 'CAMBIO_CONTACTO_APODERADO' AND "
				+ "entidad_id = ? AND estado = 'PENDIENTE'", Long.class, apoderado)).isEqualTo(1);
	}

	/**
	 * S5-M1: aprobar un cambio de SOLO el correo deja aprobado ese correo, no el celular del personal; el celular sigue sin
	 * recibir (1644) y nadie lo marca aprobado por SQL (1644).
	 */
	@Test
	void aprobarElCorreoNoAprueboElCelularDelPersonalFallaCon1644() {
		Usuario cajera = guardar("m1." + sufijo, Rol.CAJA);
		String suyo = jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class,
				cajera.getId());
		Object[] f = familiaConCelular(suyo.substring(3));
		Long apoderado = (Long) f[1];
		Long aprobarCelular = jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'CAMBIO_CONTACTO_APODERADO' "
				+ "AND entidad_id = ? AND estado = 'PENDIENTE'", Long.class, apoderado);
		UsuariosDePrueba.iniciarSesion(persona(560, "director.m1", Rol.DIRECTOR));
		bandeja.rechazar(aprobarCelular, "No es el celular de la familia: es de la cajera");
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		servicioFamilias.actualizarApoderado(apoderado, new pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoRequest(
				pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento.DNI, (String) f[3], "Vega", "Soto", "Ana",
				pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco.MADRE, suyo.substring(3),
				"ana.m1." + sufijo + "@correo.pe", "Corrige el correo de la madre"));
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.OtraPersona.apruebaLaDe(bandeja, jdbc, "apoderado", apoderado);
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForMap("SELECT contacto_aprobado_telefono, contacto_aprobado_correo FROM apoderado WHERE id = ?",
				apoderado)).containsEntry("contacto_aprobado_telefono", null)
				.containsEntry("contacto_aprobado_correo", "ana.m1." + sufijo + "@correo.pe");
		assertThat(codigoAl(() -> jdbc.update(insertarAvisoDePago, "s5m1-" + sufijo, "VERIFICACION_CONTACTO", "WHATSAPP",
				apoderado, f[0], suyo))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE apoderado SET contacto_aprobado_telefono = telefono_whatsapp "
				+ "WHERE id = ?", apoderado))).isEqualTo(1644);
	}

	/** S5-M2: quien cobró el pago del que se queja la familia no atiende su aviso (en la base también). */
	@Test
	void avisoAtendidoPorQuienCobroFallaCon1644() {
		FamiliasCaja familias = familiasDeCaja();
		Long seccion = jdbc.queryForObject("SELECT seccion_id FROM matricula WHERE alumno_id = ?", Long.class,
				familias.hermano1());
		int anio = jdbc.queryForObject("SELECT a.anio FROM seccion s JOIN anio_escolar a ON a.id = s.anio_escolar_id "
				+ "WHERE s.id = ?", Integer.class, seccion);
		String base = String.format("%07d", Math.floorMod(System.nanoTime() + 91, 10_000_000L));
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		var nuevo = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
				.conApoderadoNuevo("7" + base, "Ríos", "Paz", "Tomás", java.time.LocalDate.of(anio - 10, 5, 2), "4" + base,
						"Paz", "León", "Elena", "955000333", null, seccion));
		var cajera = cajera("caja.m2");
		UsuariosDePrueba.iniciarSesion(cajera);
		Long pago = cobrarEfectivo(nuevo.familiaId(), java.util.List.of(cuotaDe(nuevo.alumnoId(), 3)), "450.00");
		SecurityContextHolder.clearContext();
		Long apoderado = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				nuevo.alumnoId());
		jdbc.update("INSERT INTO aviso_familia (colegio_id, familia_id, apoderado_id, tipo, pago_id, texto, estado, creado_en, "
				+ "creado_por, actualizado_en) VALUES (1, ?, ?, 'NO_RECONOZCO_PAGO', ?, 'No reconozco este pago', 'ABIERTO', "
				+ "NOW(6), 'familia', NOW(6))", nuevo.familiaId(), apoderado, pago);
		Long aviso = jdbc.queryForObject("SELECT MAX(id) FROM aviso_familia WHERE pago_id = ?", Long.class, pago);
		String atender = "UPDATE aviso_familia SET estado = 'ATENDIDO', atendido_por = ?, atendido_en = NOW(6), "
				+ "respuesta = 'Revisado', version = version + 1 WHERE id = ?";
		assertThat(codigoAl(() -> jdbc.update(atender, cajera.getUsername(), aviso))).isEqualTo(1644);
		assertThat(jdbc.update(atender, "promotor.m2." + sufijo, aviso)).isEqualTo(1);
	}

	/** S5-M3: el feriado nace propuesto, lo aprueba OTRA persona, 3 por mes como máximo y no 3 seguidos. */
	@Test
	void feriadoAprobadoPorQuienLoPropusoOFueraDeTopeFallaCon1644() {
		int anio = 2090 + Math.floorMod(System.nanoTime(), 9);
		int mes = 1 + Math.floorMod(System.nanoTime() / 7, 12);
		java.time.LocalDate dia = java.time.LocalDate.of(anio, mes, 3);
		jdbc.update("UPDATE feriado SET vigente = NULL, anulado_por = 'limpieza', anulado_en = NOW(6), motivo_anulacion = "
				+ "'Prueba de MySQL anterior' WHERE colegio_id = 1 AND vigente AND YEAR(fecha) = ? AND MONTH(fecha) = ?",
				anio, mes);
		String insertar = "INSERT INTO feriado (colegio_id, fecha, descripcion, vigente, pendiente, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, ?, 'Feriado de prueba', TRUE, TRUE, NOW(6), 'director.m3', NOW(6))";
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO feriado (colegio_id, fecha, descripcion, vigente, creado_en, "
				+ "creado_por, actualizado_en) VALUES (1, ?, 'Nace aprobado', TRUE, NOW(6), 'x', NOW(6))", dia)))
				.as("nace aprobado").isEqualTo(1644);
		assertThat(jdbc.update(insertar, dia)).isEqualTo(1);
		assertThat(jdbc.update(insertar, dia.plusDays(1))).isEqualTo(1);
		assertThat(codigoAl(() -> jdbc.update(insertar, dia.plusDays(2)))).as("3 seguidos").isEqualTo(1644);
		assertThat(jdbc.update(insertar, dia.plusDays(10))).isEqualTo(1);
		assertThat(codigoAl(() -> jdbc.update(insertar, dia.plusDays(20)))).as("4 en el mes").isEqualTo(1644);
		Long id = jdbc.queryForObject("SELECT id FROM feriado WHERE colegio_id = 1 AND fecha = ? AND vigente", Long.class,
				dia);
		String aprobar = "UPDATE feriado SET pendiente = FALSE, aprobado_por = ?, aprobado_en = NOW(6) WHERE id = ?";
		assertThat(codigoAl(() -> jdbc.update(aprobar, "director.m3", id))).as("quien lo propuso").isEqualTo(1644);
		assertThat(jdbc.update(aprobar, "promotor.m3", id)).isEqualTo(1);
		assertThat(codigoAl(() -> jdbc.update("UPDATE feriado SET pendiente = TRUE, aprobado_por = NULL, aprobado_en = NULL "
				+ "WHERE id = ?", id))).as("no vuelve a propuesto").isEqualTo(1644);
	}

	/** S5-M4: una huella (diaria o de la hora) no retrocede por debajo de una ya guardada del colegio. */
	@Test
	void huellaQueRetrocedeFallaCon1644() {
		huellaDiaria.horaEnColegio(1L, java.time.LocalDateTime.now(LIMA).plusMinutes(1));
		java.util.Map<String, Object> viejo = jdbc.queryForMap("SELECT secuencia, LEFT(hash, 16) AS codigo FROM "
				+ "evento_auditoria WHERE colegio_id = 1 ORDER BY secuencia LIMIT 1");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM huella_hora WHERE colegio_id = 1", Long.class)).isPositive();
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO huella_bitacora (colegio_id, fecha, secuencia, codigo, "
				+ "eventos_del_dia, creado_en, creado_por, actualizado_en) VALUES (1, '1999-01-02', ?, ?, 0, NOW(6), "
				+ "'sistema.auditoria', NOW(6))", viejo.get("secuencia"), viejo.get("codigo")))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO huella_hora (colegio_id, momento, secuencia, codigo, creado_en, "
				+ "creado_por, actualizado_en) VALUES (1, '1999-01-02 10:00:00', ?, ?, NOW(6), 'sistema.auditoria', NOW(6))",
				viejo.get("secuencia"), viejo.get("codigo")))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM huella_hora WHERE 1 = 0"))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("UPDATE huella_hora SET version = version WHERE 1 = 0"))).isEqualTo(1142);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sprint 6, tanda 2 (V21): foto del resumen diario comprobada contra los libros, resumen y alertas solo de
	// sistema.panel y solo a Promotoría (y Dirección), y contacto del personal con su solicitud aprobada. Además, las
	// consultas de cifras de la tanda 1 (JPQL agregado) contra MySQL 8 real.
	// ---------------------------------------------------------------------------------------------------------------

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.panel.proceso.ResumenDiarioTarea resumenDiario;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.panel.service.ResumenesDiarios resumenes;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.panel.proceso.AvisosPromotoria avisosPromotoria;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioContactoPersonal contactoPersonal;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.panel.service.PanelPromotoria panelPromotoria;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.panel.service.ReportesCobranza reportesCobranza;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.panel.service.ExportacionContador exportacionContador;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza cifrasCobranza;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.caja.service.CifrasCaja cifrasCaja;

	/** Un alumno nuevo (su apoderada con un celular sin confirmar) en la sección del escenario de caja, con su familia: [familia, alumno]. */
	private Long[] alumnoParaElPanel() {
		FamiliasCaja familias = familiasDeCaja();
		Long seccion = jdbc.queryForObject("SELECT seccion_id FROM matricula WHERE alumno_id = ?", Long.class,
				familias.hermano1());
		int anio = jdbc.queryForObject("SELECT a.anio FROM seccion s JOIN anio_escolar a ON a.id = s.anio_escolar_id "
				+ "WHERE s.id = ?", Integer.class, seccion);
		String base = String.format("%07d", Math.floorMod(System.nanoTime() + 601, 10_000_000L));
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		var nuevo = servicioAlumnos.registrar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar
				.conApoderadoNuevo("7" + base, "Salas", "Paz", "Inés", java.time.LocalDate.of(anio - 10, 5, 2), "4" + base,
						"Paz", "León", "Elena", "95" + base, null, seccion));
		SecurityContextHolder.clearContext();
		return new Long[] { nuevo.familiaId(), nuevo.alumnoId() };
	}

	/** Las cifras de los libros del colegio 1 a una fecha, directo de las tablas (como las suma el trigger de la foto). */
	private java.util.Map<String, Object> librosAl(java.time.LocalDate dia) {
		java.util.Map<String, Object> libros = new java.util.HashMap<>(jdbc.queryForMap("SELECT COALESCE(SUM(total), 0) AS "
				+ "total, COUNT(*) AS cantidad, COALESCE(SUM(CASE WHEN medio = 'EFECTIVO' THEN total ELSE 0 END), 0) AS "
				+ "efectivo, COALESCE(SUM(CASE WHEN medio = 'EFECTIVO' THEN 1 ELSE 0 END), 0) AS pagos_efectivo FROM pago "
				+ "WHERE colegio_id = 1 AND estado = 'VIGENTE' AND fecha = ?", dia));
		libros.put("mes", jdbc.queryForObject("SELECT COALESCE(SUM(total), 0) FROM pago WHERE colegio_id = 1 AND estado = "
				+ "'VIGENTE' AND fecha BETWEEN ? AND ?", java.math.BigDecimal.class, dia.withDayOfMonth(1), dia));
		libros.putAll(jdbc.queryForMap("SELECT COALESCE(SUM(c.monto - c.monto_pagado - c.monto_descuento), 0) AS deuda, "
				+ "COUNT(DISTINCT a.familia_id) AS familias FROM cuota c JOIN alumno a ON a.id = c.alumno_id WHERE "
				+ "c.colegio_id = 1 AND c.estado IN ('PENDIENTE', 'PARCIAL') AND c.fecha_vencimiento < ? AND "
				+ "(a.retirado_en IS NULL OR c.fecha_vencimiento <= a.retirado_en)", dia));
		return libros;
	}

	/**
	 * P3 y P18 con los permisos mínimos: cobro → foto (el trigger la compara AL CENTAVO con todo el libro del colegio 1,
	 * con los pagos, anulaciones, parciales y descuentos que dejaron las demás pruebas) → un mensaje PENDIENTE por cada
	 * persona de Promotoría activa, creados por sistema.panel. Una segunda corrida no hace nada (uk_resumen_diario).
	 */
	@Test
	void flujoResumenDiarioConPermisosMinimos() {
		java.time.LocalDate hoy = java.time.LocalDate.now(LIMA);
		org.junit.jupiter.api.Assumptions.assumeTrue(jdbc.queryForObject("SELECT COUNT(*) FROM resumen_diario WHERE "
				+ "colegio_id = 1 AND fecha = ?", Long.class, hoy) == 0, "la foto de hoy ya existe (otra corrida)");
		guardar("promo.resumen." + sufijo, Rol.PROMOTOR);
		huellaDiaria.horaEnColegio(1L, java.time.LocalDateTime.now(LIMA));
		Long[] alumno = alumnoParaElPanel();
		UsuariosDePrueba.iniciarSesion(cajera("caja.resumen"));
		cobrarEfectivo(alumno[0], java.util.List.of(cuotaDe(alumno[1], 3)), "450.00");
		SecurityContextHolder.clearContext();

		var foto = resumenDiario.enColegio(1L, hoy);

		assertThat(foto).isPresent();
		java.util.Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM resumen_diario WHERE id = ?",
				foto.get().getId());
		java.util.Map<String, Object> libros = librosAl(hoy);
		assertThat((java.math.BigDecimal) fila.get("cobrado_total")).isEqualByComparingTo((java.math.BigDecimal) libros
				.get("total"));
		assertThat(((Number) fila.get("pagos_cantidad")).longValue()).isEqualTo(((Number) libros.get("cantidad")).longValue());
		assertThat((java.math.BigDecimal) fila.get("cobrado_efectivo")).isEqualByComparingTo(new java.math.BigDecimal(
				libros.get("efectivo").toString()));
		assertThat((java.math.BigDecimal) fila.get("cobrado_mes")).isEqualByComparingTo((java.math.BigDecimal) libros
				.get("mes"));
		assertThat((java.math.BigDecimal) fila.get("deuda_vencida")).isEqualByComparingTo(new java.math.BigDecimal(
				libros.get("deuda").toString()));
		assertThat(((Number) fila.get("familias_morosas")).longValue()).isEqualTo(((Number) libros.get("familias"))
				.longValue());
		assertThat(fila).containsEntry("creado_por", "sistema.panel");
		long promotores = jdbc.queryForObject("SELECT COUNT(DISTINCT u.id) FROM usuario u JOIN usuario_rol r ON "
				+ "r.usuario_id = u.id WHERE u.colegio_id = 1 AND u.activo AND u.apoderado_id IS NULL AND r.rol = 'PROMOTOR' "
				+ "AND (u.telefono_whatsapp IS NOT NULL OR u.correo IS NOT NULL)", Long.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'RESUMEN_DIARIO' AND entidad_id = ? AND "
				+ "estado = 'PENDIENTE' AND creado_por = 'sistema.panel'", Long.class, foto.get().getId())).isEqualTo(promotores);
		assertThat(resumenDiario.enColegio(1L, hoy)).as("idempotente").isEmpty();
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.PROMOTOR));
		assertThat(resumenes.deFecha(hoy)).isPresent();
		assertThat(resumenes.comparar()).filteredOn(c -> c.fecha().equals(hoy)).singleElement()
				.satisfies(c -> assertThat(c.cambio()).as("recién guardada, coincide con los libros").isFalse());
	}

	/** Fase 2b: con la mensajería simulada, el resumen de hoy sale (ENVIADO) a Promotoría. */
	@Test
	void resumenDiarioSaleConLaMensajeriaSimulada() {
		org.junit.jupiter.api.Assumptions.assumeTrue(mensajeriaSimuladaHabilitada(), "falta la fila mensajeria_simulada");
		java.time.LocalDate hoy = java.time.LocalDate.now(LIMA);
		if (jdbc.queryForObject("SELECT COUNT(*) FROM resumen_diario WHERE colegio_id = 1 AND fecha = ?", Long.class,
				hoy) == 0) {
			guardar("promo.resumen2b." + sufijo, Rol.PROMOTOR);
			Long[] alumno = alumnoParaElPanel();
			UsuariosDePrueba.iniciarSesion(cajera("caja.resumen2b"));
			cobrarEfectivo(alumno[0], java.util.List.of(cuotaDe(alumno[1], 3)), "450.00");
			SecurityContextHolder.clearContext();
			resumenDiario.enColegio(1L, hoy);
		}
		Long foto = jdbc.queryForObject("SELECT id FROM resumen_diario WHERE colegio_id = 1 AND fecha = ?", Long.class, hoy);
		for (int pasada = 0; pasada < 100 && despacho.despacharColegio(1L) > 0; pasada++) {
			// de a 20, el más antiguo primero
		}
		assertThat(jdbc.queryForList("SELECT estado FROM mensaje WHERE tipo = 'RESUMEN_DIARIO' AND entidad_id = ?",
				String.class, foto)).isNotEmpty().allMatch("ENVIADO"::equals);
	}

	/** Un día pasado cualquiera de los años 50 (sin movimientos). */
	private java.time.LocalDate diaSinMovimientos() {
		return java.time.LocalDate.of(1950, 1, 1).plusDays(Math.floorMod(System.nanoTime(), 3000));
	}

	private static final String INSERTAR_FOTO = "INSERT INTO resumen_diario (colegio_id, fecha, cortado_en, cobrado_total, "
			+ "pagos_cantidad, cobrado_efectivo, pagos_efectivo, cobrado_mes, deuda_vencida, familias_morosas, "
			+ "cajas_sin_cerrar, cierres_con_diferencia, solicitudes_pendientes, alertas_criticas, avisos_familias, "
			+ "avisos_entregados, huella_secuencia, huella_codigo, parametros, creado_en, creado_por, actualizado_en) VALUES "
			+ "(1, ?, ?, ?, 0, 0, 0, ?, 0, 0, 0, 0, 0, 0, 0, 0, ?, ?, ?, NOW(6), ?, NOW(6))";

	private int foto(java.time.LocalDate dia, java.time.LocalDateTime corte, String cobrado, Long huella, String codigo,
			String actor) {
		return jdbc.update(INSERTAR_FOTO, dia, corte, new java.math.BigDecimal(cobrado), new java.math.BigDecimal(cobrado),
				huella, codigo, "x", actor);
	}

	/**
	 * La foto de hoy del colegio 1: si aún no existe, la guarda sistema.panel después de un cobro (así también sale un
	 * domingo o un feriado). Su id, o vacío si no se pudo.
	 */
	private java.util.Optional<Long> fotoDeHoy() {
		java.time.LocalDate hoy = java.time.LocalDate.now(LIMA);
		if (jdbc.queryForObject("SELECT COUNT(*) FROM resumen_diario WHERE colegio_id = 1 AND fecha = ?", Long.class,
				hoy) == 0) {
			guardar("promo.foto." + sufijo, Rol.PROMOTOR);
			familiaQuePagoEnEfectivo("caja.foto");
			resumenDiario.enColegio(1L, hoy);
		}
		return java.util.Optional.ofNullable(jdbc.queryForObject("SELECT MAX(id) FROM resumen_diario WHERE colegio_id = 1 "
				+ "AND fecha = ?", Long.class, hoy));
	}

	/**
	 * P3, P5, P18 y P21 con las correcciones del sprint 6 (S6-M1): la base solo acepta la foto de HOY, cortada AHORA, con
	 * las cifras y los conteos de los libros, de sistema.panel, con una huella guardada y con el texto de sus cifras; y
	 * nadie la edita ni la borra (1142). La foto válida la guarda el flujo con los permisos mínimos.
	 */
	@Test
	void laFotoDelResumenSoloSeAceptaConLasCifrasDeLosLibros() {
		java.time.LocalDate dia = diaSinMovimientos();
		java.time.LocalDate hoy = java.time.LocalDate.now(LIMA);
		java.time.LocalDateTime ahora = java.time.LocalDateTime.now(LIMA);
		assertThat(codigoAl(() -> foto(dia, dia.atTime(19, 30), "0.00", null, null, "sistema.panel")))
				.as("un día pasado, aunque todo esté en cero").isEqualTo(1644);
		assertThat(codigoAl(() -> foto(hoy, ahora, "0.00", null, null, "caja"))).as("otro actor").isEqualTo(1644);
		assertThat(codigoAl(() -> foto(hoy, ahora.minusHours(2), "0.00", null, null, "sistema.panel")))
				.as("un corte que no es de ahora").isEqualTo(1644);
		assertThat(codigoAl(() -> foto(hoy, ahora, "123456.00", null, null, "sistema.panel"))).as("cifras inventadas")
				.isEqualTo(1644);
		assertThat(codigoAl(() -> foto(hoy, ahora, "0.00", 999_999_999L, "0123456789abcdef", "sistema.panel")))
				.as("huella inventada (o, antes, las cifras de hoy)").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE resumen_diario SET cobrado_total = 1 WHERE fecha = ?", dia)))
				.isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM resumen_diario WHERE fecha = ?", dia))).isEqualTo(1142);
	}

	/**
	 * P7 y P21: el resumen y las alertas los crea solo sistema.panel y van solo a Promotoría (y Dirección, las alertas).
	 * Correcciones del sprint 6: el resumen lleva el texto exacto de su foto (S6-M1) y el correo externo es el de la fila
	 * de configuracion_colegio de ESE colegio (QA-S6-6).
	 */
	@Test
	void elResumenYLasAlertasSoloVanAPromotoria() {
		java.util.Optional<Long> deHoy = fotoDeHoy();
		org.junit.jupiter.api.Assumptions.assumeTrue(deHoy.isPresent(), "hoy no corresponde resumen (domingo o feriado)");
		Long foto = deHoy.get();
		String texto = jdbc.queryForObject("SELECT parametros FROM resumen_diario WHERE id = ?", String.class, foto);
		Usuario cajera = guardar("caja.p7." + sufijo, Rol.CAJA);
		Usuario promotora = guardar("promo.p7." + sufijo, Rol.PROMOTOR);
		Usuario directora = guardar("dir.p7." + sufijo, Rol.DIRECTOR);
		String insertar = "INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, usuario_id, destino, "
				+ "plantilla, parametros, entidad, entidad_id, estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, "
				+ "'WHATSAPP', 'USUARIO', ?, ?, ?, ?, ?, ?, 'PENDIENTE', NOW(6), ?, NOW(6))";
		assertThat(codigoAl(() -> jdbc.update(insertar, "p7a-" + sufijo, "RESUMEN_DIARIO", cajera.getId(),
				cajera.getTelefonoWhatsapp(), "RESUMEN_DIARIO", texto, "resumen_diario", foto, "sistema.panel")))
				.as("resumen a una cajera").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(insertar, "p7b-" + sufijo, "ALERTA_PROMOTORIA", cajera.getId(),
				cajera.getTelefonoWhatsapp(), "ALERTA_PROMOTORIA", "x", "aviso", null, "sistema.panel")))
				.as("alerta a una cajera").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(insertar, "p7c-" + sufijo, "ALERTA_PROMOTORIA", promotora.getId(),
				promotora.getTelefonoWhatsapp(), "ALERTA_PROMOTORIA", "x", "aviso", null, "caja")))
				.as("alerta que no crea sistema.panel").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(insertar, "p7d-" + sufijo, "RESUMEN_DIARIO", promotora.getId(),
				promotora.getTelefonoWhatsapp(), "RESUMEN_DIARIO", texto, "resumen_diario", 0L, "sistema.panel")))
				.as("resumen sin su foto").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(insertar, "p7e-" + sufijo, "RESUMEN_DIARIO", directora.getId(),
				directora.getTelefonoWhatsapp(), "RESUMEN_DIARIO", texto, "resumen_diario", foto, "sistema.panel")))
				.as("resumen a Dirección").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(insertar, "p7i-" + sufijo, "RESUMEN_DIARIO", promotora.getId(),
				promotora.getTelefonoWhatsapp(), "RESUMEN_DIARIO", texto.replaceFirst("S/ ", "S/ 9"), "resumen_diario", foto,
				"sistema.panel"))).as("S6-M1: con otro texto que el de su foto").isEqualTo(1644);
		String externo = "INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, destino, plantilla, "
				+ "parametros, entidad, entidad_id, estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, "
				+ "'RESUMEN_DIARIO', 'CORREO', 'EXTERNO', ?, 'RESUMEN_DIARIO', ?, 'resumen_diario', ?, 'PENDIENTE', NOW(6), "
				+ "'sistema.panel', NOW(6))";
		assertThat(codigoAl(() -> jdbc.update(externo, "p7f-" + sufijo, "otro@contador.pe", texto, foto)))
				.as("correo externo sin la fila del DBA").isEqualTo(1644);
		String claveMigrador = System.getenv("CC_MYSQL_CLAVE_MIGRADOR");
		if (claveMigrador != null && !claveMigrador.isBlank()) {
			// QA-S6-6: la fila del DBA es de un colegio; la de otro colegio (o la vieja de configuracion_bd) no sirve.
			JdbcTemplate migrador = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
					System.getenv().getOrDefault("CC_MYSQL_URL",
							"jdbc:mysql://127.0.0.1:3306/cuentasclaras?allowPublicKeyRetrieval=true&useSSL=false"),
					"cc_migrador", claveMigrador));
			String correo = "contador." + sufijo + "@estudio.pe";
			Long otroColegio = migrador.queryForObject("SELECT MAX(id) FROM colegio", Long.class);
			if (otroColegio != null && otroColegio != 1L) {
				migrador.update("INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en) VALUES (?, "
						+ "'resumen_correo_externo', ?, NOW(6))", otroColegio, correo);
				assertThat(codigoAl(() -> jdbc.update(externo, "p7j-" + sufijo, correo, texto, foto)))
						.as("QA-S6-6: el correo del contador de OTRO colegio").isEqualTo(1644);
				migrador.update("DELETE FROM configuracion_colegio WHERE colegio_id = ? AND valor = ?", otroColegio, correo);
			}
		}
		// Lo que sí: sistema.panel a Promotoría (el resumen con su texto y la alerta) y a Dirección (la alerta).
		assertThat(jdbc.update(insertar, "p7g-" + sufijo, "RESUMEN_DIARIO", promotora.getId(),
				promotora.getTelefonoWhatsapp(), "RESUMEN_DIARIO", texto, "resumen_diario", foto, "sistema.panel"))
				.isEqualTo(1);
		assertThat(jdbc.update(insertar, "p7h-" + sufijo, "ALERTA_PROMOTORIA", directora.getId(),
				directora.getTelefonoWhatsapp(), "ALERTA_PROMOTORIA", "x", "aviso", null, "sistema.panel")).isEqualTo(1);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en) "
				+ "VALUES (1, 'resumen_correo_externo', 'x@y.pe', NOW(6))"))).as("cc_app no escribe la fila del DBA")
				.isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("UPDATE configuracion_colegio SET valor = valor WHERE 1 = 0")))
				.isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM configuracion_colegio WHERE 1 = 0"))).isEqualTo(1142);
	}

	/**
	 * Decisiones 70 y 71 con los permisos mínimos: sistema.panel recorre las alertas de todos los módulos del colegio 1
	 * (con lo que dejaron las demás pruebas) y avisa la anulación por aprobar a Promotoría y Dirección, una sola vez.
	 */
	@Test
	void flujoAvisosPromotoriaConPermisosMinimos() {
		Usuario promotora = guardar("promo.avisos." + sufijo, Rol.PROMOTOR);
		Usuario directora = guardar("dir.avisos." + sufijo, Rol.DIRECTOR);
		Long[] alumno = alumnoParaElPanel();
		UsuariosDePrueba.iniciarSesion(cajera("caja.avisos"));
		Long pago = cobrarEfectivo(alumno[0], java.util.List.of(cuotaDe(alumno[1], 4)), "450.00");
		anulacionesPago.solicitarDevolucion(pago, "Se cobró dos veces la misma pensión en ventanilla");
		SecurityContextHolder.clearContext();
		Long solicitud = jdbc.queryForObject("SELECT MAX(id) FROM solicitud_cambio WHERE colegio_id = 1 AND tipo = "
				+ "'ANULACION_PAGO' AND estado = 'PENDIENTE'", Long.class);
		// Un lunes: los avisos no salen en domingo ni feriado.
		java.time.LocalDate lunes = java.time.LocalDate.now(LIMA).with(java.time.temporal.TemporalAdjusters.next(
				java.time.DayOfWeek.MONDAY));
		while (pe.edu.virgenmaria.cuentasclaras.comun.fecha.FeriadosNacionales.es(lunes)) {
			lunes = lunes.plusWeeks(1);
		}
		avisosPromotoria.enColegio(1L, lunes);
		avisosPromotoria.enColegio(1L, lunes);

		// Correcciones del sprint 6 (S6-B2): las anulaciones pendientes son UN aviso por día (S:<fecha>).
		assertThat(solicitud).isNotNull();
		String clave = "ALERTA:ANULACION_PAGO_PENDIENTE:S:" + java.time.LocalDate.now(LIMA) + ":U%";
		assertThat(jdbc.queryForList("SELECT usuario_id FROM mensaje WHERE clave LIKE ? AND creado_por = 'sistema.panel'",
				Long.class, clave)).contains(promotora.getId(), directora.getId()).doesNotHaveDuplicates();
	}

	/** P6: el celular o el correo de alguien del personal no cambian por SQL sin SU solicitud aprobada. */
	@Test
	void cambiarElCelularDeLaPromotoraSinSolicitudFallaCon1644() {
		Usuario promotora = guardar("promo.p6." + sufijo, Rol.PROMOTOR);
		assertThat(codigoAl(() -> jdbc.update("UPDATE usuario SET telefono_whatsapp = '+51900000001' WHERE id = ?",
				promotora.getId()))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE usuario SET correo = 'otro@correo.pe' WHERE id = ?",
				promotora.getId()))).isEqualTo(1644);
		Long otraAprobada = jdbc.queryForObject("SELECT MIN(id) FROM solicitud_cambio WHERE colegio_id = 1 AND estado = "
				+ "'APROBADA'", Long.class);
		if (otraAprobada != null) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE usuario SET telefono_whatsapp = '+51900000001', "
					+ "contacto_solicitud_id = ? WHERE id = ?", otraAprobada, promotora.getId())))
					.as("una solicitud aprobada que no es la suya").isEqualTo(1644);
		}
		// Lo demás de la cuenta (el ingreso, el bloqueo) sí cambia.
		assertThat(jdbc.update("UPDATE usuario SET intentos_fallidos = 0 WHERE id = ?", promotora.getId())).isEqualTo(1);
	}

	/**
	 * P6 con los permisos mínimos: el titular pide, OTRA persona aprueba, el cambio pasa el trigger con su solicitud y el
	 * aviso al contacto ANTERIOR pasa trg_mensaje_nace (solo a ese contacto). La solicitud no se reusa.
	 */
	@Test
	void flujoCambioContactoPersonalConPermisosMinimos() {
		Usuario lucia = guardar("lucia.p6." + sufijo, Rol.CAJA);
		Usuario directora = guardar("dir.p6." + sufijo, Rol.DIRECTOR);
		Usuario otra = guardar("otra.p6." + sufijo, Rol.CAJA);
		String anterior = lucia.getTelefonoWhatsapp();
		String nuevo = "9" + String.format("%08d", Math.floorMod(System.nanoTime(), 100_000_000L));
		UsuariosDePrueba.iniciarSesion(lucia);
		Long solicitud = contactoPersonal.solicitar(lucia.getId(), nuevo, null, "Cambié de número de celular este mes");
		UsuariosDePrueba.iniciarSesion(directora);
		bandeja.aprobar(solicitud, null);
		SecurityContextHolder.clearContext();

		assertThat(jdbc.queryForMap("SELECT telefono_whatsapp, contacto_solicitud_id FROM usuario WHERE id = ?",
				lucia.getId())).containsEntry("telefono_whatsapp", "+51" + nuevo)
				.containsEntry("contacto_solicitud_id", solicitud);
		assertThat(jdbc.queryForMap("SELECT destino, plantilla, estado FROM mensaje WHERE tipo = 'CONTACTO_CAMBIADO' AND "
				+ "usuario_id = ?", lucia.getId())).containsEntry("destino", anterior)
				.containsEntry("plantilla", "CONTACTO_PERSONAL_CAMBIADO").containsEntry("estado", "PENDIENTE");
		// La misma solicitud no sirve para otro cambio ni para otra persona.
		assertThat(codigoAl(() -> jdbc.update("UPDATE usuario SET correo = 'lucia@otro.pe' WHERE id = ?", lucia.getId())))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE usuario SET telefono_whatsapp = '+51911111111', "
				+ "contacto_solicitud_id = ? WHERE id = ?", solicitud, otra.getId()))).isEqualTo(1644);
		// El «aviso al contacto anterior» no sirve para escribir a otro número.
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, "
				+ "usuario_id, destino, plantilla, parametros, entidad, entidad_id, estado, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, ?, 'CONTACTO_CAMBIADO', 'WHATSAPP', 'USUARIO', ?, '+51922222222', "
				+ "'CONTACTO_PERSONAL_CAMBIADO', 'x', 'solicitud_cambio', ?, 'PENDIENTE', NOW(6), 'x', NOW(6))",
				"p6c-" + sufijo, lucia.getId(), solicitud))).isEqualTo(1644);
	}

	/**
	 * Tanda 1 en MySQL 8 real (no se había probado): las cifras del panel, los reportes en pantalla y los dos Excel con
	 * JPQL agregado, comparadas con las sumas directas del libro del colegio 1.
	 */
	@Test
	void lasCifrasYLosReportesDeLaTanda1FuncionanEnMySql() {
		java.time.LocalDate hoy = java.time.LocalDate.now(LIMA);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(1L, 4101L, "promo.cifras." + sufijo, "Promotora",
				false, EnumSet.of(Rol.PROMOTOR)));
		var panel = panelPromotoria.ver();
		java.util.Map<String, Object> libros = librosAl(hoy);
		assertThat(panel.hoy().cobrado()).isEqualTo(pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero.formatear(
				(java.math.BigDecimal) libros.get("total")));
		assertThat(panel.hoy().pagos()).isEqualTo(((Number) libros.get("cantidad")).longValue());
		assertThat(cifrasCobranza.deudaVencida(hoy).monto()).isEqualByComparingTo(new java.math.BigDecimal(
				libros.get("deuda").toString()));
		assertThat(cifrasCobranza.deudaVencida(hoy).familias()).isEqualTo(((Number) libros.get("familias")).longValue());
		assertThat(cifrasCaja.cobrado(hoy.withDayOfMonth(1), hoy).total()).isEqualByComparingTo((java.math.BigDecimal)
				libros.get("mes"));
		assertThat(cifrasCaja.cobradoPorDia(hoy.withDayOfMonth(1), hoy).values().stream()
				.map(pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoDia::total)
				.reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
				.isEqualByComparingTo((java.math.BigDecimal) libros.get("mes"));
		assertThat(cifrasCaja.cambiosPosteriores(hoy.withDayOfMonth(1), hoy, hoy.atStartOfDay()).registrados())
				.isNotNull();
		assertThat(reportesCobranza.familiasMorosas()).hasSize(((Number) libros.get("familias")).intValue());
		assertThat(reportesCobranza.ingresosPorMedio(null, null).total()).isEqualTo(pe.edu.virgenmaria.cuentasclaras.comun
				.dinero.Dinero.formatear((java.math.BigDecimal) libros.get("mes")));
		var morosidad = reportesCobranza.morosidadPorGrado(null);
		assertThat(morosidad.filas()).isNotNull();
		var ingresos = exportacionContador.exportarIngresos(hoy.withDayOfMonth(1), hoy);
		assertThat(ingresos.contenido()).isNotEmpty();
		var excelMorosidad = exportacionContador.exportarMorosidad(null);
		assertThat(excelMorosidad.contenido()).isNotEmpty();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'REPORTE_EXPORTADO' AND "
				+ "nombre_usuario = ?", Long.class, "promo.cifras." + sufijo)).isEqualTo(2);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sprint 6, tanda 3 (V22): llamada de control (decisión 77, P17). trg_llamada_control_registro calcula «hoy en Lima»
	// con DATE(UTC_TIMESTAMP() - INTERVAL 5 HOUR); la prueba usa LocalDate.now(LIMA).

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.panel.service.LlamadasControl llamadasControl;

	private static final String INSERTAR_LLAMADA = "INSERT INTO llamada_control (colegio_id, semana, familia_id, "
			+ "resultado, nota, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, ?, ?, NOW(6), ?, NOW(6))";

	private static java.time.LocalDate lunesDeEstaSemana() {
		return java.time.LocalDate.now(LIMA).with(java.time.temporal.TemporalAdjusters.previousOrSame(
				java.time.DayOfWeek.MONDAY));
	}

	/** Una familia nueva que pagó hoy en efectivo (en la ventanilla, con los permisos mínimos): su id. */
	private Long familiaQuePagoEnEfectivo(String caja) {
		Long[] alumno = alumnoParaElPanel();
		UsuariosDePrueba.iniciarSesion(cajera(caja));
		cobrarEfectivo(alumno[0], java.util.List.of(cuotaDe(alumno[1], 3)), "450.00");
		SecurityContextHolder.clearContext();
		return alumno[0];
	}

	private int llamada(java.time.LocalDate semana, Long familia, String resultado, String nota, String quien) {
		return jdbc.update(INSERTAR_LLAMADA, semana, familia, resultado, nota, quien);
	}

	/**
	 * Correcciones del sprint 6 (S6-B3): si la muestra de esta semana aún no está congelada, la congela el SERVICIO (con su
	 * semilla y los permisos mínimos: así trg_muestra_llamada_registro prueba las filas que elige la aplicación). Cada
	 * prueba que agrega familias a la muestra por SQL lo llama ANTES de crear sus familias.
	 */
	private void congelarConElServicio() {
		if (jdbc.queryForObject("SELECT COUNT(*) FROM muestra_llamada WHERE colegio_id = 1 AND semana = ?", Long.class,
				lunesDeEstaSemana()) > 0) {
			return;
		}
		// Una familia nueva con cuotas vencidas: hay al menos una candidata (DEUDA) aunque nadie haya pagado antes del lunes.
		alumnoParaElPanel();
		UsuariosDePrueba.iniciarSesion(guardar("promo.congela." + sufijo, Rol.PROMOTOR));
		try {
			llamadasControl.deEstaSemana();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	/** Correcciones del sprint 6: la familia entra a la muestra congelada de esta semana (con los permisos de cc_app). */
	private int enLaMuestra(Long familia, String motivo, String quien) {
		if (jdbc.queryForObject("SELECT COUNT(*) FROM muestra_llamada WHERE colegio_id = 1 AND semana = ? AND familia_id = ?",
				Long.class, lunesDeEstaSemana(), familia) > 0) {
			return 1;
		}
		return jdbc.update("INSERT INTO muestra_llamada (colegio_id, semana, familia_id, motivo, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, ?, ?, ?, NOW(6), ?, NOW(6))", lunesDeEstaSemana(), familia, motivo, quien);
	}

	private int llamadaIntento(Long familia, String resultado, int intento, boolean porDelegacion, String quien,
			java.time.LocalDateTime creadoEn) {
		return jdbc.update("INSERT INTO llamada_control (colegio_id, semana, familia_id, resultado, intento, por_delegacion, "
				+ "creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, ?, ?, ?, ?, ?, NOW(6))", lunesDeEstaSemana(), familia,
				resultado, intento, porDelegacion, creadoEn, quien);
	}

	/**
	 * Correcciones del sprint 6 (S6-A2 y S6-B3): trg_muestra_llamada_registro. La muestra la fija Promotoría, Dirección o
	 * sistema.panel (no Caja), para la semana en curso, con familias candidatas según su motivo (EFECTIVO: pagó en
	 * efectivo; DEUDA: tiene deuda vencida) y un reemplazo solo de quien no contestó dos veces. Solo inserción (1142).
	 */
	@Test
	void laMuestraCongeladaSoloAceptaCandidatasDeLaSemana() {
		congelarConElServicio();
		Long familia = familiaQuePagoEnEfectivo("caja.mu1");
		Long sinEfectivo = alumnoParaElPanel()[0];
		Usuario promotora = guardar("promo.mu1." + sufijo, Rol.PROMOTOR);
		Usuario caja = guardar("caja.mu1b." + sufijo, Rol.CAJA);
		assertThat(codigoAl(() -> enLaMuestra(familia, "EFECTIVO", caja.getNombreUsuario()))).as("Caja").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO muestra_llamada (colegio_id, semana, familia_id, motivo, "
				+ "creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, 'EFECTIVO', NOW(6), ?, NOW(6))",
				lunesDeEstaSemana().minusWeeks(1), familia, promotora.getNombreUsuario()))).as("otra semana").isEqualTo(1644);
		assertThat(codigoAl(() -> enLaMuestra(sinEfectivo, "EFECTIVO", promotora.getNombreUsuario())))
				.as("EFECTIVO de una familia que no pagó en efectivo").isEqualTo(1644);
		assertThat(enLaMuestra(familia, "EFECTIVO", promotora.getNombreUsuario())).isEqualTo(1);
		Long otra = familiaQuePagoEnEfectivo("caja.mu1c");
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO muestra_llamada (colegio_id, semana, familia_id, motivo, "
				+ "reemplaza_familia_id, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, 'REEMPLAZO', ?, NOW(6), ?, "
				+ "NOW(6))", lunesDeEstaSemana(), otra, familia, promotora.getNombreUsuario())))
				.as("reemplazo de una familia que no dejó de contestar dos veces").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE muestra_llamada SET motivo = 'DEUDA' WHERE familia_id = ?", familia)))
				.isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM muestra_llamada WHERE familia_id = ?", familia))).isEqualTo(1142);
	}

	/**
	 * Correcciones del sprint 6 (S6-M2): trg_delegacion_llamada_registro y trg_llamada_control_registro. Dirección no
	 * registra sin la semana delegada; la delegación la hace solo Promotoría, para la semana en curso; con ella, Dirección
	 * registra marcando por_delegacion (y Promotoría, sin marcarlo); el segundo intento solo tras un «No contesta», y a una
	 * familia reemplazada ya no se la registra.
	 */
	@Test
	void direccionRegistraSoloConLaSemanaDelegadaYElSegundoIntentoTrasNoContesta() {
		congelarConElServicio();
		Long familia = familiaQuePagoEnEfectivo("caja.de1");
		Long otra = familiaQuePagoEnEfectivo("caja.de2");
		Usuario promotora = guardar("promo.de1." + sufijo, Rol.PROMOTOR);
		Usuario directora = guardar("dir.de1." + sufijo, Rol.DIRECTOR);
		java.time.LocalDateTime ahora = java.time.LocalDateTime.now(LIMA);
		enLaMuestra(familia, "EFECTIVO", promotora.getNombreUsuario());
		enLaMuestra(otra, "EFECTIVO", promotora.getNombreUsuario());
		boolean yaDelegada = jdbc.queryForObject("SELECT COUNT(*) FROM delegacion_llamada WHERE colegio_id = 1 AND semana = ?",
				Long.class, lunesDeEstaSemana()) > 0;
		if (!yaDelegada) {
			assertThat(codigoAl(() -> llamadaIntento(familia, "CONFIRMA", 1, true, directora.getNombreUsuario(), ahora)))
					.as("Dirección sin la semana delegada").isEqualTo(1644);
		}
		String delegar = "INSERT INTO delegacion_llamada (colegio_id, semana, creado_en, creado_por, actualizado_en) VALUES "
				+ "(1, ?, NOW(6), ?, NOW(6))";
		assertThat(codigoAl(() -> jdbc.update(delegar, lunesDeEstaSemana(), directora.getNombreUsuario())))
				.as("Dirección no se delega sola").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(delegar, lunesDeEstaSemana().minusWeeks(1), promotora.getNombreUsuario())))
				.as("otra semana").isEqualTo(1644);
		if (!yaDelegada) {
			assertThat(jdbc.update(delegar, lunesDeEstaSemana(), promotora.getNombreUsuario())).isEqualTo(1);
		}
		assertThat(codigoAl(() -> jdbc.update(delegar, lunesDeEstaSemana(), promotora.getNombreUsuario())))
				.as("una por semana").isEqualTo(1062);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM delegacion_llamada WHERE 1 = 0"))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("UPDATE delegacion_llamada SET version = version WHERE 1 = 0")))
				.isEqualTo(1142);

		assertThat(codigoAl(() -> llamadaIntento(familia, "NO_CONTESTA", 1, false, directora.getNombreUsuario(), ahora)))
				.as("Dirección sin marcar por_delegacion").isEqualTo(1644);
		assertThat(codigoAl(() -> llamadaIntento(familia, "NO_CONTESTA", 1, true, promotora.getNombreUsuario(), ahora)))
				.as("Promotoría marcando por_delegacion").isEqualTo(1644);
		assertThat(codigoAl(() -> llamadaIntento(familia, "CONFIRMA", 2, false, promotora.getNombreUsuario(), ahora)))
				.as("segundo intento sin un primero").isEqualTo(1644);
		assertThat(llamadaIntento(familia, "CONFIRMA", 1, true, directora.getNombreUsuario(), ahora)).isEqualTo(1);
		assertThat(codigoAl(() -> llamadaIntento(familia, "NO_CONTESTA", 2, false, promotora.getNombreUsuario(), ahora)))
				.as("segundo intento tras un «Confirma»").isEqualTo(1644);
		assertThat(llamadaIntento(otra, "NO_CONTESTA", 1, false, promotora.getNombreUsuario(), ahora)).isEqualTo(1);
		assertThat(llamadaIntento(otra, "NO_CONTESTA", 2, false, promotora.getNombreUsuario(), ahora)).isEqualTo(1);
		assertThat(codigoAl(() -> llamadaIntento(otra, "CONFIRMA", 3, false, promotora.getNombreUsuario(), ahora)))
				.as("un tercer intento").isEqualTo(1644);
	}

	/**
	 * Correcciones del sprint 6 (S6-M2) con los permisos mínimos: el primer «No contesta» (de hace dos horas), el segundo
	 * por el servicio y el reemplazo que elige el servicio con la semilla pasan los triggers (muestra con REEMPLAZO y
	 * llamada); la familia reemplazada ya no se registra.
	 */
	@Test
	void flujoReemplazoConPermisosMinimos() {
		congelarConElServicio();
		Long familia = familiaQuePagoEnEfectivo("caja.re1");
		familiaQuePagoEnEfectivo("caja.re2");
		Usuario promotora = guardar("promo.re1." + sufijo, Rol.PROMOTOR);
		UsuariosDePrueba.iniciarSesion(promotora);
		llamadasControl.deEstaSemana();
		SecurityContextHolder.clearContext();
		enLaMuestra(familia, "EFECTIVO", promotora.getNombreUsuario());
		assertThat(llamadaIntento(familia, "NO_CONTESTA", 1, false, promotora.getNombreUsuario(),
				java.time.LocalDateTime.now(LIMA).minusHours(2))).isEqualTo(1);
		UsuariosDePrueba.iniciarSesion(promotora);
		llamadasControl.registrar(familia, new pe.edu.virgenmaria.cuentasclaras.panel.dto.LlamadaRequest(
				pe.edu.virgenmaria.cuentasclaras.panel.model.ResultadoLlamada.NO_CONTESTA, null));
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM muestra_llamada WHERE colegio_id = 1 AND semana = ? AND "
				+ "motivo = 'REEMPLAZO' AND reemplaza_familia_id = ?", Long.class, lunesDeEstaSemana(), familia))
				.as("el servicio eligió otra con la semilla").isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'LLAMADA_CONTROL_REEMPLAZADA' "
				+ "AND entidad_id = ?", Long.class, familia.toString())).isEqualTo(1);
		assertThat(codigoAl(() -> llamadaIntento(familia, "CONFIRMA", 1, false, promotora.getNombreUsuario(),
				java.time.LocalDateTime.now(LIMA)))).as("a la reemplazada ya no").isIn(1644, 1062);
	}

	/** P17: Administración o Caja no registran llamadas de control (aunque la familia pagó en efectivo). */
	@Test
	void llamadaPorAdministracionFallaCon1644() {
		Long familia = familiaQuePagoEnEfectivo("caja.lc1");
		Usuario administracion = guardar("adm.lc." + sufijo, Rol.ADMINISTRACION);
		Usuario caja = guardar("caja.lc." + sufijo, Rol.CAJA);
		assertThat(codigoAl(() -> llamada(lunesDeEstaSemana(), familia, "CONFIRMA", null,
				administracion.getNombreUsuario()))).as("Administración").isEqualTo(1644);
		assertThat(codigoAl(() -> llamada(lunesDeEstaSemana(), familia, "CONFIRMA", null, caja.getNombreUsuario())))
				.as("Caja").isEqualTo(1644);
		assertThat(codigoAl(() -> llamada(lunesDeEstaSemana(), familia, "CONFIRMA", null, "nadie." + sufijo)))
				.as("alguien que no existe").isEqualTo(1644);
	}

	/** P17: la familia de la llamada pagó en efectivo; una que no pagó (o solo por Yape) no se «confirma». */
	@Test
	void llamadaAFamiliaSinEfectivoFallaCon1644() {
		Usuario promotora = guardar("promo.lc2." + sufijo, Rol.PROMOTOR);
		Long[] sinPagos = alumnoParaElPanel();
		assertThat(codigoAl(() -> llamada(lunesDeEstaSemana(), sinPagos[0], "CONFIRMA", null,
				promotora.getNombreUsuario()))).isEqualTo(1644);
	}

	/** La semana es el lunes de la semana EN CURSO en Lima: ni una pasada, ni una futura, ni otro día. */
	@Test
	void llamadaDeOtraSemanaFallaCon1644() {
		Long familia = familiaQuePagoEnEfectivo("caja.lc3");
		Usuario promotora = guardar("promo.lc3." + sufijo, Rol.PROMOTOR);
		java.time.LocalDate lunes = lunesDeEstaSemana();
		for (java.time.LocalDate semana : java.util.List.of(lunes.minusWeeks(1), lunes.plusWeeks(1), lunes.plusDays(1))) {
			assertThat(codigoAl(() -> llamada(semana, familia, "CONFIRMA", null, promotora.getNombreUsuario())))
					.as(semana.toString()).isEqualTo(1644);
		}
	}

	/**
	 * Solo inserción: ni la corrige quien la hizo (1142); una por familia, semana e intento (1062); «No confirma» lleva
	 * nota (3819). La familia está en la muestra congelada de la semana (correcciones del sprint 6).
	 */
	@Test
	void llamadaNoSeEditaNiSeBorra() {
		congelarConElServicio();
		Long familia = familiaQuePagoEnEfectivo("caja.lc4");
		Usuario promotora = guardar("promo.lc4." + sufijo, Rol.PROMOTOR);
		assertThat(enLaMuestra(familia, "EFECTIVO", promotora.getNombreUsuario())).isEqualTo(1);
		assertThat(codigoAl(() -> llamada(lunesDeEstaSemana(), familia, "NO_CONFIRMA", null,
				promotora.getNombreUsuario()))).as("«No confirma» sin nota").isEqualTo(3819);
		assertThat(llamada(lunesDeEstaSemana(), familia, "NO_CONFIRMA", "Dice que pagó 500 soles, no 450",
				promotora.getNombreUsuario())).isEqualTo(1);
		assertThat(codigoAl(() -> llamada(lunesDeEstaSemana(), familia, "CONFIRMA", null, promotora.getNombreUsuario())))
				.as("otra vez la misma familia").isEqualTo(1062);
		assertThat(codigoAl(() -> jdbc.update("UPDATE llamada_control SET resultado = 'CONFIRMA' WHERE familia_id = ?",
				familia))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM llamada_control WHERE familia_id = ?", familia)))
				.isEqualTo(1142);
	}

	/**
	 * P17 con los permisos mínimos: la muestra de esta semana (semilla LLAMADA_CONTROL guardada con el CHECK de V22), los
	 * pagos de la familia y el registro por el servicio pasan el trigger; la bitácora queda con el resultado.
	 */
	@Test
	void flujoLlamadaControlConPermisosMinimos() {
		familiaQuePagoEnEfectivo("caja.lc5");
		Usuario promotora = guardar("promo.lc5." + sufijo, Rol.PROMOTOR);
		UsuariosDePrueba.iniciarSesion(promotora);
		var semana = llamadasControl.deEstaSemana();
		assertThat(semana.familias()).isNotEmpty();
		// Correcciones del sprint 6 (S6-B3): la muestra queda congelada en muestra_llamada; la fija SIEMPRE el servicio con
		// la semilla LLAMADA_CONTROL (las pruebas que agregan familias por SQL la congelan antes con congelarConElServicio).
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM muestra_llamada WHERE colegio_id = 1 AND semana = ?",
				Long.class, lunesDeEstaSemana())).isPositive();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM semilla_muestreo WHERE colegio_id = 1 AND ambito = "
				+ "'LLAMADA_CONTROL' AND fecha = ?", Long.class, lunesDeEstaSemana())).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE colegio_id = 1 AND accion = "
				+ "'MUESTRA_LLAMADAS_FIJADA'", Long.class)).as("la congeló el servicio (trigger con las filas de la aplicación)")
				.isPositive();
		var pendiente = semana.familias().stream().filter(f -> f.pendiente()).findFirst();
		org.junit.jupiter.api.Assumptions.assumeTrue(pendiente.isPresent(), "ya se llamó a toda la muestra (otra corrida)");
		Long familia = pendiente.get().familiaId();
		assertThat(pendiente.get().pagos()).isNotEmpty();

		Long id = llamadasControl.registrar(familia, new pe.edu.virgenmaria.cuentasclaras.panel.dto.LlamadaRequest(
				pe.edu.virgenmaria.cuentasclaras.panel.model.ResultadoLlamada.CONFIRMA, null));

		assertThat(jdbc.queryForMap("SELECT semana, familia_id, resultado, creado_por FROM llamada_control WHERE id = ?",
				id)).containsEntry("semana", java.sql.Date.valueOf(lunesDeEstaSemana())).containsEntry("familia_id", familia)
				.containsEntry("resultado", "CONFIRMA").containsEntry("creado_por", promotora.getNombreUsuario());
		assertThat(jdbc.queryForObject("SELECT valor_nuevo FROM evento_auditoria WHERE accion = "
				+ "'LLAMADA_CONTROL_REGISTRADA' AND entidad_id = ?", String.class, id.toString())).isEqualTo("CONFIRMA");
		assertThat(llamadasControl.deEstaSemana().hechas()).isGreaterThanOrEqualTo(1);
		assertThatThrownBy(() -> llamadasControl.registrar(familia, new pe.edu.virgenmaria.cuentasclaras.panel.dto
				.LlamadaRequest(pe.edu.virgenmaria.cuentasclaras.panel.model.ResultadoLlamada.NO_CONTESTA, null)))
				.isInstanceOf(pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException.class);
	}

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoTecnico estadoTecnico;

	/**
	 * Sprint 7, tanda 1 (V24): el registro de respaldos lo escribe SOLO cc_respaldo (scripts/respaldo/respaldar.sh). cc_app
	 * no lo inserta, edita ni borra (1142). Con cc_respaldo: anclas que no son eventos de la bitácora, el destino simulado sin
	 * la fila del DBA y un respaldo que no se registra al terminar fallan con 1644 (trg_respaldo_registro); cc_respaldo
	 * tampoco edita ni borra (1142). Uno con anclas reales queda, y la aplicación lo lee con cc_app (Hibernate valida V24).
	 */
	@Test
	void elRespaldoLoRegistraSoloCcRespaldoConAnclasReales() {
		String claveRespaldo = System.getenv("CC_MYSQL_CLAVE_RESPALDO");
		org.junit.jupiter.api.Assumptions.assumeTrue(claveRespaldo != null && !claveRespaldo.isBlank(),
				"Falta CC_MYSQL_CLAVE_RESPALDO");
		JdbcTemplate respaldo = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
				System.getenv().getOrDefault("CC_MYSQL_URL",
						"jdbc:mysql://127.0.0.1:3306/cuentasclaras?allowPublicKeyRetrieval=true&useSSL=false"),
				"cc_respaldo", claveRespaldo));
		java.util.Map<String, Object> cadena = jdbc.queryForMap(
				"SELECT ultima_secuencia, ultimo_hash FROM auditoria_cadena WHERE id = 1");
		long secuencia = ((Number) cadena.get("ultima_secuencia")).longValue();
		String hash = (String) cadena.get("ultimo_hash");
		String sello = respaldo.queryForObject("SELECT DATE_FORMAT(UTC_TIMESTAMP() - INTERVAL 5 HOUR, '%Y%m%d-%H%i%s')",
				String.class);
		String archivo = "cc-" + sello + ".sql.gz.age";

		for (String sentencia : new String[] { registroRespaldo("cc-20000101-000000.sql.gz.age", "UTC_TIMESTAMP(6) - "
				+ "INTERVAL 5 HOUR", secuencia, hash, "verificacion-ci"), "UPDATE respaldo SET bytes = bytes WHERE 1 = 0",
				"DELETE FROM respaldo WHERE 1 = 0" }) {
			assertThatThrownBy(() -> jdbc.update(sentencia)).as(sentencia)
					.satisfies(e -> assertThat(codigoMySql(e)).isEqualTo(1142));
		}
		assertThatThrownBy(() -> respaldo.update(registroRespaldo(archivo, "UTC_TIMESTAMP(6) - INTERVAL 5 HOUR",
				secuencia + 1000, hash, "verificacion-ci"))).as("anclas que no son de la bitácora")
				.satisfies(e -> assertThat(codigoMySql(e)).isEqualTo(1644));
		assertThatThrownBy(() -> respaldo.update(registroRespaldo(archivo, "'2000-01-01 00:00:00'", secuencia, hash,
				"verificacion-ci"))).as("no se registró al terminar").satisfies(e -> assertThat(codigoMySql(e)).isEqualTo(1644));
		if (jdbc.queryForObject("SELECT COUNT(*) FROM configuracion_bd WHERE clave = 'respaldo_simulado'", Long.class) == 0) {
			assertThatThrownBy(() -> respaldo.update(registroRespaldo(archivo, "UTC_TIMESTAMP(6) - INTERVAL 5 HOUR",
					secuencia, hash, "simulado"))).as("destino simulado sin la fila del DBA")
					.satisfies(e -> assertThat(codigoMySql(e)).isEqualTo(1644));
		}
		for (String sentencia : new String[] { "UPDATE respaldo SET bytes = bytes WHERE 1 = 0",
				"DELETE FROM respaldo WHERE 1 = 0", "UPDATE pago SET version = version WHERE 1 = 0" }) {
			assertThatThrownBy(() -> respaldo.update(sentencia)).as("cc_respaldo: " + sentencia)
					.satisfies(e -> assertThat(codigoMySql(e)).isEqualTo(1142));
		}

		respaldo.update(registroRespaldo(archivo, "UTC_TIMESTAMP(6) - INTERVAL 5 HOUR", secuencia, hash,
				"verificacion-ci"));

		var estado = estadoTecnico.respaldo();
		assertThat(estado.existe()).isTrue();
		assertThat(estado.alDia()).isTrue();
		assertThat(estado.archivo()).isEqualTo(archivo);
	}

	private static String registroRespaldo(String archivo, String fin, long secuencia, String hash, String destino) {
		return "INSERT INTO respaldo (inicio, fin, archivo, sha256, bytes, version_esquema, secuencia_antes, hash_antes, "
				+ "secuencia_despues, hash_despues, conteos, destino, comparacion, creado_en, creado_por) VALUES ("
				+ fin + " - INTERVAL 1 SECOND, " + fin + ", '" + archivo + "', REPEAT('a', 64), 1024, '24', " + secuencia + ", '"
				+ hash + "', " + secuencia + ", '" + hash + "', '{}', '" + destino + "', 'PRIMERO', UTC_TIMESTAMP(6) - "
				+ "INTERVAL 5 HOUR, 'cc_respaldo')";
	}
}
