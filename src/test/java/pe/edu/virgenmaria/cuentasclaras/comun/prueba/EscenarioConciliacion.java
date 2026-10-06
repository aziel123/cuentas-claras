package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.ConfirmacionExtractoVista;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.CuentaRequest;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.VistaPreviaExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.BancoCuenta;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Extractos del banco para las pruebas (sprint 4, tanda 3), en el formato genérico: la línea {@code cuenta;<número>},
 * la cabecera y cada movimiento con su saldo corrido (se calcula solo).
 */
public final class EscenarioConciliacion {

	public static final String CUENTA = "191-2345678-0-12";

	public static final String CABECERA = "fecha;descripcion;numero_operacion;referencia;cargo;abono;saldo";

	private EscenarioConciliacion() {
	}

	/** Un extracto en construcción. */
	public static final class Extracto {

		private final String cuenta;

		private final List<String> lineas = new ArrayList<>();

		private BigDecimal saldo;

		private Extracto(String cuenta, String saldoInicial) {
			this.cuenta = cuenta;
			this.saldo = new BigDecimal(saldoInicial);
		}

		public Extracto abono(String fecha, String descripcion, String operacion, String monto) {
			return fila(fecha, descripcion, operacion, "", null, monto);
		}

		public Extracto abonoConReferencia(String fecha, String descripcion, String referencia, String monto) {
			return fila(fecha, descripcion, "", referencia, null, monto);
		}

		public Extracto cargo(String fecha, String descripcion, String operacion, String monto) {
			return fila(fecha, descripcion, operacion, "", monto, null);
		}

		private Extracto fila(String fecha, String descripcion, String operacion, String referencia, String cargo,
				String abono) {
			saldo = cargo != null ? saldo.subtract(new BigDecimal(cargo)) : saldo.add(new BigDecimal(abono));
			lineas.add(fecha + ";" + descripcion + ";" + (operacion == null ? "" : operacion) + ";" + referencia + ";"
					+ (cargo == null ? "" : cargo) + ";" + (abono == null ? "" : abono) + ";" + saldo.toPlainString());
			return this;
		}

		/** Una fila escrita a mano (por ejemplo, con un saldo que no cuadra). */
		public Extracto filaCruda(String fila) {
			lineas.add(fila);
			return this;
		}

		public BigDecimal saldoFinal() {
			return saldo;
		}

		public byte[] csv() {
			StringBuilder texto = new StringBuilder("cuenta;").append(cuenta).append('\n').append(CABECERA).append('\n');
			lineas.forEach(l -> texto.append(l).append('\n'));
			return texto.toString().getBytes(StandardCharsets.UTF_8);
		}
	}

	public static Extracto extracto(String saldoInicial) {
		return new Extracto(CUENTA, saldoInicial);
	}

	public static Extracto extractoDe(String cuenta, String saldoInicial) {
		return new Extracto(cuenta, saldoInicial);
	}

	/** Promotoría registra la cuenta del colegio. Deja la sesión en Promotoría. */
	public static Long cuenta(ServicioCuentasBancarias cuentas) {
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		return cuentas.registrar(new CuentaRequest(BancoCuenta.BCP, CUENTA, "BCP soles cobranza"));
	}

	/** Sube y registra el extracto como {@code quien} (Administración). Deja la sesión en {@code quien}. */
	public static Long registrar(ServicioExtractos servicio, UsuarioAutenticado quien, Extracto extracto) {
		UsuariosDePrueba.iniciarSesion(quien);
		byte[] contenido = extracto.csv();
		VistaPreviaExtracto previa = servicio.previsualizar("extracto-" + System.nanoTime() + ".csv", contenido,
				contenido.length);
		return servicio.registrar(previa, previa.token());
	}

	/** La vista previa (sin registrar) como {@code quien}. */
	public static VistaPreviaExtracto previa(ServicioExtractos servicio, UsuarioAutenticado quien, Extracto extracto) {
		UsuariosDePrueba.iniciarSesion(quien);
		byte[] contenido = extracto.csv();
		return servicio.previsualizar("extracto-" + System.nanoTime() + ".csv", contenido, contenido.length);
	}

	/** Confirma a ciegas como {@code quien} con el saldo que escribe. Deja la sesión en {@code quien}. */
	public static void confirmar(ServicioExtractos servicio, UsuarioAutenticado quien, Long cuentaId, String saldo) {
		UsuariosDePrueba.iniciarSesion(quien);
		ConfirmacionExtractoVista vista = servicio.paraConfirmar(cuentaId);
		servicio.confirmar(cuentaId, vista.extractoId(), vista.version(), new BigDecimal(saldo));
	}

	public static String estadoExtracto(JdbcTemplate jdbc, Long extracto) {
		return jdbc.queryForObject("SELECT estado FROM extracto_bancario WHERE id = ?", String.class, extracto);
	}

	/** Las partidas vigentes como «REGLA ESTADO OBJETO» (por ejemplo «EXACTA CONFIRMADA PAGO»), por id. */
	public static List<String> partidas(JdbcTemplate jdbc) {
		return jdbc.queryForList("SELECT CONCAT(regla, ' ', estado, ' ', objeto_tipo) FROM partida_conciliacion "
				+ "WHERE estado <> 'DESCARTADA' ORDER BY id", String.class);
	}

	/** La verificación automática de ese pago, como «ORIGEN RESULTADO» o {@code null}. */
	public static String verificacionDePago(JdbcTemplate jdbc, Long pagoId) {
		List<String> filas = jdbc.queryForList("SELECT CONCAT(origen, ' ', resultado) FROM verificacion_bancaria "
				+ "WHERE pago_id = ?", String.class, pagoId);
		return filas.isEmpty() ? null : filas.getFirst();
	}
}
