package pe.edu.virgenmaria.cuentasclaras.fraude;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.Extracto;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.DetalleExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.VistaExtractos;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CategoriaExplicacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ResumenConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.SaldoNoCoincideException;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.ConfirmacionRecaudacionVista;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.LineaMuestra;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ConsultaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ObjetoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.AlertasConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioPartidas;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.estadoExtracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.partidas;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.verificacionDePago;

/**
 * Correcciones del sprint 4: los 7 ataques de la auditoría antifraude ({@code AtaquesSprint4Test} de la evidencia), que
 * antes FUNCIONABAN, ahora FALLAN. Cada prueba reproduce el ataque paso a paso y comprueba que el sistema lo impide o lo
 * deja en rojo para Promotoría (docs/arquitectura/sprint-4-correcciones.md).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AtaquesSprint4Test {

	static final Instant LUNES = Instant.parse("2026-10-05T14:00:00Z");

	@Autowired
	private ServicioExtractos extractos;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private AlertasConciliacion alertas;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioPartidas servicioPartidas;

	@Autowired
	private ResumenConciliacion resumen;

	@Autowired
	private ServicioRecaudacion recaudacion;

	@Autowired
	private ConsultaRecaudacion consultaRecaudacion;

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

	private Long yapeInventado(String operacion) {
		como(CAJA);
		return cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), MedioPago.YAPE, operacion,
				"450.00"));
	}

	private List<AlertaRevision> alertasPromotoria() {
		como(PROMOTORIA);
		return alertas.alertas();
	}

	/**
	 * ATAQUE 6 (S4-C1): el Yape inventado por la cajera queda CRÍTICO; Administración lo empareja «a mano» con un abono de
	 * otro monto (intereses de S/ 0.35). Ahora se rechaza por el monto, no queda ninguna partida ni verificación y la
	 * alerta crítica sigue.
	 */
	@Test
	void ataque6EmparejarAManoConOtroMontoYaNoBorraLaAlertaCritica() {
		Long inventado = yapeInventado("YP999888");
		reloj.fijar(LUNES);
		Extracto banco = extracto("10000.00").abono("2026-10-02", "INTERESES", "", "0.35");
		Long id = registrar(extractos, ADMINISTRACION, banco);
		EscenarioConciliacion.confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		assertThat(alertasPromotoria()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA && a.texto().contains("YP999888"));
		Long movimiento = jdbc.queryForObject("SELECT id FROM movimiento_bancario WHERE extracto_id = ?", Long.class, id);

		como(ADMINISTRACION);
		assertThat(servicioPartidas.posibles(movimiento)).noneMatch(o -> o.id().equals(inventado));
		assertThatThrownBy(() -> servicioPartidas.emparejarManual(movimiento, ObjetoPartida.PAGO, inventado,
				"Yape agrupado por el banco")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("MISMO monto");

		assertThat(partidas(jdbc)).isEmpty();
		assertThat(verificacionDePago(jdbc, inventado)).isNull();
		assertThat(alertasPromotoria()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA && a.texto().contains("YP999888"));
	}

	/**
	 * S4-C1, variante con el MISMO monto (F16): Administración empareja el Yape inventado con un abono real ajeno de
	 * S/ 450. La pareja queda por aprobar, no verifica nada y la alerta sigue hasta que Promotoría la apruebe mirando su
	 * app del banco; si la rechaza, todo sigue en rojo.
	 */
	@Test
	void parejaManualDelMismoMontoEsperaLaBandejaYSiSeRechazaSigueEnRojo() {
		Long inventado = yapeInventado("YP999888");
		reloj.fijar(LUNES);
		Extracto banco = extracto("10000.00").abono("2026-10-02", "TRANSFERENCIA DE UN TERCERO", "TR55501", "450.00")
				.abono("2026-10-02", "OTRA TRANSFERENCIA", "TR55502", "450.00");
		Long id = registrar(extractos, ADMINISTRACION, banco);
		EscenarioConciliacion.confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		Long movimiento = jdbc.queryForObject("SELECT id FROM movimiento_bancario WHERE extracto_id = ? AND numero = 1",
				Long.class, id);

		como(ADMINISTRACION);
		servicioPartidas.emparejarManual(movimiento, ObjetoPartida.PAGO, inventado, "Yape que el banco registró como "
				+ "transferencia");
		assertThat(partidas(jdbc)).containsExactly("MANUAL PROPUESTA PAGO");
		assertThat(verificacionDePago(jdbc, inventado)).isNull();
		assertThat(alertasPromotoria()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA && a.texto().contains("YP999888"));

		// La cajera que cobró no puede aprobarla; Promotoría la rechaza porque en su app es la transferencia de un tercero.
		Long solicitud = jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'PARTIDA_MANUAL'", Long.class);
		como(PROMOTORIA);
		bandeja.rechazar(solicitud, "En la app del banco es una transferencia de un tercero, no el Yape de la familia");
		assertThat(partidas(jdbc)).isEmpty();
		assertThat(verificacionDePago(jdbc, inventado)).isNull();
		assertThat(alertasPromotoria()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA && a.texto().contains("YP999888"));
	}

	/**
	 * ATAQUE 1 (S4-A1): la promotora ya no puede calcular el saldo «ciego» desde la pantalla. Del extracto por confirmar
	 * no ve montos, tipos ni parejas; con el saldo anterior solo, lo que escriba no coincide y el extracto editado (con
	 * el abono del Yape inventado) no se confirma: el Yape inventado sigue sin verificar y en rojo.
	 */
	@Test
	void ataque1ElSaldoCiegoYaNoSeCalculaDesdeLaPantalla() {
		Long inventado = yapeInventado("YP999888");
		reloj.fijar(LUNES);
		Extracto primero = extracto("10000.00").cargo("2026-10-01", "MANTENIMIENTO", "", "15.00");
		Long id1 = registrar(extractos, ADMINISTRACION, primero);
		EscenarioConciliacion.confirmar(extractos, PROMOTORIA, cuentaId, primero.saldoFinal().toPlainString());
		Extracto editado = extracto(primero.saldoFinal().toPlainString())
				.abono("2026-10-02", "YAPE DE ROSA QUISPE", "YP999888", "450.00");
		Long id2 = registrar(extractos, ADMINISTRACION, editado);

		UsuariosDePrueba.iniciarSesion(PROMOTORIA);
		VistaExtractos lista = resumen.extractos();
		BigDecimal anterior = lista.extractos().stream().filter(e -> e.id().equals(id1)).findFirst().orElseThrow()
				.saldoFinal();
		DetalleExtracto detalle = resumen.extracto(id2);
		assertThat(detalle.montosOcultos()).isTrue();
		assertThat(detalle.saldoFinal()).isNull();
		assertThat(detalle.movimientos()).allSatisfy(m -> {
			assertThat(m.monto()).isNull();
			assertThat(m.tipo()).isNull();
			assertThat(m.pareja()).doesNotContain("Pago", "Exacta");
		});
		assertThat(resumen.diferencias().sinPareja()).isEmpty();
		// Sin montos, lo único que puede «calcular» es el saldo anterior: no coincide.
		assertThatThrownBy(() -> EscenarioConciliacion.confirmar(extractos, PROMOTORIA, cuentaId,
				anterior.toPlainString())).isInstanceOf(SaldoNoCoincideException.class);
		// El saldo real del banco (sin el abono inventado) tampoco: el extracto editado queda RECHAZADO.
		assertThatThrownBy(() -> EscenarioConciliacion.confirmar(extractos, PROMOTORIA, cuentaId,
				primero.saldoFinal().toPlainString())).isInstanceOf(SaldoNoCoincideException.class);
		assertThat(estadoExtracto(jdbc, id2)).isEqualTo("RECHAZADO");
		assertThat(verificacionDePago(jdbc, inventado)).isNull();
		assertThat(alertasPromotoria()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA && a.texto().contains("RECHAZADO"));
	}

	/**
	 * S4-A1, variante: el extracto real se sube, se descarta y se sube uno editado de los MISMOS días. Mientras el editado
	 * espera la confirmación, el descartado tampoco muestra sus montos a quien confirma (traía los del banco).
	 */
	@Test
	void unExtractoDescartadoDeLosMismosDiasNoMuestraSusMontosMientrasOtroEsperaConfirmacion() {
		reloj.fijar(LUNES);
		Extracto real = extracto("10000.00").abono("2026-10-02", "TRANSFERENCIA", "TR1001", "120.00");
		Long descartado = registrar(extractos, ADMINISTRACION, real);
		extractos.descartar(descartado, "Lo subí incompleto, lo cargo de nuevo");
		Long editado = registrar(extractos, ADMINISTRACION, extracto("10000.00")
				.abono("2026-10-02", "TRANSFERENCIA", "TR1001", "120.00")
				.abono("2026-10-02", "YAPE DE ROSA QUISPE", "YP999888", "450.00"));
		UsuariosDePrueba.iniciarSesion(PROMOTORIA);
		assertThat(resumen.extracto(descartado).montosOcultos()).isTrue();
		assertThat(resumen.extracto(descartado).movimientos()).allSatisfy(m -> assertThat(m.monto()).isNull());
		assertThat(resumen.extracto(editado).montosOcultos()).isTrue();
		// Quien lo subió sí los ve (ya los conoce).
		como(ADMINISTRACION);
		assertThat(resumen.extracto(editado).movimientos()).allSatisfy(m -> assertThat(m.monto()).isNotNull());
	}

	/**
	 * ATAQUE 2 (S4-A2 y QA-S4-5): Administración agrega el abono del Yape inventado y un cargo «compensatorio» del mismo
	 * monto; el saldo final es el del banco y el extracto se confirma. Ahora el cargo compensatorio es una alerta CRÍTICA
	 * el mismo día, el cargo queda «sin explicar» y quien subió el extracto no puede explicarlo.
	 */
	@Test
	void ataque2ElCargoQueCompensaUnAbonoInventadoEsCritico() {
		yapeInventado("YP999888");
		reloj.fijar(LUNES);
		Extracto banco = extracto("10000.00").cargo("2026-10-02", "MANTENIMIENTO DE CUENTA", "", "15.00");
		Extracto editado = extracto("10000.00").abono("2026-10-02", "YAPE DE ROSA QUISPE", "YP999888", "450.00")
				.cargo("2026-10-02", "MANTENIMIENTO DE CUENTA", "", "15.00")
				.cargo("2026-10-02", "COMISION MANTENIMIENTO POS", "", "450.00");
		assertThat(editado.saldoFinal()).isEqualByComparingTo(banco.saldoFinal());
		Long id = registrar(extractos, ADMINISTRACION, editado);
		EscenarioConciliacion.confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		assertThat(estadoExtracto(jdbc, id)).isEqualTo("CONFIRMADO");

		List<AlertaRevision> lista = alertasPromotoria();
		assertThat(lista).anyMatch(a -> a.gravedad() == Gravedad.CRITICA && a.texto().contains("COMISION MANTENIMIENTO POS")
				&& a.texto().contains("mismo monto que un abono"));
		UsuariosDePrueba.iniciarSesion(PROMOTORIA);
		assertThat(resumen.diferencias().cargosSinExplicar()).extracting(s -> s.descripcion())
				.contains("COMISION MANTENIMIENTO POS");
		Long cargo = jdbc.queryForObject("SELECT id FROM movimiento_bancario WHERE descripcion = "
				+ "'COMISION MANTENIMIENTO POS'", Long.class);
		como(ADMINISTRACION);
		assertThatThrownBy(() -> servicioPartidas.explicar(cargo, CategoriaExplicacion.COMISION_BANCARIA,
				"Comisión del POS del mes de octubre")).isInstanceOf(AutoaprobacionException.class);
	}

	/**
	 * ATAQUE 3 (S4-A1): la muestra de la confirmación a ciegas de la recaudación ya no trae montos, es FIJA (recargar no
	 * muestra otras líneas) y nunca cubre todo el lote: el total no se reconstruye desde la pantalla.
	 */
	@Test
	void ataque3ElTotalCiegoDeRecaudacionYaNoSeReconstruyeConLaMuestra() {
		EscenarioRecaudacion.Archivo archivo = EscenarioRecaudacion.archivo();
		String[] montos = { "450.00", "350.00", "120.50", "99.90", "450.00", "75.00", "300.00", "10.10" };
		Long[] hijos = { f.mateo(), f.valeria(), f.sebastian(), f.mateo(), f.valeria(), f.sebastian(), f.mateo(),
				f.valeria() };
		for (int i = 0; i < montos.length; i++) {
			archivo.linea(EscenarioRecaudacion.FECHA, CodigoPago.deAlumno(hijos[i]), "", montos[i], "PEN", "BCP7000" + i);
		}
		Long lote = EscenarioRecaudacion.registrar(recaudacion, ADMINISTRACION, archivo);
		UsuariosDePrueba.iniciarSesion(PROMOTORIA);
		Set<String> vistas = new HashSet<>();
		ConfirmacionRecaudacionVista primera = recaudacion.paraConfirmar(lote);
		for (int recarga = 0; recarga < 50; recarga++) {
			ConfirmacionRecaudacionVista vista = recaudacion.paraConfirmar(lote);
			assertThat(vista.muestra()).isEqualTo(primera.muestra());
			vista.muestra().stream().map(LineaMuestra::operacion).forEach(vistas::add);
		}
		assertThat(vistas).hasSize(3).hasSizeLessThan(montos.length);
		assertThat(LineaMuestra.class.getRecordComponents()).extracting(c -> c.getName()).doesNotContain("monto");
		assertThat(primera.toString()).doesNotContain("450.00", "1855.50");
	}

	/**
	 * ATAQUE 7 (S4-A1): el archivo de un lote DESCARTADO ya no se descarga mientras otro lote de esas mismas fechas (el
	 * mismo contenido con un byte distinto y otro SHA-256) espera su confirmación a ciegas.
	 */
	@Test
	void ataque7ElArchivoDeUnLoteDescartadoNoSeDescargaMientrasOtroDeEsosDiasEsperaConfirmacion() {
		EscenarioRecaudacion.Archivo archivo = EscenarioRecaudacion.archivo()
				.pago(f.mateo(), null, "450.00", "BCP90001").pago(f.valeria(), null, "350.00", "BCP90002")
				.pago(f.sebastian(), null, "120.00", "BCP90003").pago(f.mateo(), null, "99.00", "BCP90004");
		Long primero = EscenarioRecaudacion.registrar(recaudacion, ADMINISTRACION, archivo);
		como(ADMINISTRACION);
		recaudacion.descartar(primero, "Subí el del día equivocado");
		byte[] casiIgual = (new String(archivo.csv(), StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8);
		Long segundo = EscenarioRecaudacion.registrar(recaudacion, ADMINISTRACION, "banco-2.csv", casiIgual);
		UsuariosDePrueba.iniciarSesion(PROMOTORIA);
		assertThatThrownBy(() -> consultaRecaudacion.archivo(primero)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("por confirmar");
		assertThat(consultaRecaudacion.detalle(primero).archivoDescargable()).isFalse();
		// Confirmado el segundo, el archivo del descartado vuelve a ser evidencia descargable (y auditada).
		EscenarioRecaudacion.confirmar(recaudacion, jdbc, PROMOTORIA, segundo, "1019.00");
		UsuariosDePrueba.iniciarSesion(PROMOTORIA);
		assertThat(new String(consultaRecaudacion.archivo(primero).contenido(), StandardCharsets.UTF_8))
				.contains("TOTAL;1019.00");
	}
}
