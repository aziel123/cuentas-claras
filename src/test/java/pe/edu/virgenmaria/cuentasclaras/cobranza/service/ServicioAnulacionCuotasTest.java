package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.CronogramaAlumno;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.MOTIVO;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/**
 * Gancho de anulación (sprint 2): solo deja la solicitud pendiente. La cuota sigue vigente y se sigue debiendo; la
 * aprobación por otra persona la conecta Aprobaciones en el sprint 3.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioAnulacionCuotasTest {

	@Autowired
	private ServicioAnulacionCuotas anulaciones;

	@Autowired
	private ServicioCronograma cronogramas;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private CuotaRepository cuotas;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private JdbcTemplate jdbc;

	private Long mateo;

	private Long setiembre;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		como(ADMINISTRACION);
		Estructura escuela = EscenarioEscolar.crearEstructura(estructura);
		mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())).alumnoId();
		EscenarioCobranza.planAprobado(planes, escuela.anio2027(), 2027, Nivel.PRIMARIA, "450", "350", null);
		setiembre = jdbc.queryForObject("SELECT id FROM cuota WHERE obligacion = 'PEN-2027-09'", Long.class);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void laSolicitudQuedaPendienteYLaCuotaSeSigueDebiendo() {
		anulaciones.solicitar(setiembre, MOTIVO);

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cuota WHERE id = ?", setiembre);
		assertThat(fila).containsEntry("estado", "PENDIENTE").containsEntry("obligacion", "PEN-2027-09")
				.containsEntry("anulacion_solicitada_por", "administracion").containsEntry("anulacion_motivo", MOTIVO);
		assertThat(fila.get("anulacion_aprobada_por")).isNull();
		assertThat(fila.get("anulada_en")).isNull();
		CronogramaAlumno cronograma = cronogramas.deAlumno(mateo);
		assertThat(cronograma.saldo()).isEqualByComparingTo("4850.00");
		assertThat(cronograma.cuotas()).filteredOn(c -> c.id().equals(setiembre)).singleElement()
				.satisfies(c -> assertThat(c.anulacionPendiente()).isTrue());
		Map<String, Object> evento = ultimoEvento(jdbc, "CUOTA_ANULACION_SOLICITADA");
		assertThat(evento).containsEntry("entidad", "cuota").containsEntry("nombre_usuario", "administracion");
		assertThat(AccionAuditoria.CUOTA_ANULACION_SOLICITADA.requiereAtencion()).isTrue();
		assertThat(evento.get("detalle").toString()).contains("Pensión setiembre 2027 S/ 450.00").contains(MOTIVO);
		assertThatThrownBy(() -> anulaciones.solicitar(setiembre, MOTIVO)).hasMessageContaining("ya tiene una solicitud");
	}

	@Test
	void elGanchoAnulaConAprobacionDeDireccion() {
		anulaciones.solicitar(setiembre, MOTIVO);
		// Lo que hará Aprobaciones en el sprint 3: otra persona aprueba la solicitud.
		new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
			var cuota = cuotas.findById(setiembre).orElseThrow();
			cuota.anular(cuota.getAnulacionMotivo(), cuota.getAnulacionSolicitadaPor(), "director",
					LocalDateTime.of(2026, 10, 2, 10, 0));
		});

		assertThat(jdbc.queryForMap("SELECT estado, obligacion, anulacion_aprobada_por FROM cuota WHERE id = ?", setiembre))
				.containsEntry("estado", "ANULADA").containsEntry("anulacion_aprobada_por", "director")
				.containsEntry("obligacion", null);
		assertThat(cronogramas.deAlumno(mateo).saldo()).isEqualByComparingTo("4400.00");
	}

	@Test
	void quienSolicitaNoAprueba() {
		anulaciones.solicitar(setiembre, MOTIVO);
		var cuota = cuotas.findById(setiembre).orElseThrow();

		assertThatThrownBy(() -> cuota.anular(MOTIVO, "administracion", "administracion", LocalDateTime.now()))
				.isInstanceOf(AutoaprobacionException.class).hasMessage("Quien solicita la anulación no puede aprobarla.");
	}

	@Test
	void motivoObligatorioYCajaRecibe403() {
		assertThatThrownBy(() -> anulaciones.solicitar(setiembre, "corto")).isInstanceOf(ReglaNegocioException.class);
		como(CAJA);
		assertThatThrownBy(() -> anulaciones.solicitar(setiembre, MOTIVO)).isInstanceOf(AuthorizationDeniedException.class);
		como(DIRECCION);
		anulaciones.solicitar(setiembre, MOTIVO);
		assertThat(jdbc.queryForObject("SELECT anulacion_solicitada_por FROM cuota WHERE id = ?", String.class, setiembre))
				.isEqualTo("director");
	}
}
