package pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Claves canónicas de las firmas (sprint 7, tanda 2; tabla de la sección 3.4). Son LAS MISMAS cadenas que arman los
 * triggers de {@code scripts/mysql/03-triggers.sql} con {@code CONCAT}: si una cambia aquí, cambia allá
 * ({@code ClaveFirmaTest} compara cada una con el archivo).
 */
public final class ClaveFirma {

	/** {@code DATE_FORMAT(x, '%Y%m%d%H%i%s%f')} de MySQL (microsegundos, la precisión de {@code DATETIME(6)}). */
	private static final DateTimeFormatter MOMENTO = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSSSS");

	private ClaveFirma() {
	}

	public static String solicitud(Long id, Enum<?> estado) {
		return "solicitud_cambio:" + id(id) + ":" + estado.name();
	}

	public static String descuento(Long id, Enum<?> estado) {
		return "descuento:" + id(id) + ":" + estado.name();
	}

	public static String cierreCaja(Long id, Enum<?> estado) {
		return "cierre_caja:" + id(id) + ":" + estado.name();
	}

	public static String planPension(Long id) {
		return "plan_pension:" + id(id) + ":APROBADO";
	}

	public static String loteSaldoInicialConfirmado(Long id) {
		return "lote_saldo_inicial:" + id(id) + ":CONFIRMADO";
	}

	/** Un lote se puede devolver varias veces: la clave lleva el momento de la devolución (al microsegundo). */
	public static String loteSaldoInicialDevuelto(Long id, LocalDateTime devueltoEn) {
		return "lote_saldo_inicial:" + id(id) + ":DEVUELTO:" + devueltoEn.truncatedTo(ChronoUnit.MICROS).format(MOMENTO);
	}

	public static String extracto(Long id, Enum<?> estado) {
		return "extracto_bancario:" + id(id) + ":" + estado.name();
	}

	public static String loteRecaudacion(Long id, Enum<?> estado) {
		return "lote_recaudacion:" + id(id) + ":" + estado.name();
	}

	public static String partida(Long id, Enum<?> estado) {
		return "partida_conciliacion:" + id(id) + ":" + estado.name();
	}

	/** Cada intento a ciegas del cierre mensual (el número que tendrá después de este intento). */
	public static String cierreMensual(Long id, int intento) {
		return "cierre_mensual_banco:" + id(id) + ":" + intento;
	}

	public static String feriado(Long id) {
		return "feriado:" + id(id) + ":APROBADO";
	}

	public static String avisoFamilia(Long id) {
		return "aviso_familia:" + id(id) + ":ATENDIDO";
	}

	public static String renovacion(Long id, Enum<?> estado) {
		return "renovacion_matricula:" + id(id) + ":" + estado.name();
	}

	/** {@code numero}: cuántas verificaciones tenía el pago antes de esta, más uno. */
	public static String verificacionPago(Long pagoId, long numero) {
		return "verificacion_bancaria:PAGO:" + id(pagoId) + ":" + numero;
	}

	/** {@code numero}: cuántas verificaciones tenía el depósito antes de esta, más uno. */
	public static String verificacionDeposito(Long depositoId, long numero) {
		return "verificacion_bancaria:DEPOSITO:" + id(depositoId) + ":" + numero;
	}

	public static String llamadaControl(LocalDate semana, Long familiaId, int intento) {
		return "llamada_control:" + Objects.requireNonNull(semana, "semana") + ":" + id(familiaId) + ":" + intento;
	}

	public static String delegacionLlamada(LocalDate semana) {
		return "delegacion_llamada:" + Objects.requireNonNull(semana, "semana");
	}

	private static Long id(Long id) {
		return Objects.requireNonNull(id, "id");
	}
}
