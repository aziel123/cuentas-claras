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
 * Sprint 10, tanda 1 (sección 3.2): un valor de la escala de calificación (AD, A, B, C) en un nivel, con su descripción
 * del CNEB y si exige conclusión descriptiva (RVM 048-2024: Inicial en A, B y C; Primaria en B y C; Secundaria en C). El
 * colegio puede exigir más (siempre la conclusión), nunca menos. Ninguna clase de Java repite esta tabla: se lee de aquí.
 */
@Entity
@Immutable
@Table(name = "escala_logro")
public class EscalaLogro {

	@Id
	private Long id;

	@Column(name = "version_id", nullable = false, updatable = false)
	private Long versionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 12)
	private Nivel nivel;

	@Column(nullable = false, updatable = false, length = 2)
	private String valor;

	@Column(nullable = false, updatable = false, length = 40)
	private String nombre;

	@Column(nullable = false, updatable = false, length = 400)
	private String descripcion;

	@Column(nullable = false, updatable = false)
	private Integer orden;

	@Column(name = "exige_conclusion", nullable = false, updatable = false)
	private Boolean exigeConclusion;

	protected EscalaLogro() {
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

	public String getValor() {
		return valor;
	}

	public String getNombre() {
		return nombre;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public int getOrden() {
		return orden;
	}

	public boolean isExigeConclusion() {
		return Boolean.TRUE.equals(exigeConclusion);
	}
}
