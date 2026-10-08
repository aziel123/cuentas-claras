package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.AutoaprobacionSolicitudException;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.descuento;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA_Y_ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Descuentos y becas (sprint 3, tanda 2): Administración los pide, otra persona de Promotoría o Dirección los aprueba,
 * y al aprobarse se vuelven a calcular. El sistema calcula cuánto se deja de cobrar.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioDescuentosTest {

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	private Long marzo;

	private Long abril;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		abril = cuota(jdbc, f.mateo(), "PEN-2027-04");
		como(ADMINISTRACION);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void administracionPideYDireccionAprueba() {
		Long id = descuentos.solicitar(descuento(f.mateo(), TipoDescuento.HERMANOS, "10", List.of(marzo, abril)));
		Map<String, Object> pedido = jdbc.queryForMap("SELECT * FROM descuento WHERE id = ?", id);
		assertThat(pedido).containsEntry("estado", "SOLICITADO").containsEntry("cuotas", "," + marzo + "," + abril + ",")
				.containsEntry("creado_por", "administracion");
		assertThat((BigDecimal) pedido.get("total_estimado")).isEqualByComparingTo("90.00");
		// Hasta que se apruebe, se sigue cobrando completo.
		assertThat(jdbc.queryForObject("SELECT monto_descuento FROM cuota WHERE id = ?", BigDecimal.class, marzo))
				.isEqualByComparingTo("0.00");

		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "descuento", id);

		assertThat(jdbc.queryForMap("SELECT estado, resuelto_por FROM descuento WHERE id = ?", id))
				.containsEntry("estado", "APROBADO").containsEntry("resuelto_por", "director");
		for (Long cuota : List.of(marzo, abril)) {
			assertThat(jdbc.queryForObject("SELECT monto_descuento FROM cuota WHERE id = ?", BigDecimal.class, cuota))
					.isEqualByComparingTo("45.00");
		}
		assertThat(jdbc.queryForObject("SELECT SUM(monto) FROM ajuste_cuota WHERE descuento_id = ?", BigDecimal.class, id))
				.isEqualByComparingTo("90.00");
		// La caja ya cobra el monto con descuento.
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(EscenarioCaja.efectivo(f.quispe(), List.of(marzo), "405.00", "405.00"));
		assertThat(jdbc.queryForObject("SELECT total FROM pago WHERE id = ?", BigDecimal.class, pago))
				.isEqualByComparingTo("405.00");
		assertThat(EscenarioCaja.estado(jdbc, marzo)).isEqualTo("PAGADA");
	}

	@Test
	void quienPideNoAprueba() {
		como(PROMOTORIA_Y_ADMINISTRACION);
		Long id = descuentos.solicitar(descuento(f.mateo(), TipoDescuento.BECA, "100", List.of(marzo)));
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "descuento", id);

		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null)).isInstanceOf(AutoaprobacionSolicitudException.class);
		assertThat(jdbc.queryForObject("SELECT estado FROM descuento WHERE id = ?", String.class, id))
				.isEqualTo("SOLICITADO");
		assertThat(contar(jdbc, "ajuste_cuota")).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'AUTOAPROBACION_RECHAZADA'",
				Long.class)).isEqualTo(1);
		// Ni por SQL: la base exige que quien resuelve no sea quien lo pidió.
		assertThatThrownBy(() -> jdbc.update("UPDATE descuento SET estado = 'APROBADO', resuelto_por = creado_por, "
				+ "resuelto_en = CURRENT_TIMESTAMP WHERE id = ?", id)).isInstanceOf(DataIntegrityViolationException.class);
		// Otra persona sí: la beca del 100 % deja la cuota EXONERADA.
		EscenarioAprobaciones.aprueba(PROMOTORIA, bandeja, jdbc, "descuento", id);
		assertThat(EscenarioCaja.estado(jdbc, marzo)).isEqualTo("EXONERADA");
	}

	@Test
	void descuentoPorHermanosExigeDosHermanosMatriculados() {
		Long cuotaSebastian = cuota(jdbc, f.sebastian(), "PEN-2027-03");

		assertThatThrownBy(() -> descuentos.solicitar(descuento(f.sebastian(), TipoDescuento.HERMANOS, "10",
				List.of(cuotaSebastian)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("dos o más hermanos con matrícula activa en 2027");
		// Una beca no lo exige.
		descuentos.solicitar(descuento(f.sebastian(), TipoDescuento.BECA, "50", List.of(cuotaSebastian)));
		assertThat(descuentos.prepararSolicitud("78451236").hermanosMatriculados())
				.containsExactlyInAnyOrder("Mateo Quispe Huamán", "Valeria Quispe Huamán");
	}

	@Test
	void soloCuotasPendientesOParciales() {
		como(EscenarioCaja.CAJA);
		cobro.cobrar(EscenarioCaja.efectivo(f.quispe(), List.of(marzo), "450.00", "450.00"));
		como(ADMINISTRACION);

		assertThatThrownBy(() -> descuentos.solicitar(descuento(f.mateo(), TipoDescuento.HERMANOS, "10",
				List.of(marzo)))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("pendientes o parciales");
		assertThatThrownBy(() -> descuentos.solicitar(descuento(f.mateo(), TipoDescuento.HERMANOS, "10",
				List.of(cuota(jdbc, f.valeria(), "PEN-2027-04"))))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no es de Mateo");
		assertThat(descuentos.prepararSolicitud("78451236").cuotas()).extracting(c -> c.id()).doesNotContain(marzo)
				.contains(abril);
		// Una cuota parcial: ServicioCobroPagoACuentaTest.descuentoSobreCuotaParcialConPagoACuentaReal.
	}

	// descuentoCambiadoDesdeLaSolicitudNoSeAplica pasó a ServicioCobroPagoACuentaTest con un pago a cuenta real.

	@Test
	void promotorYCajaReciben403AlPedir() {
		for (var quien : List.of(PROMOTORIA, EscenarioCaja.CAJA, DIRECCION)) {
			como(quien);
			assertThatThrownBy(() -> descuentos.solicitar(descuento(f.mateo(), TipoDescuento.HERMANOS, "10",
					List.of(marzo)))).as(quien.getUsername()).isInstanceOf(AccessDeniedException.class);
		}
		como(EscenarioCaja.CAJA);
		assertThatThrownBy(() -> descuentos.lista()).isInstanceOf(AccessDeniedException.class);
		assertThat(contar(jdbc, "descuento")).isZero();
	}

	@Test
	void ajustesQuedanAuditadosPorCuota() {
		Long id = descuentos.solicitar(descuento(f.mateo(), TipoDescuento.HERMANOS, "10", List.of(marzo, abril)));
		EscenarioAprobaciones.aprueba(PROMOTORIA, bandeja, jdbc, "descuento", id);

		assertThat(jdbc.queryForList("SELECT entidad_id FROM evento_auditoria WHERE accion = 'DESCUENTO_APROBADO' "
				+ "AND entidad = 'cuota' AND nombre_usuario = 'promotor' ORDER BY secuencia", String.class))
				.containsExactly(marzo.toString(), abril.toString());
		Map<String, Object> evento = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento(jdbc,
				"DESCUENTO_APROBADO");
		assertThat((String) evento.get("valor_anterior")).contains("PENDIENTE", "S/ 450.00");
		assertThat((String) evento.get("valor_nuevo")).contains("S/ 405.00");
		assertThat((String) evento.get("detalle")).contains("Descuento por hermanos", "10 %", "−S/ 45.00",
				"Pedido por administracion", "aprobado por promotor");
		assertThat(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento(jdbc,
				"DESCUENTO_SOLICITADO").get("detalle")).asString().contains("S/ 90.00");
	}
}
