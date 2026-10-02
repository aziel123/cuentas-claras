package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.MOTIVO_ANULACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.correccion;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.estado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.pagado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Anulación de pagos aprobada por otra persona (sprint 3, tanda 2): nota de crédito sin huecos, reversiones con filas
 * nuevas (nada se borra) y, si es corrección, pago de reemplazo en la misma caja con boleta nueva.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ManejadorAnulacionPagoTest {

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	private Long marzoMateo;

	private Long marzoValeria;

	private Long pago;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		marzoMateo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		marzoValeria = cuota(jdbc, f.valeria(), "PEN-2027-03");
		como(CAJA);
		pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "500.00"));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void devolucionRevierteConFilasNuevasYEmiteNotaDeCredito() {
		anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION);
		// Pedir no cambia nada.
		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("VIGENTE");
		assertThat(estado(jdbc, marzoMateo)).isEqualTo("PAGADA");

		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);

		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("ANULADO");
		assertThat(estado(jdbc, marzoMateo)).isEqualTo("PENDIENTE");
		assertThat(pagado(jdbc, marzoMateo)).isEqualByComparingTo("0.00");
		// Nada se borró: la aplicación original sigue y hay una reversión negativa que apunta a ella.
		List<Map<String, Object>> libro = jdbc.queryForList("SELECT id, tipo, monto, revierte_id FROM aplicacion_pago "
				+ "WHERE pago_id = ? ORDER BY id", pago);
		assertThat(libro).hasSize(2);
		assertThat(libro.get(0)).containsEntry("tipo", "APLICACION");
		assertThat(libro.get(1)).containsEntry("tipo", "REVERSION").containsEntry("revierte_id", libro.get(0).get("id"));
		assertThat((BigDecimal) libro.get(1).get("monto")).isEqualByComparingTo("-450.00");
		Map<String, Object> anulacion = jdbc.queryForMap("SELECT * FROM anulacion_pago WHERE pago_id = ?", pago);
		assertThat(anulacion).containsEntry("tipo", "DEVOLUCION").containsEntry("cajero_pago", "caja")
				.containsEntry("solicitado_por", "caja").containsEntry("aprobado_por", "director")
				.containsEntry("posterior_al_cierre", false);
		Map<String, Object> nota = jdbc.queryForMap("SELECT * FROM comprobante WHERE id = ?", anulacion.get("nota_credito_id"));
		assertThat(nota).containsEntry("tipo", "NOTA_CREDITO").containsEntry("serie", "BC01").containsEntry("numero", 1)
				.containsEntry("modifica_id", jdbc.queryForObject("SELECT comprobante_id FROM pago WHERE id = ?", Long.class,
						pago));
		assertThat((BigDecimal) nota.get("total")).isEqualByComparingTo("450.00");
		// La cuota vuelve a cobrarse con una boleta nueva (el número anulado no se reutiliza).
		como(CAJA);
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		assertThat(jdbc.queryForList("SELECT CONCAT(serie, '-', numero) FROM comprobante ORDER BY id", String.class))
				.containsExactly("B001-1", "BC01-1", "B001-2");
	}

	@Test
	void notaDeCreditoUsaSerieBc01ParaBoletas() {
		Long factura = cobro.cobrar(new CobroRequest(UUID.randomUUID(), f.quispe(), List.of(marzoValeria),
				MedioPago.TRANSFERENCIA, "OP-123456", null, null, new BigDecimal("450.00"), TipoComprobante.FACTURA, null,
				"20131312955", "Comercial Quispe S.A.C."));
		anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION);
		anulaciones.solicitarDevolucion(factura, "La empresa pidió anular la factura emitida");
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", factura);

		assertThat(jdbc.queryForList("SELECT CONCAT(n.serie, '-', n.numero, ' anula ', o.serie) FROM comprobante n "
				+ "JOIN comprobante o ON o.id = n.modifica_id ORDER BY n.id", String.class))
				.containsExactly("BC01-1 anula B001", "FC01-1 anula F001");
		// La transferencia anulada libera su número de operación.
		assertThat(jdbc.queryForObject("SELECT operacion_vigente FROM pago WHERE id = ?", String.class, factura)).isNull();
		assertThat(jdbc.queryForObject("SELECT numero_operacion FROM pago WHERE id = ?", String.class, factura))
				.isEqualTo("OP-123456");
	}

	@Test
	void correccionCreaReemplazoEnLaMismaCajaConBoletaNueva() {
		// Se cobró la pensión de Mateo y era la de Valeria.
		anulaciones.solicitarCorreccion(pago, correccion(f.quispe(), List.of(marzoValeria)));
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);

		assertThat(estado(jdbc, marzoMateo)).isEqualTo("PENDIENTE");
		assertThat(estado(jdbc, marzoValeria)).isEqualTo("PAGADA");
		Map<String, Object> reemplazo = jdbc.queryForMap("SELECT * FROM pago WHERE reemplaza_pago_id = ?", pago);
		Map<String, Object> original = jdbc.queryForMap("SELECT * FROM pago WHERE id = ?", pago);
		assertThat(reemplazo).containsEntry("origen", "REEMPLAZO").containsEntry("estado", "VIGENTE")
				.containsEntry("caja_diaria_id", original.get("caja_diaria_id")).containsEntry("cajero", "caja")
				.containsEntry("medio", "EFECTIVO").containsEntry("creado_por", "director");
		assertThat((BigDecimal) reemplazo.get("total")).isEqualByComparingTo("450.00");
		assertThat((BigDecimal) reemplazo.get("recibido")).isEqualByComparingTo("500.00");
		assertThat(jdbc.queryForList("SELECT CONCAT(serie, '-', numero) FROM comprobante ORDER BY id", String.class))
				.containsExactly("B001-1", "BC01-1", "B001-2");
		assertThat(jdbc.queryForObject("SELECT CONCAT(serie, '-', numero) FROM comprobante WHERE id = ?", String.class,
				reemplazo.get("comprobante_id"))).isEqualTo("B001-2");
		// En la caja, el efectivo vigente no cambia: el anulado sale y el reemplazo entra.
		assertThat(jdbc.queryForObject("SELECT SUM(total) FROM pago WHERE estado = 'VIGENTE' AND medio = 'EFECTIVO'",
				BigDecimal.class)).isEqualByComparingTo("450.00");
	}

	@Test
	void correccionAOtraFamiliaQuedaResaltada() {
		Long cuotaSebastian = cuota(jdbc, f.sebastian(), "PEN-2027-03");
		anulaciones.solicitarCorreccion(pago, correccion(f.flores(), List.of(cuotaSebastian)));

		como(DIRECCION);
		SolicitudVista tarjeta = bandeja.bandeja().pendientes().getFirst();
		assertThat(tarjeta.tipo()).isEqualTo("ANULACION_PAGO");
		assertThat(tarjeta.advertencia()).contains("OTRA familia");
		assertThat(tarjeta.resumen()).contains("pasa a OTRA familia, Familia Flores Rojas");
		assertThat(tarjeta.detalle()).anyMatch(l -> l.startsWith("Pasa a Familia Flores Rojas: Pensión marzo 2027 de "
				+ "Sebastián Flores Rojas"));

		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);
		assertThat(estado(jdbc, cuotaSebastian)).isEqualTo("PAGADA");
		assertThat(estado(jdbc, marzoMateo)).isEqualTo("PENDIENTE");
		Map<String, Object> evento = EscenarioEscolar.ultimoEvento(jdbc, "PAGO_REEMPLAZO_REGISTRADO");
		assertThat((String) evento.get("detalle")).contains("el dinero pasa a OTRA familia");
		assertThat(AccionAuditoria.PAGO_REEMPLAZO_REGISTRADO.requiereAtencion()).isTrue();
		// La boleta nueva sale a nombre del responsable de la otra familia.
		assertThat(jdbc.queryForObject("SELECT receptor_numero_documento FROM comprobante WHERE serie = 'B001' AND numero = 2",
				String.class)).isEqualTo(EscenarioCaja.DNI_PEDRO);
	}

	@Test
	void cuotasDeLaCorreccionYaPagadasRechazanLaAprobacion() {
		anulaciones.solicitarCorreccion(pago, correccion(f.quispe(), List.of(marzoValeria)));
		// Mientras esperaba, Valeria pagó esa cuota.
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoValeria), "450.00", "450.00"));

		assertThatThrownBy(() -> EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya no se puede cobrar");
		// Todo o nada: ni nota de crédito, ni anulación, ni reversiones.
		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("VIGENTE");
		assertThat(contar(jdbc, "anulacion_pago")).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante WHERE tipo = 'NOTA_CREDITO'", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aplicacion_pago WHERE tipo = 'REVERSION'", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE entidad = 'pago'", String.class))
				.isEqualTo("PENDIENTE");
	}

	@Test
	void rechazarNoTocaElPago() {
		anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION);
		como(DIRECCION);
		bandeja.rechazar(EscenarioAprobaciones.pendiente(jdbc, "pago", pago), "El apoderado confirmó que sí pagó esto");

		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("VIGENTE");
		assertThat(estado(jdbc, marzoMateo)).isEqualTo("PAGADA");
		assertThat(contar(jdbc, "anulacion_pago")).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante WHERE tipo = 'NOTA_CREDITO'", Long.class)).isZero();
		// Y se puede volver a pedir.
		como(CAJA);
		anulaciones.solicitarDevolucion(pago, "Ahora sí: el apoderado pidió su devolución");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_cambio WHERE estado = 'PENDIENTE'", Long.class))
				.isEqualTo(1);
	}

	@Test
	void anulacionYReversionesQuedanAuditadas() {
		anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION);
		Map<String, Object> pedido = EscenarioEscolar.ultimoEvento(jdbc, "PAGO_ANULACION_SOLICITADA");
		assertThat(pedido).containsEntry("nombre_usuario", "caja").containsEntry("entidad_id", pago.toString());
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);

		Map<String, Object> anulado = EscenarioEscolar.ultimoEvento(jdbc, "PAGO_ANULADO");
		assertThat(anulado).containsEntry("nombre_usuario", "director").containsEntry("valor_anterior", "VIGENTE")
				.containsEntry("valor_nuevo", "ANULADO");
		assertThat((String) anulado.get("detalle")).contains("Devolución del pago B001-00000001", "S/ 450.00",
				"Nota de crédito BC01-00000001", "Pensión marzo 2027 de Mateo Quispe Huamán vuelve a deber S/ 450.00",
				"Pedido por caja, aprobado por director", MOTIVO_ANULACION);
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "NOTA_CREDITO_EMITIDA").get("valor_nuevo"))
				.isEqualTo("BC01-00000001 · S/ 450.00");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "SOLICITUD_APROBADA")).containsEntry("nombre_usuario", "director");
		assertThat(AccionAuditoria.PAGO_ANULADO.requiereAtencion()).isTrue();
		assertThat(EscenarioEscolar.todaLaBitacora(jdbc)).doesNotContain(EscenarioEscolar.CELULAR_ROSA);
	}
}
