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
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Anulación APROBADA de un pago: una por pago y por solicitud. SOLO INSERCIÓN. Quien aprueba no es quien la pidió ni
 * la cajera del pago (CHECK en la base). Se inserta ANTES de marcar el pago como ANULADO (el trigger del pago la exige).
 * {@code posteriorAlCierre}: el pago era de una caja ya cerrada; ese cierre no se toca.
 */
@Entity
@Immutable
@Table(name = "anulacion_pago")
public class AnulacionPago extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pago_id", nullable = false, updatable = false)
	private Pago pago;

	@Column(name = "solicitud_id", nullable = false, updatable = false)
	private Long solicitudId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nota_credito_id", nullable = false, updatable = false)
	private Comprobante notaCredito;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private TipoAnulacion tipo;

	@Column(nullable = false, updatable = false, length = 500)
	private String motivo;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Column(name = "cajero_pago", nullable = false, updatable = false, length = 60)
	private String cajeroPago;

	@Column(name = "solicitado_por", nullable = false, updatable = false, length = 60)
	private String solicitadoPor;

	@Column(name = "aprobado_por", nullable = false, updatable = false, length = 60)
	private String aprobadoPor;

	@Column(name = "posterior_al_cierre", nullable = false, updatable = false)
	private boolean posteriorAlCierre;

	protected AnulacionPago() {
		// requerido por JPA
	}

	public static AnulacionPago registrar(Pago pago, Long solicitudId, Comprobante notaCredito, TipoAnulacion tipo,
			String motivo, String solicitadoPor, String aprobadoPor, boolean posteriorAlCierre) {
		Objects.requireNonNull(pago, "pago");
		Objects.requireNonNull(notaCredito, "notaCredito");
		if (!pago.vigente()) {
			throw new ReglaNegocioException("El pago ya está anulado.");
		}
		if (notaCredito.getTipo() != TipoComprobante.NOTA_CREDITO
				|| !Objects.equals(notaCredito.getModificaId(), pago.getComprobante().getId())) {
			throw new IllegalStateException("La nota de crédito debe anular el comprobante del pago");
		}
		if (aprobadoPor == null || aprobadoPor.equals(solicitadoPor) || aprobadoPor.equals(pago.getCajero())) {
			throw new ReglaNegocioException("Quien pide la anulación o cobró el pago no la aprueba.");
		}
		AnulacionPago anulacion = new AnulacionPago();
		anulacion.pago = pago;
		anulacion.solicitudId = Objects.requireNonNull(solicitudId, "solicitudId");
		anulacion.notaCredito = notaCredito;
		anulacion.tipo = Objects.requireNonNull(tipo, "tipo");
		anulacion.motivo = Motivo.exigir(motivo);
		anulacion.monto = pago.getTotal();
		anulacion.cajeroPago = pago.getCajero();
		anulacion.solicitadoPor = Objects.requireNonNull(solicitadoPor, "solicitadoPor");
		anulacion.aprobadoPor = aprobadoPor;
		anulacion.posteriorAlCierre = posteriorAlCierre;
		return anulacion;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las anulaciones no se borran.");
	}

	public Pago getPago() {
		return pago;
	}

	public Long getSolicitudId() {
		return solicitudId;
	}

	public Comprobante getNotaCredito() {
		return notaCredito;
	}

	public TipoAnulacion getTipo() {
		return tipo;
	}

	public String getMotivo() {
		return motivo;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public String getCajeroPago() {
		return cajeroPago;
	}

	public String getSolicitadoPor() {
		return solicitadoPor;
	}

	public String getAprobadoPor() {
		return aprobadoPor;
	}

	public boolean isPosteriorAlCierre() {
		return posteriorAlCierre;
	}
}
