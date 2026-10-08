package pe.edu.virgenmaria.cuentasclaras.pasarela.model;

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
import java.util.Objects;

/**
 * Línea de una liquidación: un cargo (o su reembolso o contracargo) con su comisión y el IGV de la comisión. Se ata a
 * su pago en línea por la operación canónica; una línea sin pago es CRÍTICA («la pasarela cobró algo que no
 * registramos»). Un cargo se liquida una sola vez (UNIQUE por tipo y operación). SOLO INSERCIÓN.
 */
@Entity
@Immutable
@Table(name = "liquidacion_linea")
public class LiquidacionLinea extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "liquidacion_id", nullable = false, updatable = false)
	private LiquidacionPasarela liquidacion;

	@Column(nullable = false, updatable = false)
	private int numero;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private TipoLineaLiquidacion tipo;

	@Column(nullable = false, updatable = false, length = 30)
	private String operacion;

	@Column(name = "pago_id", updatable = false)
	private Long pagoId;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal bruto;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal comision;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal igv;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal neto;

	protected LiquidacionLinea() {
		// requerido por JPA
	}

	public static LiquidacionLinea nueva(LiquidacionPasarela liquidacion, int numero, TipoLineaLiquidacion tipo,
			String operacion, Long pagoId, BigDecimal bruto, BigDecimal comision, BigDecimal igv) {
		LiquidacionLinea l = new LiquidacionLinea();
		l.liquidacion = Objects.requireNonNull(liquidacion, "liquidacion");
		if (numero < 1) {
			throw new IllegalArgumentException("Las líneas se numeran desde 1");
		}
		l.numero = numero;
		l.tipo = Objects.requireNonNull(tipo, "tipo");
		l.operacion = Objects.requireNonNull(operacion, "operacion");
		l.pagoId = pagoId;
		l.bruto = Dinero.normalizar(bruto);
		l.comision = Dinero.normalizar(comision);
		l.igv = Dinero.normalizar(igv);
		if (l.comision.signum() < 0 || l.igv.signum() < 0 || (tipo == TipoLineaLiquidacion.CARGO && l.bruto.signum() <= 0)
				|| ((tipo == TipoLineaLiquidacion.REEMBOLSO || tipo == TipoLineaLiquidacion.CONTRACARGO)
						&& l.bruto.signum() >= 0)) {
			throw new IllegalArgumentException("Montos de la línea de liquidación no válidos");
		}
		l.neto = l.bruto.subtract(l.comision).subtract(l.igv);
		return l;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las líneas de una liquidación no se borran.");
	}

	public LiquidacionPasarela getLiquidacion() {
		return liquidacion;
	}

	public int getNumero() {
		return numero;
	}

	public TipoLineaLiquidacion getTipo() {
		return tipo;
	}

	public String getOperacion() {
		return operacion;
	}

	public Long getPagoId() {
		return pagoId;
	}

	public BigDecimal getBruto() {
		return bruto;
	}

	public BigDecimal getComision() {
		return comision;
	}

	public BigDecimal getIgv() {
		return igv;
	}

	public BigDecimal getNeto() {
		return neto;
	}
}
