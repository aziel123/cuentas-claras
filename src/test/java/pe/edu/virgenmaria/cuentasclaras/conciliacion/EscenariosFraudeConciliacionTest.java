package pe.edu.virgenmaria.cuentasclaras.conciliacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.DepositoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.EstadoCierreVista;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.Extracto;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.VistaDiferencias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.AlertasConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ResumenConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.SaldoNoCoincideException;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioPartidas;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.proceso.ImportadorLiquidaciones;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION_2;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.estadoExtracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.partidas;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.verificacionDePago;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 4, tanda 3: los fraudes que la conciliación automática con el extracto del banco debe destapar (sección 14 del
 * diseño). El extracto lo sube Administración y lo confirma a ciegas otra persona (Promotoría) escribiendo el saldo que
 * ve en su app del banco; lo que el banco no muestra queda en rojo para Promotoría al día hábil siguiente.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class EscenariosFraudeConciliacionTest {

	/** Lunes 5 de octubre de 2026, 09:00 en Lima: el día hábil siguiente al viernes de los cobros. */
	static final Instant LUNES = Instant.parse("2026-10-05T14:00:00Z");

	@Autowired
	private ServicioExtractos extractos;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private ServicioPartidas servicioPartidas;

	@Autowired
	private ResumenConciliacion resumen;

	@Autowired
	private AlertasConciliacion alertas;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioCierreCaja cierre;

	@Autowired
	private ServicioRecaudacion recaudacion;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioPagoEnLinea pagoEnLinea;

	@Autowired
	private PasarelaSimulada pasarela;

	@Autowired
	private ImportadorLiquidaciones importador;

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

	private Long cuentaId;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		cuentaId = EscenarioConciliacion.cuenta(cuentas);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private List<AlertaRevision> alertasDePromotoria() {
		como(PROMOTORIA);
		return alertas.alertas();
	}

	private Long yape(Long alumno, String operacion) {
		como(CAJA);
		return cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, alumno, "PEN-2027-03")), MedioPago.YAPE, operacion,
				"450.00"));
	}

	/** Administración sube el extracto y Promotoría escribe a ciegas el saldo que ve en su app del banco. */
	private Long subidoYConfirmado(Extracto banco) {
		Long id = registrar(extractos, ADMINISTRACION, banco);
		confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		assertThat(estadoExtracto(jdbc, id)).isEqualTo("CONFIRMADO");
		return id;
	}

	/**
	 * F21 (el fraude del caso): la cajera registra un Yape que nunca llegó para tapar efectivo que se llevó. El viernes
	 * nadie lo nota; el lunes (día hábil siguiente), con el extracto del viernes confirmado a ciegas, ese Yape aparece en
	 * rojo para Promotoría con quién lo cobró, mientras el Yape real queda verificado solo.
	 */
	@Test
	void unYapeInventadoEnCajaApareceEnRojoAlDiaHabilSiguiente() {
		Long real = yape(f.valeria(), "YP111222");
		Long inventado = yape(f.mateo(), "YP999888");
		// El mismo viernes no hay alarma: el banco todavía no lo muestra.
		assertThat(alertasDePromotoria()).noneMatch(a -> a.texto().contains("YP999888"));

		reloj.fijar(LUNES);
		Extracto banco = extracto("10000.00").abono("2026-10-02", "YAPE DE ROSA QUISPE", "YP111222", "450.00")
				.cargo("2026-10-02", "MANTENIMIENTO DE CUENTA", "", "15.00");
		Long id = registrar(extractos, ADMINISTRACION, banco);
		// Cargado pero sin confirmar: todavía no cuenta (quien lo subió podría haberlo editado).
		assertThat(alertasDePromotoria()).noneMatch(a -> a.texto().contains("YP999888"));
		assertThat(partidas(jdbc)).containsExactly("EXACTA PROPUESTA PAGO");
		assertThat(verificacionDePago(jdbc, real)).isNull();

		confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		assertThat(estadoExtracto(jdbc, id)).isEqualTo("CONFIRMADO");
		// El Yape real se concilió y verificó solo; el inventado no tiene pareja.
		assertThat(partidas(jdbc)).containsExactly("EXACTA CONFIRMADA PAGO");
		assertThat(verificacionDePago(jdbc, real)).isEqualTo("AUTOMATICA ENCONTRADO");
		assertThat(verificacionDePago(jdbc, inventado)).isNull();
		assertThat(jdbc.queryForObject("SELECT creado_por FROM verificacion_bancaria WHERE pago_id = ?", String.class,
				real)).isEqualTo("sistema.conciliacion");

		List<AlertaRevision> lista = alertasDePromotoria();
		assertThat(lista).filteredOn(a -> a.texto().contains("YP999888")).singleElement().satisfies(a -> {
			assertThat(a.gravedad()).isEqualTo(Gravedad.CRITICA);
			assertThat(a.texto()).contains("Yape", "S/ 450.00", "del 02/10/2026", "NO aparece en el banco",
					"¿Pago inventado para tapar efectivo?", "cobró");
			assertThat(a.enlace()).isEqualTo("/conciliacion");
		});
		// El Yape real no alarma (solo puede salir en el muestreo informativo del día).
		assertThat(lista).noneMatch(a -> a.gravedad() != Gravedad.INFORMATIVA && a.texto().contains("YP111222"));

		VistaDiferencias vista = resumen.diferencias();
		assertThat(vista.cubiertoHasta()).isEqualTo(LocalDate.of(2026, 10, 2));
		assertThat(vista.faltantes()).singleElement().satisfies(falta -> {
			assertThat(falta.operacion()).isEqualTo("YP999888");
			assertThat(falta.monto()).isEqualByComparingTo("450.00");
		});
		// Solo diferencias: el Yape real no aparece, y el cargo del banco tampoco (no es de la cobranza).
		assertThat(vista.sugeridas()).isEmpty();
		assertThat(vista.sinPareja()).isEmpty();
		assertThat(vista.resumen().emparejados()).isEqualTo(1);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'CONCILIACION_AUTOMATICA'")).isEqualTo(1);
	}

	/**
	 * F21 con complicidad: Administración agrega al extracto el abono del Yape inventado (y recalcula los saldos). El
	 * saldo final ya no es el del banco: quien confirma escribe el de su app, no coincide dos veces y el extracto queda
	 * RECHAZADO sin conciliar nada.
	 */
	@Test
	void unExtractoEditadoParaTaparElYapeQuedaRechazado() {
		Long inventado = yape(f.mateo(), "YP999888");
		reloj.fijar(LUNES);
		Extracto verdadero = extracto("10000.00").cargo("2026-10-02", "MANTENIMIENTO DE CUENTA", "", "15.00");
		Extracto editado = extracto("10000.00").abono("2026-10-02", "YAPE DE ROSA QUISPE", "YP999888", "450.00")
				.cargo("2026-10-02", "MANTENIMIENTO DE CUENTA", "", "15.00");
		Long id = registrar(extractos, ADMINISTRACION, editado);

		for (int intento = 1; intento <= 2; intento++) {
			assertThatThrownBy(() -> confirmar(extractos, PROMOTORIA, cuentaId,
					verdadero.saldoFinal().toPlainString())).isInstanceOf(SaldoNoCoincideException.class);
		}
		assertThat(estadoExtracto(jdbc, id)).isEqualTo("RECHAZADO");
		assertThat(partidas(jdbc)).isEmpty();
		assertThat(verificacionDePago(jdbc, inventado)).isNull();
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'EXTRACTO_SALDO_NO_COINCIDE'")).isEqualTo(2);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'EXTRACTO_RECHAZADO'")).isEqualTo(1);
		assertThat(alertasDePromotoria()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().contains("RECHAZADO") && a.texto().contains("administracion"));
	}

	/** F6: la cajera declara un depósito que no hizo. El extracto confirmado no lo trae: CRÍTICO al día siguiente. */
	@Test
	void unDepositoQueNoSeHizoEsCritico() {
		como(CAJA);
		cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00", "450.00"));
		cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null));
		EstadoCierreVista estado = cierre.estado();
		cierre.registrarDeposito(new DepositoRequest(estado.porDepositar().getFirst().cajaId(),
				estado.cuentas().getFirst(), "OP-5555", estado.hoy(), new BigDecimal("450.00"), null));

		reloj.fijar(LUNES);
		subidoYConfirmado(extracto("10000.00").cargo("2026-10-02", "MANTENIMIENTO DE CUENTA", "", "15.00"));

		assertThat(alertasDePromotoria()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().contains("el depósito OP5555 por S/ 450.00") && a.texto().contains("NO aparece en el banco"));
	}

	/** Con el depósito en el banco, el sistema lo empareja y lo verifica solo (nadie lo marca a mano). */
	@Test
	void elDepositoQueSiLlegoSeVerificaSolo() {
		como(CAJA);
		cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00", "450.00"));
		cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null));
		EstadoCierreVista estado = cierre.estado();
		cierre.registrarDeposito(new DepositoRequest(estado.porDepositar().getFirst().cajaId(),
				estado.cuentas().getFirst(), "OP-5555", estado.hoy(), new BigDecimal("450.00"), null));

		reloj.fijar(LUNES);
		subidoYConfirmado(extracto("10000.00").abono("2026-10-02", "DEPOSITO EN EFECTIVO VENTANILLA", "5555", "450.00"));

		assertThat(partidas(jdbc)).containsExactly("SUGERIDA PROPUESTA DEPOSITO");
		// La sugerida la confirma alguien que no depositó (Administración, no la cajera); entonces se verifica sola.
		como(ADMINISTRACION);
		Long partida = jdbc.queryForObject("SELECT id FROM partida_conciliacion", Long.class);
		servicioPartidas.confirmarSugerida(partida);
		assertThat(jdbc.queryForObject("SELECT CONCAT(origen, ' ', resultado) FROM verificacion_bancaria WHERE "
				+ "deposito_id IS NOT NULL", String.class)).isEqualTo("AUTOMATICA ENCONTRADO");
		assertThat(alertasDePromotoria()).noneMatch(a -> a.texto().contains("OP5555"));
	}

	/**
	 * F9 del lado del banco: un lote de recaudación confirmado cuyo abono nunca llega al extracto (archivo fabricado, o
	 * el banco no abonó) queda CRÍTICO; y el abono que sí llega solo lo confirma alguien que no subió el lote.
	 */
	@Test
	void unLoteDeRecaudacionSinAbonoEsCriticoYElQueLlegaLoConfirmaOtraPersona() {
		EscenarioRecaudacion.Archivo archivo = EscenarioRecaudacion.archivo()
				.pago(f.mateo(), cuota(jdbc, f.mateo(), "PEN-2027-03"), "450.00", "REC-001");
		Long lote = EscenarioRecaudacion.registrar(recaudacion, ADMINISTRACION, archivo);
		EscenarioRecaudacion.confirmar(recaudacion, jdbc, PROMOTORIA, lote, "450.00");
		Long pagoBanco = jdbc.queryForObject("SELECT p.id FROM pago p JOIN linea_recaudacion l "
				+ "ON l.id = p.linea_recaudacion_id WHERE l.lote_id = ?", Long.class, lote);

		// Un pago por banco sin verificar no se anula (se anularía un pago que quizá nunca llegó).
		como(ADMINISTRACION);
		assertThatThrownBy(() -> anulaciones.solicitarDevolucion(pagoBanco, EscenarioAprobaciones.MOTIVO_ANULACION))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("todavía no está verificado");

		reloj.fijar(LUNES);
		Extracto sinAbono = extracto("10000.00").cargo("2026-10-01", "MANTENIMIENTO DE CUENTA", "", "15.00")
				.cargo("2026-10-02", "ITF", "", "0.50");
		subidoYConfirmado(sinAbono);
		assertThat(alertasDePromotoria()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().startsWith("Recaudación cargada que el banco no abonó: Recaudación del banco (lote N.° " + lote));

		// El abono llega en el extracto siguiente (continúa al anterior).
		reloj.fijar(LUNES.plusSeconds(24 * 3600));
		Extracto conAbono = extracto(sinAbono.saldoFinal().toPlainString())
				.abonoConReferencia("2026-10-05", "ABONO RECAUDACION CODIGO ALUMNO", "LOTE 1", "450.00");
		subidoYConfirmado(conAbono);
		assertThat(partidas(jdbc)).containsExactly("SUGERIDA PROPUESTA LOTE_RECAUDACION");
		Long partida = jdbc.queryForObject("SELECT id FROM partida_conciliacion", Long.class);

		// Quien subió el lote no confirma su abono.
		como(ADMINISTRACION);
		assertThatThrownBy(() -> servicioPartidas.confirmarSugerida(partida))
				.isInstanceOf(AutoaprobacionException.class);
		assertThat(partidas(jdbc)).containsExactly("SUGERIDA PROPUESTA LOTE_RECAUDACION");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'AUTOAPROBACION_RECHAZADA'")).isEqualTo(1);

		como(ADMINISTRACION_2);
		servicioPartidas.confirmarSugerida(partida);
		assertThat(partidas(jdbc)).containsExactly("SUGERIDA CONFIRMADA LOTE_RECAUDACION");
		// Los pagos del lote quedan verificados en el banco sin que nadie los marque.
		assertThat(verificacionDePago(jdbc, pagoBanco)).isEqualTo("AUTOMATICA ENCONTRADO");
		assertThat(alertasDePromotoria()).noneMatch(a -> a.texto().startsWith("Recaudación cargada"));
		// Verificado por la conciliación, ya se puede pedir su anulación (la aprueba otra persona).
		como(ADMINISTRACION);
		anulaciones.solicitarDevolucion(pagoBanco, EscenarioAprobaciones.MOTIVO_ANULACION);
		assertThat(EscenarioAprobaciones.pendiente(jdbc, "pago", pagoBanco)).isNotNull();
	}

	/**
	 * F14: la pasarela cobró algo que el colegio no registró (el aviso nunca llegó o alguien lo desvió). La liquidación
	 * lo trae como cargo sin pago: CRÍTICO.
	 */
	@Test
	void unCargoDeLaPasarelaSinPagoRegistradoEsCritico() {
		// Un día que ninguna otra prueba usa: la pasarela simulada guarda sus cobros en memoria y liquida por día.
		reloj.fijar(Instant.parse("2026-10-21T15:00:00Z"));
		UsuarioAutenticado rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa());
		UsuariosDePrueba.iniciarSesion(rosa);
		List<Long> cuotas = List.of(cuota(jdbc, f.mateo(), "PEN-2027-03"));
		BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(cuotas)).total();
		String referencia = pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), cuotas, total, false));
		// La pasarela cobra, pero el aviso no llega al colegio.
		pasarela.simular(pasarela.proveedorOrdenIdDe(referencia), PasarelaSimulada.Accion.YAPE);
		SecurityContextHolder.clearContext();

		reloj.fijar(Instant.parse("2026-10-22T15:00:00Z"));
		assertThat(importador.importar(1L, LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 22))).isEqualTo(1);
		assertThat(importador.importar(1L, LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 22))).isZero();
		assertThat(contar(jdbc, "liquidacion_linea WHERE pago_id IS NULL")).isEqualTo(1);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'LIQUIDACION_SIN_PAGO'")).isEqualTo(1);
		assertThat(alertasDePromotoria()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().contains("liquidados sin pago registrado"));
	}
}
