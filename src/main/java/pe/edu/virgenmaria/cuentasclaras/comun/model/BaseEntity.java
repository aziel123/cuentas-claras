package pe.edu.virgenmaria.cuentasclaras.comun.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Base de toda entidad de negocio.
 * <ul>
 *   <li>{@code colegioId} lo asigna Hibernate ({@code @TenantId}) con el colegio del contexto y
 *       además filtra por él toda lectura. No tiene setter: una entidad no cambia de colegio.</li>
 *   <li>Fechas y autor los completa la auditoría JPA ({@code ConfiguracionJpa}).</li>
 *   <li>{@code version}: bloqueo optimista contra ediciones simultáneas.</li>
 * </ul>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@TenantId
	@Column(name = "colegio_id", nullable = false, updatable = false)
	private Long colegioId;

	@CreatedDate
	@Column(name = "creado_en", nullable = false, updatable = false)
	private LocalDateTime creadoEn;

	@CreatedBy
	@Column(name = "creado_por", nullable = false, updatable = false, length = 60)
	private String creadoPor;

	@LastModifiedDate
	@Column(name = "actualizado_en", nullable = false)
	private LocalDateTime actualizadoEn;

	@Version
	@Column(nullable = false)
	private Long version;

	public Long getId() {
		return id;
	}

	public Long getColegioId() {
		return colegioId;
	}

	public LocalDateTime getCreadoEn() {
		return creadoEn;
	}

	public String getCreadoPor() {
		return creadoPor;
	}

	public LocalDateTime getActualizadoEn() {
		return actualizadoEn;
	}

	public Long getVersion() {
		return version;
	}
}
