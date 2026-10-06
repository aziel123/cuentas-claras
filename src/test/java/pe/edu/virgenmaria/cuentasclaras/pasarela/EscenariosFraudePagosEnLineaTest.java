package pe.edu.virgenmaria.cuentasclaras.pasarela;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.EstadoOrdenVista;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.proceso.ProcesadorPagosEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.proceso.RecepcionAvisos;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ConsultaPagosEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada.Accion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.SimuladorPagos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.estado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 4, tanda 1: el pago en línea de punta a punta (con la pasarela simulada) y los fraudes que debe impedir. Nadie
 * digita el pago: lo registra el sistema cuando la pasarela confirma. Contra MySQL real (triggers y permisos mínimos)
 * lo prueba {@code PermisosMySqlTest}.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class EscenariosFraudePagosEnLineaTest {

	@Autowired
	private ServicioPagoEnLinea pagoEnLinea;

	@Autowired
	private SimuladorPagos simulador;

	@Autowired
	private PasarelaSimulada pasarela;

	@Autowired
	private RecepcionAvisos recepcion;

	@Autowired
	private ProcesadorPagosEnLinea procesador;

	@Autowired
	private ConsultaPagosEnLinea consulta;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private Clock reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	private UsuarioAutenticado rosa;

	private UsuarioAutenticado pedro;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		rosa = apoderado(40, "rosa.familia", f.rosa());
		pedro = apoderado(41, "pedro.familia", f.pedro());
		como(rosa);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private static UsuarioAutenticado apoderado(long id, String usuario, Long apoderadoId) {
		return new UsuarioAutenticado(id, 1L, usuario, "Nombre de " + usuario, null, true, false, false,
				EnumSet.of(Rol.APODERADO), apoderadoId);
	}

	private String iniciarPago(List<Long> cuotas) {
		BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(cuotas)).total();
		return pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), cuotas, total, false));
	}

	private Map<String, Object> orden(String referencia) {
		return jdbc.queryForMap("SELECT * FROM orden_pago WHERE referencia = ?", referencia);
	}

	/** El camino feliz: el apoderado paga con Yape (simulado), el sistema registra el pago y emite la boleta. */
	@Test
	void pagoEnLineaDePuntaAPuntaSinQueNadieDigite() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		Long abril = cuota(jdbc, f.mateo(), "PEN-2027-04");
		assertThat(pagoEnLinea.cuenta().hijos()).isNotEmpty();

		String referencia = iniciarPago(List.of(marzo, abril));
		EstadoOrdenVista enCurso = pagoEnLinea.estado(referencia);
		assertThat(enCurso.estado()).isEqualTo("CREADA");
		assertThat(enCurso.puedePagar()).isTrue();
		assertThat(enCurso.simulada()).isTrue();

		assertThat(simulador.simular(referencia, Accion.YAPE)).isEqualTo(RecepcionAvisos.Resultado.ACEPTADO);

		EstadoOrdenVista pagada = pagoEnLinea.estado(referencia);
		assertThat(pagada.estado()).isEqualTo("PAGADA");
		assertThat(pagada.comprobante()).startsWith("B001-");
		assertThat(estado(jdbc, marzo)).isEqualTo("PAGADA");
		assertThat(estado(jdbc, abril)).isEqualTo("PAGADA");
		Map<String, Object> pago = jdbc.queryForMap("SELECT p.origen, p.medio, p.total, p.cajero, c.canal, c.cajero AS caja "
				+ "FROM pago p JOIN caja_diaria c ON c.id = p.caja_diaria_id WHERE p.orden_pago_id = ?",
				orden(referencia).get("id"));
		assertThat(pago).containsEntry("origen", "PASARELA").containsEntry("medio", "YAPE")
				.containsEntry("canal", "PASARELA").containsEntry("cajero", "sistema.pasarela");
		assertThat((BigDecimal) pago.get("total")).isEqualByComparingTo("900.00");
		// La boleta se envió sola al OSE (simulado) y quedó aceptada.
		assertThat(jdbc.queryForObject("SELECT c.estado_envio FROM comprobante c JOIN pago p ON p.comprobante_id = c.id "
				+ "WHERE p.orden_pago_id = ?", String.class, orden(referencia).get("id"))).isEqualTo("ACEPTADO");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion IN ('ORDEN_PAGO_CREADA', 'PASARELA_SIMULADA_USADA')"))
				.isEqualTo(2);
	}

	/** Volver de la pasarela no basta: sin confirmación de la pasarela, la orden sigue en curso. */
	@Test
	void volverDeLaPasarelaNoMarcaPagado() {
		String referencia = iniciarPago(List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")));
		((RelojAjustable) reloj).avanzar(Duration.ofSeconds(30));

		assertThat(pagoEnLinea.estado(referencia).estado()).isEqualTo("CREADA");
		assertThat(contar(jdbc, "pago")).isZero();
	}

	/** Un aviso repetido (o reenviado por un atacante) no registra el pago dos veces. */
	@Test
	void unAvisoRepetidoNoDuplicaElPago() {
		String referencia = iniciarPago(List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")));
		simulador.simular(referencia, Accion.YAPE);
		String idEnPasarela = pasarela.proveedorOrdenIdDe(referencia);
		PasarelaSimulada.AvisoFirmado otraVez = pasarela.aviso(idEnPasarela, "SIMEVT-REPETIDO");

		assertThat(recepcion.recibir("SIMULADA", 1L, otraVez.cuerpo(), otraVez.cabeceras()))
				.isEqualTo(RecepcionAvisos.Resultado.ACEPTADO);
		assertThat(recepcion.recibir("SIMULADA", 1L, otraVez.cuerpo(), otraVez.cabeceras()))
				.isEqualTo(RecepcionAvisos.Resultado.YA_RECIBIDO);
		assertThat(contar(jdbc, "pago")).isEqualTo(1);
		assertThat(contar(jdbc, "aplicacion_pago")).isEqualTo(1);
	}

	/** Un aviso con firma falsa («la pasarela dice que pagué») no cambia nada. */
	@Test
	void unAvisoConFirmaFalsaSeRechaza() {
		String referencia = iniciarPago(List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")));
		byte[] cuerpo = ("evento=FALSO1&tipo=orden.pagada&referencia=" + referencia).getBytes();

		assertThat(recepcion.recibir("SIMULADA", 1L, cuerpo, Map.of(PasarelaSimulada.CABECERA_FIRMA, "00ff")))
				.isEqualTo(RecepcionAvisos.Resultado.NO_AUTENTICO);
		assertThat(recepcion.recibir("SIMULADA", 1L, cuerpo, Map.of())).isEqualTo(RecepcionAvisos.Resultado.NO_AUTENTICO);
		assertThat(orden(referencia)).containsEntry("estado", "CREADA");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(contar(jdbc, "evento_pasarela")).isZero();
	}

	/** Un aviso auténtico basta para despertar al sistema, pero lo que cuenta es la CONSULTA a la pasarela. */
	@Test
	void unAvisoAutenticoSinPagoNoMarcaPagado() {
		String referencia = iniciarPago(List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")));
		PasarelaSimulada.AvisoFirmado aviso = pasarela.aviso(pasarela.proveedorOrdenIdDe(referencia), "SIMEVT-SIN-PAGO");

		assertThat(recepcion.recibir("SIMULADA", 1L, aviso.cuerpo(), aviso.cabeceras()))
				.isEqualTo(RecepcionAvisos.Resultado.ACEPTADO);
		assertThat(orden(referencia)).containsEntry("estado", "CREADA");
		assertThat(contar(jdbc, "pago")).isZero();
	}

	/** La pasarela confirma MENOS de lo pedido: no se aplica a ninguna cuota; queda por revisar. */
	@Test
	void unMontoDistintoQuedaPorRevisarSinAplicarse() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		String referencia = iniciarPago(List.of(marzo));

		simulador.simular(referencia, Accion.MONTO_MENOR);

		assertThat(orden(referencia)).containsEntry("estado", "POR_REVISAR").containsEntry("motivo_revision", "MONTO_DISTINTO");
		assertThat(estado(jdbc, marzo)).isEqualTo("PENDIENTE");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'ORDEN_PAGO_POR_REVISAR'")).isEqualTo(1);
	}

	/** El monto lo calcula el servidor: si el saldo cambió desde la revisión, no se crea la orden. */
	@Test
	void elMontoLoCalculaElServidor() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");

		assertThatThrownBy(() -> pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), List.of(marzo),
				new BigDecimal("1.00"), false))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cambió");
		assertThat(contar(jdbc, "orden_pago")).isZero();
	}

	/** Doble toque o recarga: la misma clave devuelve la misma orden. */
	@Test
	void laMismaClaveDevuelveLaMismaOrden() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		PagoEnLineaRequest pedido = new PagoEnLineaRequest(UUID.randomUUID(), List.of(marzo), new BigDecimal("450.00"),
				false);

		assertThat(pagoEnLinea.crearOrden(pedido)).isEqualTo(pagoEnLinea.crearOrden(pedido));
		assertThat(contar(jdbc, "orden_pago")).isEqualTo(1);
		assertThat(contar(jdbc, "orden_pago_cuota")).isEqualTo(1);
	}

	/** Un apoderado no ve, no paga ni simula órdenes ni cuotas de otra familia (404, sin pistas). */
	@Test
	void unApoderadoNoTocaLoDeOtraFamilia() {
		Long marzoMateo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		String deRosa = iniciarPago(List.of(marzoMateo));
		como(pedro);

		assertThatThrownBy(() -> pagoEnLinea.estado(deRosa)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> simulador.simular(deRosa, Accion.YAPE)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> pagoEnLinea.revisar(new SeleccionPagoRequest(List.of(marzoMateo))))
				.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), List.of(marzoMateo),
				new BigDecimal("450.00"), false))).isInstanceOf(RuntimeException.class);
		assertThat(pagoEnLinea.cuenta().familia()).doesNotContain("Quispe");
		assertThat(contar(jdbc, "orden_pago")).isEqualTo(1);
	}

	/** Una cuenta de personal no puede pagar «como apoderado», ni un apoderado mirar los pagos de todos. */
	@Test
	void losRolesNoSeMezclan() {
		como(CAJA);
		assertThatThrownBy(() -> pagoEnLinea.cuenta()).isInstanceOfAny(AccessDeniedException.class,
				AuthorizationDeniedException.class);
		como(rosa);
		assertThatThrownBy(() -> consulta.lista()).isInstanceOfAny(AccessDeniedException.class,
				AuthorizationDeniedException.class);
		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")),
				"450.00", "450.00"))).isInstanceOfAny(AccessDeniedException.class, AuthorizationDeniedException.class);
	}

	/** Dos órdenes a la vez sobre la misma cuota: la segunda se rechaza (no se cobra dos veces en línea). */
	@Test
	void noHayDosPagosEnLineaParaLaMismaCuota() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		Long abril = cuota(jdbc, f.mateo(), "PEN-2027-04");
		iniciarPago(List.of(marzo));

		assertThatThrownBy(() -> iniciarPago(List.of(marzo, abril))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("en curso");
	}

	/**
	 * La cajera cobra en ventanilla una cuota con un pago en línea en curso (se le avisa, no se bloquea) y después la
	 * pasarela confirma: el dinero en línea NO se aplica dos veces; queda por revisar para devolverlo.
	 */
	@Test
	void cobroEnVentanillaYLuegoEnLineaNoCobraDosVeces() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		String referencia = iniciarPago(List.of(marzo));
		como(CAJA);
		assertThat(cobro.cuentaDeFamilia(f.quispe()).hayPagoEnLineaEnCurso()).isTrue();
		cobro.cobrar(efectivo(f.quispe(), List.of(marzo), "450.00", "450.00"));
		como(rosa);

		simulador.simular(referencia, Accion.YAPE);

		assertThat(orden(referencia)).containsEntry("estado", "POR_REVISAR")
				.containsEntry("motivo_revision", "CUOTA_NO_COBRABLE");
		assertThat(contar(jdbc, "pago")).isEqualTo(1);
		assertThat(EscenarioCaja.pagado(jdbc, marzo)).isEqualByComparingTo("450.00");
	}

	/** La pasarela confirma DESPUÉS de vencer la orden: si las cuotas siguen pendientes se aplica y queda marcado. */
	@Test
	void unPagoTardioSeAplicaYQuedaMarcado() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		String referencia = iniciarPago(List.of(marzo));
		Long ordenId = ((Number) orden(referencia).get("id")).longValue();
		((RelojAjustable) reloj).avanzar(Duration.ofMinutes(31));
		assertThat(procesador.procesar(1L, ordenId)).isEqualTo(ProcesadorPagosEnLinea.Resultado.VENCIDA);

		simulador.simular(referencia, Accion.YAPE);

		assertThat(orden(referencia)).containsEntry("estado", "PAGADA").containsEntry("tardia", true);
		assertThat(estado(jdbc, marzo)).isEqualTo("PAGADA");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'PAGO_EN_LINEA_TARDIO'")).isEqualTo(1);
	}

	/** Pago rechazado por la pasarela: nada se cobra y la cuota queda libre para otro intento. */
	@Test
	void unPagoRechazadoLiberaLaCuota() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		String referencia = iniciarPago(List.of(marzo));

		simulador.simular(referencia, Accion.RECHAZAR);

		assertThat(orden(referencia)).containsEntry("estado", "RECHAZADA");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(iniciarPago(List.of(marzo))).isNotEqualTo(referencia);
	}

	/** Contracargo: alerta crítica en la bitácora y solicitud de anulación para revisión; nada se anula solo. */
	@Test
	void unContracargoPideLaAnulacionSinAnularSolo() {
		String referencia = iniciarPago(List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")));
		simulador.simular(referencia, Accion.YAPE);

		simulador.simular(referencia, Accion.CONTRACARGO);

		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'CONTRACARGO_RECIBIDO'")).isEqualTo(1);
		assertThat(contar(jdbc, "solicitud_cambio WHERE estado = 'PENDIENTE'")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT estado FROM pago", String.class)).isEqualTo("VIGENTE");
	}

	/** Las órdenes en curso vencen solas (tarea programada) y la pasarela simulada lo deja en la bitácora. */
	@Test
	void elPersonalVeLosPagosEnLineaYLosPorRevisarPrimero() {
		String pagada = iniciarPago(List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")));
		simulador.simular(pagada, Accion.YAPE);
		String porRevisar = iniciarPago(List.of(cuota(jdbc, f.mateo(), "PEN-2027-04")));
		simulador.simular(porRevisar, Accion.MONTO_MENOR);
		como(EscenarioCobranza.PROMOTORIA);

		var lista = consulta.lista();

		assertThat(lista.porRevisar()).extracting(r -> r.referencia()).containsExactly(porRevisar);
		assertThat(lista.hoyPagadas()).extracting(r -> r.referencia()).containsExactly(pagada);
		assertThat(lista.simulada()).isTrue();
	}
}
