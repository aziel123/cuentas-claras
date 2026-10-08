package pe.edu.virgenmaria.cuentasclaras.caja.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Reembolso de la devolución aprobada de un pago EN LÍNEA (correcciones del sprint 4, S4-A3). Solo sale por la API de
 * la pasarela, que lo devuelve al MISMO medio de origen (tarjeta, Yape o Plin del apoderado): aquí queda el id del cargo
 * y el del reembolso que devolvió la pasarela. Nunca se registra a mano en {@link Reembolso} (su trigger rechaza los
 * pagos de la pasarela). SOLO INSERCIÓN; uno por anulación. Lo ejecuta Administración, nunca quien aprobó ni quien
 * pidió la devolución (trigger).
 */
@Entity
@Immutable
@Table(name = "reembolso_pasarela")
public class ReembolsoPasarela extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "anulacion_pago_id", nullable = false, updatable = false)
	private AnulacionPago anulacion;

	@Column(name = "pago_id", nullable = false, updatable = false)
	private Long pagoId;

	@Column(name = "cargo_id", nullable = false, updatable = false, length = 80)
	private String cargoId;

	@Column(name = "reembolso_id", nullable = false, updatable = false, length = 80)
	private String reembolsoId;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false, updatable = false)
	private LocalDate fecha;

	protected ReembolsoPasarela() {
		// requerido por JPA
	}

	public static ReembolsoPasarela registrar(AnulacionPago anulacion, String cargoId, String reembolsoId,
			BigDecimal monto, LocalDate fecha, String por) {
		Objects.requireNonNull(anulacion, "anulacion");
		if (anulacion.getTipo() != TipoAnulacion.DEVOLUCION) {
			throw new ReglaNegocioException("Solo una devolución se reembolsa (un contracargo ya devolvió el dinero).");
		}
		if (anulacion.getPago().getOrigen() != OrigenPago.PASARELA) {
			throw new IllegalStateException("Solo un pago en línea se reembolsa por la pasarela");
		}
		if (por == null || por.equals(anulacion.getAprobadoPor()) || por.equals(anulacion.getSolicitadoPor())) {
			throw new ReglaNegocioException("Quien pidió o aprobó la devolución no la ejecuta: debe hacerlo otra persona "
					+ "de Administración.");
		}
		if (monto == null || monto.compareTo(anulacion.getMonto()) != 0) {
			throw new IllegalStateException("La pasarela reembolsó otro monto");
		}
		ReembolsoPasarela r = new ReembolsoPasarela();
		r.anulacion = anulacion;
		r.pagoId = anulacion.getPago().getId();
		r.cargoId = Objects.requireNonNull(cargoId, "cargoId");
		r.reembolsoId = Objects.requireNonNull(reembolsoId, "reembolsoId");
		r.monto = anulacion.getMonto();
		r.fecha = Objects.requireNonNull(fecha, "fecha");
		return r;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los reembolsos no se borran.");
	}

	public AnulacionPago getAnulacion() {
		return anulacion;
	}

	public Long getPagoId() {
		return pagoId;
	}

	public String getCargoId() {
		return cargoId;
	}

	public String getReembolsoId() {
		return reembolsoId;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public LocalDate getFecha() {
		return fecha;
	}
}
