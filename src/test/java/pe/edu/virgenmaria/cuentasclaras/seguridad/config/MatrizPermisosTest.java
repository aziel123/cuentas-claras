package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.server.PathContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ComoUsuario;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La matriz {@link ModuloApp} es la única fuente de permisos y toda ruta fuera de ella se niega.
 */
@PruebaIntegracion
class MatrizPermisosTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mapeos;

	@BeforeEach
	@AfterEach
	void limpiar() {
		// El manejador de acceso denegado audita cada 403.
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@ParameterizedTest
	@EnumSource(Rol.class)
	void cadaRolSoloAccedeASusModulos(Rol rol) throws Exception {
		List<String> errores = new ArrayList<>();
		for (ModuloApp modulo : ModuloApp.values()) {
			int estado = mvc.perform(get(modulo.ruta()).with(UsuariosDePrueba.como(rol))).andReturn()
					.getResponse().getStatus();
			// Permitido: 200 si el módulo ya existe, 404 si aún no tiene controlador. Negado: 403. Nunca otro.
			int esperado = !modulo.permite(rol) ? 403 : modulo.disponible() ? 200 : 404;
			// Sprint 4: un usuario APODERADO de prueba sin apoderado enlazado no tiene familia que mostrar (404, sin
			// pistas). Con su apoderado enlazado lo prueba PagoEnLineaWebTest.
			if (rol == Rol.APODERADO && modulo == ModuloApp.FAMILIA) {
				esperado = 404;
			}
			if (estado != esperado) {
				errores.add(rol + " en " + modulo.ruta() + ": esperaba " + esperado + " y recibió " + estado);
			}
		}
		assertThat(errores).isEmpty();
	}

	@Test
	void anonimoEsRedirigidoAlLoginEnRutasProtegidas() throws Exception {
		List<String> rutas = new ArrayList<>(Arrays.stream(ModuloApp.values()).map(ModuloApp::ruta).toList());
		rutas.addAll(List.of("/", ModuloApp.RUTA_CAMBIAR_CLAVE, "/ruta-que-no-existe"));
		for (String ruta : rutas) {
			mvc.perform(get(ruta))
					.andExpect(status().is3xxRedirection())
					.andExpect(header().string("Location", endsWith("/login")));
		}
	}

	@Test
	void todasLasRutasDeLosControladoresEstanCubiertasPorLaMatriz() {
		List<String> rutas = new ArrayList<>();
		for (RequestMappingInfo info : mapeos.getHandlerMethods().keySet()) {
			rutas.addAll(info.getPatternValues());
		}
		assertThat(rutas).as("se leyeron las rutas de los controladores").contains("/inicio", "/login");

		List<String> sinCubrir = rutas.stream()
				.filter(ruta -> !estaCubierta(ruta.replaceAll("\\{[^}]+}", "1")))
				.toList();
		assertThat(sinCubrir).as("rutas de controladores que la matriz no cubre (quedarían en denyAll)").isEmpty();
	}

	@Test
	@ComoUsuario(roles = Rol.PROMOTOR)
	void rutaDesconocidaEsDenegada() throws Exception {
		mvc.perform(get("/ruta-que-no-existe")).andExpect(status().isForbidden());
		mvc.perform(get("/admin")).andExpect(status().isForbidden());
		mvc.perform(get("/salir")).andExpect(status().isForbidden());
	}

	@Test
	@ComoUsuario(roles = Rol.DIRECTOR, clavePendiente = true)
	void usuarioConClavePendienteSoloPuedeCambiarSuClave() throws Exception {
		mvc.perform(get(ModuloApp.RUTA_CAMBIAR_CLAVE)).andExpect(status().isOk());
		mvc.perform(get("/inicio")).andExpect(status().isForbidden());
		mvc.perform(get("/usuarios")).andExpect(status().isForbidden());
		mvc.perform(get("/auditoria")).andExpect(status().isForbidden());
	}

	/** Sprint 3: Caja entra a caja y a nada de lo que administra o aprueba dinero. */
	@Test
	@ComoUsuario(roles = Rol.CAJA)
	void cajaSoloEntraACaja() throws Exception {
		mvc.perform(get("/caja")).andExpect(status().isOk());
		mvc.perform(get("/caja/hoy")).andExpect(status().isOk());
		for (String ruta : new String[] { "/alumnos", "/pensiones", "/colegio", "/usuarios", "/auditoria" }) {
			mvc.perform(get(ruta)).andExpect(status().isForbidden());
		}
	}

	/** Quien cobra no aprueba (anulaciones, descuentos, cierres) ni verifica contra el banco. */
	@Test
	@ComoUsuario(roles = Rol.CAJA)
	void cajaRecibe403EnAprobacionesDescuentosYConciliacion() throws Exception {
		for (String ruta : new String[] { "/aprobaciones", "/aprobaciones/1/aprobar", "/descuentos", "/descuentos/nuevo",
				"/conciliacion" }) {
			mvc.perform(get(ruta)).andExpect(status().isForbidden());
		}
		mvc.perform(post("/aprobaciones/1/aprobar").with(csrf())).andExpect(status().isForbidden());
	}

	/** Tanda 2: la cajera pide anular solo sus pagos desde la caja; el estado de cuenta es de Administración y Dirección. */
	@Test
	@ComoUsuario(roles = Rol.CAJA)
	void cajaRecibe403EnEstadoDeCuentaYEnLasAnulacionesDeAdministracion() throws Exception {
		mvc.perform(get("/alumnos/1/estado-cuenta")).andExpect(status().isForbidden());
		mvc.perform(get("/alumnos/comprobantes/1")).andExpect(status().isForbidden());
		mvc.perform(get("/alumnos/pagos/1/correccion")).andExpect(status().isForbidden());
		mvc.perform(post("/alumnos/pagos/1/devolucion").with(csrf()).param("alumno", "1")).andExpect(status().isForbidden());
		mvc.perform(post("/alumnos/pagos/1/correccion").with(csrf())).andExpect(status().isForbidden());
		mvc.perform(post("/descuentos").with(csrf())).andExpect(status().isForbidden());
		mvc.perform(post("/descuentos/revisar").with(csrf())).andExpect(status().isForbidden());
	}

	/** Quien aprueba no pide anulaciones por la caja (las rutas de caja son solo de Caja). */
	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "PROMOTOR", "DIRECTOR", "ADMINISTRACION", "DOCENTE", "APODERADO" })
	void soloCajaPideAnulacionesDesdeLaCaja(Rol rol) throws Exception {
		mvc.perform(post("/caja/pagos/1/devolucion").with(csrf()).with(UsuariosDePrueba.como(rol)))
				.andExpect(status().isForbidden());
		mvc.perform(get("/caja/pagos/1/correccion").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
	}

	/** Tanda 3: solo la cajera cierra su caja; las cajas del día son de quien aprueba; verifica Administración. */
	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "PROMOTOR", "DIRECTOR", "ADMINISTRACION", "DOCENTE", "APODERADO" })
	void soloCajaCierraSuCaja(Rol rol) throws Exception {
		mvc.perform(get("/caja/cierre").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		for (String ruta : new String[] { "/caja/cierre/conteo", "/caja/cierre/reconteo", "/caja/cierre/deposito",
				"/caja/cierre/reapertura" }) {
			mvc.perform(post(ruta).with(csrf()).with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		}
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "CAJA", "ADMINISTRACION", "DOCENTE", "APODERADO" })
	void cajasDelDiaSoloParaQuienAprueba(Rol rol) throws Exception {
		mvc.perform(get("/aprobaciones/cajas").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		mvc.perform(get("/aprobaciones/cajas/1").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "CAJA", "DOCENTE", "APODERADO" })
	void conciliacionNoEsDeCajaDocenteNiApoderado(Rol rol) throws Exception {
		for (String ruta : new String[] { "/conciliacion", "/conciliacion/verificacion", "/conciliacion/extractos",
				"/conciliacion/extractos/1", "/conciliacion/cuentas", "/conciliacion/cuentas/1/confirmar" }) {
			mvc.perform(get(ruta).with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		}
		for (String ruta : new String[] { "/conciliacion/extractos/vista-previa", "/conciliacion/extractos",
				"/conciliacion/cuentas/1/confirmar", "/conciliacion/partidas/1/confirmar", "/conciliacion/partidas/1/descartar",
				"/conciliacion/movimientos/1/emparejar", "/conciliacion/movimientos/1/explicar", "/conciliacion/cuentas" }) {
			mvc.perform(post(ruta).with(csrf()).with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		}
		mvc.perform(post("/conciliacion/pagos/1").with(csrf()).with(UsuariosDePrueba.como(rol))
				.param("resultado", "ENCONTRADO")).andExpect(status().isForbidden());
		mvc.perform(post("/conciliacion/depositos/1").with(csrf()).with(UsuariosDePrueba.como(rol))
				.param("resultado", "ENCONTRADO")).andExpect(status().isForbidden());
		mvc.perform(post("/conciliacion/devoluciones/1").with(csrf()).with(UsuariosDePrueba.como(rol))
				.param("numeroOperacion", "REM1234").param("cuentaDeOrigen", "true")).andExpect(status().isForbidden());
	}

	/**
	 * Hallazgo 10 de QA: Promotoría VE la conciliación (para revisar) pero no verifica ni registra reembolsos: solo
	 * Administración. (Los POST llegan al servicio, que exige el rol.)
	 */
	@Test
	void promotoriaVeConciliacionPeroNoVerifica() throws Exception {
		mvc.perform(get("/conciliacion").with(UsuariosDePrueba.como(Rol.PROMOTOR))).andExpect(status().isOk());
		mvc.perform(get("/conciliacion/verificacion").with(UsuariosDePrueba.como(Rol.PROMOTOR))).andExpect(status().isOk());
		mvc.perform(post("/conciliacion/pagos/1").with(csrf()).with(UsuariosDePrueba.como(Rol.PROMOTOR))
				.param("resultado", "ENCONTRADO").param("operacion", "12345678").param("fecha", "2026-10-02")
				.param("monto", "450.00")).andExpect(status().isForbidden());
		mvc.perform(post("/conciliacion/depositos/1").with(csrf()).with(UsuariosDePrueba.como(Rol.PROMOTOR))
				.param("resultado", "NO_ENCONTRADO").param("nota", "No figura en el estado de cuenta"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/conciliacion/devoluciones/1").with(csrf()).with(UsuariosDePrueba.como(Rol.PROMOTOR))
				.param("numeroOperacion", "REM1234").param("cuentaDeOrigen", "true")).andExpect(status().isForbidden());
	}

	/**
	 * Sprint 4, tanda 3: Dirección VE la conciliación y confirma extractos a ciegas, pero no verifica a mano, no sube
	 * extractos, no revisa diferencias ni registra cuentas (los POST llegan al servicio, que exige el rol).
	 */
	@Test
	void direccionVeLaConciliacionPeroNoLaOpera() throws Exception {
		for (String ruta : new String[] { "/conciliacion", "/conciliacion/verificacion", "/conciliacion/extractos",
				"/conciliacion/cuentas" }) {
			mvc.perform(get(ruta).with(UsuariosDePrueba.como(Rol.DIRECTOR))).andExpect(status().isOk());
		}
		mvc.perform(post("/conciliacion/pagos/1").with(csrf()).with(UsuariosDePrueba.como(Rol.DIRECTOR))
				.param("resultado", "NO_ENCONTRADO").param("nota", "No figura en el estado de cuenta"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/conciliacion/partidas/1/confirmar").with(csrf()).with(UsuariosDePrueba.como(Rol.DIRECTOR)))
				.andExpect(status().isForbidden());
		// Correcciones del sprint 4 (S4-A2): Dirección sí explica CARGOS (mirando su app del banco); no empareja.
		mvc.perform(post("/conciliacion/movimientos/1/emparejar").with(csrf()).with(UsuariosDePrueba.como(Rol.DIRECTOR))
				.param("objeto", "PAGO:1").param("nota", "Es el Yape de la familia")).andExpect(status().isForbidden());
		mvc.perform(post("/conciliacion/cuentas").with(csrf()).with(UsuariosDePrueba.como(Rol.DIRECTOR))
				.param("banco", "BCP").param("numero", "191-1234567-0-12").param("alias", "Otra cuenta"))
				.andExpect(status().isForbidden());
	}

	/** Y al revés: Promotoría, Dirección y Administración no cobran. */
	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "PROMOTOR", "DIRECTOR", "ADMINISTRACION", "DOCENTE", "APODERADO" })
	void soloCajaCobra(Rol rol) throws Exception {
		mvc.perform(get("/caja").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		mvc.perform(post("/caja/pagos").with(csrf()).with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
	}

	@Test
	@ComoUsuario(roles = Rol.CAJA)
	void cajaRecibe403AlCrearUsuarios() throws Exception {
		mvc.perform(get("/usuarios/nuevo")).andExpect(status().isForbidden());
		mvc.perform(post("/usuarios").with(csrf()).param("nombreUsuario", "intruso")).andExpect(status().isForbidden());
	}

	@Test
	@ComoUsuario(roles = Rol.DIRECTOR)
	void directorNoPuedeAsignarRolPromotor() throws Exception {
		mvc.perform(get("/usuarios/nuevo"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("value=\"PROMOTOR\""))))
				.andExpect(content().string(not(containsString("value=\"DIRECTOR\""))));
		mvc.perform(post("/usuarios").with(csrf())
						.param("nombreCompleto", "Nueva Promotora").param("nombreUsuario", "nueva.promotora")
						.param("roles", "PROMOTOR"))
				.andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE nombre_usuario = 'nueva.promotora'",
				Long.class)).isZero();
	}

	@Test
	@ComoUsuario(roles = Rol.DIRECTOR)
	void direccionVeLaBitacoraPeroSoloPromotoriaVerificaLaIntegridad() throws Exception {
		mvc.perform(get("/auditoria")).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("verificar-integridad"))));
		mvc.perform(post("/auditoria/verificar-integridad").with(csrf())).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "DOCENTE", "APODERADO" })
	void docenteYApoderadoReciben403EnUsuariosYAuditoria(Rol rol) throws Exception {
		for (String ruta : List.of("/usuarios", "/usuarios/1", "/auditoria")) {
			mvc.perform(get(ruta).with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		}
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "CAJA", "DOCENTE", "APODERADO" })
	void cajaDocenteYApoderadoReciben403EnColegioYAlumnos(Rol rol) throws Exception {
		for (String ruta : List.of("/colegio", "/colegio/anios/1", "/alumnos", "/alumnos/1", "/alumnos/1/editar",
				"/alumnos/familias/1", "/alumnos/apoderados/1", "/alumnos/nuevo")) {
			mvc.perform(get(ruta).with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		}
		for (String ruta : List.of("/colegio/anios", "/colegio/anios/1/secciones", "/colegio/secciones/1/desactivar",
				"/alumnos/nuevo", "/alumnos/1/matricula", "/alumnos/1/responsable", "/alumnos/1/retirar",
				"/alumnos/matriculas/1/seccion", "/alumnos/familias/1/apoderados", "/alumnos/apoderados/1")) {
			mvc.perform(post(ruta).with(UsuariosDePrueba.como(rol)).with(csrf())).andExpect(status().isForbidden());
		}
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "CAJA", "DOCENTE", "APODERADO" })
	void cajaDocenteYApoderadoReciben403EnPensiones(Rol rol) throws Exception {
		for (String ruta : List.of("/pensiones", "/pensiones/planes/1", "/pensiones/planes/1/editar",
				"/pensiones/planes/nuevo?anio=1&nivel=PRIMARIA", "/pensiones/cronogramas", "/pensiones/saldo-inicial",
				"/pensiones/saldo-inicial/1", "/alumnos/1/cronograma")) {
			mvc.perform(get(ruta).with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		}
		for (String ruta : List.of("/pensiones/planes/nuevo?anio=1&nivel=PRIMARIA", "/pensiones/planes/1",
				"/pensiones/planes/1/aprobar", "/pensiones/planes/1/nueva-version", "/pensiones/planes/1/descartar",
				"/pensiones/cronogramas/generar?anio=1", "/pensiones/saldo-inicial", "/pensiones/saldo-inicial/1/lineas",
				"/pensiones/saldo-inicial/1/lineas/1/quitar", "/pensiones/saldo-inicial/1/enviar",
				"/pensiones/saldo-inicial/1/confirmar", "/pensiones/saldo-inicial/1/devolver",
				"/pensiones/saldo-inicial/1/descartar")) {
			mvc.perform(post(ruta).with(UsuariosDePrueba.como(rol)).with(csrf())).andExpect(status().isForbidden());
		}
	}

	@Test
	@ComoUsuario(roles = Rol.PROMOTOR)
	void promotorVePensionesPeroNoProponeNiGeneraNiArmaLotes() throws Exception {
		mvc.perform(get("/pensiones")).andExpect(status().isOk());
		mvc.perform(get("/pensiones/cronogramas")).andExpect(status().isOk());
		mvc.perform(get("/pensiones/saldo-inicial")).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Nuevo lote"))));
		mvc.perform(get("/pensiones/planes/nuevo").param("anio", "1").param("nivel", "PRIMARIA"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/pensiones/planes/nuevo").with(csrf()).param("anio", "1").param("nivel", "PRIMARIA")
				.param("montoMatricula", "350").param("vencimientoMatricula", "2027-02-28").param("montoPension", "450")
				.param("vencimientos[0]", "2027-03-31")).andExpect(status().isForbidden());
		mvc.perform(post("/pensiones/cronogramas/generar").with(csrf()).param("anio", "1"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/pensiones/saldo-inicial").with(csrf()).param("anioId", "1").param("fechaCorte", "2026-09-30")
				.param("documentoReferencia", "Informe").param("totalDeclarado", "100.00")).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_pension", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lote_saldo_inicial", Long.class)).isZero();
	}

	@Test
	@ComoUsuario(roles = Rol.ADMINISTRACION)
	void administracionNoApruebaPlanesNiConfirmaLotes() throws Exception {
		mvc.perform(post("/pensiones/planes/1/aprobar").with(csrf())).andExpect(status().isForbidden());
		mvc.perform(post("/pensiones/saldo-inicial/1/confirmar").with(csrf())).andExpect(status().isForbidden());
		mvc.perform(post("/pensiones/saldo-inicial/1/devolver").with(csrf()).param("motivo", "Intento de Administración"))
				.andExpect(status().isForbidden());
	}

	@Test
	@ComoUsuario(roles = Rol.PROMOTOR)
	void promotorVeAlumnosPeroRecibe403AlRegistrar() throws Exception {
		mvc.perform(get("/alumnos")).andExpect(status().isOk());
		mvc.perform(get("/colegio")).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Nuevo año escolar"))));
		mvc.perform(get("/alumnos/nuevo")).andExpect(status().isForbidden());
		mvc.perform(post("/alumnos/nuevo").with(csrf()).param("tipoDocumento", "DNI").param("numeroDocumento", "78451236")
				.param("apellidoPaterno", "Quispe").param("nombres", "Mateo").param("fechaNacimiento", "2015-06-14")
				.param("documentoApoderadoExistente", "45678912")).andExpect(status().isForbidden());
		mvc.perform(post("/colegio/anios").with(csrf()).param("anio", "2026").param("inicioClases", "2026-03-02")
				.param("finClases", "2026-12-18")).andExpect(status().isForbidden());
		mvc.perform(post("/colegio/anios/1/secciones").with(csrf()).param("grado", "PRIMARIA_1").param("nombre", "A"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/alumnos/1/retirar").with(csrf()).param("fecha", "2026-10-01")
				.param("motivo", "Intento de Promotoría")).andExpect(status().isForbidden());
		mvc.perform(post("/alumnos/1/matricula").with(csrf()).param("seccionId", "1")).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM anio_escolar", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alumno", Long.class)).isZero();
	}

	@Test
	void elMenuSaleDeLaMismaMatriz() {
		assertThat(ModuloApp.para(UsuariosDePrueba.autenticado(Rol.CAJA).getAuthorities()))
				.containsExactly(ModuloApp.INICIO, ModuloApp.CAJA_COBRO);
		assertThat(ModuloApp.para(UsuariosDePrueba.autenticado(Rol.DIRECTOR, Rol.DOCENTE).getAuthorities()))
				.contains(ModuloApp.USUARIOS, ModuloApp.ACADEMICO)
				.doesNotContain(ModuloApp.CAJA_COBRO, ModuloApp.FAMILIA);
		assertThat(ModuloApp.para(UsuariosDePrueba.autenticado(1L, 1L, "x", "X", true, EnumSet.of(Rol.PROMOTOR))
				.getAuthorities())).as("con clave pendiente no hay menú").isEmpty();
	}

	private static boolean estaCubierta(String ruta) {
		if (Arrays.asList(ModuloApp.RUTAS_PUBLICAS).contains(ruta) || ModuloApp.RUTA_CAMBIAR_CLAVE.equals(ruta)) {
			return true;
		}
		PathContainer camino = PathContainer.parsePath(ruta);
		// Correcciones del sprint 4 (S4-M2): las rutas públicas también son patrones (el enlace de activación).
		if (Arrays.stream(ModuloApp.RUTAS_PUBLICAS)
				.anyMatch(patron -> PathPatternParser.defaultInstance.parse(patron).matches(camino))) {
			return true;
		}
		// Sprint 4: los avisos de la pasarela tienen su propia cadena (sin sesión; solo POST con firma).
		if (PathPatternParser.defaultInstance.parse(ModuloApp.RUTA_WEBHOOK_PASARELA).matches(camino)) {
			return true;
		}
		return Arrays.stream(ModuloApp.values())
				.flatMap(m -> Arrays.stream(m.patrones()))
				.anyMatch(patron -> PathPatternParser.defaultInstance.parse(patron).matches(camino));
	}
}
