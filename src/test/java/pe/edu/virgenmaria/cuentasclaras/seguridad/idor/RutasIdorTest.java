package pe.edu.virgenmaria.cuentasclaras.seguridad.idor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RutasDeLaAplicacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RutasDeLaAplicacion.Ruta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.idor.CatalogoRutasConId.Recurso;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Sprint 7, tanda 3 (A01 de OWASP, IDOR; E19 a E21). Recorre TODAS las rutas con id de la aplicación (las lee de Spring
 * MVC; {@link CatalogoRutasConId} dice qué recurso nombra cada id):
 * <ul>
 *   <li>con cada rol que la ruta permite, una persona del colegio B pide los recursos reales del colegio A;</li>
 *   <li>la cuenta en línea de una familia (Quispe) pide los recursos de otra familia del mismo colegio (Flores).</li>
 * </ul>
 * Ninguna respuesta puede mostrar datos del otro (404 o 403; una redirección sin «Listo»; o una página sin ningún dato
 * del otro), ninguna puede ser un error del servidor y ninguna puede cambiar nada del colegio A (todas sus tablas quedan
 * igual). Una ruta nueva con id sin clasificar hace fallar la prueba.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class RutasIdorTest {

	/** Parámetros de formulario comunes para que las acciones lleguen a buscar el recurso (motivos, textos...). */
	private static final Map<String, String> PARAMETROS = RutasDeLaAplicacion.valores("motivo",
			"Prueba de acceso a datos de otro colegio", "comentario", "Prueba de acceso a datos de otro colegio",
			"respuesta", "Prueba de acceso a datos de otro colegio", "texto", "Prueba de acceso a datos de otra familia",
			"nombre", "Familia de prueba", "descripcion", "Prueba", "continua", "true", "hablo", "false", "medio",
			"EFECTIVO", "monto", "1.00", "total", "1.00", "totalVisto", "1.00", "recibido", "1.00", "saldo", "1.00",
			"numeroOperacion", "OP1234", "operacion", "OP1234", "fecha", "2027-04-15", "roles", "DOCENTE", "tipo", "OTRO",
			"resultado", "CONFIRMA", "explicacion", "Prueba de acceso", "documento", "00000000", "clave", "x");

	/** Rutas que no exigen sesión (su token o su firma deciden): no hay rol con el que pedirlas. */
	private static final Set<Recurso> SIN_SESION = EnumSet.of(Recurso.PUBLICO);

	@Autowired
	private MockMvc mvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mapeos;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private ServicioExtractos extractos;

	@Autowired
	private ServicioRecaudacion recaudacion;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void unaRutaNuevaSinClasificarHaceFallarLaPrueba() throws Exception {
		List<Ruta> rutas = RutasDeLaAplicacion.todas(mapeos);
		assertThat(CatalogoRutasConId.sinClasificar(rutas)).as("rutas con id que no están en CatalogoRutasConId").isEmpty();
		List<String> claves = rutas.stream().map(Ruta::clave).toList();
		assertThat(CatalogoRutasConId.CATALOGO.keySet()).as("el catálogo no tiene rutas que ya no existen")
				.allMatch(claves::contains);
		assertThat(CatalogoRutasConId.CATALOGO).hasSizeGreaterThan(120);
		// Una ruta inventada con un id (como la que agregaría un sprint nuevo) aparece como sin clasificar.
		HandlerMethod nueva = new HandlerMethod(new ControladorNuevo(),
				ControladorNuevo.class.getDeclaredMethod("ver", Long.class));
		assertThat(CatalogoRutasConId.sinClasificar(List.of(new Ruta("GET", "/notas/{id:\\d+}", nueva))))
				.containsExactly("GET /notas/{id:\\d+} [id]");
	}

	@Test
	void ningunaRutaConIdDevuelveDatosDeOtroColegio() throws Exception {
		Datos a = prepararColegioA();
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		Map<Rol, RequestPostProcessor> personasB = new EnumMap<>(Rol.class);
		for (Rol rol : List.of(Rol.PROMOTOR, Rol.DIRECTOR, Rol.ADMINISTRACION, Rol.CAJA, Rol.DOCENTE)) {
			personasB.put(rol, UsuariosDePrueba.como(UsuariosDePrueba.guardar(usuarios, codificador, colegioB,
					rol.name().toLowerCase() + ".colegio.b", UsuariosDePrueba.CLAVE, false, rol)));
		}
		// Una cuenta en línea del colegio B que dice ser de la apoderada del A.
		personasB.put(Rol.APODERADO, UsuariosDePrueba.como(new UsuarioAutenticado(990L, colegioB, "familia.b",
				"Familia B", null, true, false, false, EnumSet.of(Rol.APODERADO), a.f().rosa())));

		List<String> errores = new ArrayList<>();
		int pedidos = 0;
		for (Ruta ruta : rutasConId()) {
			Map<String, Recurso> clasificacion = CatalogoRutasConId.CATALOGO.get(ruta.clave());
			if (clasificacion.values().stream().anyMatch(SIN_SESION::contains) && !ruta.patron().startsWith("/familia/")) {
				continue;
			}
			String camino = ruta.concreta(valores(clasificacion, a.ids()));
			for (Rol rol : RutasDeLaAplicacion.roles(camino)) {
				pedidos++;
				revisar(ruta, camino, clasificacion, a.ids(), personasB.get(rol), rol.name(), a.marcas(), errores);
			}
		}
		assertThat(errores).as("rutas que dejaron ver o tocar datos del colegio A").isEmpty();
		assertThat(pedidos).as("se pidieron todas las rutas con id con cada rol").isGreaterThan(150);

		// Control: el mismo recorrido SÍ detecta a quien ve y cambia los datos (una persona del propio colegio A).
		RequestPostProcessor adminA = UsuariosDePrueba.como(UsuariosDePrueba.guardar(usuarios, codificador, 1L,
				"administracion.colegio.a", UsuariosDePrueba.CLAVE, false, Rol.ADMINISTRACION));
		List<String> control = new ArrayList<>();
		for (Ruta ruta : rutasConId()) {
			if (ruta.clave().equals("GET /alumnos/familias/{id:\\d+}")
					|| ruta.clave().equals("POST /alumnos/familias/{id:\\d+}/nombre")) {
				Map<String, Recurso> clasificacion = CatalogoRutasConId.CATALOGO.get(ruta.clave());
				revisar(ruta, ruta.concreta(valores(clasificacion, a.ids())), clasificacion, a.ids(), adminA, "control",
						a.marcas(), control);
			}
		}
		assertThat(control).as("el detector marca lo que sí se ve y lo que sí cambia")
				.anyMatch(e -> e.contains("muestra «")).anyMatch(e -> e.contains("la acción se hizo"))
				.anyMatch(e -> e.contains("cambió datos del colegio A"));
	}

	@Test
	void ningunaRutaConIdDevuelveDatosDeOtraFamilia() throws Exception {
		Datos a = prepararColegioA();
		UsuarioAutenticado rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa Huamán", null, true, false, false,
				EnumSet.of(Rol.APODERADO), a.f().rosa());
		List<String> errores = new ArrayList<>();
		int pedidos = 0;
		for (Ruta ruta : rutasConId()) {
			if (!ruta.patron().startsWith("/familia")) {
				continue;
			}
			Map<String, Recurso> clasificacion = CatalogoRutasConId.CATALOGO.get(ruta.clave());
			String camino = ruta.concreta(valores(clasificacion, a.deFlores()));
			pedidos++;
			revisar(ruta, camino, clasificacion, a.deFlores(), UsuariosDePrueba.como(rosa), "apoderada de Quispe",
					a.marcasFlores(), errores);
		}
		assertThat(errores).as("rutas del portal que dejaron ver o tocar lo de otra familia").isEmpty();
		assertThat(pedidos).isGreaterThanOrEqualTo(9);
	}

	// ------------------------------------------------------------------------------------------------------------------

	private void revisar(Ruta ruta, String camino, Map<String, Recurso> clasificacion, Map<Recurso, String> ids,
			RequestPostProcessor persona, String quien, List<String> marcas, List<String> errores) throws Exception {
		boolean post = ruta.metodo().equals("POST");
		MockHttpServletRequestBuilder pedido = post ? post(camino).with(csrf()) : get(camino);
		pedido = pedido.with(persona);
		if (post) {
			for (Map.Entry<String, String> p : PARAMETROS.entrySet()) {
				pedido = pedido.param(p.getKey(), p.getValue());
			}
		}
		for (String nombre : ruta.parametrosConId()) {
			pedido = pedido.param(nombre, valor(clasificacion.get(nombre), nombre, ids));
		}
		for (String nombre : ruta.camposConId()) {
			pedido = pedido.param(nombre, valor(clasificacion.get(nombre), nombre, ids));
		}
		Map<String, String> antes = post ? fotoColegioA() : Map.of();
		MvcResult resultado;
		try {
			resultado = mvc.perform(pedido).andReturn();
		}
		catch (Exception e) {
			errores.add(ruta.clave() + " como " + quien + ": excepción " + e.getClass().getSimpleName() + " "
					+ e.getMessage());
			SecurityContextHolder.clearContext();
			return;
		}
		SecurityContextHolder.clearContext();
		int estado = resultado.getResponse().getStatus();
		String cuerpo = resultado.getResponse().getContentAsString();
		Object exito = resultado.getFlashMap().get("exito");
		String donde = ruta.clave() + " (" + camino + ") como " + quien + ": ";
		if (estado >= 500) {
			errores.add(donde + "error del servidor " + estado);
		}
		if (estado == 200 || estado == 429) {
			marcas.stream().filter(cuerpo::contains).findFirst()
					.ifPresent(m -> errores.add(donde + "muestra «" + m + "» del otro"));
		}
		if (exito != null) {
			errores.add(donde + "la acción se hizo: «" + exito + "»");
		}
		if (post && !fotoColegioA().equals(antes)) {
			errores.add(donde + "cambió datos del colegio A");
		}
	}

	private List<Ruta> rutasConId() {
		return RutasDeLaAplicacion.todas(mapeos).stream()
				.filter(r -> !CatalogoRutasConId.nombresConId(r).isEmpty()).toList();
	}

	private static Map<String, String> valores(Map<String, Recurso> clasificacion, Map<Recurso, String> ids) {
		Map<String, String> valores = new LinkedHashMap<>();
		clasificacion.forEach((nombre, recurso) -> valores.put(nombre, valor(recurso, nombre, ids)));
		return valores;
	}

	private static String valor(Recurso recurso, String nombre, Map<Recurso, String> ids) {
		if (recurso == Recurso.VERSION) {
			return "0";
		}
		if (recurso == Recurso.PUBLICO) {
			return switch (nombre) {
				case "accion" -> "PAGADO";
				case "token" -> "a".repeat(43);
				case "proveedor" -> "SIMULADA";
				default -> "1";
			};
		}
		String valor = ids.get(recurso);
		assertThat(valor).as("hay un " + recurso + " para la prueba").isNotNull();
		return valor;
	}

	/** Cuántas filas (y la suma de sus versiones) tiene el colegio A en cada tabla: nada debe cambiar. */
	private Map<String, String> fotoColegioA() {
		Map<String, String> foto = new LinkedHashMap<>();
		for (String tabla : jdbc.queryForList("SELECT table_name FROM information_schema.columns WHERE "
				+ "column_name = 'colegio_id' AND table_schema = 'public' ORDER BY table_name", String.class)) {
			boolean conVersion = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? "
					+ "AND column_name = 'version' AND table_schema = 'public'", Long.class, tabla) > 0;
			foto.put(tabla, String.valueOf(jdbc.queryForMap("SELECT COUNT(*) AS filas" + (conVersion
					? ", COALESCE(SUM(version), 0) AS versiones" : "") + " FROM " + tabla + " WHERE colegio_id = 1")));
		}
		return foto;
	}

	// ------------------------------------------------------------------------------------------------------------------

	/** Los recursos reales del colegio A, uno de cada tipo; y los de la familia Flores para la prueba entre familias. */
	private record Datos(EscenarioCaja.Familias f, Map<Recurso, String> ids, Map<Recurso, String> deFlores,
			List<String> marcas, List<String> marcasFlores) {
	}

	private Datos prepararColegioA() {
		EscenarioPanel.Datos panel = EscenarioPanel.preparar(estructura, alumnos, planes, cobro, anulaciones, descuentos,
				bandeja, reloj, jdbc);
		EscenarioCaja.Familias f = panel.f();
		EscenarioCobranza.como(EscenarioCaja.CAJA);
		Long cuotaSebastian = EscenarioCaja.cuota(jdbc, f.sebastian(), "PEN-2027-03");
		Long pagoFlores = cobro.cobrar(EscenarioCaja.efectivo(f.flores(), List.of(cuotaSebastian), "450.00", "450.00"));
		Long usuarioA = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "usuario.a", UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE).getId();
		Long cuenta = EscenarioConciliacion.cuenta(cuentas);
		Long extracto = EscenarioConciliacion.registrar(extractos, EscenarioCobranza.ADMINISTRACION,
				EscenarioConciliacion.extracto("1000.00").abono("2027-04-14", "ABONO IDOR-A", "OP998877", "450.00"));
		Long lote = EscenarioRecaudacion.registrar(recaudacion, EscenarioCobranza.ADMINISTRACION,
				EscenarioRecaudacion.archivo().pago(f.mateo(), EscenarioCaja.cuota(jdbc, f.mateo(), "PEN-2027-05"),
						"450.00", "OP445566"));
		SecurityContextHolder.clearContext();

		Long matriculaSebastian = id("SELECT id FROM matricula WHERE alumno_id = ?", f.sebastian());
		Long matriculaMateo = id("SELECT id FROM matricula WHERE alumno_id = ?", f.mateo());
		Long seccion = id("SELECT MIN(id) FROM seccion WHERE colegio_id = 1");
		Long caja = id("SELECT MIN(id) FROM caja_diaria WHERE colegio_id = 1");
		Long movimiento = id("SELECT MIN(id) FROM movimiento_bancario WHERE colegio_id = 1");
		String ordenQuispe = UUID.randomUUID().toString();
		String ordenFlores = UUID.randomUUID().toString();
		// Lo que ningún escenario crea, directo en H2 (sin las FK, que no son lo que se prueba aquí).
		jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
		try {
			jdbc.update("INSERT INTO aviso_familia (colegio_id, familia_id, apoderado_id, tipo, texto, estado, creado_en, "
					+ "creado_por, actualizado_en) VALUES (1, ?, ?, 'OTRO', 'Aviso IDOR-A de la familia', 'ABIERTO', "
					+ "CURRENT_TIMESTAMP, 'rosa.familia', CURRENT_TIMESTAMP)", f.quispe(), f.rosa());
			jdbc.update("INSERT INTO feriado (colegio_id, fecha, descripcion, vigente, pendiente, creado_en, creado_por, "
					+ "actualizado_en) VALUES (1, DATE '2027-12-24', 'Feriado IDOR-A', TRUE, TRUE, CURRENT_TIMESTAMP, "
					+ "'promotor', CURRENT_TIMESTAMP)");
			for (Object[] orden : new Object[][] { { ordenQuispe, f.quispe(), f.rosa() }, { ordenFlores, f.flores(),
					f.pedro() } }) {
				jdbc.update("INSERT INTO orden_pago (colegio_id, referencia, familia_id, apoderado_id, proveedor, monto, "
						+ "moneda, comprobante_tipo, clave_idempotencia, vence_en, estado, creado_en, creado_por, "
						+ "actualizado_en) VALUES (1, ?, ?, ?, 'SIMULADA', 450.00, 'PEN', 'BOLETA', ?, "
						+ "TIMESTAMP '2099-01-01 00:00:00', 'CREADA', CURRENT_TIMESTAMP, 'familia', CURRENT_TIMESTAMP)",
						orden[0], orden[1], orden[2], UUID.randomUUID().toString());
			}
			for (Object[] r : new Object[][] { { f.sebastian(), f.flores(), matriculaSebastian }, { f.mateo(), f.quispe(),
					matriculaMateo } }) {
				jdbc.update("INSERT INTO renovacion_matricula (colegio_id, anio_destino_id, alumno_id, familia_id, "
						+ "matricula_origen_id, grado_destino, seccion_destino_id, vence_en, estado, creado_en, creado_por, "
						+ "actualizado_en) VALUES (1, ?, ?, ?, ?, 'SECUNDARIA_1', ?, DATE '2099-01-31', 'PROPUESTA', "
						+ "CURRENT_TIMESTAMP, 'promotor', CURRENT_TIMESTAMP)", f.anio2027(), r[0], r[1], r[2], seccion);
			}
			jdbc.update("INSERT INTO lote_saldo_inicial (colegio_id, anio_escolar_id, fecha_corte, documento_referencia, "
					+ "total_declarado, estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, DATE '2027-02-28', "
					+ "'Acta IDOR-A', 100.00, 'BORRADOR', CURRENT_TIMESTAMP, 'administracion', CURRENT_TIMESTAMP)",
					f.anio2027());
			jdbc.update("INSERT INTO linea_saldo_inicial (colegio_id, lote_id, alumno_id, concepto, mes, descripcion, monto, "
					+ "fecha_vencimiento, anio_deuda, creado_en, creado_por, actualizado_en) VALUES (1, (SELECT MAX(id) FROM "
					+ "lote_saldo_inicial), ?, 'PENSION', 3, 'Pensión IDOR-A', 100.00, DATE '2027-03-31', 2026, "
					+ "CURRENT_TIMESTAMP, 'administracion', CURRENT_TIMESTAMP)", f.mateo());
			jdbc.update("INSERT INTO cierre_mensual_banco (colegio_id, cuenta_id, anio, mes, total_abonos, total_cargos, "
					+ "saldo_final, estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, 2027, 3, 0, 0, 0, 'ABIERTO', "
					+ "CURRENT_TIMESTAMP, 'sistema.conciliacion', CURRENT_TIMESTAMP)", cuenta);
			jdbc.update("INSERT INTO deposito_caja (colegio_id, caja_diaria_id, cuenta, numero_operacion, fecha_deposito, "
					+ "monto, esperado, creado_en, creado_por, actualizado_en) VALUES (1, ?, 'BCP Soles', 'DEP7766', "
					+ "DATE '2027-04-15', 350.00, 350.00, CURRENT_TIMESTAMP, 'caja', CURRENT_TIMESTAMP)", caja);
			// La partida la propone sola la conciliación al registrar el extracto (no se inserta).
		}
		finally {
			jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
		}

		Map<Recurso, String> ids = new EnumMap<>(Recurso.class);
		ids.put(Recurso.ALUMNO, f.mateo().toString());
		ids.put(Recurso.FAMILIA, f.quispe().toString());
		ids.put(Recurso.APODERADO, f.rosa().toString());
		ids.put(Recurso.MATRICULA, matriculaMateo.toString());
		ids.put(Recurso.ANIO, f.anio2027().toString());
		ids.put(Recurso.SECCION, seccion.toString());
		ids.put(Recurso.PLAN, texto("SELECT MIN(id) FROM plan_pension WHERE colegio_id = 1"));
		ids.put(Recurso.CUOTA, EscenarioCaja.cuota(jdbc, f.mateo(), "PEN-2027-06").toString());
		ids.put(Recurso.PAGO, panel.pagoEfectivo().toString());
		ids.put(Recurso.COMPROBANTE, texto("SELECT comprobante_id FROM pago WHERE id = ?", panel.pagoEfectivo()));
		ids.put(Recurso.CAJA, caja.toString());
		ids.put(Recurso.DEPOSITO, texto("SELECT MIN(id) FROM deposito_caja WHERE colegio_id = 1"));
		ids.put(Recurso.ANULACION, texto("SELECT MIN(id) FROM anulacion_pago WHERE colegio_id = 1"));
		ids.put(Recurso.SOLICITUD, texto("SELECT MIN(id) FROM solicitud_cambio WHERE colegio_id = 1"));
		ids.put(Recurso.AVISO, texto("SELECT MIN(id) FROM aviso_familia WHERE colegio_id = 1"));
		ids.put(Recurso.RENOVACION, texto("SELECT id FROM renovacion_matricula WHERE alumno_id = ?", f.mateo()));
		ids.put(Recurso.ORDEN, texto("SELECT id FROM orden_pago WHERE referencia = ?", ordenQuispe));
		ids.put(Recurso.REFERENCIA_ORDEN, ordenQuispe);
		ids.put(Recurso.FERIADO, texto("SELECT MIN(id) FROM feriado WHERE colegio_id = 1"));
		ids.put(Recurso.MENSAJE, texto("SELECT MIN(id) FROM mensaje WHERE colegio_id = 1"));
		ids.put(Recurso.EXTRACTO, extracto.toString());
		ids.put(Recurso.CUENTA, cuenta.toString());
		ids.put(Recurso.MOVIMIENTO, movimiento.toString());
		ids.put(Recurso.PARTIDA, texto("SELECT MIN(id) FROM partida_conciliacion WHERE colegio_id = 1"));
		ids.put(Recurso.CIERRE_MENSUAL, texto("SELECT MIN(id) FROM cierre_mensual_banco WHERE colegio_id = 1"));
		ids.put(Recurso.LOTE_SALDO, texto("SELECT MIN(id) FROM lote_saldo_inicial WHERE colegio_id = 1"));
		ids.put(Recurso.LINEA_SALDO, texto("SELECT MIN(id) FROM linea_saldo_inicial WHERE colegio_id = 1"));
		ids.put(Recurso.LOTE_RECAUDACION, lote.toString());
		ids.put(Recurso.LINEA_RECAUDACION, texto("SELECT MIN(id) FROM linea_recaudacion WHERE colegio_id = 1"));
		ids.put(Recurso.USUARIO, usuarioA.toString());
		assertThat(ids.values()).as("hay un recurso real de cada tipo en el colegio A").doesNotContainNull();

		Map<Recurso, String> deFlores = new EnumMap<>(Recurso.class);
		deFlores.put(Recurso.COMPROBANTE, texto("SELECT comprobante_id FROM pago WHERE id = ?", pagoFlores));
		deFlores.put(Recurso.RENOVACION, texto("SELECT id FROM renovacion_matricula WHERE alumno_id = ?", f.sebastian()));
		deFlores.put(Recurso.REFERENCIA_ORDEN, ordenFlores);
		deFlores.put(Recurso.PAGO, pagoFlores.toString());
		deFlores.put(Recurso.CUOTA, EscenarioCaja.cuota(jdbc, f.sebastian(), "PEN-2027-05").toString());
		String comprobanteFlores = texto("SELECT CONCAT(serie, '-', LPAD(CAST(numero AS VARCHAR), 8, '0')) FROM comprobante "
				+ "WHERE id = ?", Long.valueOf(deFlores.get(Recurso.COMPROBANTE)));

		List<String> marcas = List.of("Quispe", "Flores", "Huamán", EscenarioEscolar.DNI_ROSA, EscenarioEscolar.DNI_MATEO,
				EscenarioCaja.DNI_SEBASTIAN, EscenarioCaja.DNI_PEDRO, "IDOR-A", "usuario.a", EscenarioConciliacion.CUENTA,
				comprobanteFlores);
		List<String> marcasFlores = List.of("Flores", "Sebastián", EscenarioCaja.DNI_SEBASTIAN, EscenarioCaja.DNI_PEDRO,
				comprobanteFlores);
		return new Datos(f, ids, deFlores, marcas, marcasFlores);
	}

	private Long id(String sql, Object... argumentos) {
		return jdbc.queryForObject(sql, Long.class, argumentos);
	}

	private String texto(String sql, Object... argumentos) {
		Object valor = jdbc.queryForObject(sql, Object.class, argumentos);
		return valor == null ? null : valor.toString();
	}

	/** Un controlador inventado con una ruta con id, como el que agregaría un sprint nuevo. */
	static class ControladorNuevo {

		String ver(Long id) {
			return "notas";
		}
	}
}
