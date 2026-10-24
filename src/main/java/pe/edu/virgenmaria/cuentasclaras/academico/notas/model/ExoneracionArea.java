package pe.edu.virgenmaria.cuentasclaras.academico.notas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.util.Objects;

/**
 * Sprint 10, tanda 2 (sección 3.5 y A3): un alumno exonerado de Religión o Educación Física, con el código del escrito del
 * padre. La autoriza Dirección con su firma y rige DESDE un periodo ABIERTO: las notas de los periodos anteriores quedan
 * (decisión 190). SOLO INSERCIÓN: no cambia ni se borra.
 */
@Entity
@Table(name = "exoneracion_area")
public class ExoneracionArea extends BaseEntity {

	@Column(name = "anio_escolar_id", nullable = false, updatable = false)
	private Long anioEscolarId;

	@Column(name = "matricula_id", nullable = false, updatable = false)
	private Long matriculaId;

	@Column(name = "area_id", nullable = false, updatable = false)
	private Long areaId;

	@Column(name = "desde_periodo_id", nullable = false, updatable = false)
	private Long desdePeriodoId;

	@Column(nullable = false, updatable = false, length = 40)
	private String documento;

	@Column(nullable = false, updatable = false, length = 300)
	private String motivo;

	@Column(name = "autorizado_por", nullable = false, updatable = false, length = 60)
	private String autorizadoPor;

	protected ExoneracionArea() {
		// requerido por JPA
	}

	public static ExoneracionArea nueva(Long anioEscolarId, Long matriculaId, Long areaId, Long desdePeriodoId,
			String documento, String motivo, String director) {
		ExoneracionArea e = new ExoneracionArea();
		e.anioEscolarId = Objects.requireNonNull(anioEscolarId, "anio");
		e.matriculaId = Objects.requireNonNull(matriculaId, "matricula");
		e.areaId = Objects.requireNonNull(areaId, "area");
		e.desdePeriodoId = Objects.requireNonNull(desdePeriodoId, "periodo");
		String doc = Normalizador.limpiar(documento);
		if (doc == null || doc.length() < 3 || doc.length() > 40) {
			throw new ReglaNegocioException("Escribe el código del escrito del padre (de 3 a 40 caracteres).");
		}
		String razon = Normalizador.limpiar(motivo);
		if (razon == null || razon.length() < 10 || razon.length() > 300) {
			throw new ReglaNegocioException("Escribe el motivo de la exoneración, en 10 a 300 caracteres.");
		}
		e.documento = doc;
		e.motivo = razon;
		e.autorizadoPor = Objects.requireNonNull(director, "director");
		return e;
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("Una exoneración no se cambia.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Una exoneración no se borra.");
	}

	public Long getAnioEscolarId() {
		return anioEscolarId;
	}

	public Long getMatriculaId() {
		return matriculaId;
	}

	public Long getAreaId() {
		return areaId;
	}

	public Long getDesdePeriodoId() {
		return desdePeriodoId;
	}

	public String getDocumento() {
		return documento;
	}

	public String getMotivo() {
		return motivo;
	}

	public String getAutorizadoPor() {
		return autorizadoPor;
	}
}
