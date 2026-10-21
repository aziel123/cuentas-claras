package pe.edu.virgenmaria.cuentasclaras.academico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 9, tanda 2 (A2): la marca de agua de los avisos de asistencia de UN colegio. Cada envío (10:30 y 16:00) avisa
 * las marcas con id mayor que {@link #getUltimaMarcaId()} (también las de días anteriores: lo hecho después del envío de
 * las 16:00 sale a las 10:30 del día siguiente) y la sube al id más alto que avisó. La escribe SOLO
 * {@code sistema.asistencia} con la conexión de {@code cc_sistema} (con {@code cc_app}: 1142). La clave es el colegio:
 * no extiende {@code BaseEntity}. Nunca baja ni se borra.
 */
@Entity
@Table(name = "aviso_asistencia_corte")
public class AvisoAsistenciaCorte {

	public static final String ACTOR = "sistema.asistencia";

	@Id
	@Column(name = "colegio_id", nullable = false, updatable = false)
	private Long colegioId;

	@Column(name = "ultima_marca_id", nullable = false)
	private Long ultimaMarcaId;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private LocalDateTime creadoEn;

	@Column(name = "creado_por", nullable = false, updatable = false, length = 60)
	private String creadoPor;

	@Column(name = "actualizado_en", nullable = false)
	private LocalDateTime actualizadoEn;

	@Version
	@Column(nullable = false)
	private Long version;

	protected AvisoAsistenciaCorte() {
		// requerido por JPA
	}

	/** La primera vez: la marca de agua empieza en 0 (todo lo registrado se avisa). */
	public static AvisoAsistenciaCorte nuevo(Long colegioId, LocalDateTime ahora) {
		AvisoAsistenciaCorte c = new AvisoAsistenciaCorte();
		c.colegioId = Objects.requireNonNull(colegioId, "colegioId");
		c.ultimaMarcaId = 0L;
		c.creadoEn = Objects.requireNonNull(ahora, "ahora");
		c.creadoPor = ACTOR;
		c.actualizadoEn = ahora;
		return c;
	}

	/** Sube la marca de agua al id más alto avisado (nunca baja). */
	public void subirA(long marcaId, LocalDateTime ahora) {
		if (marcaId > ultimaMarcaId) {
			ultimaMarcaId = marcaId;
			actualizadoEn = Objects.requireNonNull(ahora, "ahora");
		}
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La marca de agua de los avisos no se borra.");
	}

	public Long getColegioId() {
		return colegioId;
	}

	public long getUltimaMarcaId() {
		return ultimaMarcaId;
	}
}
