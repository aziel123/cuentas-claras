package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteDetalle;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION_2;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION_Y_ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA_Y_ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.DNI_MATEO;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.DNI_VALERIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.MOTIVO;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/** Saldo inicial: total de control, doble control (también en la base) y todo o nada al confirmar. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioSaldoInicialTest {

	private static final LocalDate CORTE = LocalDate.of(2026, 9, 30);

	@Autowired
	private ServicioSaldoInicial saldo;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private JdbcTemplate jdbc;

	private Estructura escuela;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		como(ADMINISTRACION);
		escuela = EscenarioEscolar.crearEstructura(estructura);
		alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		alumnos.registrar(EscenarioEscolar.valeriaConRosaRegistrada(escuela.primaria2B2026()));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void administracionCreaLoteYAgregaLineas() {
		Long lote = lote("900.00");
		saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00"));
		saldo.agregarLinea(lote, new LineaSaldoRequest(" 80127745 ", ConceptoSaldo.OTRO, null, "Taller de verano 2026",
				new BigDecimal("450"), LocalDate.of(2026, 2, 15)));

		LoteDetalle detalle = saldo.obtener(lote);
		assertThat(detalle.estado()).isEqualTo("BORRADOR");
		assertThat(detalle.cuadra()).isTrue();
		assertThat(detalle.puedeEnviar()).isTrue();
		assertThat(detalle.lineas()).extracting(l -> l.descripcion())
				.containsExactly("Pensión setiembre 2026", "Taller de verano 2026");
		assertThat(detalle.lineas().get(0).vencimiento()).isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(jdbc.queryForObject("SELECT creado_por FROM lote_saldo_inicial WHERE id = ?", String.class, lote))
				.isEqualTo("administracion");
		// La misma deuda dos veces en el lote, no.
		assertThatThrownBy(() -> saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00")))
				.hasMessageContaining("ya tiene en el lote la deuda «Pensión setiembre 2026»");
		assertThatThrownBy(() -> saldo.agregarLinea(lote, pension("99999999", 9, "450.00")))
				.hasMessageContaining("No hay un alumno con el documento «99999999»");
		assertThatThrownBy(() -> saldo.agregarLinea(lote, new LineaSaldoRequest(DNI_MATEO, ConceptoSaldo.PENSION, 9, null,
				new BigDecimal("450.00"), LocalDate.of(2024, 9, 30)))).hasMessage("El vencimiento debe estar en 2025 o 2026.");
	}

	@Test
	void fechaDeCorteFuturaEsRechazada() {
		assertThatThrownBy(() -> saldo.crearLote(new LoteRequest(escuela.anio2026(), LocalDate.of(2026, 12, 31),
				"Informe del contador 001", new BigDecimal("100.00")))).hasMessage("La fecha de corte no puede ser futura.");
		assertThat(contar(jdbc, "lote_saldo_inicial")).isZero();
	}

	@Test
	void noSeEnviaSiElTotalNoCuadra() {
		Long lote = lote("500.00");
		saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00"));

		assertThat(saldo.obtener(lote).puedeEnviar()).isFalse();
		assertThatThrownBy(() -> saldo.enviar(lote)).isInstanceOf(ReglaNegocioException.class)
				.hasMessage("No cuadra: la suma de las líneas es S/ 450.00 y el total declarado es S/ 500.00. Revisa las "
						+ "líneas contra el informe del contador.");
		assertThat(estado(lote)).isEqualTo("BORRADOR");
	}

	@Test
	void noSeEnviaUnLoteVacio() {
		Long lote = lote("450.00");
		assertThatThrownBy(() -> saldo.enviar(lote)).hasMessageContaining("no tiene líneas");
		// Ni con todas sus líneas quitadas.
		Long linea = saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00"));
		saldo.quitarLinea(lote, linea, MOTIVO);
		assertThatThrownBy(() -> saldo.enviar(lote)).hasMessageContaining("no tiene líneas");
	}

	@Test
	void loteEnviadoNoAdmiteCambios() {
		Long lote = enviado();

		assertThatThrownBy(() -> saldo.agregarLinea(lote, pension(DNI_VALERIA, 9, "450.00")))
				.hasMessageContaining("no admite cambios");
		Long linea = jdbc.queryForObject("SELECT MIN(id) FROM linea_saldo_inicial", Long.class);
		assertThatThrownBy(() -> saldo.quitarLinea(lote, linea, MOTIVO)).hasMessageContaining("no admite cambios");
		assertThatThrownBy(() -> saldo.descartar(lote, MOTIVO)).hasMessageContaining("no admite cambios");
		assertThatThrownBy(() -> saldo.enviar(lote)).hasMessageContaining("no admite cambios");
	}

	@Test
	void quienCreaEnviaOAgregoLineasNoPuedeConfirmar() {
		// Lo crea y lo envía un usuario con Dirección y Administración: no puede confirmarlo.
		como(DIRECCION_Y_ADMINISTRACION);
		Long propio = lote("450.00");
		saldo.agregarLinea(propio, pension(DNI_MATEO, 9, "450.00"));
		saldo.enviar(propio);
		assertThat(saldo.obtener(propio).puedeConfirmar()).isFalse();
		assertThat(saldo.obtener(propio).avisoConfirmacion()).contains("Tú participaste");
		assertThatThrownBy(() -> saldo.confirmar(propio)).isInstanceOf(AutoaprobacionException.class);
		assertThat(ultimoEvento(jdbc, "AUTOAPROBACION_RECHAZADA")).containsEntry("nombre_usuario", "subdirector")
				.containsEntry("entidad", "lote_saldo_inicial");
		assertThat(estado(propio)).isEqualTo("ENVIADO");

		// Solo agregó una línea (Promotoría que también es Administración): tampoco.
		como(ADMINISTRACION);
		Long ajeno = lote("900.00");
		saldo.agregarLinea(ajeno, pension(DNI_MATEO, 8, "450.00"));
		como(PROMOTORIA_Y_ADMINISTRACION);
		saldo.agregarLinea(ajeno, pension(DNI_VALERIA, 8, "450.00"));
		como(ADMINISTRACION);
		saldo.enviar(ajeno);
		como(PROMOTORIA_Y_ADMINISTRACION);
		assertThatThrownBy(() -> saldo.confirmar(ajeno)).isInstanceOf(AutoaprobacionException.class);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'AUTOAPROBACION_RECHAZADA'")).isEqualTo(2);
		assertThat(contar(jdbc, "cuota")).isZero();
	}

	@Test
	void laBaseRechazaConfirmacionDelCreador() {
		Long lote = enviado();
		assertThatThrownBy(() -> jdbc.update("UPDATE lote_saldo_inicial SET estado = 'CONFIRMADO', "
				+ "confirmado_por = 'administracion', confirmado_en = CURRENT_TIMESTAMP WHERE id = ?", lote))
				.isInstanceOf(DataIntegrityViolationException.class);
		// Ni sin quién confirmó.
		assertThatThrownBy(() -> jdbc.update("UPDATE lote_saldo_inicial SET estado = 'CONFIRMADO' WHERE id = ?", lote))
				.isInstanceOf(DataIntegrityViolationException.class);
		// Ni enviado sin quién lo envió.
		assertThatThrownBy(() -> jdbc.update("UPDATE lote_saldo_inicial SET enviado_por = NULL WHERE id = ?", lote))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void promotoriaConfirmaYSeCreanCuotasDeSaldoInicial() {
		Long lote = enviado();
		como(PROMOTORIA);

		assertThat(saldo.obtener(lote).puedeConfirmar()).isTrue();
		assertThat(saldo.confirmar(lote)).isEqualTo(1);

		Map<String, Object> cuota = jdbc.queryForMap("SELECT * FROM cuota");
		assertThat(cuota).containsEntry("tipo", "SALDO_INICIAL").containsEntry("obligacion", "PEN-2026-09")
				.containsEntry("descripcion", "Pensión setiembre 2026").containsEntry("estado", "PENDIENTE");
		assertThat(cuota.get("clave")).asString().startsWith("SI:");
		assertThat(cuota.get("matricula_id")).isNotNull();
		assertThat(cuota.get("plan_pension_id")).isNull();
		assertThat(jdbc.queryForMap("SELECT * FROM lote_saldo_inicial WHERE id = ?", lote))
				.containsEntry("estado", "CONFIRMADO").containsEntry("confirmado_por", "promotor");
	}

	@Test
	void confirmarDosVecesNoDuplicaCuotas() {
		Long lote = enviado();
		como(DIRECCION);
		saldo.confirmar(lote);

		assertThatThrownBy(() -> saldo.confirmar(lote)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Solo se confirma un lote enviado");
		assertThat(contar(jdbc, "cuota")).isEqualTo(1);
	}

	@Test
	void lineaQueDuplicaUnaPensionGeneradaBloqueaTodaLaConfirmacion() {
		Long lote = lote("900.00");
		saldo.agregarLinea(lote, pension(DNI_MATEO, 11, "450.00"));
		saldo.agregarLinea(lote, pension(DNI_MATEO, 12, "450.00"));
		saldo.enviar(lote);
		// Mientras tanto se aprueba el plan 2026 (cobra desde diciembre): genera la pensión de diciembre de Mateo.
		EscenarioCobranza.planAprobado(planes, escuela.anio2026(), 2026, Nivel.PRIMARIA, "450", "350",
				LocalDate.of(2026, 12, 1));
		long antes = contar(jdbc, "cuota");

		como(PROMOTORIA);
		assertThatThrownBy(() -> saldo.confirmar(lote)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("No se confirmó nada")
				.hasMessageContaining("Mateo Quispe Huamán: Pensión diciembre 2026");

		assertThat(contar(jdbc, "cuota")).isEqualTo(antes);
		assertThat(estado(lote)).isEqualTo("ENVIADO");
		// Agregar esa deuda a un lote nuevo ya avisa al momento.
		como(ADMINISTRACION);
		Long otro = lote("450.00");
		assertThatThrownBy(() -> saldo.agregarLinea(otro, pension(DNI_MATEO, 12, "450.00")))
				.hasMessageContaining("ya tiene la cuota «Pensión diciembre 2026» en su cronograma");
	}

	@Test
	void devolverExigeMotivoYVuelveABorrador() {
		Long lote = enviado();
		como(DIRECCION);
		assertThatThrownBy(() -> saldo.devolver(lote, "no")).hasMessageContaining("entre 10 y 500");

		saldo.devolver(lote, "El monto de Mateo no coincide con el informe");

		assertThat(jdbc.queryForMap("SELECT * FROM lote_saldo_inicial WHERE id = ?", lote))
				.containsEntry("estado", "BORRADOR").containsEntry("devuelto_por", "director")
				.containsEntry("motivo_devolucion", "El monto de Mateo no coincide con el informe");
		como(ADMINISTRACION);
		assertThat(saldo.obtener(lote).puedeEditar()).isTrue();
	}

	@Test
	void lineasQuitadasNoGeneranCuotas() {
		Long lote = lote("450.00");
		Long quitada = saldo.agregarLinea(lote, pension(DNI_VALERIA, 9, "300.00"));
		saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00"));
		assertThat(saldo.obtener(lote).cuadra()).isFalse();
		saldo.quitarLinea(lote, quitada, "Valeria pagó en efectivo antes del corte");
		saldo.enviar(lote);
		como(PROMOTORIA);
		saldo.confirmar(lote);

		assertThat(jdbc.queryForList("SELECT descripcion FROM cuota", String.class)).containsExactly("Pensión setiembre 2026");
		// La línea quitada sigue en la base (no se borra), marcada.
		assertThat(jdbc.queryForObject("SELECT quitada FROM linea_saldo_inicial WHERE id = ?", Boolean.class, quitada)).isTrue();
		assertThat(ultimoEvento(jdbc, "SALDO_INICIAL_LINEA_QUITADA").get("detalle").toString())
				.contains("Valeria pagó en efectivo");
	}

	@Test
	void sumaExactaConBigDecimal() {
		Long lote = lote("0.30");
		saldo.agregarLinea(lote, new LineaSaldoRequest(DNI_MATEO, ConceptoSaldo.OTRO, null, "Copias", new BigDecimal("0.1"),
				LocalDate.of(2026, 9, 1)));
		saldo.agregarLinea(lote, new LineaSaldoRequest(DNI_VALERIA, ConceptoSaldo.OTRO, null, "Copias",
				new BigDecimal("0.20"), LocalDate.of(2026, 9, 1)));

		assertThat(saldo.obtener(lote).cuadra()).isTrue();
		saldo.enviar(lote);
		assertThat(estado(lote)).isEqualTo("ENVIADO");
		assertThatThrownBy(() -> saldo.crearLote(new LoteRequest(escuela.anio2026(), CORTE, "Informe", new BigDecimal("1.005"))))
				.hasMessage("El monto debe tener como máximo 2 decimales.");
	}

	@Test
	void cadaPasoQuedaAuditado() {
		Long lote = lote("450.00");
		Long linea = saldo.agregarLinea(lote, pension(DNI_VALERIA, 9, "450.00"));
		saldo.quitarLinea(lote, linea, MOTIVO);
		saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00"));
		saldo.enviar(lote);
		como(DIRECCION);
		saldo.devolver(lote, MOTIVO);
		como(ADMINISTRACION_2);
		saldo.enviar(lote);
		como(PROMOTORIA);
		saldo.confirmar(lote);
		como(ADMINISTRACION);
		Long descartado = lote("100.00");
		saldo.descartar(descartado, MOTIVO);

		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE entidad = 'lote_saldo_inicial' "
				+ "ORDER BY secuencia", String.class)).containsExactly("SALDO_INICIAL_LOTE_CREADO",
				"SALDO_INICIAL_LINEA_AGREGADA", "SALDO_INICIAL_LINEA_QUITADA", "SALDO_INICIAL_LINEA_AGREGADA",
				"SALDO_INICIAL_ENVIADO", "SALDO_INICIAL_DEVUELTO", "SALDO_INICIAL_ENVIADO", "SALDO_INICIAL_CONFIRMADO",
				"SALDO_INICIAL_LOTE_CREADO", "SALDO_INICIAL_DESCARTADO");
		assertThat(ultimoEvento(jdbc, "SALDO_INICIAL_CONFIRMADO").get("valor_nuevo").toString())
				.contains("Total declarado S/ 450.00").contains("1 cuotas creadas por S/ 450.00");
		assertThat(ultimoEvento(jdbc, "SALDO_INICIAL_LINEA_AGREGADA").get("valor_nuevo").toString())
				.contains("DNI ****1236").doesNotContain(DNI_MATEO);
		assertThat(jdbc.queryForObject("SELECT enviado_por FROM lote_saldo_inicial WHERE id = ?", String.class, lote))
				.isEqualTo("administracion2");
	}

	@Test
	void cajaRecibe403() {
		Long lote = lote("450.00");
		como(CAJA);
		assertThatThrownBy(() -> saldo.listar()).isInstanceOf(AuthorizationDeniedException.class);
		assertThatThrownBy(() -> saldo.obtener(lote)).isInstanceOf(AuthorizationDeniedException.class);
		assertThatThrownBy(() -> saldo.crearLote(new LoteRequest(escuela.anio2026(), CORTE, "Informe", BigDecimal.TEN)))
				.isInstanceOf(AuthorizationDeniedException.class);
		assertThatThrownBy(() -> saldo.confirmar(lote)).isInstanceOf(AuthorizationDeniedException.class);
		// Y Administración no confirma (aunque no haya participado).
		como(ADMINISTRACION_2);
		assertThatThrownBy(() -> saldo.confirmar(lote)).isInstanceOf(AuthorizationDeniedException.class);
		como(PROMOTORIA);
		assertThatThrownBy(() -> saldo.crearLote(new LoteRequest(escuela.anio2026(), CORTE, "Informe", BigDecimal.TEN)))
				.isInstanceOf(AuthorizationDeniedException.class);
	}

	private Long lote(String total) {
		return saldo.crearLote(new LoteRequest(escuela.anio2026(), CORTE, "Informe CPC Ramos N.° 014-2026",
				new BigDecimal(total)));
	}

	private Long enviado() {
		como(ADMINISTRACION);
		Long lote = lote("450.00");
		saldo.agregarLinea(lote, pension(DNI_MATEO, 9, "450.00"));
		saldo.enviar(lote);
		return lote;
	}

	private static LineaSaldoRequest pension(String dni, int mes, String monto) {
		return new LineaSaldoRequest(dni, ConceptoSaldo.PENSION, mes, null, new BigDecimal(monto), null);
	}

	private String estado(Long lote) {
		return jdbc.queryForObject("SELECT estado FROM lote_saldo_inicial WHERE id = ?", String.class, lote);
	}
}
