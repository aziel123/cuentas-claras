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
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
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
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.AlertasActivacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnlacesActivacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioActivacionCuenta;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 4: el acceso en línea del apoderado. Lo da Promotoría o Administración desde la ficha del apoderado (nunca
 * desde Usuarios), queda enlazado a ESE apoderado y se quita con motivo.
 * <p>
 * Correcciones del sprint 4 (S4-M2) y sprint 5 (A2, G7): quien lo crea no ve ninguna clave NI el enlace. El enlace de
 * UN solo uso lo genera el proceso de envío y llega directo al celular (o correo) registrado del apoderado; con él, el
 * apoderado confirma su documento y elige su clave. La activación y el primer ingreso quedan en la bitácora con su IP.
 * Solo Promotoría restablece el acceso (el enlace anterior deja de servir).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AccesoApoderadosTest {

	private static final Pattern ENLACE = Pattern.compile("/activar/(\\d+)/([A-Za-z0-9_-]{43})");

	private static final String CLAVE_ROSA = "mi clave elegida por mi 2026";

	@Autowired
	private ServicioAccesoApoderados acceso;

	@Autowired
	private ServicioActivacionCuenta activacion;

	@Autowired
	private AlertasActivacion alertasActivacion;

	@Autowired
	private UsuarioRepository usuarios;

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

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes despacho;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor.BuzonSimulado buzon;

	private static final String CELULAR_ROSA = "+51" + EscenarioEscolar.CELULAR_ROSA;

	private static final String CELULAR_PEDRO = "+51912345678";

	private Familias f;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		buzon.vaciar();
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void nadieVeLaClaveYElApoderadoActivaSuCuentaConElEnlaceDeUnSoloUso() throws Exception {
		como(EscenarioCaja.CAJA);
		assertThatThrownBy(() -> acceso.darAcceso(f.rosa())).isInstanceOfAny(AccessDeniedException.class,
				AuthorizationDeniedException.class);

		como(EscenarioCobranza.ADMINISTRACION);
		var creado = acceso.darAcceso(f.rosa());
		SecurityContextHolder.clearContext();

		// Quien da el acceso solo ve a dónde se envió, nunca el enlace.
		assertThat(creado.enviadoA()).isEqualTo("Enlace enviado a WhatsApp +51 *** *** 321");
		assertThat(creado.toString()).doesNotContain("/activar/");
		Usuario usuario = ContextoColegio.en(1L, () -> usuarios.findById(creado.usuarioId()).orElseThrow());
		assertThat(usuario.getNombreUsuario()).isEqualTo(EscenarioEscolar.DNI_ROSA);
		assertThat(usuario.getRoles()).containsExactly(Rol.APODERADO);
		assertThat(usuario.getApoderadoId()).isEqualTo(f.rosa());
		// La clave al azar ya está vencida: con ella no entra nadie (ni quien la creó, que nunca la vio).
		assertThat(usuario.claveTemporalVencida(LocalDateTime.now(reloj))).isTrue();
		como(EscenarioCobranza.ADMINISTRACION);
		assertThat(acceso.cuentaDe(f.rosa())).contains(EscenarioEscolar.DNI_ROSA);
		assertThatThrownBy(() -> acceso.darAcceso(f.rosa())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya tiene");
		SecurityContextHolder.clearContext();

		// El enlace llega al celular de Rosa: un token de 256 bits; en la base solo su SHA-256 (y en el mensaje, nada).
		String ruta = EnlacesDePrueba.recibido(despacho, buzon, 1L, CELULAR_ROSA);
		String token = token(ruta);
		assertThat(contar(jdbc, "mensaje WHERE parametros LIKE '%" + token + "%'")).isZero();
		assertThat(contar(jdbc, "enlace_activacion WHERE hash_token = '" + EnlacesActivacion.hash(token) + "'"))
				.isEqualTo(1);
		assertThat(contar(jdbc, "evento_auditoria WHERE detalle LIKE '%" + token + "%'")).isZero();
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'ACCESO_APODERADO_CREADO'")).isEqualTo(1);

		mvc.perform(get(ruta)).andExpect(status().isOk())
				.andExpect(content().string(containsString("Activa tu cuenta")))
				.andExpect(content().string(containsString("Rosa")))
				// Sprint 7, tanda 3 (Ley 29733): acepta el aviso de privacidad vigente.
				.andExpect(content().string(containsString("name=\"aceptaPrivacidad\"")))
				.andExpect(content().string(containsString("href=\"/privacidad\"")));

		// Un documento que no es el suyo no activa nada.
		mvc.perform(post(ruta).with(csrf()).param("documento", EscenarioCaja.DNI_PEDRO).param("clave", CLAVE_ROSA)
				.param("confirmacion", CLAVE_ROSA).param("aceptaPrivacidad", "true")).andExpect(redirectedUrl(ruta));
		assertThat(contar(jdbc, "enlace_activacion WHERE usado_en IS NULL")).isEqualTo(1);

		// Sin aceptar el aviso de privacidad no se activa.
		mvc.perform(post(ruta).with(csrf()).param("documento", EscenarioEscolar.DNI_ROSA).param("clave", CLAVE_ROSA)
				.param("confirmacion", CLAVE_ROSA)).andExpect(redirectedUrl(ruta))
				.andExpect(flash().attribute("error", containsString("aviso de privacidad")));
		assertThat(contar(jdbc, "enlace_activacion WHERE usado_en IS NULL")).isEqualTo(1);

		mvc.perform(post(ruta).with(csrf()).param("documento", EscenarioEscolar.DNI_ROSA).param("clave", CLAVE_ROSA)
				.param("confirmacion", CLAVE_ROSA).param("aceptaPrivacidad", "true"))
				.andExpect(redirectedUrl("/login?cuenta-activada"));
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'PRIVACIDAD_ACEPTADA' AND valor_nuevo = '2027-01' "
				+ "AND nombre_usuario = '" + EscenarioEscolar.DNI_ROSA + "'")).isEqualTo(1);
		mvc.perform(get("/login").param("cuenta-activada", ""))
				.andExpect(content().string(containsString("tu cuenta está activa")));
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'ACCESO_APODERADO_ACTIVADO' AND ip IS NOT NULL"))
				.isEqualTo(1);

		// Un solo uso: el mismo enlace ya no sirve.
		mvc.perform(get(ruta)).andExpect(content().string(containsString("Este enlace ya no sirve")));
		assertThatThrownBy(() -> activacion.activar(1L, token, EscenarioEscolar.DNI_ROSA, "otra clave larguisima 99",
				"otra clave larguisima 99", true)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya no sirve");

		// Entra con la clave que eligió; el primer ingreso queda señalado con su IP.
		MvcResult ingreso = mvc.perform(post("/login").with(csrf()).param("usuario", EscenarioEscolar.DNI_ROSA)
				.param("clave", CLAVE_ROSA)).andReturn();
		assertThat(ingreso.getResponse().getRedirectedUrl()).doesNotContain("error").doesNotContain("vencida");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'INGRESO_EXITOSO' AND detalle LIKE 'PRIMER ingreso%'"))
				.isEqualTo(1);
		Usuario activo = ContextoColegio.en(1L, () -> usuarios.findById(creado.usuarioId()).orElseThrow());
		mvc.perform(get("/familia").with(UsuariosDePrueba.como(activo)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Mateo")));

		como(EscenarioCobranza.ADMINISTRACION);
		acceso.quitarAcceso(f.rosa(), "La familia pidió cerrar la cuenta en línea");
		assertThat(ContextoColegio.en(1L, () -> usuarios.findById(creado.usuarioId()).orElseThrow().isActivo())).isFalse();
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'ACCESO_APODERADO_QUITADO'")).isEqualTo(1);
	}

	@Test
	void soloPromotoriaRestableceElAccesoYElEnlaceAnteriorDejaDeServir() {
		como(EscenarioCobranza.ADMINISTRACION);
		acceso.darAcceso(f.rosa());
		String primero = token(EnlacesDePrueba.recibido(despacho, buzon, 1L, CELULAR_ROSA));
		assertThatThrownBy(() -> acceso.restablecerAcceso(f.rosa())).isInstanceOfAny(AccessDeniedException.class,
				AuthorizationDeniedException.class);

		como(EscenarioCobranza.PROMOTORIA);
		var restablecido = acceso.restablecerAcceso(f.rosa());
		SecurityContextHolder.clearContext();
		String segundo = token(EnlacesDePrueba.recibido(despacho, buzon, 1L, CELULAR_ROSA));

		assertThat(segundo).isNotEqualTo(primero);
		assertThat(activacion.vista(1L, primero)).isEmpty();
		assertThat(activacion.vista(1L, segundo)).hasValueSatisfying(v -> {
			assertThat(v.nombreCompleto()).contains("Rosa");
			assertThat(v.venceEn()).isEqualTo(restablecido.venceEn());
		});
		assertThatThrownBy(() -> activacion.activar(1L, primero, EscenarioEscolar.DNI_ROSA, CLAVE_ROSA, CLAVE_ROSA, true))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'ACCESO_APODERADO_RESTABLECIDO'")).isEqualTo(1);
		assertThat(contar(jdbc, "enlace_activacion WHERE anulado_en IS NOT NULL")).isEqualTo(1);
		// Otro colegio no abre el enlace de este.
		assertThat(activacion.vista(2L, segundo)).isEmpty();

		// El enlace vence (48 horas): después ya no sirve y Promotoría da otro.
		reloj.avanzar(Duration.ofHours(49));
		assertThat(activacion.vista(1L, segundo)).isEmpty();
		assertThatThrownBy(() -> activacion.activar(1L, segundo, EscenarioEscolar.DNI_ROSA, CLAVE_ROSA, CLAVE_ROSA, true))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya no sirve");
	}

	@Test
	void desdeLaFichaElEnlaceVaDirectoAlTitularYLaPantallaNoLoMuestra() throws Exception {
		mvc.perform(get("/alumnos/apoderados/" + f.pedro()).with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Acceso en línea")));
		mvc.perform(post("/alumnos/apoderados/" + f.pedro() + "/acceso").with(csrf())
				.with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)))
				.andExpect(status().isOk()).andExpect(content().string(containsString(EscenarioCaja.DNI_PEDRO)))
				.andExpect(content().string(containsString("Enlace enviado a WhatsApp +51 *** *** 678")))
				.andExpect(content().string(not(containsString("/activar/"))))
				.andExpect(content().string(not(containsString("Clave temporal"))));
		mvc.perform(post("/alumnos/apoderados/" + f.rosa() + "/acceso").with(csrf())
				.with(UsuariosDePrueba.como(EscenarioCaja.CAJA)))
				.andExpect(status().isForbidden());
		assertThat(contar(jdbc, "usuario WHERE apoderado_id IS NOT NULL")).isEqualTo(1);
		// El mensaje de activación nace sin token: el enlace se genera recién al enviarlo.
		assertThat(contar(jdbc, "mensaje WHERE tipo = 'ACTIVACION_CUENTA' AND estado = 'PENDIENTE'")).isEqualTo(1);
		assertThat(contar(jdbc, "enlace_activacion")).isZero();

		// Pedro lo recibe en su celular y activa su cuenta.
		String ruta = EnlacesDePrueba.recibido(despacho, buzon, 1L, CELULAR_PEDRO);
		assertThat(contar(jdbc, "enlace_activacion e JOIN mensaje m ON m.id = e.mensaje_id WHERE m.estado = 'ENVIADO' "
				+ "AND e.proposito = 'APODERADO'")).isEqualTo(1);
		mvc.perform(post(ruta).with(csrf()).with(r -> {
			r.setRemoteAddr("181.65.10.20");
			return r;
		}).param("documento", EscenarioCaja.DNI_PEDRO).param("clave", CLAVE_ROSA).param("confirmacion", CLAVE_ROSA).param("aceptaPrivacidad", "true"))
				.andExpect(redirectedUrl("/login?cuenta-activada"));
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'ACCESO_APODERADO_ACTIVADO'")).isEqualTo(1);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'ENLACE_ACTIVACION_ENVIADO'")).isEqualTo(1);

		// Solo Promotoría ve el botón (en la familia) y puede restablecer; vuelve a la familia.
		mvc.perform(get("/alumnos/familias/" + f.flores()).with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Restablecer acceso en línea")));
		mvc.perform(get("/alumnos/familias/" + f.flores()).with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)))
				.andExpect(status().isOk()).andExpect(content().string(not(containsString("Restablecer acceso"))));
		mvc.perform(post("/alumnos/apoderados/" + f.pedro() + "/acceso/restablecer").with(csrf())
				.param("familiaId", f.flores().toString())
				.with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)))
				.andExpect(status().isForbidden());
		mvc.perform(post("/alumnos/apoderados/" + f.pedro() + "/acceso/restablecer").with(csrf())
				.param("familiaId", f.flores().toString())
				.with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Acceso restablecido")))
				.andExpect(content().string(containsString("/alumnos/familias/" + f.flores())));
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'ACCESO_APODERADO_RESTABLECIDO'")).isEqualTo(1);
	}

	private static String token(String enlace) {
		Matcher m = ENLACE.matcher(enlace);
		assertThat(m.matches()).as(enlace).isTrue();
		return m.group(2);
	}
}
