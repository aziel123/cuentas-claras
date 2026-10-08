package pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
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
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor.BuzonSimulado;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CrearUsuarioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnlacesActivacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 5: el envío del outbox ({@code sistema.mensajeria}). Reintentos con espera creciente, FALLIDO definitivo con su
 * respaldo por correo, 8 intentos como máximo, y el enlace de activación que nace al enviar (y no queda si el proveedor
 * falla).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class DespachoMensajesTest {

	private static final String CELULAR_ROSA = "+51" + EscenarioEscolar.CELULAR_ROSA;

	@Autowired
	private DespachoMensajes despacho;

	@Autowired
	private BuzonSimulado buzon;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioUsuarios servicioUsuarios;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private RelojAjustable reloj;

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
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Long avisoDePago() {
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00",
				"450.00"));
		SecurityContextHolder.clearContext();
		return jdbc.queryForObject("SELECT id FROM mensaje WHERE entidad = 'pago' AND entidad_id = ?", Long.class, pago);
	}

	private Map<String, Object> mensaje(Long id) {
		return jdbc.queryForMap("SELECT * FROM mensaje WHERE id = ?", id);
	}

	@Test
	void errorDeRedReintentaConEsperaCreciente() {
		Long id = avisoDePago();
		buzon.programar(ResultadoEnvio.reintentable("sin red"), ResultadoEnvio.reintentable("503"));
		LocalDateTime inicio = LocalDateTime.now(reloj);

		assertThat(despacho.despacharColegio(1L)).isZero();
		assertThat(mensaje(id)).containsEntry("estado", "PENDIENTE").containsEntry("intentos", 1)
				.containsEntry("ultimo_error", "sin red")
				.containsEntry("proximo_intento_en", java.sql.Timestamp.valueOf(inicio.plusMinutes(1)));
		// Antes de su hora no se vuelve a intentar.
		assertThat(despacho.despacharColegio(1L)).isZero();
		assertThat(mensaje(id)).containsEntry("intentos", 1);

		reloj.avanzar(Duration.ofMinutes(1));
		despacho.despacharColegio(1L);
		assertThat(mensaje(id)).containsEntry("intentos", 2)
				.containsEntry("proximo_intento_en", java.sql.Timestamp.valueOf(inicio.plusMinutes(3)));

		reloj.avanzar(Duration.ofMinutes(2));
		assertThat(despacho.despacharColegio(1L)).isEqualTo(1);
		assertThat(mensaje(id)).containsEntry("estado", "ENVIADO").containsEntry("intentos", 3)
				.containsEntry("proveedor", "SIMULADO");
		assertThat(mensaje(id).get("proveedor_mensaje_id")).isNotNull();
		assertThat(buzon.ultimaPara(CELULAR_ROSA)).hasValueSatisfying(e -> assertThat(e.texto()).contains("S/ 450.00"));
	}

	@Test
	void errorDefinitivoCreaRespaldoPorCorreo() {
		Long id = avisoDePago();
		buzon.programar(ResultadoEnvio.definitivo("número sin WhatsApp"));

		despacho.despacharColegio(1L);

		assertThat(mensaje(id)).containsEntry("estado", "FALLIDO").containsEntry("ultimo_error", "número sin WhatsApp");
		Map<String, Object> respaldo = jdbc.queryForMap("SELECT * FROM mensaje WHERE respaldo_de_id = ?", id);
		assertThat(respaldo).containsEntry("canal", "CORREO").containsEntry("destino", EscenarioEscolar.CORREO_ROSA)
				.containsEntry("tipo", "PAGO_REGISTRADO").containsEntry("estado", "PENDIENTE")
				.containsEntry("parametros", mensaje(id).get("parametros"));
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'MENSAJE_FALLIDO'")).isEqualTo(1);

		despacho.despacharColegio(1L);
		assertThat(jdbc.queryForObject("SELECT estado FROM mensaje WHERE respaldo_de_id = ?", String.class, id))
				.isEqualTo("ENVIADO");
		assertThat(buzon.ultimaPara(EscenarioEscolar.CORREO_ROSA)).hasValueSatisfying(e ->
				assertThat(e.texto()).contains("registramos su pago").contains("/familia"));
	}

	@Test
	void ochoIntentosEsFallido() {
		Long id = avisoDePago();
		for (int i = 0; i < 8; i++) {
			buzon.programar(ResultadoEnvio.reintentable("503"));
		}
		for (int i = 0; i < 8; i++) {
			despacho.despacharColegio(1L);
			reloj.avanzar(Duration.ofMinutes(61));
		}
		assertThat(mensaje(id)).containsEntry("estado", "FALLIDO").containsEntry("intentos", 8);
		assertThat(contar(jdbc, "mensaje WHERE respaldo_de_id = " + id)).isEqualTo(1);
	}

	@Test
	void activacionGeneraElEnlaceAlEnviar() {
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		var creado = servicioUsuarios.crear(new CrearUsuarioRequest("Carla Caja", "caja3", null, "966777321",
				Set.of(Rol.CAJA)));
		SecurityContextHolder.clearContext();
		assertThat(creado.enviadoA()).isEqualTo("Enlace enviado a WhatsApp +51 *** *** 321");
		assertThat(contar(jdbc, "enlace_activacion")).isZero();

		String ruta = EnlacesDePrueba.recibido(despacho, buzon, 1L, "+51966777321");

		Map<String, Object> enlace = jdbc.queryForMap("SELECT * FROM enlace_activacion WHERE usuario_id = ?", creado.id());
		assertThat(enlace).containsEntry("proposito", "PERSONAL")
				.containsEntry("hash_token", EnlacesActivacion.hash(EnlacesDePrueba.token(ruta)));
		assertThat(jdbc.queryForMap("SELECT estado, parametros FROM mensaje WHERE id = ?", enlace.get("mensaje_id")))
				.containsEntry("estado", "ENVIADO").containsEntry("parametros", "");
		assertThat(((java.sql.Timestamp) enlace.get("vence_en")).toLocalDateTime())
				.isEqualTo(LocalDateTime.now(reloj).plusHours(48));
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'ENLACE_ACTIVACION_ENVIADO'")).isEqualTo(1);
	}

	@Test
	void siElProveedorFallaElEnlaceNoQueda() {
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		var creado = servicioUsuarios.crear(new CrearUsuarioRequest("Carla Caja", "caja3", null, "966777321",
				Set.of(Rol.CAJA)));
		SecurityContextHolder.clearContext();
		buzon.programar(ResultadoEnvio.reintentable("503"));

		despacho.despacharColegio(1L);

		assertThat(contar(jdbc, "enlace_activacion")).as("el token del intento fallido no queda").isZero();
		assertThat(jdbc.queryForMap("SELECT estado, intentos FROM mensaje WHERE usuario_id = ?", creado.id()))
				.containsEntry("estado", "PENDIENTE").containsEntry("intentos", 1);
		reloj.avanzar(Duration.ofMinutes(1));
		String ruta = EnlacesDePrueba.recibido(despacho, buzon, 1L, "+51966777321");
		assertThat(contar(jdbc, "enlace_activacion WHERE hash_token = '" + EnlacesActivacion.hash(EnlacesDePrueba.token(ruta))
				+ "'")).isEqualTo(1);
		assertThat(contar(jdbc, "enlace_activacion")).isEqualTo(1);
	}
}
