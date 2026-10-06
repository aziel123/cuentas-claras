package pe.edu.virgenmaria.cuentasclaras.recaudacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.Archivo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.proceso.AplicadorRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.estado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.pagado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.FECHA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.archivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.codigoErrado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.estadoLote;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.linea;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.registrar;

/**
 * Sprint 4, tanda 2: cómo aplica {@code sistema.recaudacion} cada línea de un lote confirmado a ciegas (sección 10.2
 * del diseño): la misma imputación de caja, pagos a cuenta resaltados, excepciones con su motivo y tandas idempotentes.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AplicadorRecaudacionTest {

	@Autowired
	private ServicioRecaudacion servicio;

	@Autowired
	private AplicadorRecaudacion aplicador;

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
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** Administración sube el archivo y Promotoría lo confirma con el total correcto: el sistema aplica los pagos. */
	private Long aplicado(Archivo banco) {
		Long lote = registrar(servicio, ADMINISTRACION, banco);
		confirmar(servicio, jdbc, PROMOTORIA, lote, banco.total().toPlainString());
		SecurityContextHolder.clearContext();
		return lote;
	}

	private Map<String, Object> pagoDeLinea(Long lote, int numero) {
		return jdbc.queryForMap("SELECT p.* FROM pago p JOIN linea_recaudacion l ON l.id = p.linea_recaudacion_id "
				+ "WHERE l.lote_id = ? AND l.numero = ?", lote, numero);
	}

	@Test
	void conReferenciaVaALaCuotaExacta() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");

		Long lote = aplicado(archivo().pago(f.mateo(), marzo, "450.00", "BCP40001"));

		assertThat(linea(jdbc, lote, 1)).isEqualTo("APLICADA");
		assertThat(estado(jdbc, marzo)).isEqualTo("PAGADA");
		// Aunque la matrícula vence antes, el banco pagó la cuota de la referencia.
		assertThat(estado(jdbc, cuota(jdbc, f.mateo(), "MAT-2027"))).isEqualTo("PENDIENTE");
		assertThat(pagoDeLinea(lote, 1)).containsEntry("a_cuenta", false).containsEntry("familia_id", f.quispe());
		assertThat(estadoLote(jdbc, lote)).isEqualTo("APLICADO");
	}

	@Test
	void sinReferenciaImputaDeLaMasAntigua() {
		Long lote = aplicado(archivo().pago(f.mateo(), null, "800.00", "BCP40011")
				.pago(f.valeria(), null, "500.00", "BCP40012"));

		// Mateo: 800.00 = matrícula (350) + marzo (450), ambas PAGADA.
		assertThat(estado(jdbc, cuota(jdbc, f.mateo(), "MAT-2027"))).isEqualTo("PAGADA");
		assertThat(estado(jdbc, cuota(jdbc, f.mateo(), "PEN-2027-03"))).isEqualTo("PAGADA");
		assertThat(estado(jdbc, cuota(jdbc, f.mateo(), "PEN-2027-04"))).isEqualTo("PENDIENTE");
		assertThat(pagoDeLinea(lote, 1)).containsEntry("a_cuenta", false);
		// Valeria: 500.00 = matrícula (350) + marzo a cuenta (150). Nunca se pasa a su hermano.
		assertThat(estado(jdbc, cuota(jdbc, f.valeria(), "MAT-2027"))).isEqualTo("PAGADA");
		assertThat(estado(jdbc, cuota(jdbc, f.valeria(), "PEN-2027-03"))).isEqualTo("PARCIAL");
		assertThat(pagado(jdbc, cuota(jdbc, f.valeria(), "PEN-2027-03"))).isEqualByComparingTo("150.00");
		assertThat(pagoDeLinea(lote, 2)).containsEntry("a_cuenta", true);
		Long pagoMateo = (Long) pagoDeLinea(lote, 1).get("id");
		assertThat(contar(jdbc, "aplicacion_pago WHERE pago_id = " + pagoMateo)).isEqualTo(2);
		assertThat(contar(jdbc, "comprobante_linea l JOIN pago p ON p.comprobante_id = l.comprobante_id WHERE p.id = "
				+ pagoMateo)).isEqualTo(2);
	}

	@Test
	void parcialQuedaACuentaYResaltado() {
		Long matricula = cuota(jdbc, f.sebastian(), "MAT-2027");

		Long lote = aplicado(archivo().pago(f.sebastian(), matricula, "200.00", "BCP40021"));

		assertThat(estado(jdbc, matricula)).isEqualTo("PARCIAL");
		assertThat(pagoDeLinea(lote, 1)).containsEntry("a_cuenta", true);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'PAGO_A_CUENTA'")).isEqualTo(1);
	}

	@Test
	void excesoQuedaEnExcepcion() {
		Long matricula = cuota(jdbc, f.mateo(), "MAT-2027");

		Long lote = aplicado(archivo().pago(f.mateo(), matricula, "400.00", "BCP40031")
				.pago(f.valeria(), null, "99999.99", "BCP40032"));

		assertThat(linea(jdbc, lote, 1)).isEqualTo("EXCEPCION:EXCESO");
		assertThat(linea(jdbc, lote, 2)).isEqualTo("EXCEPCION:EXCESO");
		assertThat(estado(jdbc, matricula)).isEqualTo("PENDIENTE");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(estadoLote(jdbc, lote)).isEqualTo("APLICADO");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'RECAUDACION_LINEA_EXCEPCION'")).isEqualTo(2);
	}

	@Test
	void codigoConDigitoErradoEsCodigoInvalido() {
		Long lote = aplicado(archivo().linea(FECHA, codigoErrado(f.mateo()), "", "450.00", "PEN", "BCP40041")
				.linea(FECHA, "12345678901", "", "450.00", "PEN", "BCP40042"));

		assertThat(linea(jdbc, lote, 1)).isEqualTo("EXCEPCION:CODIGO_INVALIDO");
		assertThat(linea(jdbc, lote, 2)).isEqualTo("EXCEPCION:CODIGO_INVALIDO");
		assertThat(contar(jdbc, "pago")).isZero();
	}

	/** F11/F12: la misma operación (en caja, en otro archivo o dos veces en el archivo) nunca se registra dos veces. */
	@Test
	void operacionYaRegistradaQuedaEnExcepcion() {
		Archivo banco = archivo().pago(f.sebastian(), null, "350.00", "BCP40051")
				.pago(f.mateo(), null, "350.00", "BCP40052").pago(f.valeria(), null, "350.00", "BCP40052");
		Long lote = registrar(servicio, ADMINISTRACION, banco);
		// Antes de que se confirme, la cajera registra un «Yape» con la operación del banco de Sebastián.
		como(CAJA);
		cobro.cobrar(digital(f.flores(), List.of(cuota(jdbc, f.sebastian(), "MAT-2027")), MedioPago.YAPE, "BCP40051",
				"350.00"));

		confirmar(servicio, jdbc, PROMOTORIA, lote, banco.total().toPlainString());

		assertThat(linea(jdbc, lote, 1)).isEqualTo("EXCEPCION:OPERACION_DUPLICADA");
		assertThat(linea(jdbc, lote, 2)).isEqualTo("APLICADA");
		assertThat(linea(jdbc, lote, 3)).isEqualTo("EXCEPCION:OPERACION_DUPLICADA");
		assertThat(contar(jdbc, "pago WHERE numero_operacion = 'BCP40052'")).isEqualTo(1);
		assertThat(estado(jdbc, cuota(jdbc, f.valeria(), "MAT-2027"))).isEqualTo("PENDIENTE");
	}

	@Test
	void usdEsExcepcion() {
		Long lote = aplicado(archivo().linea(FECHA, pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago.deAlumno(
				f.mateo()), "", "120.00", "USD", "BCP40061"));

		assertThat(linea(jdbc, lote, 1)).isEqualTo("EXCEPCION:MONEDA");
		assertThat(contar(jdbc, "pago")).isZero();
	}

	@Test
	void boletaANombreDelResponsableDePago() {
		Long lote = aplicado(archivo().pago(f.valeria(), null, "350.00", "BCP40071"));

		Map<String, Object> boleta = jdbc.queryForMap("SELECT c.tipo, c.receptor_numero_documento, c.fecha_emision, "
				+ "c.total FROM comprobante c JOIN pago p ON p.comprobante_id = c.id WHERE p.id = ?",
				pagoDeLinea(lote, 1).get("id"));
		assertThat(boleta).containsEntry("tipo", "BOLETA").containsEntry("receptor_numero_documento",
				EscenarioEscolar.DNI_ROSA);
		assertThat(boleta.get("fecha_emision").toString()).isEqualTo("2026-10-02");
		assertThat((BigDecimal) boleta.get("total")).isEqualByComparingTo("350.00");
		assertThat(pagoDeLinea(lote, 1).get("fecha").toString()).isEqualTo(FECHA);
	}

	/** 55 líneas (dos tandas de 50): todas se aplican una vez; volver a aplicar el lote no duplica nada. */
	@Test
	void tandasDeCincuentaSonIdempotentes() {
		Archivo banco = archivo();
		List<Long> hijos = List.of(f.mateo(), f.valeria(), f.sebastian());
		for (int i = 0; i < 55; i++) {
			banco.pago(hijos.get(i % 3), null, "10.00", "BCP5" + String.format("%04d", i));
		}

		Long lote = aplicado(banco);

		assertThat(estadoLote(jdbc, lote)).isEqualTo("APLICADO");
		assertThat(contar(jdbc, "linea_recaudacion WHERE estado = 'APLICADA'")).isEqualTo(55);
		assertThat(contar(jdbc, "pago WHERE origen = 'RECAUDACION'")).isEqualTo(55);
		AplicadorRecaudacion.Resumen otraVez = aplicador.aplicar(1L, lote);
		assertThat(otraVez.loteAplicado()).isTrue();
		assertThat(contar(jdbc, "pago")).isEqualTo(55);
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT clave_idempotencia) FROM pago", Long.class)).isEqualTo(55);
		assertThat(pagado(jdbc, cuota(jdbc, f.mateo(), "MAT-2027"))).isEqualByComparingTo("190.00");
		assertThat(contar(jdbc, "caja_diaria WHERE canal = 'RECAUDACION'")).isEqualTo(1);
	}

	/** Un lote que nadie confirmó no se aplica, aunque alguien llame al proceso. */
	@Test
	void loteSinConfirmarNoSeAplica() {
		Long lote = registrar(servicio, ADMINISTRACION, archivo().pago(f.mateo(), null, "350.00", "BCP40081"));
		SecurityContextHolder.clearContext();

		AplicadorRecaudacion.Resumen resumen = aplicador.aplicar(1L, lote);

		assertThat(resumen.aplicadas()).isZero();
		assertThat(resumen.pendientes()).isEqualTo(1);
		assertThat(estadoLote(jdbc, lote)).isEqualTo("CARGADO");
		assertThat(contar(jdbc, "pago")).isZero();
	}
}
