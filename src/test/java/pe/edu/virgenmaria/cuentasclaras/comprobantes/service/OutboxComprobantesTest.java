package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioReemision;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 4, tanda 1: el outbox del OSE. Cada comprobante se envía solo; si el OSE no responde se reintenta con espera
 * creciente; un rechazo se reemite con número NUEVO (el rechazado no cambia); y la reconsulta nocturna avisa si el OSE
 * no reconoce lo que dimos por aceptado.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class OutboxComprobantesTest {

	@MockitoSpyBean
	private EmisorElectronico emisor;

	@Autowired
	private EnvioComprobantes envio;

	@Autowired
	private ServicioReemision reemision;

	@Autowired
	private ConsultaComprobantes consulta;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private Clock reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(CAJA);
	}

	@AfterEach
	void limpiar() {
		reset(emisor);
		SecurityContextHolder.clearContext();
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
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
		return new ResultadoEnvio(EstadoEnvio.ACEPTADO, "La Boleta ha sido aceptada", "hash-ose", "https://ose/pdf", "0",
				null);
	}

	@Test
	void sinRespuestaDelOseSeReintentaConEsperaCrecienteYLuegoSeAcepta() {
		doThrow(new IllegalStateException("OSE caído")).when(emisor).enviar(any());
		Long id = cobrarMarzo();
		LocalDateTime inicio = LocalDateTime.now(reloj);

		assertThat(comprobante(id)).containsEntry("estado_envio", "PENDIENTE").containsEntry("intentos", 1);
		assertThat(((java.sql.Timestamp) comprobante(id).get("proximo_intento_en")).toLocalDateTime())
				.isEqualTo(inicio.plusMinutes(1));
		// Antes de su hora no se reintenta; después sí, y la espera se duplica.
		assertThat(envio.porEnviar()).doesNotContain(id);
		((RelojAjustable) reloj).avanzar(Duration.ofMinutes(1));
		assertThat(envio.porEnviar()).contains(id);
		assertThat(envio.procesar(id)).isEqualTo(EstadoEnvio.PENDIENTE);
		assertThat(comprobante(id)).containsEntry("intentos", 2);
		assertThat(((java.sql.Timestamp) comprobante(id).get("proximo_intento_en")).toLocalDateTime())
				.isEqualTo(inicio.plusMinutes(1 + 2));

		doReturn(aceptado()).when(emisor).enviar(any());
		assertThat(envio.procesar(id)).isEqualTo(EstadoEnvio.ACEPTADO);
		assertThat(comprobante(id)).containsEntry("codigo_hash", "hash-ose").containsEntry("codigo_respuesta", "0");
		assertThat(comprobante(id).get("aceptado_en")).isNotNull();
		// Uno aceptado ya no se envía de nuevo.
		assertThat(envio.procesar(id)).isEqualTo(EstadoEnvio.ACEPTADO);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'COMPROBANTE_ACEPTADO'")).isEqualTo(1);
	}

	@Test
	void unRechazoSeReemiteConNumeroNuevoSoloPorAdministracion() {
		doReturn(new ResultadoEnvio(EstadoEnvio.RECHAZADO, "El número de documento del receptor no es válido", null, null,
				"2017", null)).when(emisor).enviar(any());
		Long rechazado = cobrarMarzo();
		assertThat(comprobante(rechazado)).containsEntry("estado_envio", "RECHAZADO");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'COMPROBANTE_RECHAZADO'")).isEqualTo(1);

		// La cajera no reemite (quien cobra no corrige sus comprobantes).
		assertThatThrownBy(() -> reemision.reemitir(rechazado, "El DNI estaba mal escrito en la ficha"))
				.isInstanceOfAny(AccessDeniedException.class, AuthorizationDeniedException.class);

		como(EscenarioCobranza.ADMINISTRACION);
		assertThat(consulta.bandeja().rechazados()).extracting(fila -> fila.id()).containsExactly(rechazado);
		assertThatThrownBy(() -> reemision.reemitir(rechazado, "corto")).isInstanceOf(ReglaNegocioException.class);
		doReturn(aceptado()).when(emisor).enviar(any());
		Long nuevo = reemision.reemitir(rechazado, "El DNI estaba mal escrito en la ficha");

		assertThat(nuevo).isNotEqualTo(rechazado);
		assertThat(comprobante(nuevo)).containsEntry("reemplaza_id", rechazado).containsEntry("numero", 2)
				.containsEntry("estado_envio", "ACEPTADO");
		assertThat(comprobante(rechazado)).containsEntry("estado_envio", "RECHAZADO").containsEntry("numero", 1);
		assertThat(contar(jdbc, "pago")).isEqualTo(1);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'COMPROBANTE_REEMITIDO'")).isEqualTo(1);
		assertThatThrownBy(() -> reemision.reemitir(rechazado, "Otra vez, por si acaso"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya se reemitió");
		assertThatThrownBy(() -> reemision.reemitir(nuevo, "No está rechazado todavía"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("rechazó");
	}

	@Test
	void laReconsultaNocturnaAvisaSiElOseNoReconoceUnAceptado() {
		Long id = cobrarMarzo();
		assertThat(comprobante(id)).containsEntry("estado_envio", "ACEPTADO");
		doReturn(new ResultadoEnvio(EstadoEnvio.PENDIENTE, null, null, null)).when(emisor).consultar(any(), anyString(),
				anyInt());

		assertThat(envio.reconsultar(LocalDate.now(reloj))).isEqualTo(1);

		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'COMPROBANTE_NO_COINCIDE_OSE'")).isEqualTo(1);
		assertThat(comprobante(id)).containsEntry("estado_envio", "ACEPTADO");
	}

}
