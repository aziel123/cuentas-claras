package pe.edu.virgenmaria.cuentasclaras.caja.model;

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
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Libro de aplicaciones: cuánto de un pago se aplicó a una cuota. SOLO INSERCIÓN ({@code @Immutable} y, en MySQL, sin
 * UPDATE ni DELETE). {@code cuota.monto_pagado} es la suma de estas filas. Al anular un pago se agregan reversiones
 * (tanda 2), nunca se borra.
 */
@Entity
@Immutable
@Table(name = "aplicacion_pago")
public class AplicacionPago extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pago_id", nullable = false, updatable = false)
	private Pago pago;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cuota_id", nullable = false, updatable = false)
	private Cuota cuota;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private TipoAplicacion tipo;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Column(name = "revierte_id", updatable = false)
	private Long revierteId;

	protected AplicacionPago() {
		// requerido por JPA
	}

	/** Aplica parte (o todo) del pago a una cuota de la misma familia. */
	public static AplicacionPago aplicar(Pago pago, Cuota cuota, BigDecimal monto) {
		Objects.requireNonNull(pago, "pago");
		Objects.requireNonNull(cuota, "cuota");
		if (!pago.vigente()) {
			throw new ReglaNegocioException("Un pago anulado no se aplica a cuotas.");
		}
		if (!Objects.equals(cuota.getAlumno().getFamilia().getId(), pago.getFamilia().getId())) {
			throw new ReglaNegocioException("El pago solo se aplica a cuotas de su familia.");
		}
		AplicacionPago aplicacion = new AplicacionPago();
		aplicacion.pago = pago;
		aplicacion.cuota = cuota;
		aplicacion.tipo = TipoAplicacion.APLICACION;
		aplicacion.monto = Dinero.positivo(monto, "el monto aplicado");
		return aplicacion;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("El libro de pagos no se borra.");
	}

	public Pago getPago() {
		return pago;
	}

	public Cuota getCuota() {
		return cuota;
	}

	public TipoAplicacion getTipo() {
		return tipo;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public Long getRevierteId() {
		return revierteId;
	}
}
