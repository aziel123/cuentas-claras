package pe.edu.virgenmaria.cuentasclaras.conciliacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Cierre bancario mensual a ciegas (sprint 5, tanda 3; decisión 60 y G22). Lo crea {@code sistema.conciliacion} con los
 * totales de abonos y cargos del mes y el saldo al cierre de su último día, calculados de los extractos CONFIRMADOS (en
 * MySQL lo comprueba trg_cierre_mensual_banco_nace). Una persona de Promotoría o Dirección que no subió ni confirmó
 * extractos de ese mes escribe A CIEGAS los tres números del estado de cuenta oficial del banco. Un abono inventado y
 * tapado con cargos de montos distintos, o compensado en un extracto de varios días, cambia los totales del mes aunque el
 * saldo cuadre.
 * <p>
 * Los totales, la cuenta y el mes no cambian (GRANT por columna: 1143). Un cierre resuelto no cambia
 * (trg_cierre_mensual_banco_estado).
 */
@Entity
@Table(name = "cierre_mensual_banco")
public class CierreMensualBanco extends BaseEntity {

	/** Intentos a ciegas antes de quedar en DISCREPANCIA. */
	public static final int INTENTOS_MAXIMOS = 2;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cuenta_id", nullable = false, updatable = false)
	private CuentaBancaria cuenta;

	@Column(nullable = false, updatable = false)
	private int anio;

	@Column(nullable = false, updatable = false)
	private int mes;

	@Column(name = "total_abonos", nullable = false, updatable = false, precision = 14, scale = 2)
	private BigDecimal totalAbonos;

	@Column(name = "total_cargos", nullable = false, updatable = false, precision = 14, scale = 2)
	private BigDecimal totalCargos;

	@Column(name = "saldo_final", nullable = false, updatable = false, precision = 14, scale = 2)
	private BigDecimal saldoFinal;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoCierreMensual estado = EstadoCierreMensual.ABIERTO;

	@Column(nullable = false)
	private int intentos;

	@Column(name = "abonos_ciego", precision = 14, scale = 2)
	private BigDecimal abonosCiego;

	@Column(name = "cargos_ciego", precision = 14, scale = 2)
	private BigDecimal cargosCiego;

	@Column(name = "saldo_ciego", precision = 14, scale = 2)
	private BigDecimal saldoCiego;

	@Column(name = "registrado_por", length = 60)
	private String registradoPor;

	@Column(name = "registrado_en")
	private LocalDateTime registradoEn;

	protected CierreMensualBanco() {
	}

	public static CierreMensualBanco nuevo(CuentaBancaria cuenta, YearMonth periodo, BigDecimal abonos, BigDecimal cargos,
			BigDecimal saldo) {
		CierreMensualBanco c = new CierreMensualBanco();
		c.cuenta = Objects.requireNonNull(cuenta, "cuenta");
		c.anio = periodo.getYear();
		c.mes = periodo.getMonthValue();
		c.totalAbonos = Objects.requireNonNull(abonos, "abonos");
		c.totalCargos = Objects.requireNonNull(cargos, "cargos");
		c.saldoFinal = Objects.requireNonNull(saldo, "saldo");
		return c;
	}

	/** Si los tres números escritos a ciegas coinciden con los calculados (compareTo: 10.5 = 10.50). */
	public boolean coincide(BigDecimal abonos, BigDecimal cargos, BigDecimal saldo) {
		return abonos.compareTo(totalAbonos) == 0 && cargos.compareTo(totalCargos) == 0 && saldo.compareTo(saldoFinal) == 0;
	}

	/**
	 * Un intento a ciegas: suma un intento y guarda lo escrito. Si coincide queda CUADRADO; si no, y es el último, queda
	 * en DISCREPANCIA.
	 */
	public EstadoCierreMensual intentar(BigDecimal abonos, BigDecimal cargos, BigDecimal saldo, String por,
			LocalDateTime cuando) {
		if (estado != EstadoCierreMensual.ABIERTO) {
			throw new IllegalStateException("El cierre ya está resuelto");
		}
		intentos++;
		abonosCiego = abonos;
		cargosCiego = cargos;
		saldoCiego = saldo;
		registradoPor = Objects.requireNonNull(por, "por");
		registradoEn = Objects.requireNonNull(cuando, "cuando");
		if (coincide(abonos, cargos, saldo)) {
			estado = EstadoCierreMensual.CUADRADO;
		}
		else if (intentos >= INTENTOS_MAXIMOS) {
			estado = EstadoCierreMensual.DISCREPANCIA;
		}
		return estado;
	}

	public int intentosRestantes() {
		return Math.max(0, INTENTOS_MAXIMOS - intentos);
	}

	public YearMonth periodo() {
		return YearMonth.of(anio, mes);
	}

	public CuentaBancaria getCuenta() {
		return cuenta;
	}

	public int getAnio() {
		return anio;
	}

	public int getMes() {
		return mes;
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

	public EstadoCierreMensual getEstado() {
		return estado;
	}

	public int getIntentos() {
		return intentos;
	}

	public BigDecimal getAbonosCiego() {
		return abonosCiego;
	}

	public BigDecimal getCargosCiego() {
		return cargosCiego;
	}

	public BigDecimal getSaldoCiego() {
		return saldoCiego;
	}

	public String getRegistradoPor() {
		return registradoPor;
	}

	public LocalDateTime getRegistradoEn() {
		return registradoEn;
	}
}
