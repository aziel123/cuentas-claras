package pe.edu.virgenmaria.cuentasclaras.caja.service;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AplicacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA_2;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.estado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Los fraudes de caja que el sistema debe impedir (tanda 1). Cada uno reproduce el ataque; la defensa en MySQL (los
 * triggers) se prueba además en {@code PermisosMySqlTest}.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class EscenariosFraudeCajaTest {

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private CuotaRepository cuotas;

	@Autowired
	private PagoRepository pagos;

	@Autowired
	private PlatformTransactionManager transacciones;

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
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** Cobrar a una familia y aplicar el dinero a la deuda de otra (el «jineteo» entre familias). */
	@Test
	void fraudeAplicarPagoACuotaDeOtraFamiliaEsRechazado() {
		Long cuotaSebastian = cuota(jdbc, f.sebastian(), "PEN-2027-03");

		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), List.of(cuotaSebastian), "450.00", "450.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("otra familia");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(estado(jdbc, cuotaSebastian)).isEqualTo("PENDIENTE");

		// Ni siquiera el modelo deja aplicar un pago de los Quispe a una cuota de los Flores.
		Long pagoQuispe = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00",
				"450.00"));
		assertThatThrownBy(() -> new TransactionTemplate(transacciones).executeWithoutResult(t ->
				AplicacionPago.aplicar(pagos.findById(pagoQuispe).orElseThrow(), cuotas.findById(cuotaSebastian).orElseThrow(),
						new BigDecimal("1.00"))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("solo se aplica a cuotas de su familia");
	}

	@Test
	void fraudeCuotasDeDosFamiliasEnUnPagoEsRechazado() {
		Long mateo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		Long sebastian = cuota(jdbc, f.sebastian(), "PEN-2027-03");

		for (Long familia : List.of(f.quispe(), f.flores())) {
			assertThatThrownBy(() -> cobro.cobrar(efectivo(familia, List.of(mateo, sebastian), "900.00", "900.00")))
					.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("misma familia");
		}
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(contar(jdbc, "comprobante")).isZero();
		assertThat(estado(jdbc, mateo)).isEqualTo("PENDIENTE");
		assertThat(estado(jdbc, sebastian)).isEqualTo("PENDIENTE");
	}

	/** El robo original: cobrar S/ 900 y registrar menos. Sin pago a cuenta habilitado no hay forma. */
	@Test
	void fraudeRegistrarMenosDeLoCobradoNoEsPosibleSinPagoACuenta() {
		List<Long> dos = List.of(cuota(jdbc, f.mateo(), "PEN-2027-03"), cuota(jdbc, f.valeria(), "PEN-2027-03"));

		// Un «total visto» menor: se rechaza.
		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), dos, "450.00", "450.00")))
				.isInstanceOf(MontoCambiadoException.class);
		// Un monto a cuenta: deshabilitado.
		assertThatThrownBy(() -> cobro.cobrar(new CobroRequest(java.util.UUID.randomUUID(), f.quispe(), dos,
				pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.EFECTIVO, null, new BigDecimal("450.00"),
				new BigDecimal("450.00"), new BigDecimal("900.00"),
				pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante.BOLETA, null, null, null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no está habilitado");
		// Lo recibido no cambia el total: con S/ 1,000 recibidos el pago es por S/ 900 y el vuelto, S/ 100.
		Long pago = cobro.cobrar(efectivo(f.quispe(), dos, "900.00", "1000.00"));
		assertThat(jdbc.queryForObject("SELECT total FROM pago WHERE id = ?", BigDecimal.class, pago))
				.isEqualByComparingTo("900.00");
		assertThat(contar(jdbc, "pago")).isEqualTo(1);
	}

	/** Dos cajeras cobran la MISMA cuota en el mismo instante: solo una lo logra (bloqueo de la cuota). */
	@Test
	void fraudeDobleCobroConcurrenteSoloUnoLoLogra() throws Exception {
		List<Long> cuotasMateo = jdbc.queryForList("SELECT id FROM cuota WHERE alumno_id = ? AND tipo = 'PENSION' "
				+ "ORDER BY numero LIMIT 5", Long.class, f.mateo());
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			for (Long cuota : cuotasMateo) {
				CountDownLatch largada = new CountDownLatch(1);
				Future<Long> primera = hilos.submit(cobrarComo(CAJA, largada, efectivo(f.quispe(), List.of(cuota), "450.00",
						"500.00")));
				Future<Long> segunda = hilos.submit(cobrarComo(CAJA_2, largada, efectivo(f.quispe(), List.of(cuota),
						"450.00", "450.00")));
				largada.countDown();
				List<Object> resultados = new ArrayList<>();
				for (Future<Long> r : List.of(primera, segunda)) {
					try {
						resultados.add(r.get(30, TimeUnit.SECONDS));
					}
					catch (ExecutionException e) {
						resultados.add(e.getCause());
					}
				}
				assertThat(resultados).as("cuota " + cuota).filteredOn(Long.class::isInstance).hasSize(1);
				assertThat(resultados).filteredOn(ReglaNegocioException.class::isInstance).hasSize(1)
						.first().extracting(e -> ((Exception) e).getMessage()).asString().contains("ya no se puede cobrar");
				assertThat(EscenarioCaja.pagado(jdbc, cuota)).isEqualByComparingTo("450.00");
				assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aplicacion_pago WHERE cuota_id = ?", Long.class, cuota))
						.isEqualTo(1);
			}
		}
		finally {
			hilos.shutdownNow();
		}
		assertThat(contar(jdbc, "pago")).isEqualTo(cuotasMateo.size());
		// Y la serie no dejó huecos: un número por pago (los rechazos devolvieron el suyo).
		assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie_comprobante WHERE serie = 'B001'", Integer.class))
				.isEqualTo(cuotasMateo.size());
		assertThat(jdbc.queryForList("SELECT numero FROM comprobante ORDER BY numero", Integer.class))
				.containsExactly(1, 2, 3, 4, 5);
	}

	private Callable<Long> cobrarComo(UsuarioAutenticado cajera, CountDownLatch largada, CobroRequest solicitud) {
		return () -> {
			largada.await();
			como(cajera);
			try {
				return cobro.cobrar(solicitud);
			}
			finally {
				SecurityContextHolder.clearContext();
			}
		};
	}

	/**
	 * No hay camino en la aplicación para dejar una cuota PAGADA sin un pago: la cuota no tiene setters, solo
	 * {@code reflejarPagos} cambia lo pagado y solo {@link LibroPagos} lo llama (después de insertar la aplicación).
	 * En MySQL además lo impide el trigger trg_cuota_libro (PermisosMySqlTest).
	 */
	@Test
	void noExisteCaminoParaMarcarUnaCuotaPagadaSinPago() {
		Set<String> publicosQueCambian = Arrays.stream(Cuota.class.getDeclaredMethods())
				.filter(m -> Modifier.isPublic(m.getModifiers()) && m.getReturnType() == void.class)
				.map(Method::getName).collect(Collectors.toSet());
		assertThat(publicosQueCambian).containsExactlyInAnyOrder("reflejarPagos", "reflejarDescuentos",
				"solicitarAnulacion", "descartarSolicitudAnulacion", "anular");

		JavaClasses produccion = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("pe.edu.virgenmaria.cuentasclaras");
		Set<String> quienesReflejanPagos = produccion.get(Cuota.class).getMethodCallsToSelf().stream()
				.filter(llamada -> llamada.getName().equals("reflejarPagos"))
				.map(JavaMethodCall::getOriginOwner).map(c -> c.getName()).collect(Collectors.toSet());
		assertThat(quienesReflejanPagos).containsExactly(LibroPagos.class.getName());

		// En la base (también en H2): PAGADA exige lo pagado completo.
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		assertThatThrownBy(() -> jdbc.update("UPDATE cuota SET estado = 'PAGADA' WHERE id = ?", marzo))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(estado(jdbc, marzo)).isEqualTo("PENDIENTE");
	}
}
