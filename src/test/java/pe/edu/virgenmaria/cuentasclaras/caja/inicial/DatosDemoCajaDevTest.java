package pe.edu.virgenmaria.cuentasclaras.caja.inicial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.inicial.DatosDemoColegioDevDePrueba;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.inicial.DatosDemoPensionesDevDePrueba;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PersonasDemoDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caja de demostración (perfil dev): ayer, una caja cerrada, aprobada y depositada y otra con un faltante de S/ 50 por
 * aprobar; hoy, un descuento por hermanos aprobado y una anulación pendiente en la bandeja.
 */
@PruebaIntegracion
@Import({ ConfiguracionRelojAjustable.class, DatosDemoColegioDevDePrueba.class, DatosDemoPensionesDevDePrueba.class })
class DatosDemoCajaDevTest {

	@Autowired
	private DatosDemoColegioDevDePrueba colegioDemo;

	@Autowired
	private DatosDemoPensionesDevDePrueba pensionesDemo;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioCierreCaja cierres;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private PersonaDemo personaDemo;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		colegioDemo.crear();
		pensionesDemo.crear();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void creaDescuentoPorHermanosAprobadoYAnulacionPendiente() {
		assertThat(demo(true).crearSiCorresponde()).isTrue();

		Map<String, Object> descuento = jdbc.queryForMap("SELECT d.*, a.numero_documento FROM descuento d "
				+ "JOIN alumno a ON a.id = d.alumno_id");
		assertThat(descuento).containsEntry("tipo", "HERMANOS").containsEntry("estado", "APROBADO")
				.containsEntry("creado_por", "administracion").containsEntry("resuelto_por", "promotor")
				.containsEntry("numero_documento", DatosDemoCajaDev.DNI_VALERIA);
		assertThat((BigDecimal) descuento.get("total_estimado")).isEqualByComparingTo("45.00");
		assertThat(jdbc.queryForObject("SELECT SUM(monto) FROM ajuste_cuota", BigDecimal.class))
				.isEqualByComparingTo("45.00");
		assertThat(jdbc.queryForObject("SELECT c.monto - c.monto_descuento FROM cuota c JOIN alumno a ON a.id = c.alumno_id "
				+ "WHERE a.numero_documento = ?", BigDecimal.class, DatosDemoCajaDev.DNI_VALERIA))
				.isEqualByComparingTo("405.00");

		Map<String, Object> solicitud = jdbc.queryForMap("SELECT * FROM solicitud_cambio WHERE estado = 'PENDIENTE' "
				+ "AND tipo = 'ANULACION_PAGO'");
		assertThat(solicitud).containsEntry("solicitado_por", "caja");
		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, solicitud.get("entidad_id")))
				.isEqualTo("VIGENTE");

		// Ayer: la caja de Lucía cuadró, se aprobó y se depositó; la de Pedro tiene un faltante de S/ 50 por aprobar.
		LocalDate ayer = LocalDate.now(reloj).minusDays(1);
		assertThat(jdbc.queryForList("SELECT CONCAT(d.cajero, ' ', c.estado, ' ', c.diferencia) FROM cierre_caja c "
				+ "JOIN caja_diaria d ON d.id = c.caja_diaria_id WHERE d.fecha = ? ORDER BY d.cajero", String.class, ayer))
				.containsExactly("caja APROBADO 0.00", "caja2 POR_REVISAR -50.00");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_cambio WHERE estado = 'PENDIENTE' "
				+ "AND tipo = 'CIERRE_CAJA'", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deposito_caja", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT medio FROM pago WHERE fecha = ? ORDER BY id", String.class, ayer))
				.containsExactly("EFECTIVO", "YAPE", "EFECTIVO");
		// El reloj volvió a hoy: las cajas de hoy siguen abiertas.
		assertThat(jdbc.queryForList("SELECT estado FROM caja_diaria WHERE fecha = ?", String.class,
				LocalDate.now(reloj))).containsOnly("ABIERTA");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Long.class)).isEqualTo(4);
	}

	@Test
	void noRepiteNiCreaFueraDeH2EnMemoria() {
		assertThat(demo(false).crearSiCorresponde()).isFalse();
		assertThat(new DatosDemoCajaDev(descuentos, bandeja, cobro, anulaciones, cierres, reloj, personaDemo,
				"jdbc:mysql://localhost/cuentasclaras", true).crearSiCorresponde()).isFalse();
		assertThat(demo(true).crearSiCorresponde()).isTrue();
		assertThat(demo(true).crearSiCorresponde()).isFalse();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM descuento", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Long.class)).isEqualTo(4);
	}

	/** Sprint 7, tanda 2: con las personas de la demo (actúan con su sesión de la base y firman sus aprobaciones). */
	private DatosDemoCajaDev demo(boolean habilitado) {
		PersonasDemoDePrueba.asegurar(usuarios, codificador, jdbc, "administracion", "promotor", "director", "caja",
				"caja2");
		return new DatosDemoCajaDev(descuentos, bandeja, cobro, anulaciones, cierres, reloj, personaDemo,
				"jdbc:h2:mem:demo", habilitado);
	}
}
