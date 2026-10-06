package pe.edu.virgenmaria.cuentasclaras.conciliacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.Extracto;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.Archivo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioPartidas;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION_2;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.partidas;

/**
 * QA del sprint 4 (segregación, F16): con {@code abono-recaudacion: POR_PAGO} cada pago por banco es un objeto PAGO
 * cuyo cajero y autor son {@code sistema.recaudacion}. Quien subió el archivo de recaudación es la persona responsable
 * de ese pago y no debería confirmar su pareja SUGERIDA con un abono del banco (como pasa con el lote en POR_LOTE).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
@TestPropertySource(properties = "cuentasclaras.conciliacion.abono-recaudacion=POR_PAGO")
class SegregacionRecaudacionPorPagoQaTest {

	static final Instant LUNES = Instant.parse("2026-10-05T14:00:00Z");

	@Autowired
	private ServicioRecaudacion recaudacion;

	@Autowired
	private ServicioExtractos extractos;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private ServicioPartidas servicioPartidas;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	private Long cuentaId;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		cuentaId = EscenarioConciliacion.cuenta(cuentas);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	@Disabled("QA-S4-6: ServicioPartidas.exigirOtraPersona (líneas 274-277) toma como responsables de un PAGO de "
			+ "recaudación solo a sistema.recaudacion: quien subió el lote confirma la SUGERIDA de su propio pago")
	void debeImpedirQueQuienSubioLaRecaudacionConfirmeLaParejaDeSuPago() {
		Archivo banco = EscenarioRecaudacion.archivo().pago(f.mateo(), null, "350.00", "BCP60001");
		Long lote = EscenarioRecaudacion.registrar(recaudacion, ADMINISTRACION, banco);
		EscenarioRecaudacion.confirmar(recaudacion, jdbc, PROMOTORIA, lote, "350.00");
		assertThat(EscenarioRecaudacion.estadoLote(jdbc, lote)).isEqualTo("APLICADO");
		reloj.fijar(LUNES);
		// El banco abona otro pago del mismo monto, con otra operación: sale como SUGERIDA.
		Extracto delBanco = extracto("10000.00").abono("2026-10-02", "ABONO RECAUDACION", "BCP777001", "350.00");
		EscenarioConciliacion.registrar(extractos, ADMINISTRACION_2, delBanco);
		EscenarioConciliacion.confirmar(extractos, PROMOTORIA, cuentaId, delBanco.saldoFinal().toPlainString());
		assertThat(partidas(jdbc)).containsExactly("SUGERIDA PROPUESTA PAGO");
		Long sugerida = jdbc.queryForObject("SELECT id FROM partida_conciliacion WHERE estado = 'PROPUESTA'", Long.class);

		como(ADMINISTRACION);
		assertThatThrownBy(() -> servicioPartidas.confirmarSugerida(sugerida))
				.isInstanceOf(AutoaprobacionException.class);
	}
}
