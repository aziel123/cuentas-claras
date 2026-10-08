package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * QA del sprint 4 (outbox del OSE, sección 5): un ENVIADO que el OSE no encuentra vuelve a PENDIENTE y se reenvía (no
 * se queda consultando para siempre); una consulta que falla no lo reenvía; un resultado definitivo no se toca.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class OutboxOseQaTest {

	@MockitoSpyBean
	private EmisorElectronico emisor;

	@Autowired
	private EnvioComprobantes envio;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos anulaciones;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones bandeja;

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

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(CAJA);
	}

	@AfterEach
	void limpiar() {
		reset(emisor);
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Long cobrarMarzo() {
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00", "450.00"));
		return jdbc.queryForObject("SELECT comprobante_id FROM pago WHERE id = ?", Long.class, pago);
	}

	private Map<String, Object> comprobante(Long id) {
		return jdbc.queryForMap("SELECT * FROM comprobante WHERE id = ?", id);
	}

	private static ResultadoEnvio aceptado() {
		return new ResultadoEnvio(EstadoEnvio.ACEPTADO, "La Boleta ha sido aceptada", "hash-ose", null, "0", null);
	}

	@Test
	void debeVolverAPendienteYReenviarUnEnviadoQueElOseNoEncuentra() {
		doReturn(new ResultadoEnvio(EstadoEnvio.ENVIADO, "Recibido", null, null)).when(emisor).enviar(any());
		Long id = cobrarMarzo();
		assertThat(comprobante(id)).containsEntry("estado_envio", "ENVIADO");
		doReturn(new ResultadoEnvio(EstadoEnvio.PENDIENTE, null, null, null)).when(emisor).consultar(any(), anyString(),
				anyInt());
		reloj.avanzar(Duration.ofMinutes(5));

		assertThat(envio.procesar(id)).isEqualTo(EstadoEnvio.PENDIENTE);
		assertThat((String) comprobante(id).get("ultimo_error")).contains("no lo encontró");

		doReturn(aceptado()).when(emisor).enviar(any());
		assertThat(envio.procesar(id)).isEqualTo(EstadoEnvio.ACEPTADO);
		verify(emisor, times(2)).enviar(any());
	}

	@Test
	void debeSeguirConsultandoSinReenviarCuandoFallaLaConsultaDeUnEnviado() {
		doReturn(new ResultadoEnvio(EstadoEnvio.ENVIADO, "Recibido", null, null)).when(emisor).enviar(any());
		Long id = cobrarMarzo();
		doThrow(new IllegalStateException("timeout")).when(emisor).consultar(any(), anyString(), anyInt());
		reloj.avanzar(Duration.ofMinutes(5));

		assertThat(envio.procesar(id)).isEqualTo(EstadoEnvio.ENVIADO);
		verify(emisor, times(1)).enviar(any());
		assertThat(comprobante(id)).containsEntry("intentos", 2);
	}

	@Test
	void debeAceptarAlConsultarUnEnviadoSinCambiarSuFechaDeEnvio() {
		doReturn(new ResultadoEnvio(EstadoEnvio.ENVIADO, "Recibido", null, null)).when(emisor).enviar(any());
		Long id = cobrarMarzo();
		Object enviadoEn = comprobante(id).get("enviado_en");
		doReturn(aceptado()).when(emisor).consultar(any(), anyString(), anyInt());
		reloj.avanzar(Duration.ofMinutes(5));

		assertThat(envio.procesar(id)).isEqualTo(EstadoEnvio.ACEPTADO);
		assertThat(comprobante(id).get("enviado_en")).isEqualTo(enviadoEn);
		assertThat(envio.procesar(id)).isEqualTo(EstadoEnvio.ACEPTADO);
		verify(emisor, times(1)).consultar(any(), anyString(), anyInt());
	}

	/**
	 * Correcciones del sprint 4 (mutación X3 viva): la nota de crédito ESPERA a que su comprobante sea válido. Con la
	 * boleta todavía ENVIADA (sin respuesta del OSE), la nota no se envía ni suma intentos; cuando el OSE acepta la
	 * boleta, la nota sale en el siguiente intento.
	 */
	@Test
	void debeEsperarQueSuComprobanteSeaValidoAntesDeEnviarLaNotaDeCredito() {
		doReturn(new ResultadoEnvio(EstadoEnvio.ENVIADO, "Recibido", null, null)).when(emisor).enviar(any());
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00", "450.00"));
		Long boleta = jdbc.queryForObject("SELECT comprobante_id FROM pago WHERE id = ?", Long.class, pago);
		anulaciones.solicitarDevolucion(pago, "La familia pagó dos veces la misma cuota por error");
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(
				pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION, bandeja, jdbc, "pago", pago);
		Long nota = jdbc.queryForObject("SELECT id FROM comprobante WHERE modifica_id = ?", Long.class, boleta);

		reloj.avanzar(Duration.ofMinutes(5));
		assertThat(envio.procesar(nota)).isEqualTo(EstadoEnvio.PENDIENTE);
		assertThat(comprobante(nota)).containsEntry("estado_envio", "PENDIENTE").containsEntry("intentos", 0);
		assertThat((String) comprobante(nota).get("ultimo_error")).contains("Espera que el OSE acepte");
		verify(emisor, times(1)).enviar(any());

		doReturn(aceptado()).when(emisor).consultar(any(), anyString(), anyInt());
		assertThat(envio.procesar(boleta)).isEqualTo(EstadoEnvio.ACEPTADO);
		doReturn(aceptado()).when(emisor).enviar(any());
		reloj.avanzar(Duration.ofMinutes(5));
		assertThat(envio.procesar(nota)).isEqualTo(EstadoEnvio.ACEPTADO);
		verify(emisor, times(2)).enviar(any());
	}
}
