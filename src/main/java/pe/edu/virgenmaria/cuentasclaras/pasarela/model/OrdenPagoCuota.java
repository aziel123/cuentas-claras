package pe.edu.virgenmaria.cuentasclaras.pasarela.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Una cuota de la orden con el saldo que tenía al crearla. SOLO INSERCIÓN ({@code @Immutable} y, en MySQL, sin UPDATE ni
 * DELETE): si al confirmarse el pago el saldo ya no es este, la orden queda por revisar. Σ montos = monto de la orden.
 */
@Entity
@Immutable
@Table(name = "orden_pago_cuota")
public class OrdenPagoCuota extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "orden_pago_id", nullable = false, updatable = false)
	private OrdenPago orden;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cuota_id", nullable = false, updatable = false)
	private Cuota cuota;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	protected OrdenPagoCuota() {
		// requerido por JPA
	}

	public static OrdenPagoCuota de(OrdenPago orden, Cuota cuota) {
		OrdenPagoCuota fila = new OrdenPagoCuota();
		fila.orden = Objects.requireNonNull(orden, "orden");
		fila.cuota = Objects.requireNonNull(cuota, "cuota");
		fila.monto = Dinero.positivo(cuota.saldo(), "el saldo de la cuota");
		return fila;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las cuotas de una orden de pago no se borran.");
	}

	public OrdenPago getOrden() {
		return orden;
	}

	public Cuota getCuota() {
		return cuota;
	}

	public BigDecimal getMonto() {
		return monto;
	}
}
