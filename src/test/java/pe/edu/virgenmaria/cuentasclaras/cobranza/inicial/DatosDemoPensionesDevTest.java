package pe.edu.virgenmaria.cuentasclaras.cobranza.inicial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.inicial.DatosDemoColegioDevDePrueba;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PersonasDemoDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pensiones de demostración (perfil dev): planes 2026 y 2027 aprobados por otra persona y un lote enviado. */
@PruebaIntegracion
@Import({ ConfiguracionRelojAjustable.class, DatosDemoColegioDevDePrueba.class })
class DatosDemoPensionesDevTest {

	@Autowired
	private DatosDemoColegioDevDePrueba colegioDemo;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioSaldoInicial saldoInicial;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private Clock reloj;

	@Autowired
	private PersonaDemo personaDemo;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	private long colegioB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		colegioB = colegios.save(new Colegio(DatosDemoDev.NOMBRE_COLEGIO_B)).getId();
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void creaPlanesAprobadosCronogramasYUnLoteEnviado() {
		assertThat(colegioDemo.crear()).isTrue();
		assertThat(demo(true).crearSiCorresponde()).isTrue();

		assertThat(jdbc.queryForList("SELECT CONCAT(a.anio, ' ', p.nivel, ' ', p.monto_pension, ' ', p.estado, ' ', "
				+ "p.editado_por, '→', p.aprobado_por) FROM plan_pension p JOIN anio_escolar a ON a.id = p.anio_escolar_id "
				+ "ORDER BY a.anio, p.nivel", String.class)).containsExactly(
				"2026 INICIAL 380.00 APROBADO administracion→director",
				"2026 PRIMARIA 450.00 APROBADO administracion→director",
				"2026 SECUNDARIA 480.00 APROBADO administracion→director",
				"2027 INICIAL 380.00 APROBADO administracion→director",
				"2027 PRIMARIA 450.00 APROBADO administracion→director",
				"2027 SECUNDARIA 480.00 APROBADO administracion→director");
		assertThat(jdbc.queryForList("SELECT DISTINCT cobro_desde FROM plan_pension p JOIN anio_escolar a "
				+ "ON a.id = p.anio_escolar_id WHERE a.anio = 2026", String.class)).containsExactly("2026-03-01");
		// Las 10 matrículas 2026 tienen sus pensiones de marzo a diciembre; la matrícula (vence el 28/02, antes de que el
		// sistema cobre) no está en el cronograma: la que se debe entra como saldo inicial.
		assertThat(jdbc.queryForList("SELECT DISTINCT descripcion FROM cuota", String.class)).containsExactlyInAnyOrder(
				"Pensión marzo 2026", "Pensión abril 2026", "Pensión mayo 2026", "Pensión junio 2026", "Pensión julio 2026",
				"Pensión agosto 2026", "Pensión setiembre 2026", "Pensión octubre 2026", "Pensión noviembre 2026",
				"Pensión diciembre 2026");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cuota", Long.class)).isEqualTo(100);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cuota WHERE tipo = 'MATRICULA'", Long.class)).isZero();

		Map<String, Object> lote = jdbc.queryForMap("SELECT * FROM lote_saldo_inicial");
		assertThat(lote).containsEntry("estado", "ENVIADO").containsEntry("creado_por", "administracion")
				.containsEntry("enviado_por", "administracion");
		assertThat((BigDecimal) lote.get("total_declarado")).isEqualByComparingTo("2100.00");
		assertThat(lote.get("fecha_corte").toString()).isEqualTo("2026-02-28");
		assertThat(jdbc.queryForObject("SELECT SUM(monto) FROM linea_saldo_inicial", BigDecimal.class))
				.isEqualByComparingTo("2100.00");
		// Seis alumnos deben su matrícula 2026 (S/ 350 cada una); ninguna línea es de pensión (la cobra el plan).
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT alumno_id) FROM linea_saldo_inicial "
				+ "WHERE concepto = 'MATRICULA' AND monto = 350.00", Long.class)).isEqualTo(6);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM linea_saldo_inicial WHERE concepto <> 'MATRICULA'",
				Long.class)).isZero();
		// El colegio B no recibe planes ni lotes.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_pension WHERE colegio_id = ?", Long.class, colegioB))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'PLAN_PENSION_APROBADO' "
				+ "AND nombre_usuario = 'director'", Long.class)).isEqualTo(6);
	}

	@Test
	void noRepiteNiCreaFueraDeH2EnMemoria() {
		colegioDemo.crear();
		assertThat(demo(false).crearSiCorresponde()).isFalse();
		assertThat(new DatosDemoPensionesDev(planes, saldoInicial, reloj, personaDemo,
				"jdbc:mysql://localhost/cuentasclaras", true)
				.crearSiCorresponde()).isFalse();
		assertThat(demo(true).crearSiCorresponde()).isTrue();
		assertThat(demo(true).crearSiCorresponde()).isFalse();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_pension", Long.class)).isEqualTo(6);
	}

	/** Sprint 7, tanda 2: con las personas de la demo que crean y aprueban (actúan con su sesión de la base). */
	private DatosDemoPensionesDev demo(boolean habilitado) {
		PersonasDemoDePrueba.asegurar(usuarios, codificador, jdbc, "administracion", "director");
		return new DatosDemoPensionesDev(planes, saldoInicial, reloj, personaDemo, "jdbc:h2:mem:demo", habilitado);
	}
}
