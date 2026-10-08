package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioFamilias;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AplicacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AplicacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Comprobantes: numeración por serie SIN HUECOS (la serie se bloquea y el número se guarda en la transacción del pago),
 * boleta o factura según el receptor, y envío al OSE después del commit sin poner en riesgo el pago.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioComprobantesTest {

	@MockitoSpyBean
	private AplicacionPagoRepository aplicaciones;

	@MockitoSpyBean
	private EmisorElectronico emisor;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioFamilias familias;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones bandeja;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private DataSource fuenteDatos;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(CAJA);
	}

	@AfterEach
	void limpiar() {
		reset(aplicaciones, emisor);
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void numeracionCorrelativaSinHuecos() {
		for (String mes : new String[] { "03", "04", "05" }) {
			cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-" + mes)), "450.00", "450.00"));
		}

		assertThat(jdbc.queryForList("SELECT CONCAT(serie, '-', numero) FROM comprobante ORDER BY id", String.class))
				.containsExactly("B001-1", "B001-2", "B001-3");
		assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie_comprobante WHERE serie = 'B001'", Integer.class))
				.isEqualTo(3);
		assertThat(cobro.confirmacion(jdbc.queryForObject("SELECT MAX(id) FROM pago", Long.class)).comprobante())
				.isEqualTo("B001-00000003");
	}

	/** El pago falla DESPUÉS de emitir la boleta: el rollback devuelve el número y el siguiente cobro lo usa. */
	@Test
	void cobroFallidoNoGastaNumeroDeBoleta() {
		cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00", "450.00"));
		doThrow(new IllegalStateException("Falla simulada al guardar el libro")).when(aplicaciones)
				.save(any(AplicacionPago.class));

		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-04")),
				"450.00", "450.00"))).hasMessageContaining("Falla simulada");
		assertThat(contar(jdbc, "comprobante")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie_comprobante WHERE serie = 'B001'", Integer.class))
				.isEqualTo(1);

		reset(aplicaciones);
		cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-04")), "450.00", "450.00"));
		assertThat(jdbc.queryForList("SELECT numero FROM comprobante ORDER BY numero", Integer.class)).containsExactly(1, 2);
	}

	/** 20 cajeras cobran a la vez 20 cuotas distintas: 20 boletas numeradas del 1 al 20, sin saltos ni repetidos. */
	@Test
	void serieSinHuecosConVeinteCobrosConcurrentes() throws Exception {
		List<Long> cuotas = new ArrayList<>(jdbc.queryForList("SELECT id FROM cuota WHERE alumno_id IN (?, ?) "
				+ "AND tipo = 'PENSION' ORDER BY id", Long.class, f.mateo(), f.valeria()));
		assertThat(cuotas).hasSize(20);
		ExecutorService hilos = Executors.newFixedThreadPool(20);
		CountDownLatch largada = new CountDownLatch(1);
		try {
			List<Future<Long>> resultados = new ArrayList<>();
			for (int i = 0; i < cuotas.size(); i++) {
				UsuarioAutenticado cajera = EscenarioCobranza.persona(200 + i, "caja.hilo" + i, Rol.CAJA);
				CobroRequest solicitud = efectivo(f.quispe(), List.of(cuotas.get(i)), "450.00", "450.00");
				resultados.add(hilos.submit(() -> {
					largada.await();
					como(cajera);
					try {
						return cobro.cobrar(solicitud);
					}
					finally {
						SecurityContextHolder.clearContext();
					}
				}));
			}
			largada.countDown();
			for (Future<Long> resultado : resultados) {
				assertThat(resultado.get(120, TimeUnit.SECONDS)).isNotNull();
			}
		}
		finally {
			hilos.shutdownNow();
		}
		assertThat(jdbc.queryForList("SELECT numero FROM comprobante WHERE serie = 'B001' ORDER BY numero", Integer.class))
				.containsExactlyElementsOf(IntStream.rangeClosed(1, 20).boxed().toList());
		assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie_comprobante WHERE serie = 'B001'", Integer.class))
				.isEqualTo(20);
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT comprobante_id) FROM pago", Long.class)).isEqualTo(20);
		assertThat(contar(jdbc, "caja_diaria")).isEqualTo(20);
	}

	/**
	 * B2 (correcciones del sprint 3): la factura solo sale con el RUC REGISTRADO y aprobado de un apoderado de la
	 * familia. Un RUC cualquiera escrito en caja (de un tercero que se queda con el crédito fiscal) se rechaza, y la
	 * razón social sale del registro, no de lo que se escriba.
	 */
	@Test
	void facturaSoloConElRucRegistradoDeLaFamilia() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");

		for (String ruc : List.of("20131312955", "20131312954", "")) {
			assertThatThrownBy(() -> cobro.cobrar(factura(List.of(marzo), ruc, "Comercial Quispe S.A.C.")))
					.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("RUC registrado de la familia");
		}
		assertThat(contar(jdbc, "comprobante")).isZero();
		// Registrarlo pide la aprobación de otra persona y valida el RUC.
		como(EscenarioCobranza.ADMINISTRACION);
		assertThatThrownBy(() -> familias.solicitarDatosFacturacion(f.rosa(), "20131312954", "Comercial Quispe S.A.C.",
				"La empresa de la familia pide factura")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("RUC no es válido");
		assertThatThrownBy(() -> familias.solicitarDatosFacturacion(f.rosa(), "20131312955", " ",
				"La empresa de la familia pide factura")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("razón social");
		familias.solicitarDatosFacturacion(f.rosa(), "20131312955", "Comercial Quispe S.A.C.",
				"La empresa de la familia pide factura");
		como(CAJA);
		assertThatThrownBy(() -> cobro.cobrar(factura(List.of(marzo), "20131312955", "Comercial Quispe S.A.C.")))
				.as("pendiente de aprobación").isInstanceOf(ReglaNegocioException.class);
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba.iniciarSesion(EscenarioCobranza.DIRECCION);
		bandeja.aprobar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.pendiente(jdbc, "apoderado",
				f.rosa()), null);
		assertThat(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento(jdbc,
				"DATOS_FACTURACION_CAMBIADOS")).containsEntry("nombre_usuario", "director");

		como(CAJA);
		Long pago = cobro.cobrar(factura(List.of(marzo), "20131312955", "Otra Empresa S.A.C."));
		Map<String, Object> comprobante = jdbc.queryForMap("SELECT * FROM comprobante");
		assertThat(comprobante).containsEntry("tipo", "FACTURA").containsEntry("serie", "F001").containsEntry("numero", 1)
				.containsEntry("receptor_tipo_documento", "RUC").containsEntry("receptor_numero_documento", "20131312955")
				.containsEntry("receptor_nombre", "Comercial Quispe S.A.C.");
		assertThat(cobro.confirmacion(pago).comprobante()).isEqualTo("F001-00000001");
		// Otra familia no puede usar ese RUC.
		Long sebastian = cuota(jdbc, f.sebastian(), "PEN-2027-03");
		assertThatThrownBy(() -> cobro.cobrar(new CobroRequest(UUID.randomUUID(), f.flores(), List.of(sebastian),
				MedioPago.EFECTIVO, null, new BigDecimal("450.00"), null, new BigDecimal("450.00"), TipoComprobante.FACTURA,
				null, "20131312955", null))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("RUC registrado de la familia");
	}

	@Test
	void boletaVaANombreDelResponsableDePago() {
		como(EscenarioCobranza.ADMINISTRACION);
		Long padre = familias.agregarApoderado(f.quispe(), EscenarioEscolar.apoderado("43218765", "Quispe", "Luis",
				Parentesco.PADRE, "987111222", null, "Registro del padre en secretaría"));
		como(CAJA);
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		Long abril = cuota(jdbc, f.mateo(), "PEN-2027-04");

		// Por defecto: la responsable de pago (Rosa).
		cobro.cobrar(efectivo(f.quispe(), List.of(marzo), "450.00", "450.00"));
		// A pedido: otro apoderado de la MISMA familia.
		cobro.cobrar(conReceptor(List.of(abril), padre));
		assertThat(jdbc.queryForList("SELECT receptor_numero_documento FROM comprobante ORDER BY numero", String.class))
				.containsExactly(EscenarioEscolar.DNI_ROSA, "43218765");
		// Nunca a nombre de un apoderado de otra familia.
		assertThatThrownBy(() -> cobro.cobrar(conReceptor(List.of(cuota(jdbc, f.mateo(), "PEN-2027-05")), f.pedro())))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("apoderado activo de esta familia");
		assertThat(contar(jdbc, "comprobante")).isEqualTo(2);
	}

	@Test
	void lineasSumanElTotal() {
		Long marzoMateo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		Long matriculaValeria = cuota(jdbc, f.valeria(), "MAT-2027");
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo, matriculaValeria), "800.00", "800.00"));

		Long comprobante = jdbc.queryForObject("SELECT id FROM comprobante", Long.class);
		assertThat(jdbc.queryForObject("SELECT total FROM comprobante", BigDecimal.class)).isEqualByComparingTo("800.00");
		assertThat(jdbc.queryForObject("SELECT SUM(monto) FROM comprobante_linea WHERE comprobante_id = ?",
				BigDecimal.class, comprobante)).isEqualByComparingTo("800.00");
		// Una línea por cuota, de la que vence primero (matrícula, febrero) a la última.
		assertThat(jdbc.queryForList("SELECT descripcion FROM comprobante_linea ORDER BY orden", String.class))
				.containsExactly("Matrícula 2027 · Valeria Quispe Huamán", "Pensión marzo 2027 · Mateo Quispe Huamán");
	}

	/** El OSE recibe el comprobante cuando el pago ya está confirmado (otra conexión ya lo ve). */
	@Test
	void envioAlOseOcurreDespuesDelCommit() throws InterruptedException {
		AtomicLong pagosVisiblesAlEnviar = new AtomicLong(-1);
		doAnswer(invocacion -> {
			try (Connection otra = fuenteDatos.getConnection();
					ResultSet filas = otra.createStatement().executeQuery("SELECT COUNT(*) FROM pago")) {
				filas.next();
				pagosVisiblesAlEnviar.set(filas.getLong(1));
			}
			return invocacion.callRealMethod();
		}).when(emisor).enviar(any());

		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00", "450.00"));

		verify(emisor, timeout(10_000).times(1)).enviar(any());
		esperarEnvio();
		assertThat(pagosVisiblesAlEnviar.get()).isEqualTo(1);
		assertThat(jdbc.queryForMap("SELECT estado_envio, intentos, respuesta, codigo_hash FROM comprobante"))
				.containsEntry("estado_envio", EstadoEnvio.ACEPTADO.name()).containsEntry("intentos", 1)
				.containsEntry("respuesta", EmisorSimulado.RESPUESTA)
				.satisfies(c -> assertThat((String) c.get("codigo_hash")).hasSize(64));
		assertThat(cobro.confirmacion(pago).estadoEnvio()).isEqualTo("ACEPTADO");
		// Hallazgo 13 de QA: en el perfil de pruebas el envío es síncrono; no quedan hilos vivos para la prueba siguiente.
		assertThat(Thread.getAllStackTraces().keySet()).noneMatch(t -> t.getName().startsWith("envio-comprobantes-"));
	}

	@Test
	void siElOseFallaElPagoSeConservaYQuedaPendiente() throws InterruptedException {
		doThrow(new IllegalStateException("OSE caído")).when(emisor).enviar(any());

		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00", "450.00"));

		assertThat(pago).isNotNull();
		esperarEnvio();
		assertThat(contar(jdbc, "pago")).isEqualTo(1);
		assertThat(EscenarioCaja.estado(jdbc, cuota(jdbc, f.mateo(), "PEN-2027-03"))).isEqualTo("PAGADA");
		assertThat(jdbc.queryForMap("SELECT estado_envio, intentos, respuesta FROM comprobante"))
				.containsEntry("estado_envio", "PENDIENTE").containsEntry("intentos", 1)
				.containsEntry("respuesta", EnvioComprobantes.SIN_RESPUESTA);
		// El emisor simulado es idempotente: el mismo documento da el mismo hash.
		reset(emisor);
		ResultadoEnvio uno = emisor.enviar(new DocumentoElectronico(TipoComprobante.BOLETA, "B001", 1,
				java.time.LocalDate.of(2026, 10, 2), pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Receptor.de(
						pe.edu.virgenmaria.cuentasclaras.comprobantes.model.DocumentoReceptor.DNI, "45678912", "Rosa"),
				"PEN", new BigDecimal("450.00"), pe.edu.virgenmaria.cuentasclaras.comprobantes.model.AfectacionIgv.INAFECTO,
				List.of(new pe.edu.virgenmaria.cuentasclaras.comprobantes.model.LineaDocumento("Pensión", new BigDecimal(
						"450.00"))), null));
		assertThat(uno.codigoHash()).isEqualTo(emisor.enviar(new DocumentoElectronico(TipoComprobante.BOLETA, "B001", 1,
				java.time.LocalDate.of(2026, 10, 2), pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Receptor.de(
						pe.edu.virgenmaria.cuentasclaras.comprobantes.model.DocumentoReceptor.DNI, "45678912", "Rosa"),
				"PEN", new BigDecimal("450.00"), pe.edu.virgenmaria.cuentasclaras.comprobantes.model.AfectacionIgv.INAFECTO,
				List.of(new pe.edu.virgenmaria.cuentasclaras.comprobantes.model.LineaDocumento("Pensión", new BigDecimal(
						"450.00"))), null)).codigoHash());
	}

	/**
	 * El envío corre después del commit. En el perfil de pruebas es síncrono (hallazgo 13 de QA), así que ya terminó;
	 * la espera queda por si se prueba con el ejecutor de hilos (máximo 10 s).
	 */
	private void esperarEnvio() throws InterruptedException {
		long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (jdbc.queryForObject("SELECT COUNT(*) FROM comprobante WHERE intentos = 0", Long.class) > 0) {
			if (System.nanoTime() > limite) {
				throw new AssertionError("El comprobante no se envió en 10 s");
			}
			Thread.sleep(50);
		}
	}

	private CobroRequest factura(List<Long> cuotas, String ruc, String razonSocial) {
		return new CobroRequest(UUID.randomUUID(), f.quispe(), cuotas, MedioPago.TRANSFERENCIA, "OP-" + UUID.randomUUID()
				.toString().substring(0, 8), null, null, new BigDecimal("450.00"), TipoComprobante.FACTURA, null, ruc,
				razonSocial);
	}

	private CobroRequest conReceptor(List<Long> cuotas, Long apoderado) {
		return new CobroRequest(UUID.randomUUID(), f.quispe(), cuotas, MedioPago.EFECTIVO, null, new BigDecimal("450.00"),
				null, new BigDecimal("450.00"), TipoComprobante.BOLETA, apoderado, null, null);
	}
}
