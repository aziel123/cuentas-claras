package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.VistaPreviaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Archivos de recaudación para las pruebas (sprint 4, tanda 2), en el formato genérico. Con el reloj de
 * {@link ConfiguracionRelojAjustable} hoy es el 02/10/2026: los pagos son del 01/10/2026.
 */
public final class EscenarioRecaudacion {

	public static final String FECHA = "2026-10-01";

	public static final String CABECERA = "fecha_pago;codigo_alumno;referencia_deuda;monto;moneda;numero_operacion;canal";

	private EscenarioRecaudacion() {
	}

	/** Un archivo del banco en construcción. */
	public static final class Archivo {

		private final List<String> lineas = new ArrayList<>();

		private BigDecimal total = BigDecimal.ZERO.setScale(2);

		/** Pago con el código del alumno (y la cuota, si {@code cuotaId} no es null). */
		public Archivo pago(Long alumnoId, Long cuotaId, String monto, String operacion) {
			return linea(FECHA, CodigoPago.deAlumno(alumnoId), cuotaId == null ? "" : CodigoPago.deCuota(cuotaId), monto,
					"PEN", operacion);
		}

		public Archivo linea(String fecha, String codigo, String referencia, String monto, String moneda,
				String operacion) {
			lineas.add(fecha + ";" + codigo + ";" + referencia + ";" + monto + ";" + moneda + ";" + operacion + ";Agente");
			total = total.add(new BigDecimal(monto));
			return this;
		}

		public BigDecimal total() {
			return total;
		}

		public int lineas() {
			return lineas.size();
		}

		/** El CSV con el pie {@code TOTAL;<suma>;<cantidad>}. */
		public byte[] csv() {
			StringBuilder texto = new StringBuilder(CABECERA).append('\n');
			lineas.forEach(l -> texto.append(l).append('\n'));
			texto.append("TOTAL;").append(total.toPlainString()).append(';').append(lineas.size()).append('\n');
			return texto.toString().getBytes(StandardCharsets.UTF_8);
		}
	}

	public static Archivo archivo() {
		return new Archivo();
	}

	/** El código del alumno con el dígito verificador cambiado (un error de tipeo en la ventanilla del banco). */
	public static String codigoErrado(Long alumnoId) {
		String codigo = CodigoPago.deAlumno(alumnoId);
		char ultimo = codigo.charAt(7);
		return codigo.substring(0, 7) + (char) ('0' + ((ultimo - '0' + 1) % 10));
	}

	/** Sube y registra el archivo como {@code quien} (Administración). Deja la sesión en {@code quien}. */
	public static Long registrar(ServicioRecaudacion servicio, UsuarioAutenticado quien, Archivo archivo) {
		return registrar(servicio, quien, "banco-" + System.nanoTime() + ".csv", archivo.csv());
	}

	public static Long registrar(ServicioRecaudacion servicio, UsuarioAutenticado quien, String nombre, byte[] contenido) {
		UsuariosDePrueba.iniciarSesion(quien);
		VistaPreviaRecaudacion previa = servicio.previsualizar(nombre, contenido, contenido.length);
		return servicio.registrar(previa, previa.token());
	}

	/** Confirma como {@code quien} con el total que escribe a ciegas. Deja la sesión en {@code quien}. */
	public static void confirmar(ServicioRecaudacion servicio, JdbcTemplate jdbc, UsuarioAutenticado quien, Long lote,
			String total) {
		UsuariosDePrueba.iniciarSesion(quien);
		servicio.confirmar(lote, version(jdbc, lote), new BigDecimal(total));
	}

	public static Long version(JdbcTemplate jdbc, Long lote) {
		return jdbc.queryForObject("SELECT version FROM lote_recaudacion WHERE id = ?", Long.class, lote);
	}

	public static String estadoLote(JdbcTemplate jdbc, Long lote) {
		return jdbc.queryForObject("SELECT estado FROM lote_recaudacion WHERE id = ?", String.class, lote);
	}

	/** Estado (y motivo, si lo hay) de la línea {@code numero} del lote: «APLICADA» o «EXCEPCION:EXCESO». */
	public static String linea(JdbcTemplate jdbc, Long lote, int numero) {
		return jdbc.queryForObject("SELECT CASE WHEN motivo_excepcion IS NULL THEN estado ELSE CONCAT(estado, ':', "
				+ "motivo_excepcion) END FROM linea_recaudacion WHERE lote_id = ? AND numero = ?", String.class, lote, numero);
	}

	public static Long idLinea(JdbcTemplate jdbc, Long lote, int numero) {
		return jdbc.queryForObject("SELECT id FROM linea_recaudacion WHERE lote_id = ? AND numero = ?", Long.class, lote,
				numero);
	}
}
