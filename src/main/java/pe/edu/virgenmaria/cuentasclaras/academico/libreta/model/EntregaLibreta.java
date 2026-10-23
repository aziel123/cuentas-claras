package pe.edu.virgenmaria.cuentasclaras.academico.libreta.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 10, tanda 3 (sección 3.7, M9): la constancia de que el colegio ENTREGÓ una libreta (útil ante un reclamo en
 * INDECOPI): quién, por qué canal y con qué sesión. SOLO INSERCIÓN (1142). PORTAL lleva el apoderado; IMPRESA y ENTREVISTA,
 * no (lo exigen {@code ck_entrega_libreta_canal} y trg_entrega_libreta_nace).
 */
@Entity
@Table(name = "entrega_libreta")
public class EntregaLibreta extends BaseEntity {

	@Column(name = "libreta_id", nullable = false, updatable = false)
	private Long libretaId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private CanalEntrega canal;

	@Column(name = "apoderado_id", updatable = false)
	private Long apoderadoId;

	@Column(name = "sesion_id", nullable = false, updatable = false)
	private Long sesionId;

	@Column(name = "registrado_bd", updatable = false)
	private LocalDateTime registradoBd;

	protected EntregaLibreta() {
		// requerido por JPA
	}

	public static EntregaLibreta porPortal(Long libretaId, Long apoderadoId, Long sesionId, LocalDateTime ahora) {
		EntregaLibreta e = base(libretaId, CanalEntrega.PORTAL, sesionId, ahora);
		e.apoderadoId = Objects.requireNonNull(apoderadoId, "apoderadoId");
		return e;
	}

	/** Impresa por Dirección o el tutor; en Inicial, entregada en la entrevista (ENTREVISTA). */
	public static EntregaLibreta porElColegio(Long libretaId, CanalEntrega canal, Long sesionId, LocalDateTime ahora) {
		if (canal == CanalEntrega.PORTAL) {
			throw new IllegalArgumentException("La entrega por el portal es del apoderado");
		}
		return base(libretaId, canal, sesionId, ahora);
	}

	private static EntregaLibreta base(Long libretaId, CanalEntrega canal, Long sesionId, LocalDateTime ahora) {
		EntregaLibreta e = new EntregaLibreta();
		e.libretaId = Objects.requireNonNull(libretaId, "libretaId");
		e.canal = canal;
		e.sesionId = Objects.requireNonNull(sesionId, "sesionId");
		e.registradoBd = ahora;
		return e;
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("La constancia de entrega es de solo inserción.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La constancia de entrega no se borra.");
	}

	public Long getLibretaId() {
		return libretaId;
	}

	public CanalEntrega getCanal() {
		return canal;
	}

	public Long getApoderadoId() {
		return apoderadoId;
	}

	public Long getSesionId() {
		return sesionId;
	}
}
