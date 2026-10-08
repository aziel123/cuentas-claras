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
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.AplicacionIngresoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.DevolucionesPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioIngresosPorRevisar;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada.Accion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.SimuladorPagos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 4, tanda 1: un ingreso en línea que quedó por revisar. Administración PIDE aplicarlo o devolverlo; Promotoría o
 * Dirección aprueban; el pago lo registra el sistema y la devolución la ejecuta otra persona de Administración, siempre
 * al mismo medio de origen. Nadie entrega efectivo.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class IngresosPorRevisarTest {

	@Autowired
	private ServicioPagoEnLinea pagoEnLinea;

	@Autowired
	private SimuladorPagos simulador;

	@Autowired
	private ServicioIngresosPorRevisar ingresos;

	@Autowired
	private DevolucionesPasarela devoluciones;

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

	private Long ordenId;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		UsuarioAutenticado rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa());
		// Rosa inicia el pago en línea de marzo; la cajera cobra marzo en ventanilla; luego la pasarela confirma.
		como(rosa);
		BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(List.of(marzo))).total();
		String referencia = pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), List.of(marzo), total, false));
		como(CAJA);
		cobro.cobrar(efectivo(f.quispe(), List.of(marzo), "450.00", "450.00"));
		como(rosa);
		simulador.simular(referencia, Accion.YAPE);
		ordenId = jdbc.queryForObject("SELECT id FROM orden_pago WHERE referencia = ?", Long.class, referencia);
		assertThat(estadoOrden()).isEqualTo("POR_REVISAR");
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private String estadoOrden() {
		return jdbc.queryForObject("SELECT estado FROM orden_pago WHERE id = ?", String.class, ordenId);
	}

	@Test
	void aplicarAOtraCuotaConAprobacionLoRegistraElSistema() {
		Long abril = cuota(jdbc, f.mateo(), "PEN-2027-04");
		como(CAJA);
		assertThatThrownBy(() -> ingresos.solicitarAplicacion(ordenId, new AplicacionIngresoRequest(f.quispe(),
				List.of(abril), "Llamé a la familia y pide aplicarlo a abril"))).isInstanceOfAny(AccessDeniedException.class,
				AuthorizationDeniedException.class);

		como(EscenarioCobranza.ADMINISTRACION);
		ingresos.solicitarAplicacion(ordenId, new AplicacionIngresoRequest(f.quispe(), List.of(abril),
				"Llamé a la familia y pide aplicarlo a abril"));
		assertThatThrownBy(() -> ingresos.solicitarDevolucion(ordenId, "Mejor devolverlo a la familia"))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(EscenarioCaja.estado(jdbc, abril)).isEqualTo("PENDIENTE");

		EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "orden_pago", ordenId);

		assertThat(estadoOrden()).isEqualTo("APLICADA");
		assertThat(EscenarioCaja.estado(jdbc, abril)).isEqualTo("PAGADA");
		assertThat(jdbc.queryForObject("SELECT CONCAT(origen, ':', cajero) FROM pago WHERE orden_pago_id = ?", String.class,
				ordenId)).isEqualTo("PASARELA:sistema.pasarela");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion IN ('INGRESO_APLICACION_SOLICITADA', 'INGRESO_APLICADO')"))
				.isEqualTo(2);
	}

	@Test
	void laDevolucionLaEjecutaOtraPersonaYVuelveAlMismoMedio() {
		como(EscenarioCobranza.ADMINISTRACION);
		assertThatThrownBy(() -> devoluciones.devolverOrden(ordenId)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("aprobada");
		ingresos.solicitarDevolucion(ordenId, "La familia ya pagó marzo en ventanilla");

		// Quien pidió no aprueba; quien aprueba (también de Administración) no ejecuta.
		assertThatThrownBy(() -> bandeja.aprobar(EscenarioAprobaciones.pendiente(jdbc, "orden_pago", ordenId), null))
				.isInstanceOfAny(AccessDeniedException.class, AuthorizationDeniedException.class, ReglaNegocioException.class);
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION_Y_ADMINISTRACION, bandeja, jdbc, "orden_pago", ordenId);
		assertThat(estadoOrden()).isEqualTo("POR_REVISAR");
		assertThatThrownBy(() -> devoluciones.devolverOrden(ordenId)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("otra persona");

		como(EscenarioCobranza.ADMINISTRACION_2);
		String reembolso = devoluciones.devolverOrden(ordenId);

		assertThat(reembolso).startsWith("SIMREF");
		assertThat(estadoOrden()).isEqualTo("DEVUELTA");
		assertThat(jdbc.queryForObject("SELECT CONCAT(devolucion_operacion, ':', devuelto_por) FROM orden_pago WHERE id = ?",
				String.class, ordenId)).isEqualTo(reembolso + ":administracion2");
		assertThat(contar(jdbc, "pago WHERE orden_pago_id IS NOT NULL")).isZero();
		assertThatThrownBy(() -> devoluciones.devolverOrden(ordenId)).isInstanceOf(ReglaNegocioException.class);
	}
}
