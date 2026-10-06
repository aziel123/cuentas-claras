package pe.edu.virgenmaria.cuentasclaras.recaudacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.AutoaprobacionSolicitudException;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CausaDevolucion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.RegistroPagosAutomaticos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.Archivo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.AplicacionLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.DevolucionLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.LineaExcepcionVista;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.formato.FilaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.formato.FormatoGenericoCsv;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.formato.LecturaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.proceso.AplicadorRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioExcepcionesRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.estado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION_2;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION_Y_ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA_Y_ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.FECHA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.archivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.codigoErrado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.estadoLote;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.idLinea;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.linea;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.registrar;

/**
 * Sprint 4, tanda 2: los fraudes de la recaudación bancaria (sección 14 del diseño, F9 a F12 y F20) y la resolución
 * de las líneas por revisar con aprobación de otra persona.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class EscenariosFraudeRecaudacionTest {

	@Autowired
	private ServicioRecaudacion servicio;

	@Autowired
	private ServicioExcepcionesRecaudacion excepciones;

	@Autowired
	private AplicadorRecaudacion aplicador;

	@Autowired
	private RegistroPagosAutomaticos registro;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

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
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Long aplicado(Archivo banco) {
		Long lote = registrar(servicio, ADMINISTRACION, banco);
		confirmar(servicio, jdbc, PROMOTORIA, lote, banco.total().toPlainString());
		return lote;
	}

	/**
	 * F9: Administración fabrica un archivo (un «pago» de Mateo para tapar efectivo). Sin la confirmación a ciegas de otra
	 * persona no se aplica nada: ni llamando al proceso ni registrando el pago por su cuenta.
	 */
	@Test
	void loteFabricadoNoAplicaSinConfirmacion() {
		Long matricula = cuota(jdbc, f.mateo(), "MAT-2027");
		Long lote = registrar(servicio, ADMINISTRACION, archivo().pago(f.mateo(), matricula, "350.00", "BCP60001"));

		aplicador.aplicar(1L, lote);
		assertThatThrownBy(() -> registro.prepararCanal(pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja.RECAUDACION,
				LocalDate.of(2026, 10, 1))).isInstanceOf(AccessDeniedException.class);
		// Quien confirma no ve el total: con dos totales que no coinciden con el banco, el lote queda RECHAZADO.
		assertThatThrownBy(() -> confirmar(servicio, jdbc, PROMOTORIA, lote, "300.00"))
				.isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> confirmar(servicio, jdbc, DIRECCION, lote, "349.00"))
				.isInstanceOf(ReglaNegocioException.class);

		assertThat(estadoLote(jdbc, lote)).isEqualTo("RECHAZADO");
		assertThat(estado(jdbc, matricula)).isEqualTo("PENDIENTE");
		assertThat(contar(jdbc, "pago")).isZero();
	}

	/**
	 * F10 (riesgo residual): si Administración cambia una línea del archivo real, el archivo original queda guardado tal
	 * cual, con su SHA-256: cualquiera puede compararlo con el del banco y volver a leerlo reproduce el lote.
	 */
	@Test
	void lineaDesviadaQuedaTrazadaConElArchivoOriginal() throws Exception {
		Archivo banco = archivo().pago(f.sebastian(), null, "350.00", "BCP60011").pago(f.mateo(), null, "350.00",
				"BCP60012");
		Long lote = aplicado(banco);

		Map<String, Object> archivo = jdbc.queryForMap("SELECT a.contenido, a.sha256, a.nombre FROM archivo_cargado a "
				+ "JOIN lote_recaudacion l ON l.archivo_id = a.id WHERE l.id = ?", lote);
		byte[] contenido = (byte[]) archivo.get("contenido");
		assertThat(contenido).isEqualTo(banco.csv());
		assertThat(archivo.get("sha256")).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(banco.csv())));
		LecturaRecaudacion releido = new FormatoGenericoCsv(
				pe.edu.virgenmaria.cuentasclaras.recaudacion.config.PropiedadesRecaudacion.porDefecto())
				.leer((String) archivo.get("nombre"), contenido, LocalDate.of(2026, 10, 2));
		List<Map<String, Object>> lineas = jdbc.queryForList("SELECT codigo, monto, numero_operacion FROM "
				+ "linea_recaudacion WHERE lote_id = ? ORDER BY numero", lote);
		assertThat(releido.filas()).hasSameSizeAs(lineas);
		for (int i = 0; i < lineas.size(); i++) {
			FilaRecaudacion fila = releido.filas().get(i);
			assertThat(lineas.get(i)).containsEntry("codigo", fila.codigo()).containsEntry("numero_operacion",
					fila.operacion());
			assertThat((BigDecimal) lineas.get(i).get("monto")).isEqualByComparingTo(fila.monto());
		}
		// El pago desviado aparece en la familia equivocada: la familia que pagó lo ve en su estado de cuenta.
		assertThat(jdbc.queryForObject("SELECT p.familia_id FROM pago p JOIN linea_recaudacion l ON l.id = "
				+ "p.linea_recaudacion_id WHERE l.lote_id = ? AND l.numero = 1", Long.class, lote)).isEqualTo(f.flores());
	}

	/**
	 * Una línea con el código errado: Administración pide aplicarla a la familia que llamó (otra familia), Promotoría o
	 * Dirección lo aprueban llamando a esa familia y el sistema registra el pago. Quien lo pide no lo aprueba.
	 */
	@Test
	void aplicacionAOtraFamiliaExigeAprobacion() {
		Long lote = aplicado(archivo().linea(FECHA, codigoErrado(f.sebastian()), "", "450.00", "PEN", "BCP60021"));
		Long linea = idLinea(jdbc, lote, 1);
		assertThat(linea(jdbc, lote, 1)).isEqualTo("EXCEPCION:CODIGO_INVALIDO");
		Long matricula = cuota(jdbc, f.sebastian(), "MAT-2027");
		Long marzo = cuota(jdbc, f.sebastian(), "PEN-2027-03");

		como(PROMOTORIA_Y_ADMINISTRACION);
		LineaExcepcionVista vista = excepciones.detalle(linea, CodigoPago.deAlumno(f.sebastian()));
		assertThat(vista.familiaDestinoId()).isEqualTo(f.flores());
		assertThat(vista.puedePedir()).isTrue();
		excepciones.solicitarAplicacion(linea, new AplicacionLineaRequest(f.flores(), List.of(matricula, marzo),
				"Llamé al Sr. Pedro: se equivocó en el último dígito del código"));
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "linea_recaudacion", linea);
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null, true, List.of("912345678")))
				.isInstanceOf(AutoaprobacionSolicitudException.class);
		// Sin llamar a la familia no se aprueba.
		UsuariosDePrueba.iniciarSesion(DIRECCION);
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null)).isInstanceOf(ReglaNegocioException.class);
		assertThat(contar(jdbc, "pago")).isZero();

		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "linea_recaudacion", linea);

		assertThat(linea(jdbc, lote, 1)).isEqualTo("APLICADA_REVISION:CODIGO_INVALIDO");
		Map<String, Object> pago = jdbc.queryForMap("SELECT familia_id, a_cuenta, origen, cajero FROM pago "
				+ "WHERE linea_recaudacion_id = ?", linea);
		assertThat(pago).containsEntry("familia_id", f.flores()).containsEntry("a_cuenta", true)
				.containsEntry("origen", "RECAUDACION").containsEntry("cajero", "sistema.recaudacion");
		assertThat(estado(jdbc, matricula)).isEqualTo("PAGADA");
		assertThat(estado(jdbc, marzo)).isEqualTo("PARCIAL");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'INGRESO_APLICADO'")).isEqualTo(1);
	}

	/**
	 * Un pago en dólares no se aplica a cuotas en soles: se devuelve a la cuenta de destino que se pidió, y lo registra
	 * alguien que no lo pidió ni lo aprobó (correcciones del sprint 4, S4-A4).
	 */
	@Test
	void devolucionLaRegistraQuienNoLaPidioNiLaAprobo() {
		Long lote = aplicado(archivo().linea(FECHA, CodigoPago.deAlumno(f.mateo()), "", "120.00", "USD", "BCP60031"));
		Long linea = idLinea(jdbc, lote, 1);
		como(ADMINISTRACION);
		assertThatThrownBy(() -> excepciones.solicitarAplicacion(linea, new AplicacionLineaRequest(f.quispe(),
				List.of(cuota(jdbc, f.mateo(), "MAT-2027")), "Aplicar el pago en dólares a la matrícula")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("solo se devuelve");
		assertThatThrownBy(() -> excepciones.solicitarDevolucion(linea, new DevolucionLineaRequest("BCP", "abc", "Rosa "
				+ "Quispe Huamán", "Pagó en dólares: se le devuelve por transferencia")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cuenta de destino");
		excepciones.solicitarDevolucion(linea, new DevolucionLineaRequest("BCP", "191-7654321-0-55", "Rosa Quispe Huamán",
				"Pagó en dólares: se le devuelve por transferencia"));
		EscenarioAprobaciones.aprueba(DIRECCION_Y_ADMINISTRACION, bandeja, jdbc, "linea_recaudacion", linea);
		assertThatThrownBy(() -> excepciones.registrarDevolucion(linea, "TRF-0099"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Aprobaste");
		como(ADMINISTRACION);
		assertThatThrownBy(() -> excepciones.registrarDevolucion(linea, "TRF-0099"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Pediste");

		como(ADMINISTRACION_2);
		excepciones.registrarDevolucion(linea, "TRF-0099");

		Map<String, Object> fila = jdbc.queryForMap("SELECT estado, devolucion_operacion, devuelto_por, devolucion_banco, "
				+ "devolucion_cuenta, devolucion_titular FROM linea_recaudacion WHERE id = ?", linea);
		assertThat(fila).containsEntry("estado", "DEVUELTA").containsEntry("devolucion_operacion", "TRF0099")
				.containsEntry("devuelto_por", "administracion2").containsEntry("devolucion_banco", "BCP")
				.containsEntry("devolucion_cuenta", "191-7654321-0-55").containsEntry("devolucion_titular",
						"Rosa Quispe Huamán");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'INGRESO_DEVUELTO'")).isEqualTo(1);
	}

	/** F12: la cajera registra un «Yape» con la operación de un pago que ya entró por el banco: se rechaza. */
	@Test
	void yapeEnCajaConOperacionDeRecaudacionEsRechazado() {
		aplicado(archivo().pago(f.sebastian(), null, "350.00", "BCP60041"));
		como(CAJA);

		assertThatThrownBy(() -> cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.mateo(), "MAT-2027")),
				MedioPago.YAPE, "BCP-60041", "350.00"))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya está registrado");
	}

	/** F20: una cajera no registra en caja un pago «por banco con el código del alumno». */
	@Test
	void cajaNoRegistraRecaudacionBancaria() {
		como(CAJA);

		assertThatThrownBy(() -> cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.mateo(), "MAT-2027")),
				MedioPago.RECAUDACION_BANCARIA, "BCP60051", "350.00"))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no se registra en caja");
		assertThat(cobro.cuentaDeFamilia(f.quispe()).medios()).doesNotContain(MedioPago.RECAUDACION_BANCARIA);
		assertThat(contar(jdbc, "pago")).isZero();
	}

	/** A1: un pago por banco se anula solo si Administración lo encontró en el banco (como cualquier digital). */
	@Test
	void pagoDeRecaudacionNoSeAnulaSinVerificacion() {
		Long lote = aplicado(archivo().pago(f.sebastian(), null, "350.00", "BCP60061"));
		Long pago = jdbc.queryForObject("SELECT p.id FROM pago p JOIN linea_recaudacion l ON l.id = p.linea_recaudacion_id "
				+ "WHERE l.lote_id = ?", Long.class, lote);
		como(ADMINISTRACION);

		assertThatThrownBy(() -> anulaciones.solicitarDevolucion(pago, CausaDevolucion.OTRA,
				"El banco informó que el pago fue revertido")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("verificado en el banco");
	}
}
