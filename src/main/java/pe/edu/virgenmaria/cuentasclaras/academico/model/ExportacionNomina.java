package pe.edu.virgenmaria.cuentasclaras.academico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * Sprint 9, tanda 3 (sección 3.11, E34, B14): cada descarga de la nómina de una sección para el SIAGIE, sin datos de
 * alumnos (el archivo no se guarda): el perfil, cuántas filas y cuántas con datos faltantes, el código impreso, el SHA-256
 * del archivo y la sesión de Dirección que la sacó (trg_exportacion_nomina_nace). SOLO INSERCIÓN (1142).
 */
@Entity
@Table(name = "exportacion_nomina")
public class ExportacionNomina extends BaseEntity {

	@Column(name = "anio_escolar_id", nullable = false, updatable = false)
	private Long anioEscolarId;

	@Column(name = "seccion_id", nullable = false, updatable = false)
	private Long seccionId;

	@Column(nullable = false, updatable = false, length = 40)
	private String perfil;

	@Column(nullable = false, updatable = false)
	private Integer filas;

	@Column(nullable = false, updatable = false)
	private Integer faltantes;

	@Column(nullable = false, updatable = false, length = 16)
	private String codigo;

	@Column(nullable = false, updatable = false, length = 64)
	private String sha256;

	@Column(name = "sesion_id", nullable = false, updatable = false)
	private Long sesionId;

	protected ExportacionNomina() {
		// requerido por JPA
	}

	public static ExportacionNomina de(Long anioEscolarId, Long seccionId, String perfil, int filas, int faltantes,
			String codigo, String sha256, Long sesionId) {
		ExportacionNomina e = new ExportacionNomina();
		e.anioEscolarId = Objects.requireNonNull(anioEscolarId, "anioEscolarId");
		e.seccionId = Objects.requireNonNull(seccionId, "seccionId");
		e.perfil = Objects.requireNonNull(perfil, "perfil");
		e.filas = filas;
		e.faltantes = faltantes;
		e.codigo = Objects.requireNonNull(codigo, "codigo");
		e.sha256 = Objects.requireNonNull(sha256, "sha256");
		e.sesionId = Objects.requireNonNull(sesionId, "sesionId");
		return e;
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("La exportación de la nómina es de solo inserción.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La exportación de la nómina no se borra.");
	}

	public Long getSeccionId() {
		return seccionId;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getSha256() {
		return sha256;
	}

	public int getFilas() {
		return filas;
	}

	public int getFaltantes() {
		return faltantes;
	}
}
