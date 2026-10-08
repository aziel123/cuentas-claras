package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.Extracto;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.CierreMensualVista;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoCierreMensual;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.proceso.CierresMensuales;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar;

/**
 * QA sprint 5 (tanda 3, G22): bordes del cierre mensual a ciegas. Dado el cierre de setiembre de 2026 (abonos
 * 1,800.00, cargos 300.35 y saldo 11,499.65), cuando Dirección escribe a ciegas lo que dice el estado de cuenta oficial,
 * entonces los montos se comparan como dinero (escala 2), un dato mal escrito no gasta un intento y nadie más que
 * Promotoría o Dirección escribe.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CierreMensualQaTest {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 2);

	@Autowired
	private ServicioExtractos extractos;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private ServicioCierreMensual cierres;

	@Autowired
	private CierresMensuales proceso;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Long cierre;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		Long cuentaId = EscenarioConciliacion.cuenta(cuentas);
		Extracto setiembre = extracto("10000.00").abono("2026-09-01", "YAPE RECIBIDO", "YP1001", "800.00")
				.cargo("2026-09-15", "COMISION MANTENIMIENTO", "", "300.00")
				.abono("2026-09-30", "TRANSFERENCIA DE TERCEROS", "TR2002", "1000.00")
				.cargo("2026-09-30", "ITF", "", "0.35");
		registrar(extractos, ADMINISTRACION, setiembre);
		confirmar(extractos, PROMOTORIA, cuentaId, setiembre.saldoFinal().toPlainString());
		SecurityContextHolder.clearContext();
		assertThat(proceso.enColegio(1L, HOY)).isEqualTo(1);
		cierre = jdbc.queryForObject("SELECT id FROM cierre_mensual_banco WHERE anio = 2026 AND mes = 9", Long.class);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private EstadoCierreMensual escribir(String abonos, String cargos, String saldo) {
		UsuariosDePrueba.iniciarSesion(DIRECCION);
		CierreMensualVista vista = cierres.vista(cierre);
		return cierres.registrar(cierre, vista.version(), abonos == null ? null : new BigDecimal(abonos),
				new BigDecimal(cargos), new BigDecimal(saldo));
	}

	private int intentos() {
		return jdbc.queryForObject("SELECT intentos FROM cierre_mensual_banco WHERE id = ?", Integer.class, cierre);
	}

	@Test
	void debeCuadrarAunqueElMontoSeEscribaConUnSoloDecimal() {
		assertThat(escribir("1800.0", "300.35", "11499.650")).isEqualTo(EstadoCierreMensual.CUADRADO);
	}

	@Test
	void debeRechazarTresDecimalesSinGastarUnIntento() {
		assertThatThrownBy(() -> escribir("1800.001", "300.35", "11499.65")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("2 decimales");

		assertThat(intentos()).isZero();
		assertThat(escribir("1800.00", "300.35", "11499.65")).isEqualTo(EstadoCierreMensual.CUADRADO);
	}

	@Test
	void debeRechazarUnTotalNegativoOFaltanteSinGastarUnIntento() {
		assertThatThrownBy(() -> escribir("-1800.00", "300.35", "11499.65")).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> escribir(null, "300.35", "11499.65")).isInstanceOf(ReglaNegocioException.class);

		assertThat(intentos()).isZero();
	}

	@Test
	void unaPantallaDesactualizadaNoGastaUnIntento() {
		UsuariosDePrueba.iniciarSesion(DIRECCION);
		Long versionVieja = cierres.vista(cierre).version();
		assertThatThrownBy(() -> escribir("1.00", "1.00", "1.00")).isInstanceOf(ReglaNegocioException.class);
		assertThat(intentos()).isEqualTo(1);

		UsuariosDePrueba.iniciarSesion(DIRECCION);
		assertThatThrownBy(() -> cierres.registrar(cierre, versionVieja, new BigDecimal("1800.00"),
				new BigDecimal("300.35"), new BigDecimal("11499.65"))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("cambió");
		assertThat(intentos()).isEqualTo(1);
	}

	@Test
	void laCajaNoEscribeNiVeElCierreMensual() {
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.CAJA));

		assertThatThrownBy(() -> cierres.vista(cierre)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> cierres.registrar(cierre, 0L, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(intentos()).isZero();
	}

	@Test
	void unaPersonaNoPuedeCrearCierresNiCalcularTotales() {
		UsuariosDePrueba.iniciarSesion(DIRECCION);

		assertThatThrownBy(() -> cierres.totales(1L, java.time.YearMonth.of(2026, 9)))
				.isInstanceOf(AccessDeniedException.class);
	}
}
