package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Correcciones del sprint 8 (S8-A2): el cambio del correo externo de control de un colegio EN PRODUCCIÓN. El correo de
 * control es el testigo independiente del colegio (recibe la huella de la bitácora y el resumen diario con montos), así que
 * el operador ya no lo cambia solo: lo PROPONE (con la firma de su sesión en el colegio de plataforma, y en ese momento se
 * avisa al correo anterior y a toda la Promotoría) y lo APRUEBA una Promotoría activa del colegio con la firma de la suya
 * ({@code cambio_correo_control:{id}:APROBADO}). Hasta entonces sigue el correo anterior. Lo escribe solo {@code cc_sistema}
 * (trg_cambio_correo_control_*); nada se borra.
 */
@Entity
@Table(name = "cambio_correo_control")
public class CambioCorreoControl extends BaseEntity {

	/** PROPUESTO por el operador; APROBADO o RECHAZADO por la Promotoría. */
	public enum Estado {
		PROPUESTO, APROBADO, RECHAZADO
	}

	@Column(name = "correo_anterior", updatable = false, length = 100)
	private String correoAnterior;

	@Column(name = "correo_nuevo", nullable = false, updatable = false, length = 100)
	private String correoNuevo;

	@Column(nullable = false, updatable = false, length = 500)
	private String motivo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private Estado estado = Estado.PROPUESTO;

	/** TRUE solo mientras está PROPUESTO (UNIQUE por colegio: uno pendiente a la vez); si no, {@code null}. */
	@Column
	private Boolean pendiente = Boolean.TRUE;

	@Column(name = "propuesto_por", nullable = false, updatable = false, length = 60)
	private String propuestoPor;

	@Column(name = "operador_id", nullable = false, updatable = false)
	private Long operadorId;

	@Column(name = "plataforma_id", nullable = false, updatable = false)
	private Long plataformaId;

	@Column(name = "resuelto_por", length = 60)
	private String resueltoPor;

	@Column(name = "resuelto_en")
	private LocalDateTime resueltoEn;

	protected CambioCorreoControl() {
		// requerido por JPA
	}

	public static CambioCorreoControl propuesto(String correoAnterior, String correoNuevo, String motivo, Long operadorId,
			Long plataformaId, String operador) {
		String texto = motivo == null ? "" : motivo.strip();
		if (texto.length() < 10 || texto.length() > 500) {
			throw new ReglaNegocioException("Escribe el motivo del cambio (de 10 a 500 caracteres): por ejemplo, la adenda "
					+ "del contrato o el pedido escrito de la Promotoría.");
		}
		if (correoNuevo == null || !correoNuevo.contains("@") || correoNuevo.equalsIgnoreCase(correoAnterior)) {
			throw new ReglaNegocioException("Escribe un correo de control distinto del actual.");
		}
		CambioCorreoControl c = new CambioCorreoControl();
		c.correoAnterior = correoAnterior;
		c.correoNuevo = correoNuevo;
		c.motivo = texto;
		c.operadorId = Objects.requireNonNull(operadorId, "operadorId");
		c.plataformaId = Objects.requireNonNull(plataformaId, "plataformaId");
		c.propuestoPor = Objects.requireNonNull(operador, "operador");
		return c;
	}

	public void aprobar(String promotoria, LocalDateTime ahora) {
		resolver(Estado.APROBADO, promotoria, ahora);
	}

	public void rechazar(String promotoria, LocalDateTime ahora) {
		resolver(Estado.RECHAZADO, promotoria, ahora);
	}

	private void resolver(Estado nuevo, String quien, LocalDateTime ahora) {
		if (estado != Estado.PROPUESTO) {
			throw new ReglaNegocioException("Este cambio del correo de control ya no está por resolver.");
		}
		if (Objects.equals(quien, propuestoPor)) {
			throw new ReglaNegocioException("Quien propuso el cambio no lo resuelve.");
		}
		estado = nuevo;
		pendiente = null;
		resueltoPor = Objects.requireNonNull(quien, "quien");
		resueltoEn = Objects.requireNonNull(ahora, "ahora");
	}

	@PreRemove
	void impedirBorrado() {
		throw new UnsupportedOperationException("Un cambio del correo de control no se borra");
	}

	public String getCorreoAnterior() {
		return correoAnterior;
	}

	public String getCorreoNuevo() {
		return correoNuevo;
	}

	public String getMotivo() {
		return motivo;
	}

	public Estado getEstado() {
		return estado;
	}

	public String getPropuestoPor() {
		return propuestoPor;
	}

	public String getResueltoPor() {
		return resueltoPor;
	}

	public LocalDateTime getResueltoEn() {
		return resueltoEn;
	}
}
