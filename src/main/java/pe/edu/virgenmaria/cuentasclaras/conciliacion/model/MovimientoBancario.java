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
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Un movimiento del extracto tal como lo dio el banco. SOLO INSERCIÓN ({@code @Immutable} y, en MySQL, sin GRANT de
 * UPDATE ni DELETE), solo mientras su extracto está CARGADO y dentro de sus fechas (trigger).
 */
@Entity
@Immutable
@Table(name = "movimiento_bancario")
public class MovimientoBancario extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "extracto_id", nullable = false, updatable = false)
	private ExtractoBancario extracto;

	@Column(name = "cuenta_id", nullable = false, updatable = false)
	private Long cuentaId;

	@Column(nullable = false, updatable = false)
	private int numero;

	@Column(nullable = false, updatable = false)
	private LocalDate fecha;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private TipoMovimiento tipo;

	@Column(nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal monto;

	@Column(updatable = false, precision = 14, scale = 2)
	private BigDecimal saldo;

	@Column(nullable = false, updatable = false, length = 200)
	private String descripcion;

	@Column(name = "numero_operacion", updatable = false, length = 30)
	private String numeroOperacion;

	@Column(updatable = false, length = 60)
	private String referencia;

	protected MovimientoBancario() {
		// requerido por JPA
	}

	public static MovimientoBancario nuevo(ExtractoBancario extracto, int numero, LocalDate fecha, TipoMovimiento tipo,
			BigDecimal monto, BigDecimal saldo, String descripcion, String numeroOperacion, String referencia) {
		MovimientoBancario m = new MovimientoBancario();
		m.extracto = Objects.requireNonNull(extracto, "extracto");
		m.cuentaId = extracto.getCuenta().getId();
		if (numero < 1) {
			throw new IllegalArgumentException("El número del movimiento empieza en 1");
		}
		m.numero = numero;
		m.fecha = Objects.requireNonNull(fecha, "fecha");
		if (!extracto.cubre(fecha)) {
			throw new IllegalArgumentException("El movimiento debe estar dentro de las fechas del extracto");
		}
		m.tipo = Objects.requireNonNull(tipo, "tipo");
		m.monto = Dinero.normalizar(monto);
		if (m.monto.signum() <= 0) {
			throw new IllegalArgumentException("El monto de un movimiento es positivo");
		}
		m.saldo = saldo == null ? null : Dinero.normalizar(saldo);
		m.descripcion = Objects.requireNonNull(descripcion, "descripcion");
		m.numeroOperacion = numeroOperacion;
		m.referencia = referencia;
		return m;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los movimientos del banco no se borran.");
	}

	public ExtractoBancario getExtracto() {
		return extracto;
	}

	public Long getCuentaId() {
		return cuentaId;
	}

	public int getNumero() {
		return numero;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public TipoMovimiento getTipo() {
		return tipo;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public BigDecimal getSaldo() {
		return saldo;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public String getNumeroOperacion() {
		return numeroOperacion;
	}

	public String getReferencia() {
		return referencia;
	}

	public boolean abono() {
		return tipo == TipoMovimiento.ABONO;
	}
}
