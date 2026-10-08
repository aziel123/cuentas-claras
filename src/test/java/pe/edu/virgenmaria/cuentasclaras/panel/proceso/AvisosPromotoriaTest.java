package pe.edu.virgenmaria.cuentasclaras.panel.proceso;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AvisosPromotoriaListos;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Sprint 6, tanda 2 (decisiones 70 y 71): las alertas CRÍTICAS (y las dos ATENCIÓN que también salen) llegan al celular
 * de Promotoría UNA sola vez, con texto fijo y sin nombres (P1, P2), la anulación por aprobar también a Dirección pero
 * nunca a quien participó, con tope diario (P19) y nunca en domingo.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AvisosPromotoriaTest {

	private static final LocalDate JUEVES = LocalDate.of(2027, 4, 15);

	@Autowired
	private AvisosPromotoria avisos;

	@Autowired
	private ApplicationEventPublisher eventos;

	@Autowired
	private PlatformTransactionManager transacciones;

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
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioPanel.Datos datos;

	private Usuario promotora;

	private Usuario directora;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		datos = EscenarioPanel.preparar(estructura, alumnos, planes, cobro, anulaciones, descuentos, bandeja, reloj, jdbc);
		promotora = guardar("promotora", Rol.PROMOTOR);
		directora = guardar("directora", Rol.DIRECTOR);
		guardar("caja", Rol.CAJA);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Usuario guardar(String nombre, Rol rol) {
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, rol);
	}

	private void a(LocalDate dia, int hora, int minuto) {
		reloj.fijar(dia.atTime(hora, minuto).atZone(reloj.getZone()).toInstant());
	}

	private List<Map<String, Object>> alertasDe(String tipo) {
		return jdbc.queryForList("SELECT usuario_id, canal, destino, parametros, creado_por, plantilla FROM mensaje "
				+ "WHERE tipo = 'ALERTA_PROMOTORIA' AND clave LIKE ? ORDER BY id", "ALERTA:" + tipo + ":%");
	}

	/** P1: la caja de un día anterior sin cerrar llega al celular de Promotoría UNA vez, con texto fijo y sin nombres. */
	@Test
	void unaAlertaCriticaLlegaAlCelularUnaSolaVezConTextoFijo() {
		LocalDate viernes = JUEVES.plusDays(1);
		a(viernes, 8, 0);
		avisos.enColegio(1L, viernes);
		avisos.enColegio(1L, viernes);

		assertThat(alertasDe("CAJA_SIN_CERRAR")).singleElement().satisfies(m -> {
			assertThat(m).containsEntry("usuario_id", promotora.getId()).containsEntry("canal", "WHATSAPP")
					.containsEntry("creado_por", "sistema.panel").containsEntry("plantilla", "ALERTA_PROMOTORIA");
			assertThat((String) m.get("parametros")).isEqualTo("Una caja de un día anterior sigue abierta\n15/04/2027")
					.doesNotContain("Nombre de", "no hizo su cierre");
		});
		// Dirección no recibe las de caja (solo la anulación por aprobar).
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'ALERTA_PROMOTORIA' AND usuario_id = ?",
				Long.class, directora.getId())).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'AVISOS_PROMOTORIA_ENVIADOS'",
				Long.class)).isEqualTo(1);
	}

	/** P2: la caja de hoy abierta pasada la hora límite (ATENCIÓN) también sale, con la hora límite como dato. */
	@Test
	void cajaAbiertaPasadaLaHoraLimiteAvisaAPromotoria() {
		a(JUEVES, 18, 45);
		avisos.enColegio(1L, JUEVES);
		assertThat(alertasDe("CIERRE_NO_REALIZADO")).isEmpty();
		a(JUEVES, 19, 15);
		avisos.enColegio(1L, JUEVES);
		assertThat(alertasDe("CIERRE_NO_REALIZADO")).singleElement().satisfies(m -> assertThat((String) m.get("parametros"))
				.isEqualTo("Una caja no se cerró a la hora límite\n19:00"));
	}

	/**
	 * Decisión 71: la anulación de pago por aprobar llega a Promotoría y a Dirección, pero nunca a quien participó: aquí,
	 * la directora que creó la cuenta de la cajera del pago hace una semana (A5).
	 */
	@Test
	void anulacionPorAprobarAvisaADireccionPeroNoAQuienParticipo() {
		Usuario padrino = guardar("director.padrino", Rol.DIRECTOR);
		jdbc.update("UPDATE usuario SET creado_por = 'director.padrino', creado_en = ? WHERE nombre_usuario = 'caja'",
				JUEVES.minusDays(7).atTime(9, 0));
		a(JUEVES, 10, 0);
		como(EscenarioCaja.CAJA);
		anulaciones.solicitarDevolucion(datos.pagoEfectivo(), EscenarioAprobaciones.MOTIVO_ANULACION);
		SecurityContextHolder.clearContext();

		avisos.enColegio(1L, JUEVES);

		List<Map<String, Object>> anulacion = alertasDe("ANULACION_PAGO_PENDIENTE");
		assertThat(anulacion).extracting(m -> m.get("usuario_id")).containsExactlyInAnyOrder(promotora.getId(),
				directora.getId()).doesNotContain(padrino.getId());
		assertThat(anulacion).allSatisfy(m -> assertThat((String) m.get("parametros"))
				.isEqualTo("Anulación de pago por aprobar\nS/ 350.00"));
	}

	/**
	 * P19 con S6-B2 (QA-S6-7): 10 alertas de ATENCIÓN por persona y día como máximo; lo retenido en cada pasada sale en un
	 * solo «hoy hay N alertas más» de esa pasada; las CRÍTICAS no cuentan para el tope ni se retienen.
	 */
	@Test
	void tope10PorDiaYUnoDeResumen() {
		a(JUEVES, 9, 0);
		publicar(JUEVES, IntStream.rangeClosed(1, 12).mapToObj(i -> new Aviso(TipoAviso.CIERRE_NO_REALIZADO, "T" + i))
				.toList());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE usuario_id = ? AND plantilla = "
				+ "'ALERTA_PROMOTORIA'", Long.class, promotora.getId())).isEqualTo(10);
		assertThat(jdbc.queryForList("SELECT parametros FROM mensaje WHERE usuario_id = ? AND plantilla = 'ALERTA_MAS'",
				String.class, promotora.getId())).containsExactly("2");
		// Otra pasada el mismo día: la ATENCIÓN nueva se retiene y sale su propio «1 más»; la CRÍTICA sale igual.
		a(JUEVES, 9, 15);
		publicar(JUEVES, List.of(new Aviso(TipoAviso.CIERRE_NO_REALIZADO, "T13"), new Aviso(TipoAviso.OTRA_CRITICA, "C1")));
		assertThat(jdbc.queryForList("SELECT parametros FROM mensaje WHERE usuario_id = ? AND plantilla = 'ALERTA_MAS' "
				+ "ORDER BY id", String.class, promotora.getId())).containsExactly("2", "1");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE usuario_id = ? AND clave LIKE "
				+ "'ALERTA:OTRA_CRITICA:C1:%'", Long.class, promotora.getId())).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE usuario_id = ?", Long.class, promotora.getId()))
				.isEqualTo(13);
		// Al día siguiente salen las retenidas (una sola vez cada una).
		LocalDate viernes = JUEVES.plusDays(1);
		a(viernes, 7, 0);
		publicar(viernes, IntStream.rangeClosed(1, 13).mapToObj(i -> new Aviso(TipoAviso.CIERRE_NO_REALIZADO, "T" + i))
				.toList());
		assertThat(jdbc.queryForList("SELECT clave FROM mensaje WHERE usuario_id = ? AND plantilla = 'ALERTA_PROMOTORIA' "
				+ "AND creado_en >= ?", String.class, promotora.getId(), viernes.atStartOfDay()))
				.containsExactlyInAnyOrder("ALERTA:CIERRE_NO_REALIZADO:T11:U" + promotora.getId() + ":WHATSAPP",
						"ALERTA:CIERRE_NO_REALIZADO:T12:U" + promotora.getId() + ":WHATSAPP",
						"ALERTA:CIERRE_NO_REALIZADO:T13:U" + promotora.getId() + ":WHATSAPP");
	}

	/** S6-B2: veinte CRÍTICAS en un día salen todas (no hay tope para lo crítico). */
	@Test
	void lasCriticasNoTienenTope() {
		a(JUEVES, 9, 0);
		publicar(JUEVES, IntStream.rangeClosed(1, 20).mapToObj(i -> new Aviso(TipoAviso.OTRA_CRITICA, "X" + i)).toList());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE usuario_id = ? AND plantilla = "
				+ "'ALERTA_PROMOTORIA'", Long.class, promotora.getId())).isEqualTo(20);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE usuario_id = ? AND plantilla = 'ALERTA_MAS'",
				Long.class, promotora.getId())).isZero();
	}

	/** Domingos y feriados no sale nada: sale el siguiente día de mensajes. */
	@Test
	void enDomingoNoSaleNada() {
		LocalDate domingo = LocalDate.of(2027, 4, 18);
		a(domingo, 9, 0);
		publicar(domingo, List.of(new Aviso(TipoAviso.OTRA_CRITICA, "DOM")));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'ALERTA_PROMOTORIA'", Long.class))
				.isZero();
	}

	/** Lo que haría AvisosPromotoria con esas alertas: como sistema.panel, en una transacción. */
	private void publicar(LocalDate fecha, List<Aviso> lista) {
		EjecucionComoSistema.como(ActorSistema.PANEL, 1L, () -> new TransactionTemplate(transacciones)
				.executeWithoutResult(t -> eventos.publishEvent(new AvisosPromotoriaListos(1L, fecha, lista))));
	}
}
