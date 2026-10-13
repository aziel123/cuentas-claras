package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

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
 * Correcciones del sprint 8 (S8-A1): el cambio del celular o el correo del titular de la primera Promotoría ANTES de que
 * active su cuenta. Lo PROPONE un operador y lo APLICA OTRO operador, cada uno con la firma de su sesión en el colegio de
 * plataforma ({@code cambio_contacto_titular:{colegio}:{usuario}:{n}} y {@code cambio_contacto_titular:{id}:APLICADO}).
 * Al aplicarlo se avisa al contacto anterior, al correo institucional y al correo externo de control en la misma
 * transacción. Guarda el contacto anterior y el nuevo: trg_usuario_contacto exige que la cuenta pase EXACTAMENTE del uno
 * al otro. Lo escribe solo {@code cc_sistema}; nada se borra.
 */
@Entity
@Table(name = "cambio_contacto_titular")
public class CambioContactoTitular extends BaseEntity {

	/** El estado del cambio: PROPUESTO, APLICADO (por otro operador) o RECHAZADO. */
	public enum Estado {
		PROPUESTO, APLICADO, RECHAZADO
	}

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(name = "telefono_anterior", updatable = false, length = 16)
	private String telefonoAnterior;

	@Column(name = "correo_anterior", updatable = false, length = 150)
	private String correoAnterior;

	@Column(name = "telefono_nuevo", updatable = false, length = 16)
	private String telefonoNuevo;

	@Column(name = "correo_nuevo", updatable = false, length = 150)
	private String correoNuevo;

	@Column(nullable = false, updatable = false, length = 500)
	private String motivo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private Estado estado = Estado.PROPUESTO;

	/** TRUE solo mientras está PROPUESTO (UNIQUE por titular: uno pendiente a la vez); si no, {@code null}. */
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

	protected CambioContactoTitular() {
		// requerido por JPA
	}

	public static CambioContactoTitular propuesto(Usuario titular, String telefonoNuevo, String correoNuevo, String motivo,
			Long operadorId, Long plataformaId, String operador) {
		Objects.requireNonNull(titular, "titular");
		if (telefonoNuevo == null && correoNuevo == null) {
			throw new ReglaNegocioException("Escribe el celular (WhatsApp) o el correo del titular.");
		}
		String texto = motivo == null ? "" : motivo.strip();
		if (texto.length() < 10 || texto.length() > 500) {
			throw new ReglaNegocioException("Escribe el motivo del cambio (de 10 a 500 caracteres): por ejemplo, quién lo "
					+ "pidió y cómo lo confirmaste con el titular.");
		}
		CambioContactoTitular c = new CambioContactoTitular();
		c.usuarioId = titular.getId();
		c.telefonoAnterior = titular.getTelefonoWhatsapp();
		c.correoAnterior = titular.getCorreo();
		c.telefonoNuevo = telefonoNuevo;
		c.correoNuevo = correoNuevo;
		c.motivo = texto;
		c.operadorId = Objects.requireNonNull(operadorId, "operadorId");
		c.plataformaId = Objects.requireNonNull(plataformaId, "plataformaId");
		c.propuestoPor = Objects.requireNonNull(operador, "operador");
		return c;
	}

	/** Lo aplica OTRO operador (nunca quien lo propuso). */
	public void aplicar(String operador, LocalDateTime ahora) {
		exigirPropuesto();
		if (Objects.equals(operador, propuestoPor)) {
			throw new ReglaNegocioException("Tú propusiste este cambio: lo aplica otro operador, con su propia sesión.");
		}
		estado = Estado.APLICADO;
		resolver(operador, ahora);
	}

	/** Lo rechaza (o lo retira quien lo propuso). */
	public void rechazar(String operador, LocalDateTime ahora) {
		exigirPropuesto();
		estado = Estado.RECHAZADO;
		resolver(operador, ahora);
	}

	private void resolver(String operador, LocalDateTime ahora) {
		pendiente = null;
		resueltoPor = Objects.requireNonNull(operador, "operador");
		resueltoEn = Objects.requireNonNull(ahora, "ahora");
	}

	private void exigirPropuesto() {
		if (estado != Estado.PROPUESTO) {
			throw new ReglaNegocioException("Este cambio de contacto ya no está por resolver.");
		}
	}

	@PreRemove
	void impedirBorrado() {
		throw new UnsupportedOperationException("Un cambio del contacto del titular no se borra");
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getTelefonoAnterior() {
		return telefonoAnterior;
	}

	public String getCorreoAnterior() {
		return correoAnterior;
	}

	public String getTelefonoNuevo() {
		return telefonoNuevo;
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

	public Long getPlataformaId() {
		return plataformaId;
	}

	public String getResueltoPor() {
		return resueltoPor;
	}

	public LocalDateTime getResueltoEn() {
		return resueltoEn;
	}
}
