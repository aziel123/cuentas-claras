package pe.edu.virgenmaria.cuentasclaras.plataforma.fin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ReconteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.CodigosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.AltaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.ConservacionDatos;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.PasoAProduccion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CrearUsuarioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.FacultadTitular;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA del sprint 11: el ciclo de vida completo de un colegio, con una familia REAL (su apoderado y su comprobante): el
 * operador da de alta, el titular arma su equipo con su facultad, crea el año, Administración arma secciones, alumno y
 * pensiones, el titular firma la conservación (paso 16) y pasa a producción; Caja cobra y cierra; dos operadores
 * suspenden, el titular recibe la exportación y dos operadores cierran. En CERRADO la familia sigue viendo su estado de
 * cuenta, sus comprobantes y sus libretas (INDECOPI) hasta {@code conservar_hasta} (12 meses desde la suspensión, con la
 * autorización), no escribe nada y el día siguiente ya no entra. Las pruebas que había usaban una familia inventada (sin
 * apoderado ni comprobante en la base).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class QaSprint11CicloCompletoTest extends BaseFinColegio {

	private static final String DNI = "45678991";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private FacultadTitular facultad;

	@Autowired
	private ServicioUsuarios servicioUsuarios;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private PasoAProduccion paso;

	@Autowired
	private ConservacionDatos conservacion;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioCierreCaja cierre;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Test
	void debeLlevarAlColegioDelAltaAlCierreYDejarALaFamiliaConsultarSusComprobantesHastaConservarHasta()
			throws Exception {
		// 1. El operador da de alta el colegio y registra el correo externo de control.
		como(ana);
		long colegio = alta.crear(new AltaColegioRequest("Colegio Ciclo QA", "Asociación Ciclo QA S.A.C.",
				EscenarioPlataforma.RUC_A, "Av. Los Educadores 123, Lima", "contacto@ciclo-qa.edu.pe",
				EnumSet.of(Nivel.PRIMARIA), "CT-ciclo-qa", "Titular Ciclo QA", DNI, "titular.cicloqa", "987650091",
				"titular-cicloqa@correo.pe"), null, null).colegioId();
		alta.registrarCorreoControl(colegio, "contador.cicloqa@estudio.pe");
		SecurityContextHolder.clearContext();

		// 2. El titular activa su cuenta, crea el año y, con su facultad y su código, la cuenta de Dirección.
		Usuario titular = activar(colegio, "titular.cicloqa");
		ingresar(titular);
		Long anio = estructura.crearAnio(new CrearAnioEscolarRequest(2027, LocalDate.of(2027, 3, 1),
				LocalDate.of(2027, 12, 17), false));
		ingresar(titular);
		CodigoPromotoria.Pedido pedido = facultad.pedirCodigo(Rol.DIRECTOR, "qadireccion");
		String codigo = CodigosDePrueba.codigo(jdbc, derivador, colegio, pedido.mensajeId());
		ingresar(titular);
		facultad.crearCuentaDirectiva(new CrearUsuarioRequest("Diana Dirección", "qadireccion", null, "987650092",
				EnumSet.of(Rol.DIRECTOR)), codigo, pedido);
		Usuario direccion = activar(colegio, "qadireccion");
		ingresar(titular);
		servicioUsuarios.crear(new CrearUsuarioRequest("Ana Admin", "qaadmin", null, "987650093",
				Set.of(Rol.ADMINISTRACION)));
		ingresar(titular);
		servicioUsuarios.crear(new CrearUsuarioRequest("Carla Caja", "qacaja", null, "987650094", Set.of(Rol.CAJA)));
		Usuario admin = activar(colegio, "qaadmin");
		Usuario caja = activar(colegio, "qacaja");

		// 3. Secciones, alumno y pensiones (Administración propone, Dirección aprueba).
		ingresar(admin);
		Long seccion = estructura.crearSeccion(anio, new CrearSeccionRequest(Grado.PRIMARIA_6, "A"));
		ingresar(admin);
		var mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(seccion));
		ingresar(admin);
		Long plan = planes.crearBorrador(anio, Nivel.PRIMARIA, EscenarioCobranza.plan(2027, "450", "350", null));
		ingresar(admin);
		planes.enviar(plan);
		ingresar(direccion);
		planes.aprobar(plan, planes.obtener(plan).version());
		Long cuota = EscenarioCaja.cuota(jdbc, mateo.alumnoId(), "PEN-2027-03");

		// 4. El resto de la puesta en marcha, el paso 16 (conservación, solo el titular) y producción.
		EscenarioPuestaEnMarcha.cuentaBancariaVigente(jdbc, colegio, "titular.cicloqa", "qadireccion");
		EscenarioPuestaEnMarcha.respaldoAlDia(jdbc);
		jdbc.update("UPDATE colegio SET aviso_privacidad_version = '2026-1', aviso_privacidad_aprobado_por = 'qadireccion', "
				+ "aviso_privacidad_aprobado_en = CURRENT_TIMESTAMP WHERE id = ?", colegio);
		jdbc.update("UPDATE mensaje SET estado = 'ENVIADO', proveedor = 'SIMULADO', proveedor_mensaje_id = 'SIM-ciclo-qa', "
				+ "enviado_en = CURRENT_TIMESTAMP, intentos = 1 WHERE id = ?", pedido.mensajeId());
		EscenarioEscolar.contactosConfirmados(jdbc);
		ingresar(titular);
		paso.sinSaldoInicial();
		ingresar(titular);
		paso.aceptarOseSimulado();
		ingresar(titular);
		ContextoColegio.en(colegio, () -> conservacion.autorizar());
		ingresar(titular);
		paso.pasar();
		assertThat(estado(colegio)).isEqualTo("PRODUCCION");

		// 5. Caja cobra en efectivo (con comprobante) y cierra con faltante; Dirección aprueba el cierre.
		ingresar(caja);
		cobro.cobrar(EscenarioCaja.efectivo(mateo.familiaId(), List.of(cuota), "450.00", "450.00"));
		ingresar(caja);
		cierre.contar(new ConteoRequest(new BigDecimal("440.00"), null));
		ingresar(caja);
		cierre.recontar(new ReconteoRequest(new BigDecimal("440.00"), null, "Volví a contar dos veces y faltan diez soles"));
		Long cierreId = jdbc.queryForObject("SELECT id FROM cierre_caja WHERE colegio_id = ?", Long.class, colegio);
		Long solicitud = ContextoColegio.en(colegio, () -> EscenarioAprobaciones.pendiente(jdbc, "cierre_caja", cierreId));
		ingresar(direccion);
		bandeja.aprobar(solicitud, "Conversé con la cajera: repuso el faltante");
		SecurityContextHolder.clearContext();
		String comprobante = jdbc.queryForObject("SELECT CONCAT(serie, '-', LPAD(numero, 8, '0')) FROM comprobante WHERE "
				+ "colegio_id = ?", String.class, colegio);
		Long comprobanteId = jdbc.queryForObject("SELECT id FROM comprobante WHERE colegio_id = ?", Long.class, colegio);

		// 6. Dos operadores suspenden; el titular recibe la exportación; dos operadores cierran.
		reloj.mover(Duration.ofDays(2));
		suspender(colegio);
		recibir(colegio, DNI);
		cerrar(colegio);
		assertThat(estado(colegio)).isEqualTo("CERRADO");
		LocalDate suspendidoEn = jdbc.queryForObject("SELECT suspendido_en FROM colegio WHERE id = ?",
				java.sql.Timestamp.class, colegio).toLocalDateTime().toLocalDate();
		LocalDate hasta = jdbc.queryForObject("SELECT conservar_hasta FROM colegio WHERE id = ?", java.sql.Date.class,
				colegio).toLocalDate();
		assertThat(hasta).isEqualTo(suspendidoEn.plusMonths(12));

		// 7. En CERRADO, la familia real consulta su estado de cuenta, sus comprobantes y sus libretas (INDECOPI).
		Long rosa = jdbc.queryForObject("SELECT id FROM apoderado WHERE colegio_id = ? AND familia_id = ?", Long.class,
				colegio, mateo.familiaId());
		UsuarioAutenticado familia = new UsuarioAutenticado(9_901L, colegio, "rosa.cicloqa", "Rosa Quispe", null, true,
				false, false, EnumSet.of(Rol.APODERADO), rosa);
		mvc.perform(get("/familia/estado-de-cuenta").with(UsuariosDePrueba.como(familia))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Mateo")));
		mvc.perform(get("/familia/comprobantes").with(UsuariosDePrueba.como(familia))).andExpect(status().isOk())
				.andExpect(content().string(containsString(comprobante.substring(0, 5))));
		mvc.perform(get("/familia/comprobantes/" + comprobanteId).with(UsuariosDePrueba.como(familia)))
				.andExpect(status().isOk());
		mvc.perform(get("/familia/libretas").with(UsuariosDePrueba.como(familia))).andExpect(status().isOk());
		mvc.perform(get("/familia/comunicados").with(UsuariosDePrueba.como(familia))).andExpect(status().isOk());
		// No escribe nada (modo consulta) y el personal ya no entra.
		mvc.perform(post("/familia/preferencias").with(csrf()).with(UsuariosDePrueba.como(familia)))
				.andExpect(status().isForbidden());
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(caja))).andExpect(redirectedUrl("/login?error"));

		// 8. El último día de conservar_hasta todavía consulta; el día siguiente, no.
		reloj.fijar(hasta.atTime(23, 30).atZone(ZoneId.of("America/Lima")).toInstant());
		mvc.perform(get("/familia/comprobantes").with(UsuariosDePrueba.como(familia))).andExpect(status().isOk());
		reloj.mover(Duration.ofHours(1));
		mvc.perform(get("/familia/comprobantes").with(UsuariosDePrueba.como(familia)))
				.andExpect(redirectedUrl("/login?error"));
	}
}
