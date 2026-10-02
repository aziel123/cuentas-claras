package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;

/** Línea de un comprobante (una por cuota pagada). Solo inserción: {@code @Immutable} y, en MySQL, sin UPDATE. */
@Entity
@Immutable
@Table(name = "comprobante_linea")
public class ComprobanteLinea extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "comprobante_id", nullable = false, updatable = false)
	private Comprobante comprobante;

	@Column(nullable = false, updatable = false)
	private int orden;

	@Column(nullable = false, updatable = false, length = 250)
	private String descripcion;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	protected ComprobanteLinea() {
		// requerido por JPA
	}

	static ComprobanteLinea de(Comprobante comprobante, int orden, String descripcion, BigDecimal monto) {
		ComprobanteLinea linea = new ComprobanteLinea();
		linea.comprobante = comprobante;
		linea.orden = orden;
		linea.descripcion = descripcion;
		linea.monto = monto;
		return linea;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las líneas de un comprobante no se borran.");
	}

	public Comprobante getComprobante() {
		return comprobante;
	}

	public int getOrden() {
		return orden;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public BigDecimal getMonto() {
		return monto;
	}
}
