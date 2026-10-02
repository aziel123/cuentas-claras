package pe.edu.virgenmaria.cuentasclaras.cobranza.inicial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.inicial.DatosDemoColegioDevDePrueba;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;

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
				+ "ON a.id = p.anio_escolar_id WHERE a.anio = 2026", String.class)).containsExactly("2026-12-01");
		// Las 10 matrículas 2026 tienen su pensión de diciembre (lo anterior es saldo inicial).
		assertThat(jdbc.queryForList("SELECT DISTINCT descripcion FROM cuota", String.class))
				.containsExactly("Pensión diciembre 2026");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cuota", Long.class)).isEqualTo(10);

		Map<String, Object> lote = jdbc.queryForMap("SELECT * FROM lote_saldo_inicial");
		assertThat(lote).containsEntry("estado", "ENVIADO").containsEntry("creado_por", "administracion")
				.containsEntry("enviado_por", "administracion");
		assertThat((BigDecimal) lote.get("total_declarado")).isEqualByComparingTo("5280.00");
		assertThat(jdbc.queryForObject("SELECT SUM(monto) FROM linea_saldo_inicial", BigDecimal.class))
				.isEqualByComparingTo("5280.00");
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT alumno_id) FROM linea_saldo_inicial WHERE mes = 9",
				Long.class)).isEqualTo(10);
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
		assertThat(new DatosDemoPensionesDev(planes, saldoInicial, reloj, "jdbc:mysql://localhost/cuentasclaras", true)
				.crearSiCorresponde()).isFalse();
		assertThat(demo(true).crearSiCorresponde()).isTrue();
		assertThat(demo(true).crearSiCorresponde()).isFalse();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_pension", Long.class)).isEqualTo(6);
	}

	private DatosDemoPensionesDev demo(boolean habilitado) {
		return new DatosDemoPensionesDev(planes, saldoInicial, reloj, "jdbc:h2:mem:demo", habilitado);
	}
}
