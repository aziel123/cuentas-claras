package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Libro de descuentos: cuánto se descontó de una cuota por un descuento APROBADO. SOLO INSERCIÓN ({@code @Immutable};
 * en MySQL sin UPDATE ni DELETE). {@code cuota.monto_descuento} es la suma de estas filas.
 */
@Entity
@Immutable
@Table(name = "ajuste_cuota")
public class AjusteCuota extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cuota_id", nullable = false, updatable = false)
	private Cuota cuota;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "descuento_id", nullable = false, updatable = false)
	private Descuento descuento;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	protected AjusteCuota() {
		// requerido por JPA
	}

	public static AjusteCuota de(Descuento descuento, Cuota cuota, BigDecimal monto) {
		Objects.requireNonNull(descuento, "descuento");
		Objects.requireNonNull(cuota, "cuota");
		if (descuento.getEstado() != EstadoDescuento.APROBADO) {
			throw new IllegalStateException("Un ajuste solo nace de un descuento aprobado");
		}
		if (!descuento.cuotaIds().contains(cuota.getId())
				|| !Objects.equals(descuento.getAlumno().getId(), cuota.getAlumno().getId())) {
			throw new ReglaNegocioException("El descuento no incluye esa cuota.");
		}
		AjusteCuota ajuste = new AjusteCuota();
		ajuste.descuento = descuento;
		ajuste.cuota = cuota;
		ajuste.monto = Dinero.positivo(monto, "el ajuste");
		return ajuste;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("El libro de descuentos no se borra.");
	}

	public Cuota getCuota() {
		return cuota;
	}

	public Descuento getDescuento() {
		return descuento;
	}

	public BigDecimal getMonto() {
		return monto;
	}
}
