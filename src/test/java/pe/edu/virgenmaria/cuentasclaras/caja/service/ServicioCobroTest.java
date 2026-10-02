package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConfirmacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.RevisionCobro;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.SeleccionCobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioAnulacionCuotas;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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

/** Cobro en caja (sprint 3, tanda 1): el sistema calcula el total, emite la boleta y el libro refleja cada cuota. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioCobroTest {

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioAnulacionCuotas anulaciones;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	private Familias f;

	private Long marzoMateo;

	private Long marzoValeria;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		marzoMateo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		marzoValeria = cuota(jdbc, f.valeria(), "PEN-2027-03");
		como(CAJA);
	}

	@AfterEach
	void limpiar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void cobraDosCuotasYQuedanPagadas() {
		RevisionCobro revision = cobro.revisar(f.quispe(), new SeleccionCobroRequest(List.of(marzoValeria, marzoMateo),
				MedioPago.EFECTIVO), null);
		assertThat(revision.total()).isEqualByComparingTo("900.00");
		assertThat(revision.receptorPorDefecto()).isEqualTo(f.rosa());

		Long pagoId = cobro.cobrar(new CobroRequest(revision.clave(), f.quispe(), revision.cuotaIds(), MedioPago.EFECTIVO,
				null, new BigDecimal("1000"), null, revision.total(), TipoComprobante.BOLETA, revision.receptorPorDefecto(),
				null, null));

		Map<String, Object> pago = jdbc.queryForMap("SELECT * FROM pago WHERE id = ?", pagoId);
		assertThat((BigDecimal) pago.get("total")).isEqualByComparingTo("900.00");
		assertThat((BigDecimal) pago.get("recibido")).isEqualByComparingTo("1000.00");
		assertThat((BigDecimal) pago.get("vuelto")).isEqualByComparingTo("100.00");
		assertThat(pago).containsEntry("medio", "EFECTIVO").containsEntry("estado", "VIGENTE")
				.containsEntry("cajero", "caja").containsEntry("creado_por", "caja").containsEntry("origen", "CAJA")
				.containsEntry("clave_idempotencia", revision.clave().toString());
		for (Long cuota : List.of(marzoMateo, marzoValeria)) {
			assertThat(estado(jdbc, cuota)).isEqualTo("PAGADA");
			assertThat(EscenarioCaja.pagado(jdbc, cuota)).isEqualByComparingTo("450.00");
		}
		assertThat(jdbc.queryForObject("SELECT SUM(monto) FROM aplicacion_pago WHERE pago_id = ?", BigDecimal.class,
				pagoId)).isEqualByComparingTo("900.00");

		ConfirmacionPago confirmacion = cobro.confirmacion(pagoId);
		assertThat(confirmacion.comprobante()).isEqualTo("B001-00000001");
		assertThat(confirmacion.vuelto()).isEqualByComparingTo("100.00");
		assertThat(confirmacion.lineas()).hasSize(2);
		assertThat(confirmacion.simulado()).isTrue();
		// La boleta sale a nombre de la responsable de pago, con su DNI.
		assertThat(jdbc.queryForMap("SELECT receptor_nombre, receptor_numero_documento, total FROM comprobante"))
				.containsEntry("receptor_nombre", "Rosa Huamán Ccori")
				.containsEntry("receptor_numero_documento", EscenarioEscolar.DNI_ROSA);
		// Las demás cuotas siguen por pagar.
		assertThat(estado(jdbc, cuota(jdbc, f.mateo(), "PEN-2027-04"))).isEqualTo("PENDIENTE");
	}

	/** La cajera nunca escribe el monto: el formulario no tiene ese campo y lo que «vio» se compara con el sistema. */
	@Test
	void cajaNoPuedeEscribirElMontoDelPago() {
		assertThat(Arrays.stream(CobroRequest.class.getRecordComponents()).map(c -> c.getName()))
				.doesNotContain("total", "monto", "importe", "montoPagado");

		// Si manda un total «visto» menor (para registrar menos de lo cobrado), se rechaza y no se registra nada.
		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo, marzoValeria), "450.00", "450.00")))
				.isInstanceOf(MontoCambiadoException.class).hasMessageContaining("S/ 900.00");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(contar(jdbc, "comprobante")).isZero();
		assertThat(estado(jdbc, marzoMateo)).isEqualTo("PENDIENTE");
	}

	@Test
	void montoCambiadoDesdeLaRevisionPideRevisarDeNuevo() {
		RevisionCobro revision = cobro.revisar(f.quispe(), new SeleccionCobroRequest(List.of(marzoMateo, marzoValeria),
				MedioPago.EFECTIVO), null);
		// Entre la revisión y el cobro, otra caja registró un pago a cuenta de S/ 100 sobre la cuota de Valeria.
		jdbc.update("UPDATE cuota SET estado = 'PARCIAL', monto_pagado = 100.00 WHERE id = ?", marzoValeria);

		assertThatThrownBy(() -> cobro.cobrar(new CobroRequest(revision.clave(), f.quispe(), revision.cuotaIds(),
				MedioPago.EFECTIVO, null, new BigDecimal("1000"), null, revision.total(), TipoComprobante.BOLETA, null,
				null, null)))
				.isInstanceOf(MontoCambiadoException.class)
				.hasMessage("El monto cambió desde que lo revisaste (ahora es S/ 800.00). Revisa de nuevo antes de cobrar.");
		assertThat(contar(jdbc, "pago")).isZero();
		// Con la revisión nueva (misma clave) sí se cobra lo que corresponde.
		RevisionCobro nueva = cobro.revisar(f.quispe(), new SeleccionCobroRequest(revision.cuotaIds(), MedioPago.EFECTIVO),
				revision.clave());
		assertThat(nueva.total()).isEqualByComparingTo("800.00");
		assertThat(nueva.clave()).isEqualTo(revision.clave());
	}

	@Test
	void digitalSinNumeroDeOperacionEsRechazado() {
		for (String operacion : new String[] { null, "  ", "12" }) {
			assertThatThrownBy(() -> cobro.cobrar(EscenarioCaja.digital(f.quispe(), List.of(marzoMateo), MedioPago.YAPE,
					operacion, "450.00"))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("operación");
		}
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(contar(jdbc, "comprobante")).isZero();
		// El número de boleta no se gastó: la serie sigue en 0.
		assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie_comprobante WHERE serie = 'B001'", Integer.class))
				.isZero();
	}

	@Test
	void mismoNumeroDeOperacionYapeNoSeRegistraDosVeces() {
		cobro.cobrar(EscenarioCaja.digital(f.quispe(), List.of(marzoMateo), MedioPago.YAPE, "abc 12345", "450.00"));
		assertThat(jdbc.queryForObject("SELECT operacion_vigente FROM pago", String.class)).isEqualTo("ABC12345");

		assertThatThrownBy(() -> cobro.cobrar(EscenarioCaja.digital(f.quispe(), List.of(marzoValeria), MedioPago.YAPE,
				"ABC12345", "450.00"))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya está registrado en otro pago");
		assertThat(contar(jdbc, "pago")).isEqualTo(1);
		assertThat(estado(jdbc, marzoValeria)).isEqualTo("PENDIENTE");
		// En la base también es UNIQUE (colegio, medio, operación vigente).
		assertThatThrownBy(() -> jdbc.update("INSERT INTO pago (colegio_id, familia_id, caja_diaria_id, cajero, fecha, "
				+ "comprobante_id, medio, numero_operacion, operacion_vigente, total, origen, clave_idempotencia, estado, "
				+ "creado_en, creado_por, actualizado_en) SELECT colegio_id, familia_id, caja_diaria_id, cajero, fecha, "
				+ "comprobante_id, medio, numero_operacion, operacion_vigente, total, origen, 'otra-clave', estado, creado_en, "
				+ "creado_por, actualizado_en FROM pago")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	@Test
	void dobleClicConLaMismaClaveDevuelveElMismoPago() throws Exception {
		CobroRequest solicitud = efectivo(f.quispe(), List.of(marzoMateo, marzoValeria), "900.00", "1000.00");

		Long primero = cobro.cobrar(solicitud);
		Long segundo = cobro.cobrar(solicitud);
		assertThat(segundo).isEqualTo(primero);

		// Y a la vez (dos pestañas o un doble clic real): la caja bloqueada los pone en fila.
		CobroRequest otra = efectivo(f.flores(), List.of(cuota(jdbc, f.sebastian(), "PEN-2027-03")), "450.00", "500.00");
		ExecutorService enParalelo = Executors.newFixedThreadPool(2);
		CountDownLatch largada = new CountDownLatch(1);
		try {
			Future<Long> a = enParalelo.submit(() -> cobrarComo(largada, otra));
			Future<Long> b = enParalelo.submit(() -> cobrarComo(largada, otra));
			largada.countDown();
			assertThat(a.get(30, TimeUnit.SECONDS)).isEqualTo(b.get(30, TimeUnit.SECONDS));
		}
		finally {
			enParalelo.shutdownNow();
		}
		assertThat(contar(jdbc, "pago")).isEqualTo(2);
		assertThat(contar(jdbc, "comprobante")).isEqualTo(2);
		assertThat(contar(jdbc, "aplicacion_pago")).isEqualTo(3);
		assertThat(EscenarioCaja.pagado(jdbc, marzoMateo)).isEqualByComparingTo("450.00");
	}

	private Long cobrarComo(CountDownLatch largada, CobroRequest solicitud) throws InterruptedException {
		largada.await();
		como(CAJA);
		try {
			return cobro.cobrar(solicitud);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	void cuotaConAnulacionPendienteNoSeCobra() {
		como(EscenarioCobranza.ADMINISTRACION);
		anulaciones.solicitar(marzoMateo, "Se generó con el monto equivocado");
		como(CAJA);

		assertThatThrownBy(() -> cobro.revisar(f.quispe(), new SeleccionCobroRequest(List.of(marzoMateo), MedioPago.YAPE),
				null)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("anulación esperando aprobación");
		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("anulación esperando aprobación");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(cobro.cuentaDeFamilia(f.quispe()).alumnos().stream().flatMap(a -> a.cuotas().stream())
				.filter(c -> c.id().equals(marzoMateo)).findFirst().orElseThrow().cobrable()).isFalse();
	}

	@Test
	void pagoACuentaDesactivadoRechazaMontoMenor() {
		CobroRequest aCuenta = new CobroRequest(UUID.randomUUID(), f.quispe(), List.of(marzoMateo, marzoValeria),
				MedioPago.EFECTIVO, null, new BigDecimal("500.00"), new BigDecimal("500.00"), new BigDecimal("900.00"),
				TipoComprobante.BOLETA, null, null, null);

		assertThatThrownBy(() -> cobro.cobrar(aCuenta)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("pago a cuenta no está habilitado");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(estado(jdbc, marzoMateo)).isEqualTo("PENDIENTE");
	}

	@Test
	void noSePuedeCobrarSinCerrarLaCajaDeAyer() {
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		((RelojAjustable) reloj).avanzar(Duration.ofDays(1));

		assertThat(cobro.buscar("quispe").cajaAnteriorAbierta()).isEqualTo(java.time.LocalDate.of(2026, 10, 2));
		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), List.of(marzoValeria), "450.00", "450.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Primero cierra tu caja del 02/10/2026");
		assertThat(contar(jdbc, "pago")).isEqualTo(1);
		// Otra cajera sí puede cobrar: su caja de ayer no existe.
		como(EscenarioCaja.CAJA_2);
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoValeria), "450.00", "450.00"));
		assertThat(contar(jdbc, "pago")).isEqualTo(2);
	}

	@Test
	void cobroQuedaAuditadoConComprobanteMontosYCuotas() {
		Long pagoId = cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo, marzoValeria), "900.00", "1000.00"));

		Map<String, Object> evento = EscenarioEscolar.ultimoEvento(jdbc, "PAGO_REGISTRADO");
		assertThat(evento).containsEntry("nombre_usuario", "caja").containsEntry("entidad", "pago")
				.containsEntry("entidad_id", pagoId.toString());
		assertThat((String) evento.get("valor_nuevo")).contains("VIGENTE", "S/ 900.00", "Efectivo", "B001-00000001");
		assertThat((String) evento.get("detalle")).contains("B001-00000001", "S/ 900.00", "recibido S/ 1,000.00",
				"vuelto S/ 100.00", "Familia Quispe Huamán", "Pensión marzo 2027 de Mateo Quispe Huamán",
				"Pensión marzo 2027 de Valeria Quispe Huamán", "PAGADA", "DNI ****8912");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "COMPROBANTE_EMITIDO").get("valor_nuevo"))
				.isEqualTo("B001-00000001 · S/ 900.00");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "CAJA_ABIERTA")).containsEntry("valor_nuevo", "ABIERTA");
		// Ley 29733: la bitácora no guarda documentos completos.
		assertThat(EscenarioEscolar.todaLaBitacora(jdbc)).doesNotContain(EscenarioEscolar.DNI_ROSA);
	}
}
