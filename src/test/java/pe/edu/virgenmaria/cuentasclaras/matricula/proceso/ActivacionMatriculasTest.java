package pe.edu.virgenmaria.cuentasclaras.matricula.proceso;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ReservasMatricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.AlertasMatricula;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioCampanaRenovacion;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioRenovacionFamilia;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.SimuladorPagos;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion.RESPONDEN_HASTA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion.renovacionDe;

/**
 * Sprint 5, tanda 2 (G18; decisión 57): la matrícula 2027 pasa a ACTIVA solo cuando su cuota de matrícula queda pagada,
 * la activa {@code sistema.matricula} (nunca una persona) y recién entonces se generan las 10 pensiones. Si después se
 * anula ese pago, no se desactiva sola: queda una alerta.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ActivacionMatriculasTest {

	@Autowired
	private ActivacionMatriculas activacion;

	@Autowired
	private ReservasMatricula reservas;

	@Autowired
	private ServicioCampanaRenovacion campana;

	@Autowired
	private ServicioRenovacionFamilia familia;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioPagoEnLinea pagoEnLinea;

	@Autowired
	private SimuladorPagos simulador;

	@Autowired
	private AlertasMatricula alertas;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioRenovacion.Datos d;

	private Long matricula;

	private Long cuotaMatricula;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		d = EscenarioRenovacion.preparar(estructura, alumnos, planes, jdbc, true);
		como(ADMINISTRACION);
		campana.abrir(d.anio2027(), RESPONDEN_HASTA);
		como(d.rosaEnLinea());
		familia.responder(renovacionDe(jdbc, d.mateo()), true);
		SecurityContextHolder.clearContext();
		matricula = jdbc.queryForObject("SELECT matricula_id FROM renovacion_matricula WHERE alumno_id = ?", Long.class,
				d.mateo());
		cuotaMatricula = EscenarioCaja.cuota(jdbc, d.mateo(), "MAT-2027");
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private String estadoMatricula() {
		return jdbc.queryForObject("SELECT estado FROM matricula WHERE id = ?", String.class, matricula);
	}

	private long pensiones() {
		return contar(jdbc, "cuota WHERE tipo = 'PENSION' AND matricula_id = " + matricula);
	}

	private Long pagarMatriculaEnCaja() {
		como(EscenarioCobranza.CAJA);
		Long pago = cobro.cobrar(efectivo(d.quispe(), List.of(cuotaMatricula), "300.00", "300.00"));
		SecurityContextHolder.clearContext();
		return pago;
	}

	@Test
	void soloSeActivaConLaMatriculaPagada() {
		// Sin pagar, el barrido no la activa y nadie la activa a mano.
		assertThat(activacion.activar(1L)).isZero();
		assertThat(estadoMatricula()).isEqualTo("RESERVADA");
		como(DIRECCION);
		assertThatThrownBy(() -> reservas.activar(matricula)).isInstanceOf(AccessDeniedException.class);
		SecurityContextHolder.clearContext();

		pagarMatriculaEnCaja();
		// Después del commit del pago, sistema.matricula la activó.
		assertThat(jdbc.queryForMap("SELECT estado, activada_por FROM matricula WHERE id = ?", matricula))
				.containsEntry("estado", "ACTIVA").containsEntry("activada_por", "sistema.matricula");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "MATRICULA_ACTIVADA")).containsEntry("nombre_usuario",
				"sistema.matricula").containsEntry("valor_anterior", "RESERVADA");
		// Idempotente: el barrido no hace nada más.
		assertThat(activacion.activar(1L)).isZero();
	}

	@Test
	void alActivarseGeneraLasDiezPensiones() {
		assertThat(pensiones()).isZero();
		pagarMatriculaEnCaja();
		assertThat(pensiones()).isEqualTo(10);
		assertThat(jdbc.queryForObject("SELECT SUM(monto) FROM cuota WHERE tipo = 'PENSION' AND matricula_id = ?",
				BigDecimal.class, matricula)).isEqualByComparingTo("4500.00");
		// La de matrícula no se duplica ni se registra como «deuda existente».
		assertThat(contar(jdbc, "cuota WHERE tipo = 'MATRICULA' AND matricula_id = " + matricula)).isEqualTo(1);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'CUOTA_OMITIDA_DEUDA_EXISTENTE'")).isZero();
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "CRONOGRAMA_GENERADO")).containsEntry("nombre_usuario",
				"sistema.matricula");
	}

	@Test
	void pagoEnLineaDeLaMatriculaActivaSola() {
		como(d.rosaEnLinea());
		BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(List.of(cuotaMatricula))).total();
		String referencia = pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), List.of(cuotaMatricula),
				total, false));
		simulador.simular(referencia, PasarelaSimulada.Accion.YAPE);
		SecurityContextHolder.clearContext();
		assertThat(EscenarioCaja.estado(jdbc, cuotaMatricula)).isEqualTo("PAGADA");
		assertThat(estadoMatricula()).isEqualTo("ACTIVA");
		assertThat(pensiones()).isEqualTo(10);
		// La familia recibió el aviso del pago (con su boleta), sin que nadie digite nada.
		assertThat(contar(jdbc, "mensaje WHERE tipo = 'PAGO_REGISTRADO' AND familia_id = " + d.quispe())).isEqualTo(1);
	}

	@Test
	void anularElPagoNoDesactivaPeroAlerta() {
		Long pago = pagarMatriculaEnCaja();
		assertThat(estadoMatricula()).isEqualTo("ACTIVA");
		como(EscenarioCobranza.CAJA);
		anulaciones.solicitarDevolucion(pago, EscenarioAprobaciones.MOTIVO_ANULACION);
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);
		SecurityContextHolder.clearContext();

		assertThat(EscenarioCaja.estado(jdbc, cuotaMatricula)).isEqualTo("PENDIENTE");
		assertThat(estadoMatricula()).isEqualTo("ACTIVA");
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).anySatisfy(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.ATENCION);
			assertThat(a.texto()).contains("Mateo Quispe Huamán").contains("otra vez por pagar");
		});
	}

	@Test
	void desistimientoConLaCuotaAnuladaRetiraLaReservada() {
		// Administración pide anular la cuota de matrícula y otra persona lo aprueba (decisión 58).
		jdbc.update("UPDATE cuota SET estado = 'ANULADA', obligacion = NULL, anulada_en = CURRENT_TIMESTAMP, "
				+ "anulacion_motivo = 'La familia desistió de la matrícula', anulacion_solicitada_por = 'administracion', "
				+ "anulacion_aprobada_por = 'director' WHERE id = ?", cuotaMatricula);
		assertThat(activacion.activar(1L)).isZero();
		assertThat(activacion.desistir(1L)).isEqualTo(1);
		assertThat(estadoMatricula()).isEqualTo("RETIRADA");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "MATRICULA_DESISTIDA")).containsEntry("nombre_usuario",
				"sistema.matricula");
	}
}
