package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TotalNoCoincideException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.Archivo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.ConfirmacionRecaudacionVista;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.LineaPrevia;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.LoteDetalle;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.VistaPreviaRecaudacion;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.estado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA_Y_ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.archivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.codigoErrado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.estadoLote;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.registrar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.version;

/**
 * Sprint 4, tanda 2: subir el archivo del banco en 3 pasos (vista previa sin guardar nada, registro todo o nada) y la
 * confirmación a ciegas por OTRA persona. Contra MySQL real (triggers y permisos mínimos) lo prueba
 * {@code PermisosMySqlTest}.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioRecaudacionTest {

	@Autowired
	private ServicioRecaudacion servicio;

	@Autowired
	private ConsultaRecaudacion consulta;

	@Autowired
	private AlertasRecaudacion alertas;

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

	private Long matricula;

	private Long marzo;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		matricula = cuota(jdbc, f.mateo(), "MAT-2027");
		marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Archivo tresPagos() {
		return archivo().pago(f.mateo(), matricula, "350.00", "BCP10001").pago(f.valeria(), null, "800.00", "BCP10002")
				.linea(EscenarioRecaudacion.FECHA, codigoErrado(f.sebastian()), "", "450.00", "PEN", "BCP10003");
	}

	@Test
	void vistaPreviaNoGuardaNada() {
		Archivo banco = tresPagos();
		como(ADMINISTRACION);
		long eventos = contar(jdbc, "evento_auditoria");

		VistaPreviaRecaudacion previa = servicio.previsualizar("banco.csv", banco.csv(), banco.csv().length);

		assertThat(previa.registrable()).isTrue();
		assertThat(previa.lineas()).isEqualTo(3);
		assertThat(previa.aplicaran()).isEqualTo(2);
		assertThat(previa.excepciones()).isEqualTo(1);
		assertThat(previa.detalle()).extracting(LineaPrevia::resultado).containsExactly(LineaPrevia.APLICAR,
				LineaPrevia.APLICAR, LineaPrevia.EXCEPCION);
		assertThat(previa.detalle().get(2).texto()).contains("no corresponde a ningún alumno");
		for (String tabla : new String[] { "archivo_cargado", "lote_recaudacion", "linea_recaudacion", "pago" }) {
			assertThat(contar(jdbc, tabla)).as(tabla).isZero();
		}
		assertThat(contar(jdbc, "evento_auditoria")).isEqualTo(eventos);
	}

	@Test
	void registrarGuardaElArchivoOriginalElLoteYSusLineasSinElTotalEnLaBitacora() {
		Archivo banco = tresPagos();

		Long lote = registrar(servicio, ADMINISTRACION, "banco.csv", banco.csv());

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM lote_recaudacion WHERE id = ?", lote);
		assertThat(fila).containsEntry("estado", "CARGADO").containsEntry("lineas", 3).containsEntry("banco", "GENERICO")
				.containsEntry("formato", "GENERICO_CSV").containsEntry("creado_por", "administracion");
		assertThat((BigDecimal) fila.get("total")).isEqualByComparingTo("1600.00");
		assertThat((BigDecimal) fila.get("total_banco")).isEqualByComparingTo("1600.00");
		byte[] guardado = jdbc.queryForObject("SELECT contenido FROM archivo_cargado WHERE id = ?", byte[].class,
				fila.get("archivo_id"));
		assertThat(guardado).isEqualTo(banco.csv());
		assertThat(contar(jdbc, "linea_recaudacion WHERE estado = 'PENDIENTE' AND lote_id = " + lote)).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT alumno_id FROM linea_recaudacion WHERE lote_id = ? AND numero = 3",
				Long.class, lote)).isNull();
		String detalle = jdbc.queryForObject("SELECT detalle FROM evento_auditoria WHERE accion = 'RECAUDACION_CARGADA'",
				String.class);
		assertThat(detalle).contains("SHA-256").doesNotContain("1,600.00").doesNotContain("1600.00");
		assertThat(contar(jdbc, "pago")).isZero();
	}

	@Test
	void huellaCambiadaPideRevisarDeNuevo() {
		Archivo banco = archivo().pago(f.mateo(), marzo, "450.00", "BCP20001");
		como(ADMINISTRACION);
		VistaPreviaRecaudacion previa = servicio.previsualizar("banco.csv", banco.csv(), banco.csv().length);
		// Mientras Administración revisa, la familia paga esa cuota en caja.
		como(CAJA);
		cobro.cobrar(efectivo(f.quispe(), List.of(marzo), "450.00", "450.00"));

		como(ADMINISTRACION);
		assertThatThrownBy(() -> servicio.registrar(previa, previa.token())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("cambiaron");
		assertThat(contar(jdbc, "lote_recaudacion")).isZero();
		// Una revisión de otra sesión tampoco sirve.
		como(PROMOTORIA_Y_ADMINISTRACION);
		assertThatThrownBy(() -> servicio.registrar(previa, previa.token())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("otra sesión");
	}

	@Test
	void mismoArchivoDosVecesEsRechazado() {
		Archivo banco = tresPagos();
		Long lote = registrar(servicio, ADMINISTRACION, "banco.csv", banco.csv());

		VistaPreviaRecaudacion otraVez = servicio.previsualizar("copia.csv", banco.csv(), banco.csv().length);
		assertThat(otraVez.yaCargado()).contains("ya se cargó en el lote " + lote);
		assertThat(otraVez.registrable()).isFalse();
		assertThatThrownBy(() -> servicio.registrar(otraVez, otraVez.token())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya se cargó");

		// Descartado por quien lo subió, el mismo archivo se puede volver a cargar (el original no se duplica).
		servicio.descartar(lote, "Subí el archivo del día equivocado, era el de ayer");
		Long otro = registrar(servicio, ADMINISTRACION, "banco.csv", banco.csv());
		assertThat(otro).isNotEqualTo(lote);
		assertThat(contar(jdbc, "archivo_cargado")).isEqualTo(1);
		assertThat(estadoLote(jdbc, lote)).isEqualTo("DESCARTADO");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'RECAUDACION_DESCARTADA'")).isEqualTo(1);
	}

	@Test
	void soloQuienLoSubioLoDescarta() {
		Long lote = registrar(servicio, ADMINISTRACION, tresPagos());
		como(EscenarioCaja.CAJA_2);
		assertThatThrownBy(() -> servicio.descartar(lote, "Quiero borrar este archivo del banco"))
				.isInstanceOf(AccessDeniedException.class);
		como(PROMOTORIA_Y_ADMINISTRACION);
		assertThatThrownBy(() -> servicio.descartar(lote, "Quiero borrar este archivo del banco"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Solo quien subió");
		assertThat(estadoLote(jdbc, lote)).isEqualTo("CARGADO");
	}

	@Test
	void quienSubeNoConfirma() {
		Archivo banco = tresPagos();
		Long lote = registrar(servicio, PROMOTORIA_Y_ADMINISTRACION, banco);

		ConfirmacionRecaudacionVista vista = servicio.paraConfirmar(lote);
		assertThat(vista.participaste()).isTrue();
		assertThatThrownBy(() -> servicio.confirmar(lote, version(jdbc, lote), banco.total()))
				.isInstanceOf(AutoaprobacionException.class);

		assertThat(estadoLote(jdbc, lote)).isEqualTo("CARGADO");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'AUTOAPROBACION_RECHAZADA' AND entidad = "
				+ "'lote_recaudacion'")).isEqualTo(1);
		// Administración no confirma (aunque no lo haya subido) y Promotoría no sube.
		como(EscenarioCaja.CAJA_2);
		assertThatThrownBy(() -> servicio.paraConfirmar(lote)).isInstanceOf(AccessDeniedException.class);
		como(ADMINISTRACION);
		assertThatThrownBy(() -> servicio.confirmar(lote, version(jdbc, lote), banco.total()))
				.isInstanceOf(AccessDeniedException.class);
		como(PROMOTORIA);
		assertThatThrownBy(() -> servicio.previsualizar("b.csv", banco.csv(), 10)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void totalACiegasDistintoNoConfirmaYAlColegioLeLlegaAlerta() {
		Archivo banco = tresPagos();
		Long lote = registrar(servicio, ADMINISTRACION, banco);

		assertThatThrownBy(() -> confirmar(servicio, jdbc, PROMOTORIA, lote, "1500.00"))
				.isInstanceOf(TotalNoCoincideException.class).hasMessageContaining("Te queda 1 intento")
				.hasMessageNotContaining("1600");

		assertThat(estadoLote(jdbc, lote)).isEqualTo("CARGADO");
		assertThat(jdbc.queryForObject("SELECT intentos_confirmacion FROM lote_recaudacion WHERE id = ?", Integer.class,
				lote)).isEqualTo(1);
		assertThat(contar(jdbc, "pago")).isZero();
		Map<String, Object> evento = jdbc.queryForMap("SELECT valor_nuevo, detalle, nombre_usuario FROM evento_auditoria "
				+ "WHERE accion = 'RECAUDACION_TOTAL_NO_COINCIDE'");
		assertThat(evento).containsEntry("nombre_usuario", "promotor");
		assertThat((String) evento.get("valor_nuevo")).contains("1,500.00");
		assertThat((String) evento.get("detalle")).doesNotContain("1,600.00");
	}

	@Test
	void dosIntentosRechazanElLote() {
		Archivo banco = tresPagos();
		Long lote = registrar(servicio, ADMINISTRACION, banco);
		assertThatThrownBy(() -> confirmar(servicio, jdbc, PROMOTORIA, lote, "1599.00"))
				.isInstanceOf(TotalNoCoincideException.class);

		assertThatThrownBy(() -> confirmar(servicio, jdbc, DIRECCION, lote, "1601.00"))
				.isInstanceOf(TotalNoCoincideException.class).hasMessageContaining("RECHAZADO");

		Map<String, Object> fila = jdbc.queryForMap("SELECT estado, sha_vigente, rechazado_por, motivo_rechazo "
				+ "FROM lote_recaudacion WHERE id = ?", lote);
		assertThat(fila).containsEntry("estado", "RECHAZADO").containsEntry("rechazado_por", "director");
		assertThat(fila.get("sha_vigente")).isNull();
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'RECAUDACION_RECHAZADA'")).isEqualTo(1);
		// Ya no se confirma, ni siquiera con el total correcto.
		assertThatThrownBy(() -> confirmar(servicio, jdbc, PROMOTORIA, lote, "1600.00"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya no está por confirmar");
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anySatisfy(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA);
			assertThat(a.texto()).contains("RECHAZADO");
		});
	}

	/** Quien confirma ve el banco, las fechas, la cantidad y unas líneas al azar; nunca el total ni el archivo. */
	@Test
	void confirmarNoMuestraElTotal() {
		Archivo banco = tresPagos();
		Long lote = registrar(servicio, ADMINISTRACION, banco);
		como(DIRECCION);

		ConfirmacionRecaudacionVista vista = servicio.paraConfirmar(lote);
		assertThat(vista.lineas()).isEqualTo(3);
		assertThat(vista.muestra()).hasSize(3);
		assertThat(vista.intentosRestantes()).isEqualTo(2);
		assertThat(vista.participaste()).isFalse();
		assertThat(vista.toString()).doesNotContain("1600");
		LoteDetalle detalle = consulta.detalle(lote);
		assertThat(detalle.total()).isNull();
		assertThat(detalle.montosVisibles()).isFalse();
		assertThat(detalle.archivoDescargable()).isFalse();
		assertThat(detalle.detalle()).allSatisfy(l -> assertThat(l.monto()).isNull());
		assertThat(consulta.lista().porConfirmar()).singleElement().satisfies(l -> assertThat(l.total()).isNull());
		assertThatThrownBy(() -> consulta.archivo(lote)).isInstanceOf(ReglaNegocioException.class);
	}

	/** El total correcto: el lote queda confirmado y el sistema aplica los pagos en la caja de recaudación. */
	@Test
	void confirmacionCorrectaAplicaLosPagosConBoletaEnLaCajaDeRecaudacion() {
		Archivo banco = tresPagos();
		Long lote = registrar(servicio, ADMINISTRACION, banco);

		confirmar(servicio, jdbc, PROMOTORIA, lote, "1600.00");

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM lote_recaudacion WHERE id = ?", lote);
		assertThat(fila).containsEntry("estado", "APLICADO").containsEntry("confirmado_por", "promotor")
				.containsEntry("lineas_aplicadas", 2).containsEntry("lineas_excepcion", 1);
		assertThat((BigDecimal) fila.get("monto_aplicado")).isEqualByComparingTo("1150.00");
		assertThat((BigDecimal) fila.get("monto_excepcion")).isEqualByComparingTo("450.00");
		assertThat(estado(jdbc, matricula)).isEqualTo("PAGADA");
		assertThat(estado(jdbc, cuota(jdbc, f.valeria(), "MAT-2027"))).isEqualTo("PAGADA");
		assertThat(estado(jdbc, cuota(jdbc, f.valeria(), "PEN-2027-03"))).isEqualTo("PAGADA");
		List<Map<String, Object>> pagos = jdbc.queryForList("SELECT p.origen, p.medio, p.cajero, p.creado_por, p.fecha, "
				+ "c.canal, c.cajero AS caja, b.tipo, b.estado_envio FROM pago p JOIN caja_diaria c ON c.id = p.caja_diaria_id "
				+ "JOIN comprobante b ON b.id = p.comprobante_id ORDER BY p.id");
		assertThat(pagos).hasSize(2).allSatisfy(p -> assertThat(p).containsEntry("origen", "RECAUDACION")
				.containsEntry("medio", "RECAUDACION_BANCARIA").containsEntry("cajero", "sistema.recaudacion")
				.containsEntry("creado_por", "sistema.recaudacion").containsEntry("canal", "RECAUDACION")
				.containsEntry("caja", "sistema.recaudacion").containsEntry("tipo", "BOLETA")
				.containsEntry("estado_envio", "ACEPTADO"));
		assertThat(pagos.getFirst().get("fecha").toString()).isEqualTo(EscenarioRecaudacion.FECHA);
		for (String accion : new String[] { "RECAUDACION_CONFIRMADA", "RECAUDACION_APLICADA",
				"RECAUDACION_LINEA_EXCEPCION" }) {
			assertThat(contar(jdbc, "evento_auditoria WHERE accion = '" + accion + "'")).as(accion).isEqualTo(1);
		}
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'PAGO_REGISTRADO' AND nombre_usuario = "
				+ "'sistema.recaudacion'")).isEqualTo(2);
		como(DIRECCION);
		LoteDetalle detalle = consulta.detalle(lote);
		assertThat(detalle.total()).isEqualByComparingTo("1600.00");
		assertThat(detalle.archivoDescargable()).isTrue();
		assertThat(detalle.detalle().getFirst().comprobante()).startsWith("B001-");
		assertThat(consulta.archivo(lote).contenido()).isEqualTo(banco.csv());
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'ARCHIVO_BANCO_DESCARGADO'")).isEqualTo(1);
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anySatisfy(a -> assertThat(a.texto()).contains("por revisar"));
	}

	@Test
	void baseDeDeudasLlevaLosCodigosYSeAudita() {
		como(ADMINISTRACION);

		ServicioRecaudacion.ArchivoExportado base = servicio.exportarBaseDeudas();

		String texto = new String(base.contenido(), StandardCharsets.UTF_8);
		assertThat(texto).startsWith("codigo_alumno;referencia_deuda;alumno;concepto;vencimiento;saldo;moneda")
				.contains(CodigoPago.deAlumno(f.mateo()) + ";" + CodigoPago.deCuota(matricula) + ";")
				.contains(";350.00;PEN");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'BASE_DEUDAS_EXPORTADA'")).isEqualTo(1);
		como(PROMOTORIA);
		assertThatThrownBy(() -> servicio.exportarBaseDeudas()).isInstanceOf(AccessDeniedException.class);
	}

	/** Un archivo con errores (o una referencia de deuda que no es del colegio) no se registra. */
	@Test
	void archivoConErroresNoSeRegistra() {
		byte[] malo = (EscenarioRecaudacion.CABECERA + "\n2026-10-01;" + CodigoPago.deAlumno(f.mateo()) + ";;450,00;PEN;"
				+ "BCP30001;\n2026-10-01;" + CodigoPago.deAlumno(f.mateo()) + ";" + CodigoPago.deCuota(99_999_999L)
				+ ";450.00;PEN;BCP30002;\n").getBytes(StandardCharsets.UTF_8);
		como(ADMINISTRACION);

		VistaPreviaRecaudacion previa = servicio.previsualizar("banco.csv", malo, malo.length);

		assertThat(previa.errores()).hasSize(2);
		assertThat(previa.registrable()).isFalse();
		assertThatThrownBy(() -> servicio.registrar(previa, previa.token())).isInstanceOf(ReglaNegocioException.class);
		byte[] referenciaAjena = (EscenarioRecaudacion.CABECERA + "\n2026-10-01;" + CodigoPago.deAlumno(f.mateo()) + ";"
				+ CodigoPago.deCuota(99_999_999L) + ";450.00;PEN;BCP30002;\n").getBytes(StandardCharsets.UTF_8);
		assertThat(servicio.previsualizar("b.csv", referenciaAjena, referenciaAjena.length).errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).contains("no es ninguna cuota del colegio"));
		assertThat(contar(jdbc, "lote_recaudacion")).isZero();
	}
}
