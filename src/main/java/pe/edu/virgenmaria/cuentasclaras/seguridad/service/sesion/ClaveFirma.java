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

	/**
	 * Correcciones del sprint 7 (QA-S7-1): Promotoría resuelve la alerta «Faltan filas» de ese respaldo. Sprint 8, tanda 3
	 * (V30): la de SU colegio ({@code respaldo:{id}:{colegio}:RESUELTO}).
	 */
	public static String respaldoResuelto(Long respaldoId, Long colegioId) {
		return "respaldo:" + id(respaldoId) + ":" + id(colegioId) + ":RESUELTO";
	}

	/**
	 * Sprint 8, tanda 1 (sección 3.3): el operador firma, en el colegio de plataforma, la primera Promotoría de ESE colegio.
	 * {@code intento}: cuántas firmas de la primera Promotoría de ese colegio hay antes de esta, más uno (la firma es única
	 * por clave y «Completar alta» firma de nuevo; el trigger exige la del último intento).
	 */
	public static String primeraPromotoria(Long colegioId, long intento) {
		return "primera_promotoria:" + id(colegioId) + ":" + intento;
	}

	/** Sprint 8, tanda 1 (sección 3.3): el operador corrige el contacto del titular (la cuenta, con su versión actual). */
	public static String titularContacto(Long colegioId, Long usuarioId, long version) {
		return "titular_contacto:" + id(colegioId) + ":" + id(usuarioId) + ":" + version;
	}

	/** Sprint 8, tanda 1 (sección 3.4): Promotoría pasa su colegio a producción. */
	public static String colegioProduccion(Long colegioId) {
		return "colegio:" + id(colegioId) + ":PRODUCCION";
	}

	/**
	 * Sprint 8, tanda 1 (H10): el operador registra ({@code version} 0) o cambia (la versión que tendrá la fila) el correo
	 * externo de control de ese colegio, firmando en el colegio de plataforma.
	 */
	public static String configuracionColegio(Long colegioId, String clave, long version) {
		return "configuracion_colegio:" + id(colegioId) + ":" + Objects.requireNonNull(clave, "clave") + ":" + version;
	}

	/**
	 * Sprint 8, tanda 2 (sección 3.6): quien propone un conector firma su propuesta (la clave pública opaca del conector,
	 * que aún no tiene id). trg_conector_colegio_nace la exige: con la clave de cc_app nadie propone a nombre de otro.
	 */
	public static String conectorPropuesto(String clavePublica) {
		return "conector_colegio:" + java.util.UUID.fromString(Objects.requireNonNull(clavePublica, "clavePublica")) + ":PROPUESTO";
	}

	/** Sprint 8, tanda 2: quien rechaza el cambio de un conector lo firma (trg_conector_colegio_estado). */
	public static String conectorRechazado(Long conectorId) {
		return "conector_colegio:" + id(conectorId) + ":RECHAZADO";
	}

	/**
	 * Sprint 8, tanda 2 (sección 3.5): Promotoría acepta que el colegio empieza sin deudas anteriores (no hay lote de saldo
	 * inicial). La firma es el registro: única por colegio y clave, de solo inserción.
	 */
	public static String colegioSinSaldoInicial(Long colegioId) {
		return "colegio:" + id(colegioId) + ":SIN_SALDO_INICIAL";
	}

	/**
	 * Sprint 8, tanda 2 (sección 3.5): Promotoría acepta usar el comprobante simulado (o el de pruebas del OSE) y emitir su
	 * comprobante legal aparte.
	 */
	public static String colegioOseSimulado(Long colegioId) {
		return "colegio:" + id(colegioId) + ":OSE_SIMULADO";
	}

	private static Long id(Long id) {
		return Objects.requireNonNull(id, "id");
	}
}
