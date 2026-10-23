package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;

/**
 * Sprint 10, tanda 1 (sección 3.2): un área curricular de un nivel en una versión del catálogo. «Competencias
 * transversales» es un área marcada {@code transversal} (la califica el TUTOR, decisión 175; no se asigna). Educación Física
 * y Educación Religiosa son {@code exonerable}. Solo lectura (catálogo nacional, sin colegio).
 */
@Entity
@Immutable
@Table(name = "area_curricular")
public class AreaCurricular {

	@Id
	private Long id;

	@Column(name = "version_id", nullable = false, updatable = false)
	private Long versionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 12)
	private Nivel nivel;

	@Column(nullable = false, updatable = false, length = 8)
	private String codigo;

	@Column(nullable = false, updatable = false, length = 80)
	private String nombre;

	@Column(nullable = false, updatable = false)
	private Integer orden;

	@Column(nullable = false, updatable = false)
	private Boolean transversal;

	@Column(nullable = false, updatable = false)
	private Boolean exonerable;

	protected AreaCurricular() {
		// requerido por JPA
	}

	@PreUpdate
	@PreRemove
	void impedirCambios() {
		throw new IllegalStateException("El catálogo nacional no se cambia ni se borra: una corrección es una versión nueva.");
	}

	public Long getId() {
		return id;
	}

	public Long getVersionId() {
		return versionId;
	}

	public Nivel getNivel() {
		return nivel;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getNombre() {
		return nombre;
	}

	public int getOrden() {
		return orden;
	}

	public boolean isTransversal() {
		return Boolean.TRUE.equals(transversal);
	}

	public boolean isExonerable() {
		return Boolean.TRUE.equals(exonerable);
	}
}
