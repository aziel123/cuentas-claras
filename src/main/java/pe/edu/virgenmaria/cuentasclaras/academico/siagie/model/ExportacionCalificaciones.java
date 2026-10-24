package pe.edu.virgenmaria.cuentasclaras.academico.siagie.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 10, tanda 3 (secciones 3.10 y 3.13, A6): cada descarga de la exportación de calificaciones para el SIAGIE, sin
 * datos de alumnos (el archivo no se guarda): el perfil, cuántas filas, la huella de las libretas vigentes usadas (A6), el
 * código impreso, el SHA-256 del archivo y la sesión de quien la sacó (Dirección o la persona delegada,
 * trg_exportacion_calificaciones_nace). SOLO INSERCIÓN (1142).
 */
@Entity
@Table(name = "exportacion_calificaciones")
public class ExportacionCalificaciones extends BaseEntity {

	@Column(name = "periodo_id", nullable = false, updatable = false)
	private Long periodoId;

	@Column(name = "seccion_id", nullable = false, updatable = false)
	private Long seccionId;

	@Column(nullable = false, updatable = false, length = 40)
	private String perfil;

	@Column(nullable = false, updatable = false)
	private Integer filas;

	@Column(name = "huella_libretas", nullable = false, updatable = false, length = 64)
	private String huellaLibretas;

	@Column(nullable = false, updatable = false, length = 16)
	private String codigo;

	@Column(nullable = false, updatable = false, length = 64)
	private String sha256;

	@Column(name = "sesion_id", nullable = false, updatable = false)
	private Long sesionId;

	@Column(name = "registrado_bd", updatable = false)
	private LocalDateTime registradoBd;

	protected ExportacionCalificaciones() {
		// requerido por JPA
	}

	public static ExportacionCalificaciones de(Long periodoId, Long seccionId, String perfil, int filas,
			String huellaLibretas, String codigo, String sha256, Long sesionId, LocalDateTime ahora) {
		ExportacionCalificaciones e = new ExportacionCalificaciones();
		e.periodoId = Objects.requireNonNull(periodoId, "periodoId");
		e.seccionId = Objects.requireNonNull(seccionId, "seccionId");
		e.perfil = Objects.requireNonNull(perfil, "perfil");
		e.filas = filas;
		e.huellaLibretas = Objects.requireNonNull(huellaLibretas, "huellaLibretas");
		e.codigo = Objects.requireNonNull(codigo, "codigo");
		e.sha256 = Objects.requireNonNull(sha256, "sha256");
		e.sesionId = Objects.requireNonNull(sesionId, "sesionId");
		e.registradoBd = ahora;
		return e;
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("La exportación de calificaciones es de solo inserción.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La exportación de calificaciones no se borra.");
	}

	public Long getPeriodoId() {
		return periodoId;
	}

	public Long getSeccionId() {
		return seccionId;
	}

	public String getHuellaLibretas() {
		return huellaLibretas;
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
}
