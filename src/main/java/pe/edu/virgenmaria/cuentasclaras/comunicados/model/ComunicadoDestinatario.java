package pe.edu.virgenmaria.cuentasclaras.comunicados.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 9, tanda 3 (secciones 3.8 y 3.10): una familia destinataria, congelada al enviarse. La familia y el comunicado
 * no cambian (1143); solo se escriben, una vez, cuándo la abrió por primera vez y qué mensaje la avisó y cuál la recordó.
 */
@Entity
@Table(name = "comunicado_destinatario")
public class ComunicadoDestinatario extends BaseEntity {

	@Column(name = "comunicado_id", nullable = false, updatable = false)
	private Long comunicadoId;

	@Column(name = "familia_id", nullable = false, updatable = false)
	private Long familiaId;

	@Column(nullable = false, updatable = false)
	private Integer alumnos;

	@Column(name = "abierto_en")
	private LocalDateTime abiertoEn;

	@Column(name = "avisado_mensaje_id")
	private Long avisadoMensajeId;

	@Column(name = "recordado_mensaje_id")
	private Long recordadoMensajeId;

	protected ComunicadoDestinatario() {
		// requerido por JPA
	}

	public static ComunicadoDestinatario nuevo(Long comunicadoId, Long familiaId, int alumnos) {
		ComunicadoDestinatario d = new ComunicadoDestinatario();
		d.comunicadoId = Objects.requireNonNull(comunicadoId, "comunicadoId");
		d.familiaId = Objects.requireNonNull(familiaId, "familiaId");
		d.alumnos = Math.max(1, alumnos);
		return d;
	}

	/** La primera vez que la familia lo abre en el portal (las siguientes no cambian nada). */
	public boolean marcarAbierto(LocalDateTime ahora) {
		if (abiertoEn != null) {
			return false;
		}
		abiertoEn = Objects.requireNonNull(ahora, "ahora");
		return true;
	}

	/** El mensaje que lo avisó por primera vez (una sola vez). */
	public void avisadoPor(Long mensajeId) {
		if (avisadoMensajeId == null) {
			avisadoMensajeId = mensajeId;
		}
	}

	/** El mensaje que llevó el recordatorio de las 48 horas (una sola vez). */
	public void recordadoPor(Long mensajeId) {
		if (recordadoMensajeId == null) {
			recordadoMensajeId = mensajeId;
		}
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Un destinatario de un comunicado no se borra.");
	}

	public Long getComunicadoId() {
		return comunicadoId;
	}

	public Long getFamiliaId() {
		return familiaId;
	}

	public int getAlumnos() {
		return alumnos;
	}

	public LocalDateTime getAbiertoEn() {
		return abiertoEn;
	}

	public Long getAvisadoMensajeId() {
		return avisadoMensajeId;
	}

	public Long getRecordadoMensajeId() {
		return recordadoMensajeId;
	}
}
