package pe.edu.virgenmaria.cuentasclaras.academico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 9, tanda 2 (sección 3.4, V33): el registro de la asistencia de una sección en un día. Uno por colegio, sección y
 * fecha (UNIQUE). SOLO INSERCIÓN: ninguna columna se actualiza ({@code updatable = false}), {@code cc_app} no tiene
 * UPDATE ni DELETE (1142) y la entidad rechaza un UPDATE o un borrado. La fecha y la hora las decide la base
 * ({@code registrado_bd}, que pone trg_registro_asistencia_nace; aquí es de solo lectura).
 * <p>
 * Lo registra SOLO un docente con asignación VIGENTE en la sección, con su sesión y su firma (decisión 144: Dirección no
 * registra). {@code fuente} es siempre {@link FuenteAsistencia#DOCENTE} en este sprint: no hay forma de pedir otra.
 */
@Entity
@Table(name = "registro_asistencia")
public class RegistroAsistencia extends BaseEntity {

	@Column(name = "anio_escolar_id", nullable = false, updatable = false)
	private Long anioEscolarId;

	@Column(name = "seccion_id", nullable = false, updatable = false)
	private Long seccionId;

	@Column(nullable = false, updatable = false)
	private LocalDate fecha;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private OrigenRegistro origen;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private FuenteAsistencia fuente;

	@Column(name = "solicitud_id", updatable = false)
	private Long solicitudId;

	@Column(name = "registrado_por", nullable = false, updatable = false, length = 60)
	private String registradoPor;

	@Column(name = "sesion_id", nullable = false, updatable = false)
	private Long sesionId;

	/** La pone la base (trigger); la aplicación no la escribe. */
	@Column(name = "registrado_bd", insertable = false, updatable = false)
	private LocalDateTime registradoBd;

	@Column(nullable = false, updatable = false)
	private Integer alumnos;

	protected RegistroAsistencia() {
		// requerido por JPA
	}

	/** El registro de HOY (en Lima) de la sección, a nombre del docente en sesión. */
	public static RegistroAsistencia delDia(Long anioEscolarId, Long seccionId, LocalDate fecha, String docente,
			Long sesionId, int alumnos) {
		return nuevo(anioEscolarId, seccionId, fecha, OrigenRegistro.DEL_DIA, null, docente, sesionId, alumnos);
	}

	/** El registro de un día pasado, con su REGISTRO_ASISTENCIA_TARDIO aprobada y firmada por otra persona. */
	public static RegistroAsistencia tardio(Long anioEscolarId, Long seccionId, LocalDate fecha, Long solicitudId,
			String docente, Long sesionId, int alumnos) {
		return nuevo(anioEscolarId, seccionId, fecha, OrigenRegistro.TARDIO, Objects.requireNonNull(solicitudId,
				"solicitudId"), docente, sesionId, alumnos);
	}

	private static RegistroAsistencia nuevo(Long anioEscolarId, Long seccionId, LocalDate fecha, OrigenRegistro origen,
			Long solicitudId, String docente, Long sesionId, int alumnos) {
		RegistroAsistencia r = new RegistroAsistencia();
		r.anioEscolarId = Objects.requireNonNull(anioEscolarId, "anioEscolarId");
		r.seccionId = Objects.requireNonNull(seccionId, "seccionId");
		r.fecha = Objects.requireNonNull(fecha, "fecha");
		r.origen = origen;
		r.fuente = FuenteAsistencia.DOCENTE;
		r.solicitudId = solicitudId;
		r.registradoPor = Objects.requireNonNull(docente, "docente");
		r.sesionId = Objects.requireNonNull(sesionId, "sesionId");
		if (alumnos < 0) {
			throw new IllegalArgumentException("alumnos");
		}
		r.alumnos = alumnos;
		return r;
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("La asistencia es de solo inserción: un registro no se edita.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La asistencia es de solo inserción: un registro no se borra.");
	}

	public Long getAnioEscolarId() {
		return anioEscolarId;
	}

	public Long getSeccionId() {
		return seccionId;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public OrigenRegistro getOrigen() {
		return origen;
	}

	public FuenteAsistencia getFuente() {
		return fuente;
	}

	public Long getSolicitudId() {
		return solicitudId;
	}

	public String getRegistradoPor() {
		return registradoPor;
	}

	public Long getSesionId() {
		return sesionId;
	}

	public LocalDateTime getRegistradoBd() {
		return registradoBd;
	}

	public int getAlumnos() {
		return alumnos;
	}
}
