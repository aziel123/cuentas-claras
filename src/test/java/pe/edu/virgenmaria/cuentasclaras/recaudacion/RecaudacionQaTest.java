package pe.edu.virgenmaria.cuentasclaras.recaudacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.Archivo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.AplicacionLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.VistaPreviaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioExcepcionesRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.estado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.pagado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.archivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.estadoLote;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.linea;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.registrar;

/**
 * QA del sprint 4 (recaudación bancaria): céntimos, hermanos, descuentos, fin de mes en la caja de canal e idempotencia
 * con el mismo archivo guardado con otros finales de línea.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class RecaudacionQaTest {

	@Autowired
	private ServicioRecaudacion servicio;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private ServicioExcepcionesRecaudacion excepciones;

	@Autowired
	private BandejaAprobaciones bandeja;

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
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Long aplicado(Archivo banco) {
		Long lote = registrar(servicio, ADMINISTRACION, banco);
		confirmar(servicio, jdbc, PROMOTORIA, lote, banco.total().toPlainString());
		SecurityContextHolder.clearContext();
		return lote;
	}

	@Test
	void debeDejarEnExcesoElPagoQueSuperaLaDeudaDelAlumnoAunqueAlcanceParaSuHermano() {
		Long lote = aplicado(archivo().pago(f.mateo(), null, "5000.00", "BCP50001"));

		assertThat(linea(jdbc, lote, 1)).isEqualTo("EXCEPCION:EXCESO");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(estado(jdbc, cuota(jdbc, f.valeria(), "MAT-2027"))).isEqualTo("PENDIENTE");
		assertThat(estadoLote(jdbc, lote)).isEqualTo("APLICADO");
	}

	@Test
	void debeImputarElCentimoSobranteALaCuotaSiguienteComoPagoACuenta() {
		Long lote = aplicado(archivo().pago(f.mateo(), null, "350.01", "BCP50011"));

		assertThat(linea(jdbc, lote, 1)).isEqualTo("APLICADA");
		assertThat(estado(jdbc, cuota(jdbc, f.mateo(), "MAT-2027"))).isEqualTo("PAGADA");
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		assertThat(estado(jdbc, marzo)).isEqualTo("PARCIAL");
		assertThat(pagado(jdbc, marzo)).isEqualByComparingTo("0.01");
		assertThat(jdbc.queryForObject("SELECT a_cuenta FROM pago", Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject("SELECT total FROM pago", BigDecimal.class)).isEqualByComparingTo("350.01");
	}

	@Test
	void debePagarAlCentimoPorReferenciaUnaCuotaConDescuentoDeHermanos() {
		Long marzoValeria = cuota(jdbc, f.valeria(), "PEN-2027-03");
		EscenarioCobranza.como(ADMINISTRACION);
		Long descuento = descuentos.solicitar(EscenarioAprobaciones.descuento(f.valeria(), TipoDescuento.HERMANOS, "10",
				List.of(marzoValeria)));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "descuento", descuento);

		Long lote = aplicado(archivo().pago(f.valeria(), marzoValeria, "405.00", "BCP50021"));

		assertThat(linea(jdbc, lote, 1)).isEqualTo("APLICADA");
		assertThat(estado(jdbc, marzoValeria)).isEqualTo("PAGADA");
		assertThat(jdbc.queryForObject("SELECT a_cuenta FROM pago", Boolean.class)).isFalse();
	}

	@Test
	void debeDejarEnExcesoElPagoDeLaCuotaCompletaSiTieneDescuento() {
		Long marzoValeria = cuota(jdbc, f.valeria(), "PEN-2027-03");
		EscenarioCobranza.como(ADMINISTRACION);
		Long descuento = descuentos.solicitar(EscenarioAprobaciones.descuento(f.valeria(), TipoDescuento.HERMANOS, "10",
				List.of(marzoValeria)));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "descuento", descuento);

		Long lote = aplicado(archivo().pago(f.valeria(), marzoValeria, "450.00", "BCP50022"));

		assertThat(linea(jdbc, lote, 1)).isEqualTo("EXCEPCION:EXCESO");
		assertThat(estado(jdbc, marzoValeria)).isEqualTo("PENDIENTE");
	}

	@Test
	void debeRegistrarCadaPagoEnLaCajaDeSuFechaDePagoAunqueCruceElFinDeMes() {
		Archivo banco = archivo()
				.linea("2026-09-30", CodigoPago.deAlumno(f.mateo()), "", "350.00", "PEN", "BCP50031")
				.linea("2026-10-01", CodigoPago.deAlumno(f.valeria()), "", "350.00", "PEN", "BCP50032");

		aplicado(banco);

		assertThat(jdbc.queryForList("SELECT c.fecha FROM pago p JOIN caja_diaria c ON c.id = p.caja_diaria_id "
				+ "ORDER BY p.id", LocalDate.class)).containsExactly(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1));
		assertThat(contar(jdbc, "caja_diaria WHERE canal = 'RECAUDACION'")).isEqualTo(2);
		// La boleta se emite con la fecha de hoy (02/10/2026), no con la del banco.
		assertThat(jdbc.queryForList("SELECT c.fecha_emision FROM comprobante c JOIN pago p ON p.comprobante_id = c.id",
				LocalDate.class)).containsOnly(LocalDate.of(2026, 10, 2));
	}

	@Test
	void debeRegistrarUnaSolaVezLasOperacionesDelMismoArchivoGuardadoConOtrosFinesDeLinea() {
		Archivo banco = archivo().pago(f.mateo(), null, "350.00", "BCP50041").pago(f.valeria(), null, "350.00",
				"BCP50042");
		Long primero = registrar(servicio, ADMINISTRACION, banco);
		byte[] conCrlf = new String(banco.csv(), StandardCharsets.UTF_8).replace("\n", "\r\n")
				.getBytes(StandardCharsets.UTF_8);

		UsuariosDePrueba.iniciarSesion(ADMINISTRACION);
		VistaPreviaRecaudacion previa = servicio.previsualizar("banco-crlf.csv", conCrlf, conCrlf.length);
		assertThat(previa.yaRegistradas()).isEqualTo(2);
		Long segundo = servicio.registrar(previa, previa.token());
		confirmar(servicio, jdbc, PROMOTORIA, primero, "700.00");
		confirmar(servicio, jdbc, PROMOTORIA, segundo, "700.00");
		SecurityContextHolder.clearContext();

		assertThat(contar(jdbc, "pago")).isEqualTo(2);
		assertThat(linea(jdbc, segundo, 1)).isEqualTo("EXCEPCION:OPERACION_DUPLICADA");
		assertThat(linea(jdbc, segundo, 2)).isEqualTo("EXCEPCION:OPERACION_DUPLICADA");
		assertThat(pagado(jdbc, cuota(jdbc, f.mateo(), "MAT-2027"))).isEqualByComparingTo("350.00");
	}

	@Test
	void debeAplicarSinMarcarACuentaUnaLineaRevisadaQueCubreJustoLaCuota() {
		Long lote = aplicado(archivo().linea(EscenarioRecaudacion.FECHA, EscenarioRecaudacion.codigoErrado(f.sebastian()),
				"", "350.00", "PEN", "BCP50091"));
		Long linea = EscenarioRecaudacion.idLinea(jdbc, lote, 1);
		Long matricula = cuota(jdbc, f.sebastian(), "MAT-2027");
		EscenarioCobranza.como(EscenarioCobranza.PROMOTORIA_Y_ADMINISTRACION);
		excepciones.solicitarAplicacion(linea, new AplicacionLineaRequest(f.flores(), List.of(matricula),
				"Llamé al Sr. Pedro: se equivocó en el último dígito del código"));

		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "linea_recaudacion", linea);

		assertThat(estado(jdbc, matricula)).isEqualTo("PAGADA");
		assertThat(jdbc.queryForObject("SELECT a_cuenta FROM pago WHERE linea_recaudacion_id = ?", Boolean.class, linea))
				.isFalse();
	}

	@Test
	void debeRechazarElMismoArchivoAunqueSeSubaConOtroNombre() {
		Archivo banco = archivo().pago(f.mateo(), null, "350.00", "BCP50051");
		registrar(servicio, ADMINISTRACION, "banco-lunes.csv", banco.csv());

		assertThatThrownBy(() -> registrar(servicio, ADMINISTRACION, "otro-nombre.csv", banco.csv()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya se cargó");
		assertThat(contar(jdbc, "lote_recaudacion")).isEqualTo(1);
	}

	@Test
	void debeAplicarSoloUnaDeDosLineasConLaMismaOperacionEnElMismoArchivo() {
		Long lote = aplicado(archivo().pago(f.mateo(), null, "350.00", "BCP50061").pago(f.valeria(), null, "350.00",
				"BCP-50061"));

		assertThat(linea(jdbc, lote, 1)).isEqualTo("APLICADA");
		assertThat(linea(jdbc, lote, 2)).isEqualTo("EXCEPCION:OPERACION_DUPLICADA");
		assertThat(contar(jdbc, "pago")).isEqualTo(1);
	}

	@Test
	void debeRechazarLaConfirmacionConUnTotalQueDifiereEnUnCentimo() {
		Archivo banco = archivo().pago(f.mateo(), null, "350.00", "BCP50071");
		Long lote = registrar(servicio, ADMINISTRACION, banco);

		assertThatThrownBy(() -> confirmar(servicio, jdbc, PROMOTORIA, lote, "350.01"))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(estadoLote(jdbc, lote)).isEqualTo("CARGADO");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(jdbc.queryForObject("SELECT intentos_confirmacion FROM lote_recaudacion WHERE id = ?", Integer.class,
				lote)).isEqualTo(1);
	}

	@Test
	void debeRechazarUnTotalConfirmadoConMasDeDosDecimalesSinGastarUnIntento() {
		Archivo banco = archivo().pago(f.mateo(), null, "350.00", "BCP50081");
		Long lote = registrar(servicio, ADMINISTRACION, banco);

		assertThatThrownBy(() -> confirmar(servicio, jdbc, PROMOTORIA, lote, "350.001"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("2 decimales");
		assertThat(jdbc.queryForObject("SELECT intentos_confirmacion FROM lote_recaudacion WHERE id = ?", Integer.class,
				lote)).isZero();
		assertThat(EscenarioRecaudacion.estadoLote(jdbc, lote)).isEqualTo("CARGADO");
	}
}
