package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;

import java.util.Arrays;

/**
 * Sprint 10, tanda 1 (sección 3.2 y contrato de la sección 6 del brief): una competencia del catálogo. El código
 * {@code {INI|PRI|SEC}-{AREA}-{n}} no se renombra (lo usan las notas y el perfil del SIAGIE). {@code soloGrados}: NULL vale
 * para todos los grados del nivel; si no, la lista de grados (las transversales y Castellano como segunda lengua de Inicial
 * solo en 5 años). Solo lectura.
 */
@Entity
@Immutable
@Table(name = "competencia")
public class Competencia {

	@Id
	private Long id;

	@Column(name = "version_id", nullable = false, updatable = false)
	private Long versionId;

	@Column(name = "area_id", nullable = false, updatable = false)
	private Long areaId;

	@Column(nullable = false, updatable = false, length = 16)
	private String codigo;

	@Column(nullable = false, updatable = false, length = 300)
	private String nombre;

	@Column(nullable = false, updatable = false)
	private Integer orden;

	@Column(name = "solo_grados", updatable = false, length = 60)
	private String soloGrados;

	protected Competencia() {
		// requerido por JPA
	}

	@PreUpdate
	@PreRemove
	void impedirCambios() {
		throw new IllegalStateException("El catálogo nacional no se cambia ni se borra: una corrección es una versión nueva.");
	}

	/** Si la competencia se califica en ese grado (respeta {@code soloGrados}). */
	public boolean aplicaA(Grado grado) {
		return soloGrados == null || Arrays.stream(soloGrados.split(",")).map(String::trim)
				.anyMatch(g -> g.equals(grado.name()));
	}

	public Long getId() {
		return id;
	}

	public Long getVersionId() {
		return versionId;
	}

	public Long getAreaId() {
		return areaId;
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

	public String getSoloGrados() {
		return soloGrados;
	}
}
