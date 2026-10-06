package pe.edu.virgenmaria.cuentasclaras.pasarela;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.AplicacionIngresoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.TipoLineaLiquidacion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.proceso.ProcesadorPagosEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.proceso.RecepcionAvisos;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ConsultaPagosEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.LiquidacionLeida;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.RegistroLiquidaciones;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioIngresosPorRevisar;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada.Accion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.SimuladorPagos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.estado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * QA del sprint 4 (pago en línea): contracargos en todos los estados de la orden, contracargo por la liquidación,
 * vencimiento al segundo, medianoche de fin de mes en Lima, céntimos con descuento, monto máximo, carrera entre caja y
 * pasarela sobre la misma cuota y aislamiento entre colegios (F24, cuya clase de prueba nombrada en el diseño no existe).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class PagosEnLineaQaTest {

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
	private ServicioIngresosPorRevisar ingresos;

	@Autowired
	private RegistroLiquidaciones liquidaciones;

	@Autowired
	private BandejaAprobaciones bandeja;

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
	private ColegioRepository colegios;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	private UsuarioAutenticado rosa;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa());
		como(rosa);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private String iniciarPago(List<Long> cuotas) {
		BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(cuotas)).total();
		return pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), cuotas, total, false));
	}

	private Long ordenId(String referencia) {
		return jdbc.queryForObject("SELECT id FROM orden_pago WHERE referencia = ?", Long.class, referencia);
	}

	private String estadoOrden(String referencia) {
		return jdbc.queryForObject("SELECT estado FROM orden_pago WHERE referencia = ?", String.class, referencia);
	}

	/** La cajera cobra marzo en ventanilla con la orden abierta y luego la pasarela confirma: queda POR_REVISAR. */
	private String ordenPorRevisar(Long marzo) {
		String referencia = iniciarPago(List.of(marzo));
		como(CAJA);
		cobro.cobrar(efectivo(f.quispe(), List.of(marzo), "450.00", "450.00"));
		como(rosa);
		simulador.simular(referencia, Accion.YAPE);
		assertThat(estadoOrden(referencia)).isEqualTo("POR_REVISAR");
		return referencia;
	}

	// ------------------------------------------------------------------ contracargos

	/**
	 * Sección 5: «un contracargo posterior no cambia la orden: genera una alerta y una solicitud de anulación
	 * prellenada». Una orden POR_REVISAR que Administración aplicó a otra cuota (APLICADA) tiene un pago vigente con su
	 * boleta, igual que una PAGADA: si el apoderado desconoce el cargo, el dinero sale y nadie se entera.
	 */
	@Test
	void debeAlertarElContracargoDeUnaOrdenAplicadaTrasRevision() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		Long abril = cuota(jdbc, f.mateo(), "PEN-2027-04");
		String referencia = ordenPorRevisar(marzo);
		como(EscenarioCobranza.ADMINISTRACION);
		ingresos.solicitarAplicacion(ordenId(referencia), new AplicacionIngresoRequest(f.quispe(), List.of(abril),
				"La familia pidió aplicarlo a abril por teléfono"));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "orden_pago", ordenId(referencia));
		assertThat(estadoOrden(referencia)).isEqualTo("APLICADA");
		como(rosa);

		simulador.simular(referencia, Accion.CONTRACARGO);

		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'CONTRACARGO_RECIBIDO'")).isEqualTo(1);
		assertThat(contar(jdbc, "solicitud_cambio WHERE estado = 'PENDIENTE' AND tipo = 'ANULACION_PAGO'")).isEqualTo(1);
	}

	/**
	 * Un ingreso POR_REVISAR cuyo cargo el apoderado desconoce: el dinero ya no está, pero nada lo marca y Administración
	 * puede pedir aplicarlo a otra cuota (el sistema emitiría una boleta por dinero que el banco devolvió).
	 */
	@Test
	void debeAlertarElContracargoDeUnIngresoPorRevisar() {
		String referencia = ordenPorRevisar(cuota(jdbc, f.mateo(), "PEN-2027-03"));

		simulador.simular(referencia, Accion.CONTRACARGO);

		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'CONTRACARGO_RECIBIDO'")).isEqualTo(1);
		como(EscenarioCobranza.ADMINISTRACION);
		assertThatThrownBy(() -> ingresos.solicitarAplicacion(ordenId(referencia), new AplicacionIngresoRequest(
				f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-04")), "Aplicarlo a abril como pidió la familia")))
				.isInstanceOf(ReglaNegocioException.class);
	}

	/** Sección 10.1, punto 8: el contracargo «llega como aviso O como línea CONTRACARGO de la liquidación». */
	@Test
	void debeAlertarElContracargoQueLlegaEnLaLiquidacion() {
		String referencia = iniciarPago(List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")));
		simulador.simular(referencia, Accion.YAPE);
		String operacion = jdbc.queryForObject("SELECT numero_operacion FROM pago WHERE orden_pago_id = ?", String.class,
				ordenId(referencia));
		LiquidacionLeida leida = new LiquidacionLeida("LIQ-QA-1", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6),
				List.of(new LiquidacionLeida.Linea(TipoLineaLiquidacion.CARGO, operacion, new BigDecimal("450.00"),
						new BigDecimal("15.75"), new BigDecimal("2.84")),
						new LiquidacionLeida.Linea(TipoLineaLiquidacion.CONTRACARGO, operacion, new BigDecimal("-450.00"),
								BigDecimal.ZERO, BigDecimal.ZERO)));

		EjecucionComoSistema.como(ActorSistema.PASARELA, 1L, () -> new TransactionTemplate(transacciones)
				.execute(t -> liquidaciones.registrar(ProveedorPasarela.SIMULADA, leida)));

		assertThat(contar(jdbc, "liquidacion_linea WHERE tipo = 'CONTRACARGO'")).isEqualTo(1);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'CONTRACARGO_RECIBIDO'")).isEqualTo(1);
		assertThat(contar(jdbc, "solicitud_cambio WHERE estado = 'PENDIENTE' AND tipo = 'ANULACION_PAGO'")).isEqualTo(1);
	}

	@Test
	void debeRegistrarElNetoDeLaLiquidacionConComisionEIgvAlCentimo() {
		String referencia = iniciarPago(List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")));
		simulador.simular(referencia, Accion.YAPE);
		String operacion = jdbc.queryForObject("SELECT numero_operacion FROM pago WHERE orden_pago_id = ?", String.class,
				ordenId(referencia));
		LiquidacionLeida leida = new LiquidacionLeida("LIQ-QA-2", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6),
				List.of(new LiquidacionLeida.Linea(TipoLineaLiquidacion.CARGO, operacion, new BigDecimal("450.00"),
						new BigDecimal("15.75"), new BigDecimal("2.84"))));

		EjecucionComoSistema.como(ActorSistema.PASARELA, 1L, () -> new TransactionTemplate(transacciones)
				.execute(t -> liquidaciones.registrar(ProveedorPasarela.SIMULADA, leida)));
		// Repetida (la importación diaria trae los últimos 10 días): no se registra dos veces.
		EjecucionComoSistema.como(ActorSistema.PASARELA, 1L, () -> new TransactionTemplate(transacciones)
				.execute(t -> liquidaciones.registrar(ProveedorPasarela.SIMULADA, leida)));

		assertThat(jdbc.queryForObject("SELECT total_neto FROM liquidacion_pasarela", BigDecimal.class))
				.isEqualByComparingTo("431.41");
		assertThat(jdbc.queryForObject("SELECT pago_id IS NOT NULL FROM liquidacion_linea", Boolean.class)).isTrue();
		assertThat(contar(jdbc, "liquidacion_pasarela")).isEqualTo(1);
		// El apoderado pagó el bruto: la cuota quedó pagada por 450.00 aunque el colegio reciba 431.41.
		assertThat(EscenarioCaja.pagado(jdbc, cuota(jdbc, f.mateo(), "PEN-2027-03"))).isEqualByComparingTo("450.00");
	}

	// ------------------------------------------------------------------ fechas y vencimiento

	@Test
	void debeSeguirEnCursoUnSegundoAntesDeVencerYVencerAlSegundoExacto() {
		String referencia = iniciarPago(List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")));
		LocalDateTime venceEn = jdbc.queryForObject("SELECT vence_en FROM orden_pago WHERE referencia = ?",
				LocalDateTime.class, referencia);
		ZoneId lima = ZoneId.of("America/Lima");

		reloj.fijar(venceEn.minusSeconds(1).atZone(lima).toInstant());
		assertThat(procesador.procesar(1L, ordenId(referencia))).isEqualTo(ProcesadorPagosEnLinea.Resultado.SIN_CAMBIOS);
		assertThat(estadoOrden(referencia)).isEqualTo("CREADA");

		reloj.fijar(venceEn.atZone(lima).toInstant());
		assertThat(procesador.procesar(1L, ordenId(referencia))).isEqualTo(ProcesadorPagosEnLinea.Resultado.VENCIDA);
		assertThat(contar(jdbc, "pago")).isZero();
	}

	@Test
	void debeRegistrarEnLaCajaDelUltimoDiaDelMesUnPagoHechoAntesDeLaMedianocheDeLima() {
		// Sábado 31/10/2026, 23:59:30 en Lima = domingo 01/11/2026, 04:59:30 UTC.
		reloj.fijar(Instant.parse("2026-11-01T04:59:30Z"));
		String referencia = iniciarPago(List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")));

		simulador.simular(referencia, Accion.YAPE);

		assertThat(estadoOrden(referencia)).isEqualTo("PAGADA");
		assertThat(jdbc.queryForObject("SELECT c.fecha FROM pago p JOIN caja_diaria c ON c.id = p.caja_diaria_id "
				+ "WHERE p.orden_pago_id = ?", LocalDate.class, ordenId(referencia))).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(jdbc.queryForObject("SELECT fecha FROM pago WHERE orden_pago_id = ?", LocalDate.class,
				ordenId(referencia))).isEqualTo(LocalDate.of(2026, 10, 31));
	}

	// ------------------------------------------------------------------ montos

	@Test
	void debeCobrarAlCentimoDosCuotasDeHermanosConDescuento() {
		Long marzoValeria = cuota(jdbc, f.valeria(), "PEN-2027-03");
		Long marzoMateo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		como(EscenarioCobranza.ADMINISTRACION);
		Long descuento = descuentos.solicitar(EscenarioAprobaciones.descuento(f.valeria(), TipoDescuento.HERMANOS, "10",
				List.of(marzoValeria)));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "descuento", descuento);
		como(rosa);

		BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(List.of(marzoMateo, marzoValeria))).total();
		assertThat(total).isEqualByComparingTo("855.00");
		String referencia = pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(),
				List.of(marzoValeria, marzoMateo), total, false));
		simulador.simular(referencia, Accion.TARJETA);

		assertThat(estadoOrden(referencia)).isEqualTo("PAGADA");
		assertThat(estado(jdbc, marzoValeria)).isEqualTo("PAGADA");
		assertThat(estado(jdbc, marzoMateo)).isEqualTo("PAGADA");
		assertThat(EscenarioCaja.pagado(jdbc, marzoValeria)).isEqualByComparingTo("405.00");
		assertThat(jdbc.queryForObject("SELECT total FROM pago WHERE orden_pago_id = ?", BigDecimal.class,
				ordenId(referencia))).isEqualByComparingTo("855.00");
	}

	@Test
	void debeRechazarCrearLaOrdenSiElTotalVistoDifiereEnUnCentimo() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");

		assertThatThrownBy(() -> pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), List.of(marzo),
				new BigDecimal("449.99"), false))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cambió");
		assertThat(contar(jdbc, "orden_pago")).isZero();
	}

	@Test
	void debeRechazarCrearLaOrdenSiElTotalVistoEsMayorQueElSaldo() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");

		assertThatThrownBy(() -> pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), List.of(marzo),
				new BigDecimal("450.01"), false))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cambió");
		assertThat(contar(jdbc, "orden_pago")).isZero();
	}

	@Test
	void debeRechazarCrearDirectamenteUnaOrdenQueSuperaElMaximoSinPasarPorLaRevision() {
		List<Long> todas = jdbc.queryForList("SELECT id FROM cuota WHERE alumno_id IN (?, ?) ORDER BY id", Long.class,
				f.mateo(), f.valeria());
		BigDecimal total = jdbc.queryForObject("SELECT SUM(monto) FROM cuota WHERE alumno_id IN (?, ?)", BigDecimal.class,
				f.mateo(), f.valeria());

		assertThatThrownBy(() -> pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), todas, total, false)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("5,000.00");
		assertThat(contar(jdbc, "orden_pago")).isZero();
	}

	@Test
	void debeRechazarUnaSeleccionQueSuperaElMaximoPorOrden() {
		List<Long> todas = jdbc.queryForList("SELECT id FROM cuota WHERE alumno_id IN (?, ?) ORDER BY id", Long.class,
				f.mateo(), f.valeria());

		assertThatThrownBy(() -> pagoEnLinea.revisar(new SeleccionPagoRequest(todas)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("5,000.00");
		assertThat(contar(jdbc, "orden_pago")).isZero();
	}

	@Test
	void debeCobrarLaMismaCuotaElegidaDosVecesUnaSolaVez() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");

		assertThat(pagoEnLinea.revisar(new SeleccionPagoRequest(List.of(marzo, marzo))).total())
				.isEqualByComparingTo("450.00");
	}

	// ------------------------------------------------------------------ concurrencia caja vs. pasarela (F17)

	@Test
	void debeAplicarUnaSolaVezLaCuotaSiCajaYPasarelaConfirmanALaVez() throws Exception {
		for (String obligacion : List.of("PEN-2027-03", "PEN-2027-04", "PEN-2027-05")) {
			Long cuotaId = cuota(jdbc, f.mateo(), obligacion);
			como(rosa);
			String referencia = iniciarPago(List.of(cuotaId));
			SecurityContextHolder.clearContext();

			List<Object> resultados = enParalelo(
					() -> {
						UsuariosDePrueba.iniciarSesion(CAJA);
						cobro.cobrar(efectivo(f.quispe(), List.of(cuotaId), "450.00", "450.00"));
						return "caja";
					},
					() -> {
						UsuariosDePrueba.iniciarSesion(rosa);
						return simulador.simular(referencia, Accion.YAPE).name();
					});

			assertThat(EscenarioCaja.pagado(jdbc, cuotaId)).as("pagado de %s (%s)", obligacion, resultados)
					.isLessThanOrEqualTo(new BigDecimal("450.00"));
			assertThat(contar(jdbc, "aplicacion_pago a JOIN pago p ON p.id = a.pago_id WHERE p.estado = 'VIGENTE' "
					+ "AND a.cuota_id = " + cuotaId)).as("aplicaciones de %s", obligacion).isLessThanOrEqualTo(1);
			String estadoOrden = estadoOrden(referencia);
			if ("PAGADA".equals(estadoOrden)) {
				assertThat(resultados.getFirst()).as("la caja no cobra lo que la pasarela ya aplicó")
						.isInstanceOf(RuntimeException.class);
			}
			else if (!"CREADA".equals(estadoOrden)) {
				assertThat(estadoOrden).isEqualTo("POR_REVISAR");
			}
		}
	}

	private List<Object> enParalelo(Callable<String> a, Callable<String> b) throws InterruptedException {
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch largada = new CountDownLatch(1);
			List<Future<String>> futuros = new ArrayList<>();
			for (Callable<String> tarea : List.of(a, b)) {
				futuros.add(hilos.submit(() -> {
					largada.await();
					try {
						return tarea.call();
					}
					finally {
						SecurityContextHolder.clearContext();
					}
				}));
			}
			largada.countDown();
			List<Object> resultados = new ArrayList<>();
			for (Future<String> futuro : futuros) {
				try {
					resultados.add(futuro.get(60, TimeUnit.SECONDS));
				}
				catch (ExecutionException e) {
					resultados.add(e.getCause());
				}
				catch (java.util.concurrent.TimeoutException e) {
					resultados.add(e);
				}
			}
			return resultados;
		}
		finally {
			hilos.shutdownNow();
		}
	}

	// ------------------------------------------------------------------ aislamiento (F24)

	@Test
	void debeImpedirQueOtroColegioVeaOConfirmeUnPagoEnLineaDelColegioA() {
		String referencia = iniciarPago(List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")));
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		UsuarioAutenticado promotorB = UsuariosDePrueba.autenticado(colegioB, 92L, "promotor.b", "Promotor B", false,
				EnumSet.of(Rol.PROMOTOR));
		// El aviso auténtico de la orden del A, reenviado a la URL del colegio B.
		PasarelaSimulada.AvisoFirmado aviso = pasarela.simular(pasarela.proveedorOrdenIdDe(referencia), Accion.YAPE);

		assertThat(recepcion.recibir("SIMULADA", colegioB, aviso.cuerpo(), aviso.cabeceras()))
				.isEqualTo(RecepcionAvisos.Resultado.ACEPTADO);
		assertThat(estadoOrden(referencia)).isEqualTo("CREADA");
		assertThat(contar(jdbc, "pago")).isZero();
		UsuariosDePrueba.iniciarSesion(promotorB);
		assertThat(consulta.lista().enCurso()).isEmpty();
		assertThatThrownBy(() -> consulta.detalle(ordenId(referencia))).isInstanceOf(RecursoNoEncontradoException.class);

		// El mismo aviso en la URL del colegio A sí se procesa: el evento del B no lo bloquea.
		SecurityContextHolder.clearContext();
		assertThat(recepcion.recibir("SIMULADA", 1L, aviso.cuerpo(), aviso.cabeceras()))
				.isEqualTo(RecepcionAvisos.Resultado.ACEPTADO);
		assertThat(estadoOrden(referencia)).isEqualTo("PAGADA");
	}
}
