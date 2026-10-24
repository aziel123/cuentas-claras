package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.Equipo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.MOTIVO_ANULACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.CORREO_CONTROL;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.MOTIVO;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/**
 * Sprint 11, tanda 1c (decisión 216 y sección 3.16b): lo sensible que pide una Promotoría queda PENDIENTE hasta que ella
 * misma lo aplica con su código por WhatsApp, atado a ESA acción: sin código, con uno equivocado, vencido, de otra acción
 * o de otra persona, no pasa nada. Anulación de pago (avisa también a la familia), descuento, dar Dirección y quitar la
 * única Dirección en producción (f16b ajustada: directa con código y avisos, sin espera).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AccionesSensiblesTest {

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioUsuarios servicioUsuarios;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private CodigoPromotoria codigos;

	@Autowired
	private DerivadorSecreto derivador;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	private Equipo e;

	private EscenarioCaja.Familias f;

	/** Promotoría que también hace trabajo de Administración (pide anulaciones y descuentos). */
	private Usuario promotoraAdm;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		e = EscenarioDirecta.preparar(usuarios, codificador, jdbc, reloj);
		promotoraAdm = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora.adm.dir", UsuariosDePrueba.CLAVE,
				false, Rol.PROMOTOR, Rol.ADMINISTRACION);
		jdbc.update("UPDATE usuario SET creado_en = ? WHERE id = ?", LocalDateTime.now(reloj).minusDays(40),
				promotoraAdm.getId());
		EscenarioDirecta.rolDesde(jdbc, promotoraAdm, Rol.PROMOTOR, LocalDateTime.now(reloj).minusDays(40));
		promotoraAdm = EscenarioDirecta.recargar(usuarios, promotoraAdm);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Long darDireccionAlDocente(Usuario quien, Usuario docente) {
		como(quien, () -> servicioUsuarios.cambiarRoles(docente.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DIRECTOR),
				MOTIVO)));
		return EscenarioAprobaciones.pendiente(jdbc, "usuario", docente.getId());
	}

	private CodigoPromotoria.Pedido pedir(Usuario quien, Long solicitud) {
		return como(quien, () -> bandeja.pedirCodigo(solicitud));
	}

	private void aprobarCon(Usuario quien, Long solicitud, CodigoPromotoria.Escrito escrito) {
		como(quien, () -> {
			List<String> telefonos = EscenarioAprobaciones.telefonosPorLlamar(bandeja, jdbc, solicitud);
			bandeja.aprobar(solicitud, null, !telefonos.isEmpty(), telefonos, escrito);
		});
	}

	private String estado(Long solicitud) {
		return jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, solicitud);
	}

	@Test
	void darDireccionSinElCodigoCorrectoNoPasaYConElSi() {
		Long solicitud = darDireccionAlDocente(e.promotora(), e.docente());
		assertThat(estado(solicitud)).as("lo sensible no se aplica al pedirlo").isEqualTo("PENDIENTE");

		// Sin código.
		assertThatThrownBy(() -> aprobarCon(e.promotora(), solicitud, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("pide tu código");
		CodigoPromotoria.Pedido pedido = pedir(e.promotora(), solicitud);
		assertThat(jdbc.queryForMap("SELECT tipo, canal, usuario_id FROM mensaje WHERE id = ?", pedido.mensajeId()))
				.containsEntry("tipo", "CODIGO_PROMOTORIA").containsEntry("canal", "WHATSAPP")
				.containsEntry("usuario_id", e.promotora().getId());
		// Un código equivocado.
		assertThatThrownBy(() -> aprobarCon(e.promotora(), solicitud, EscenarioDirecta.equivocado(jdbc, derivador, pedido)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no coincide");
		// El código de OTRA acción (dar Dirección a otra cuenta).
		Usuario otroDocente = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "docente2.dir", UsuariosDePrueba.CLAVE,
				false, Rol.DOCENTE);
		Long otra = darDireccionAlDocente(e.promotora(), otroDocente);
		CodigoPromotoria.Pedido deOtra = pedir(e.promotora(), otra);
		assertThatThrownBy(() -> aprobarCon(e.promotora(), solicitud, EscenarioDirecta.escrito(jdbc, derivador, deOtra)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("otra acción");
		// El código de OTRA persona (la socia pidió uno para la misma acción).
		CodigoPromotoria.Accion accion = como(e.promotora(), () -> bandeja.accionDelCodigo(solicitud));
		CodigoPromotoria.Pedido deLaSocia = como(e.socia(), () -> codigos.pedir(accion));
		assertThatThrownBy(() -> aprobarCon(e.promotora(), solicitud, EscenarioDirecta.escrito(jdbc, derivador,
				deLaSocia))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es tuyo");
		// Vencido (10 minutos).
		((RelojAjustable) reloj).avanzar(Duration.ofMinutes(11));
		assertThatThrownBy(() -> aprobarCon(e.promotora(), solicitud, EscenarioDirecta.escrito(jdbc, derivador, pedido)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("venció");
		assertThat(estado(solicitud)).isEqualTo("PENDIENTE");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario_rol WHERE usuario_id = ? AND rol = 'DIRECTOR'",
				Long.class, e.docente().getId())).isZero();

		// Con el correcto (uno nuevo), se aplica en un paso.
		CodigoPromotoria.Pedido nuevo = pedir(e.promotora(), solicitud);
		aprobarCon(e.promotora(), solicitud, EscenarioDirecta.escrito(jdbc, derivador, nuevo));
		assertThat(jdbc.queryForMap("SELECT estado, directa, resuelto_por, codigo_mensaje_id FROM solicitud_cambio "
				+ "WHERE id = ?", solicitud)).containsEntry("estado", "APROBADA").containsEntry("directa", true)
				.containsEntry("resuelto_por", "promotora.dir").containsEntry("codigo_mensaje_id", nuevo.mensajeId());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario_rol WHERE usuario_id = ? AND rol = 'DIRECTOR'",
				Long.class, e.docente().getId())).isEqualTo(1L);
		// El código no se usa dos veces.
		assertThatThrownBy(() -> aprobarCon(e.promotora(), otra, EscenarioDirecta.escrito(jdbc, derivador, nuevo)))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(EscenarioDirecta.eventos(jdbc, "ACCION_DIRECTA_PROMOTORIA")).isEqualTo(1L);
		assertThat(EscenarioDirecta.destinos(jdbc, "ACCION_DIRECTA")).contains(CORREO_CONTROL);
	}

	@Test
	void quitarLaUnicaDireccionEnProduccionEsDirectaConCodigoYAvisos() {
		// f16b ajustada (decisión 220): en producción, con su código; sin espera; avisa también a la persona afectada.
		assertThat(jdbc.queryForObject("SELECT estado FROM colegio WHERE id = 1", String.class)).isEqualTo("PRODUCCION");
		como(e.promotora(), () -> servicioUsuarios.cambiarRoles(e.directora().getId(), new CambiarRolesRequest(
				EnumSet.of(Rol.DOCENTE), MOTIVO)));
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "usuario", e.directora().getId());
		CodigoPromotoria.Pedido pedido = pedir(e.promotora(), solicitud);
		aprobarCon(e.promotora(), solicitud, EscenarioDirecta.escrito(jdbc, derivador, pedido));

		assertThat(estado(solicitud)).isEqualTo("APROBADA");
		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class,
				e.directora().getId())).containsExactly("DOCENTE");
		assertThat(EscenarioDirecta.avisados(jdbc, "ACCION_DIRECTA")).contains(e.promotora().getId(), e.socia().getId(),
				e.directora().getId());
		assertThat(EscenarioDirecta.destinos(jdbc, "ACCION_DIRECTA")).contains(CORREO_CONTROL);
	}

	@Test
	void anularUnPagoPideElCodigoYAvisaTambienALaFamilia() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		EscenarioCobranza.como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzo), "450.00", "500.00"));
		SecurityContextHolder.clearContext();
		como(promotoraAdm, () -> anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION));
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "pago", pago);

		assertThatThrownBy(() -> aprobarCon(promotoraAdm, solicitud, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("pide tu código");
		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("VIGENTE");
		CodigoPromotoria.Pedido pedido = pedir(promotoraAdm, solicitud);
		aprobarCon(promotoraAdm, solicitud, EscenarioDirecta.escrito(jdbc, derivador, pedido));

		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("ANULADO");
		assertThat(jdbc.queryForObject("SELECT aprobado_por FROM anulacion_pago WHERE pago_id = ?", String.class, pago))
				.isEqualTo("promotora.adm.dir");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE plantilla = 'PAGO_ANULADO'", Long.class))
				.as("la familia se entera").isPositive();
		assertThat(EscenarioDirecta.destinos(jdbc, "ACCION_DIRECTA")).contains(CORREO_CONTROL);
	}

	/** A1c-9: con una sola Promotoría, lo que anula o devuelve dinero lo ven también los operadores de la plataforma. */
	@Test
	void unaSolaPromotoriaAvisaALosOperadores() {
		jdbc.update("UPDATE usuario_rol SET rol = 'DOCENTE' WHERE rol = 'PROMOTOR' AND usuario_id IN (?, ?)",
				e.promotora().getId(), e.socia().getId());
		Usuario operador = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma.operador(usuarios, codificador,
				colegios, "operador.avisos");
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		EscenarioCobranza.como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzo), "450.00", "500.00"));
		SecurityContextHolder.clearContext();
		como(promotoraAdm, () -> anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION));
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "pago", pago);
		CodigoPromotoria.Pedido pedido = pedir(promotoraAdm, solicitud);
		aprobarCon(promotoraAdm, solicitud, EscenarioDirecta.escrito(jdbc, derivador, pedido));

		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("ANULADO");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE usuario_id = ? AND plantilla = 'ACCION_DIRECTA'",
				Long.class, operador.getId())).isEqualTo(1L);
	}

	@Test
	void unDescuentoPideElCodigo() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		Long descuento = como(promotoraAdm, () -> descuentos.solicitar(EscenarioAprobaciones.descuento(f.mateo(),
				TipoDescuento.HERMANOS, "10", List.of(marzo))));
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "descuento", descuento);
		assertThatThrownBy(() -> aprobarCon(promotoraAdm, solicitud, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("pide tu código");
		assertThat(jdbc.queryForObject("SELECT estado FROM descuento WHERE id = ?", String.class, descuento))
				.isEqualTo("SOLICITADO");

		CodigoPromotoria.Pedido pedido = pedir(promotoraAdm, solicitud);
		aprobarCon(promotoraAdm, solicitud, EscenarioDirecta.escrito(jdbc, derivador, pedido));
		assertThat(jdbc.queryForObject("SELECT estado FROM descuento WHERE id = ?", String.class, descuento))
				.isEqualTo("APROBADO");
	}
}
