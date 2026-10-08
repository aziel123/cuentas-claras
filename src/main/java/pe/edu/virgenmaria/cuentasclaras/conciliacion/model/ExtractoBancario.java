package pe.edu.virgenmaria.cuentasclaras.conciliacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.MuestraAlAzar;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * Extracto bancario: un tramo de días COMPLETOS de una cuenta (sprint 4, tanda 3). Nada se borra.
 * <ul>
 *   <li>Es una CADENA por cuenta: el extracto {@code n+1} empieza el día siguiente al fin del {@code n} y con su saldo
 *       final (trigger), y su saldo final es el inicial más abonos menos cargos (CHECK). La cuenta, la secuencia, el
 *       archivo, las fechas y los saldos no cambian ({@code updatable = false} y GRANT por columna).</li>
 *   <li>Lo sube Administración (CARGADO) con el archivo original y su SHA-256, y lo confirma OTRA persona (Promotoría o
 *       Dirección) escribiendo a ciegas el saldo final que ve en su app del banco, en este extracto o en uno posterior
 *       de la cadena («el lunes sella el fin de semana»). Con {@code intentos-confirmacion} saldos distintos queda
 *       RECHAZADO.</li>
 *   <li>{@code secuencia_vigente} impide dos extractos vigentes en la misma posición (UNIQUE).</li>
 * </ul>
 */
@Entity
@Table(name = "extracto_bancario")
public class ExtractoBancario extends BaseEntity {

	/** Tope de los saldos ({@code DECIMAL(14,2)}, hallazgo 12 del diseño). */
	public static final BigDecimal MAXIMO_SALDO = new BigDecimal("999999999999.99");

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cuenta_id", nullable = false, updatable = false)
	private CuentaBancaria cuenta;

	@Column(nullable = false, updatable = false)
	private int secuencia;

	@Column(name = "anterior_id", updatable = false)
	private Long anteriorId;

	@Column(name = "archivo_id", nullable = false, updatable = false)
	private Long archivoId;

	@Column(name = "archivo_sha256", nullable = false, updatable = false, length = 64)
	private String archivoSha256;

	@Column(nullable = false, updatable = false, length = 30)
	private String formato;

	@Column(nullable = false, updatable = false)
	private LocalDate desde;

	@Column(nullable = false, updatable = false)
	private LocalDate hasta;

	@Column(name = "saldo_inicial", nullable = false, updatable = false, precision = 14, scale = 2)
	private BigDecimal saldoInicial;

	@Column(name = "total_abonos", nullable = false, updatable = false, precision = 14, scale = 2)
	private BigDecimal totalAbonos;

	@Column(name = "total_cargos", nullable = false, updatable = false, precision = 14, scale = 2)
	private BigDecimal totalCargos;

	@Column(name = "saldo_final", nullable = false, updatable = false, precision = 14, scale = 2)
	private BigDecimal saldoFinal;

	@Column(nullable = false, updatable = false)
	private int movimientos;

	/**
	 * S4-A1 (correcciones del sprint 4): los números de los movimientos que ve quien confirma, elegidos UNA vez al
	 * registrar con un azar que quien sube no predice; nunca todos. Inmutable (sin GRANT UPDATE).
	 */
	@Column(updatable = false, length = 100)
	private String muestra;

	/** S4-A2: la semilla secreta del muestreo diario de este extracto (antes era la fecha, predecible). Inmutable. */
	@Column(name = "semilla_muestreo", updatable = false)
	private Long semillaMuestreo;

	// --- Lo que puede cambiar (GRANT UPDATE por columna; trg_extracto_bancario_estado vigila la confirmación) ---

	@Column(name = "secuencia_vigente")
	private Integer secuenciaVigente;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoExtracto estado;

	@Column(name = "intentos_confirmacion", nullable = false)
	private int intentosConfirmacion;

	@Column(name = "saldo_final_ciego", precision = 14, scale = 2)
	private BigDecimal saldoFinalCiego;

	@Column(name = "confirmacion_extracto_id")
	private Long confirmacionExtractoId;

	@Column(name = "confirmado_por", length = 60)
	private String confirmadoPor;

	@Column(name = "confirmado_en")
	private LocalDateTime confirmadoEn;

	@Column(name = "rechazado_por", length = 60)
	private String rechazadoPor;

	@Column(name = "rechazado_en")
	private LocalDateTime rechazadoEn;

	@Column(name = "motivo_rechazo", length = 500)
	private String motivoRechazo;

	protected ExtractoBancario() {
		// requerido por JPA
	}

	/**
	 * Extracto nuevo (CARGADO). {@code anterior}: el último vigente de la cuenta ({@code null} si es el primero); este
	 * empieza el día siguiente a su fin y con su saldo final.
	 */
	public static ExtractoBancario registrar(CuentaBancaria cuenta, ExtractoBancario anterior, Long archivoId,
			String archivoSha256, String formato, LocalDate desde, LocalDate hasta, BigDecimal saldoInicial,
			BigDecimal totalAbonos, BigDecimal totalCargos, int movimientos, int muestreo) {
		Objects.requireNonNull(desde, "desde");
		Objects.requireNonNull(hasta, "hasta");
		if (desde.isAfter(hasta) || movimientos < 0) {
			throw new IllegalArgumentException("Un extracto tiene sus fechas en orden");
		}
		ExtractoBancario e = new ExtractoBancario();
		e.cuenta = Objects.requireNonNull(cuenta, "cuenta");
		if (anterior != null) {
			if (!anterior.getEstado().vigente() || !desde.equals(anterior.hasta.plusDays(1))
					|| !Dinero.iguales(saldoInicial, anterior.saldoFinal)) {
				throw new IllegalArgumentException("El extracto debe continuar al anterior (fechas y saldo)");
			}
			e.secuencia = anterior.secuencia + 1;
			e.anteriorId = anterior.getId();
		}
		else {
			e.secuencia = 1;
		}
		e.secuenciaVigente = e.secuencia;
		e.archivoId = Objects.requireNonNull(archivoId, "archivoId");
		e.archivoSha256 = Objects.requireNonNull(archivoSha256, "archivoSha256");
		e.formato = Objects.requireNonNull(formato, "formato");
		e.desde = desde;
		e.hasta = hasta;
		e.saldoInicial = saldo(saldoInicial);
		e.totalAbonos = saldo(totalAbonos);
		e.totalCargos = saldo(totalCargos);
		if (e.totalAbonos.signum() < 0 || e.totalCargos.signum() < 0) {
			throw new IllegalArgumentException("Los totales de abonos y cargos no son negativos");
		}
		e.saldoFinal = saldo(e.saldoInicial.add(e.totalAbonos).subtract(e.totalCargos));
		e.movimientos = movimientos;
		e.muestra = MuestraAlAzar.elegir(movimientos, muestreo);
		e.semillaMuestreo = MuestraAlAzar.semilla();
		e.estado = EstadoExtracto.CARGADO;
		e.intentosConfirmacion = 0;
		return e;
	}

	/** Los números de los movimientos de la muestra fija (vacía si el extracto tiene un solo movimiento). */
	public java.util.List<Integer> numerosMuestra() {
		return MuestraAlAzar.numeros(muestra);
	}

	public String getMuestra() {
		return muestra;
	}

	public Long getSemillaMuestreo() {
		return semillaMuestreo;
	}

	private static BigDecimal saldo(BigDecimal monto) {
		BigDecimal normalizado = Dinero.normalizar(monto);
		if (normalizado.abs().compareTo(MAXIMO_SALDO) > 0) {
			throw new ReglaNegocioException("El saldo del extracto pasa el máximo permitido.");
		}
		return normalizado;
	}

	/**
	 * Quien confirma escribió a ciegas el saldo final CORRECTO de este extracto (el último pendiente de la cadena): se
	 * guarda una sola vez (CHECK: debe ser igual al saldo final). Se escribe ANTES de confirmar la cadena.
	 */
	public void escribirSaldoCiego(BigDecimal saldoVisto) {
		exigirCargado();
		if (saldoFinalCiego != null) {
			throw new IllegalStateException("El saldo a ciegas ya se escribió");
		}
		if (saldoVisto == null || !Dinero.iguales(saldoVisto, saldoFinal)) {
			throw new IllegalArgumentException("El saldo escrito no coincide con el del extracto");
		}
		saldoFinalCiego = saldoFinal;
	}

	/**
	 * CONFIRMADO por {@code por} (nunca quien lo subió), con el saldo a ciegas escrito en {@code confirmacionId} (este
	 * mismo o uno posterior de la cadena). En la base, el trigger exige además el anterior ya confirmado y los movimientos
	 * completos.
	 */
	public void confirmar(String por, Long confirmacionId, LocalDateTime ahora) {
		exigirCargado();
		if (por == null || por.equals(getCreadoPor())) {
			throw new IllegalStateException("Quien subió el extracto no lo confirma");
		}
		estado = EstadoExtracto.CONFIRMADO;
		confirmacionExtractoId = Objects.requireNonNull(confirmacionId, "confirmacionId");
		confirmadoPor = por;
		confirmadoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/**
	 * Un saldo a ciegas distinto: suma un intento. Al llegar a {@code maximo}, el extracto queda RECHAZADO y sale de la
	 * cadena.
	 *
	 * @return si quedó RECHAZADO
	 */
	public boolean intentoFallido(int maximo, String por, LocalDateTime ahora) {
		exigirCargado();
		intentosConfirmacion++;
		if (intentosConfirmacion >= maximo) {
			salir(EstadoExtracto.RECHAZADO, por, ahora, intentosConfirmacion + " saldos escritos a ciegas no coinciden con "
					+ "el saldo final del extracto: no se confirma ni se concilia.");
			return true;
		}
		return false;
	}

	/** Quien lo subió lo descarta antes de que se confirme (motivo de 10 a 500 caracteres). */
	public void descartar(String por, String motivo, LocalDateTime ahora) {
		exigirCargado();
		salir(EstadoExtracto.DESCARTADO, por, ahora, Motivo.exigir(motivo));
	}

	private void salir(EstadoExtracto nuevo, String por, LocalDateTime ahora, String motivo) {
		estado = nuevo;
		secuenciaVigente = null;
		rechazadoPor = Objects.requireNonNull(por, "por");
		rechazadoEn = Objects.requireNonNull(ahora, "ahora");
		motivoRechazo = motivo;
	}

	public int intentosRestantes(int maximo) {
		return Math.max(0, maximo - intentosConfirmacion);
	}

	private void exigirCargado() {
		if (estado != EstadoExtracto.CARGADO) {
			throw new ReglaNegocioException("El extracto ya no está por confirmar (está "
					+ estado.etiqueta().toLowerCase(Locale.ROOT) + ").");
		}
	}

	/** Si una fecha está dentro de los días que cubre. */
	public boolean cubre(LocalDate fecha) {
		return fecha != null && !fecha.isBefore(desde) && !fecha.isAfter(hasta);
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los extractos no se borran: se descartan o se rechazan.");
	}

	public CuentaBancaria getCuenta() {
		return cuenta;
	}

	public int getSecuencia() {
		return secuencia;
	}

	public Long getAnteriorId() {
		return anteriorId;
	}

	public Long getArchivoId() {
		return archivoId;
	}

	public String getArchivoSha256() {
		return archivoSha256;
	}

	public String getFormato() {
		return formato;
	}

	public LocalDate getDesde() {
		return desde;
	}

	public LocalDate getHasta() {
		return hasta;
	}

	public BigDecimal getSaldoInicial() {
		return saldoInicial;
	}

	public BigDecimal getTotalAbonos() {
		return totalAbonos;
	}

	public BigDecimal getTotalCargos() {
		return totalCargos;
	}

	public BigDecimal getSaldoFinal() {
		return saldoFinal;
	}

	public int getMovimientos() {
		return movimientos;
	}

	public Integer getSecuenciaVigente() {
		return secuenciaVigente;
	}

	public EstadoExtracto getEstado() {
		return estado;
	}

	public int getIntentosConfirmacion() {
		return intentosConfirmacion;
	}

	public BigDecimal getSaldoFinalCiego() {
		return saldoFinalCiego;
	}

	public Long getConfirmacionExtractoId() {
		return confirmacionExtractoId;
	}

	public String getConfirmadoPor() {
		return confirmadoPor;
	}

	public LocalDateTime getConfirmadoEn() {
		return confirmadoEn;
	}

	public String getRechazadoPor() {
		return rechazadoPor;
	}

	public LocalDateTime getRechazadoEn() {
		return rechazadoEn;
	}

	public String getMotivoRechazo() {
		return motivoRechazo;
	}
}
