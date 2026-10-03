package pe.edu.virgenmaria.cuentasclaras.caja.service;

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
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoConteo;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CajaDiariaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ContenidoVisible;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.MOTIVO_ANULACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Hallazgos de QA del sprint 3 (mutaciones que sobrevivían): concurrencia entre cobro, cierre y anulación (1), cierre
 * tras reapertura (4), fechas y horas límite (8) y bordes de descuentos (10). Cada prueba falla con la mutación.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class HallazgosQaCajaTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioCierreCaja cierre;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private AlertasCaja alertas;

	@Autowired
	private CajaDiariaRepository cajas;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	private Long marzoMateo;

	private Long marzoValeria;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		marzoMateo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		marzoValeria = cuota(jdbc, f.valeria(), "PEN-2027-03");
		como(CAJA);
	}

	@AfterEach
	void limpiar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	// ------------------------------------------------------------------ 1. concurrencia (M19)

	/**
	 * El cierre ya calculó el esperado y todavía no confirma; un cobro en efectivo llega en ese instante. Como el cobro
	 * bloquea la caja primero, espera al cierre y encuentra la caja CERRADA: no queda un pago fuera del esperado.
	 */
	@Test
	void cobroEnEfectivoDuranteElCierreNoQuedaFueraDelEsperado() throws Exception {
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		Long caja = jdbc.queryForObject("SELECT id FROM caja_diaria", Long.class);

		Object cobroConcurrente = mientrasSeCierra(caja, CAJA, () -> cobro.cobrar(efectivo(f.quispe(),
				List.of(marzoValeria), "450.00", "450.00")));

		assertThat(cobroConcurrente).isInstanceOf(ReglaNegocioException.class).extracting(e -> ((Exception) e).getMessage())
				.asString().contains("ya se cerró");
		BigDecimal esperado = jdbc.queryForObject("SELECT efectivo_cobrado FROM cierre_caja", BigDecimal.class);
		BigDecimal enLaCaja = jdbc.queryForObject("SELECT COALESCE(SUM(total), 0) FROM pago WHERE caja_diaria_id = ? "
				+ "AND medio = 'EFECTIVO' AND estado = 'VIGENTE'", BigDecimal.class, caja);
		assertThat(enLaCaja).isEqualByComparingTo(esperado).isEqualByComparingTo("450.00");
	}

	/**
	 * La aprobación de una devolución en efectivo llega mientras se cierra: espera el bloqueo de la caja y la ve CERRADA,
	 * así la anulación queda «posterior al cierre» y el esperado del cierre sigue siendo coherente con los pagos.
	 */
	@Test
	void aprobacionDeAnulacionDuranteElCierreDejaElEsperadoCoherente() throws Exception {
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoValeria), "450.00", "450.00"));
		anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION);
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "pago", pago);
		UsuariosDePrueba.iniciarSesion(DIRECCION);
		List<String> telefonos = EscenarioAprobaciones.telefonosPorLlamar(bandeja, jdbc, solicitud);
		Long caja = jdbc.queryForObject("SELECT id FROM caja_diaria", Long.class);

		Object aprobacion = mientrasSeCierra(caja, DIRECCION, () -> {
			bandeja.aprobar(solicitud, null, true, telefonos);
			return "ok";
		});

		assertThat(aprobacion).isEqualTo("ok");
		assertThat(jdbc.queryForObject("SELECT posterior_al_cierre FROM anulacion_pago", Boolean.class)).isTrue();
		BigDecimal esperado = jdbc.queryForObject("SELECT efectivo_cobrado FROM cierre_caja", BigDecimal.class);
		BigDecimal vigente = jdbc.queryForObject("SELECT COALESCE(SUM(total), 0) FROM pago WHERE caja_diaria_id = ? "
				+ "AND medio = 'EFECTIVO' AND estado = 'VIGENTE'", BigDecimal.class, caja);
		BigDecimal anuladoDespues = jdbc.queryForObject("SELECT COALESCE(SUM(monto), 0) FROM anulacion_pago "
				+ "WHERE posterior_al_cierre = TRUE", BigDecimal.class);
		assertThat(esperado).isEqualByComparingTo("900.00").isEqualByComparingTo(vigente.add(anuladoDespues));
	}

	/**
	 * La cajera cuenta y cierra en una transacción que retiene el bloqueo de la caja después de registrar el cierre;
	 * mientras tanto, otro hilo intenta la operación. Devuelve su resultado o la excepción.
	 */
	private Object mientrasSeCierra(Long caja, UsuarioAutenticado quien, java.util.concurrent.Callable<Object> operacion)
			throws Exception {
		CountDownLatch cerrado = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			Future<?> cierreEnCurso = hilos.submit(() -> {
				UsuariosDePrueba.iniciarSesion(CAJA);
				try {
					new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
						cajas.bloquearPorId(caja).orElseThrow();
						assertThat(cierre.contar(new ConteoRequest(esperadoDe(caja), null)))
								.isEqualTo(ResultadoConteo.COINCIDE);
						cerrado.countDown();
						dormir(1_500);
					});
				}
				finally {
					SecurityContextHolder.clearContext();
				}
			});
			Future<Object> otra = hilos.submit(() -> {
				UsuariosDePrueba.iniciarSesion(quien);
				try {
					assertThat(cerrado.await(30, TimeUnit.SECONDS)).isTrue();
					return operacion.call();
				}
				catch (RuntimeException e) {
					return e;
				}
				finally {
					SecurityContextHolder.clearContext();
				}
			});
			cierreEnCurso.get(60, TimeUnit.SECONDS);
			return otra.get(60, TimeUnit.SECONDS);
		}
		finally {
			hilos.shutdownNow();
		}
	}

	private BigDecimal esperadoDe(Long caja) {
		return jdbc.queryForObject("SELECT COALESCE(SUM(total), 0) FROM pago WHERE caja_diaria_id = ? "
				+ "AND medio = 'EFECTIVO' AND estado = 'VIGENTE'", BigDecimal.class, caja);
	}

	private static void dormir(long milisegundos) {
		try {
			Thread.sleep(milisegundos);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// ------------------------------------------------------------------ 4. cierre tras reapertura

	/**
	 * Después de una reapertura, la vista de la cajera no trae ningún cierre ni monto mientras haya caja por cerrar; el
	 * cierre siguiente queda marcado «tras reapertura», va primero en la bandeja, exige comentario y avisa a Promotoría.
	 */
	@Test
	void trasLaReaperturaElConteoSigueSiendoCiego() {
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null));
		Long caja = jdbc.queryForObject("SELECT id FROM caja_diaria", Long.class);
		EscenarioAprobaciones.aprueba(PROMOTORIA, bandeja, jdbc, "cierre_caja",
				jdbc.queryForObject("SELECT id FROM cierre_caja", Long.class));
		como(CAJA);
		cierre.solicitarReapertura("Llegó una familia a pagar en efectivo tarde");
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "caja_diaria", caja);
		assertThat(jdbc.queryForObject("SELECT reapertura_solicitud_id FROM caja_diaria", Long.class)).isNotNull();

		como(CAJA);
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoValeria), "450.00", "450.00"));
		var estado = cierre.estado();
		assertThat(estado.porCerrar()).isNotNull();
		assertThat(estado.ultimoCierre()).isNull();
		assertThat(ContenidoVisible.muestraMonto(estado, "450.00")).isFalse();
		assertThat(ContenidoVisible.muestraMonto(estado, "900.00")).isFalse();
		assertThat(cierre.contar(new ConteoRequest(new BigDecimal("900.00"), null))).isEqualTo(ResultadoConteo.COINCIDE);

		assertThat(jdbc.queryForList("SELECT CONCAT(numero, ' ', tras_reapertura) FROM cierre_caja ORDER BY numero",
				String.class)).containsExactly("1 FALSE", "2 TRUE");
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.ATENCION
				&& a.texto().startsWith("Cierre tras reapertura"));
		var tarjeta = bandeja.bandeja().pendientes().getFirst();
		assertThat(tarjeta.tipo()).isEqualTo("CIERRE_CAJA");
		assertThat(tarjeta.pideComentario()).isTrue();
		assertThat(tarjeta.advertencia()).startsWith("Cierre tras reapertura");
		assertThatThrownBy(() -> bandeja.aprobar(tarjeta.id(), null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("tras reapertura");
		bandeja.aprobar(tarjeta.id(), "Revisé el pago de Valeria cobrado después de reabrir");
		assertThat(jdbc.queryForObject("SELECT estado FROM cierre_caja WHERE numero = 2", String.class))
				.isEqualTo("APROBADO");
	}

	// ------------------------------------------------------------------ 8. fechas y horas límite

	/** La alerta «caja sin cerrar» de hoy aparece a las 19:00:00 exactas, no a las 18:59:59. */
	@Test
	void alertaCajaSinCerrarDesdeLas1900Exactas() {
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		como(PROMOTORIA);
		reloj.fijar(ZonedDateTime.of(2026, 10, 2, 18, 59, 59, 0, LIMA).toInstant());
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().startsWith("Pasó la hora límite"));
		reloj.fijar(ZonedDateTime.of(2026, 10, 2, 19, 0, 0, 0, LIMA).toInstant());
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.ATENCION
				&& a.texto().startsWith("Pasó la hora límite (19:00)"));
	}

	/** Un cobro a las 23:59:59 va a la caja de ese día; a las 00:00:00 ya no se cobra sin cerrarla. */
	@Test
	void cobroA235959VaALaCajaDelDiaYA0000ExigeCerrarla() {
		reloj.fijar(ZonedDateTime.of(2026, 10, 2, 23, 59, 59, 0, LIMA).toInstant());
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		assertThat(jdbc.queryForObject("SELECT d.fecha FROM pago p JOIN caja_diaria d ON d.id = p.caja_diaria_id "
				+ "WHERE p.id = ?", java.sql.Date.class, pago).toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 2));
		reloj.fijar(ZonedDateTime.of(2026, 10, 3, 0, 0, 0, 0, LIMA).toInstant());
		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), List.of(marzoValeria), "450.00", "450.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Primero cierra tu caja del 02/10/2026");
		assertThat(contar(jdbc, "pago")).isEqualTo(1);
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().contains("del 02/10/2026 sigue abierta"));
	}

	// ------------------------------------------------------------------ 10. bordes de descuentos

	/** Cobrar una cuota con descuento y anular el pago: la cuota vuelve a deberse CON su descuento. */
	@Test
	void cobrarCuotaConDescuentoYAnularVuelveASaldoConDescuento() {
		descuentoAprobado(TipoDescuento.HERMANOS, "10", marzoMateo);
		como(CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "405.00", "405.00"));
		assertThat(EscenarioCaja.estado(jdbc, marzoMateo)).isEqualTo("PAGADA");
		anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION);
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);

		Map<String, Object> fila = jdbc.queryForMap("SELECT estado, monto_pagado, monto_descuento FROM cuota WHERE id = ?",
				marzoMateo);
		assertThat(fila).containsEntry("estado", "PENDIENTE");
		assertThat((BigDecimal) fila.get("monto_pagado")).isEqualByComparingTo("0.00");
		assertThat((BigDecimal) fila.get("monto_descuento")).isEqualByComparingTo("45.00");
		como(CAJA);
		assertThat(cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "405.00", "405.00"))).isNotNull();
	}

	/** Una beca del 100 % deja la cuota sin saldo: no se cobra en caja. */
	@Test
	void becaCienPorCientoNoSeCobraEnCaja() {
		descuentoAprobado(TipoDescuento.BECA, "100", marzoMateo);
		como(CAJA);
		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "0.00", "0.00")))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(jdbc.queryForObject("SELECT monto_descuento FROM cuota WHERE id = ?", BigDecimal.class, marzoMateo))
				.isEqualByComparingTo("450.00");
	}

	private void descuentoAprobado(TipoDescuento tipo, String porcentaje, Long cuota) {
		como(ADMINISTRACION);
		Long id = descuentos.solicitar(EscenarioAprobaciones.descuento(f.mateo(), tipo, porcentaje, List.of(cuota)));
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "descuento", id);
		assertThat(EscenarioEscolar.contar(jdbc, "ajuste_cuota")).isEqualTo(1);
	}
}
