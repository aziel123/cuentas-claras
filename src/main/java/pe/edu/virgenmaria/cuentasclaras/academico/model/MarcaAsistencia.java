package pe.edu.virgenmaria.cuentasclaras.academico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Sprint 9, tanda 2 (secciones 3.4 y 3.5, V33): la marca de un alumno en un registro. SOLO INSERCIÓN, como los pagos:
 * corregir es INSERTAR otra marca que reemplaza a la vigente ({@code reemplaza_id}); una sola sucesora por marca (UNIQUE,
 * E8), así la historia es una cadena y la vigente es la que nadie reemplazó. Ninguna columna se actualiza, {@code cc_app}
 * no tiene UPDATE ni DELETE (1142) y la entidad rechaza un UPDATE o un borrado.
 * <ul>
 *   <li>{@link #inicial}: la del registro, a nombre de quien registró (A5).</li>
 *   <li>{@link #correccion}: el mismo día ({@link OrigenMarca#CORRECCION_DEL_DIA}) o con su solicitud aprobada
 *       ({@link OrigenMarca#CORRECCION_APROBADA}); con motivo. Una FALTA_JUSTIFICADA solo por solicitud aprobada (A1).</li>
 *   <li>{@link #justificacion}: la familia pasa una FALTA a FALTA_JUSTIFICADA, con un motivo de la lista y una nota
 *       opcional corta.</li>
 *   <li>{@link #reversion}: Dirección deja otra vez la FALTA, con motivo.</li>
 * </ul>
 * La {@code fuente} es siempre {@link FuenteAsistencia#DOCENTE} (A15).
 */
@Entity
@Table(name = "marca_asistencia")
public class MarcaAsistencia extends BaseEntity {

	public static final int MIN_MOTIVO = 10;

	public static final int MAX_MOTIVO = 300;

	public static final int MAX_NOTA = 200;

	@Column(name = "registro_id", nullable = false, updatable = false)
	private Long registroId;

	@Column(name = "seccion_id", nullable = false, updatable = false)
	private Long seccionId;

	@Column(nullable = false, updatable = false)
	private LocalDate fecha;

	@Column(name = "alumno_id", nullable = false, updatable = false)
	private Long alumnoId;

	@Column(name = "matricula_id", nullable = false, updatable = false)
	private Long matriculaId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private EstadoAsistencia estado;

	@Column(name = "hora_llegada", updatable = false)
	private LocalTime horaLlegada;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private FuenteAsistencia fuente;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 24)
	private OrigenMarca origen;

	@Column(updatable = false)
	private Boolean primera;

	@Column(name = "reemplaza_id", updatable = false)
	private Long reemplazaId;

	@Column(name = "solicitud_id", updatable = false)
	private Long solicitudId;

	@Column(updatable = false, length = MAX_MOTIVO)
	private String motivo;

	@Enumerated(EnumType.STRING)
	@Column(name = "motivo_justificacion", updatable = false, length = 12)
	private MotivoJustificacion motivoJustificacion;

	@Column(name = "nota_justificacion", updatable = false, length = MAX_NOTA)
	private String notaJustificacion;

	@Column(name = "apoderado_id", updatable = false)
	private Long apoderadoId;

	@Column(name = "familia_id", updatable = false)
	private Long familiaId;

	@Column(name = "sesion_id", nullable = false, updatable = false)
	private Long sesionId;

	protected MarcaAsistencia() {
		// requerido por JPA
	}

	/** La marca del registro (estado inicial del alumno), a nombre de quien registra. */
	public static MarcaAsistencia inicial(RegistroAsistencia registro, Long alumnoId, Long matriculaId,
			EstadoAsistencia estado, LocalTime horaLlegada, Long sesionId) {
		if (estado == EstadoAsistencia.FALTA_JUSTIFICADA) {
			throw new ReglaNegocioException("Una falta justificada la registra la familia desde el portal (o una "
					+ "corrección aprobada por Dirección), no la hoja del día.");
		}
		MarcaAsistencia m = base(registro.getId(), registro.getSeccionId(), registro.getFecha(), alumnoId, matriculaId,
				estado, horaLlegada, sesionId);
		m.origen = OrigenMarca.REGISTRO;
		m.primera = Boolean.TRUE;
		return m;
	}

	/** Una corrección del docente (el mismo día) o aprobada por Dirección (con su solicitud): reemplaza a la vigente. */
	public static MarcaAsistencia correccion(MarcaAsistencia vigente, EstadoAsistencia nuevo, LocalTime horaLlegada,
			String motivo, Long solicitudId, Long sesionId) {
		OrigenMarca origen = solicitudId == null ? OrigenMarca.CORRECCION_DEL_DIA : OrigenMarca.CORRECCION_APROBADA;
		if (nuevo == EstadoAsistencia.FALTA_JUSTIFICADA && origen == OrigenMarca.CORRECCION_DEL_DIA) {
			// A1: el personal no convierte una falta en justificada sin una solicitud aprobada.
			throw new ReglaNegocioException("Una falta justificada solo la registra la familia o una corrección aprobada "
					+ "por Dirección: pide la corrección.");
		}
		MarcaAsistencia m = sucesora(vigente, nuevo, horaLlegada, sesionId);
		m.origen = origen;
		m.solicitudId = solicitudId;
		m.motivo = motivoValido(motivo);
		return m;
	}

	/** La familia justifica una FALTA (motivo de la lista; nota opcional de hasta 200 caracteres). */
	public static MarcaAsistencia justificacion(MarcaAsistencia vigente, MotivoJustificacion motivo, String nota,
			Long apoderadoId, Long familiaId, Long sesionId) {
		if (vigente.estado != EstadoAsistencia.FALTA) {
			throw new ReglaNegocioException("Solo se justifica una falta.");
		}
		if (vigente.origen == OrigenMarca.REVERSION_JUSTIFICACION) {
			throw new ReglaNegocioException("Dirección revirtió la justificación de esta falta: ya no se puede justificar "
					+ "desde el portal. Si es un error, comuníquese con el colegio.");
		}
		if (motivo == null) {
			throw new ReglaNegocioException("Elige el motivo de la falta.");
		}
		String limpia = Normalizador.limpiar(nota);
		if (limpia != null && limpia.length() > MAX_NOTA) {
			throw new ReglaNegocioException("La nota puede tener hasta " + MAX_NOTA + " caracteres.");
		}
		MarcaAsistencia m = sucesora(vigente, EstadoAsistencia.FALTA_JUSTIFICADA, null, sesionId);
		m.origen = OrigenMarca.JUSTIFICACION;
		m.motivoJustificacion = motivo;
		m.notaJustificacion = limpia;
		m.apoderadoId = Objects.requireNonNull(apoderadoId, "apoderadoId");
		m.familiaId = Objects.requireNonNull(familiaId, "familiaId");
		return m;
	}

	/** Dirección revierte una justificación: la marca vuelve a FALTA, con motivo. */
	public static MarcaAsistencia reversion(MarcaAsistencia vigente, String motivo, Long sesionId) {
		if (vigente.origen != OrigenMarca.JUSTIFICACION) {
			throw new ReglaNegocioException("Solo se revierte una falta que justificó la familia.");
		}
		MarcaAsistencia m = sucesora(vigente, EstadoAsistencia.FALTA, null, sesionId);
		m.origen = OrigenMarca.REVERSION_JUSTIFICACION;
		m.motivo = motivoValido(motivo);
		return m;
	}

	private static MarcaAsistencia sucesora(MarcaAsistencia vigente, EstadoAsistencia nuevo, LocalTime horaLlegada,
			Long sesionId) {
		Objects.requireNonNull(vigente, "vigente");
		if (nuevo == null) {
			throw new ReglaNegocioException("Elige el estado correcto.");
		}
		if (nuevo == vigente.estado && Objects.equals(horaLlegada(nuevo, horaLlegada), vigente.horaLlegada)) {
			throw new ReglaNegocioException("La marca ya está en " + nuevo.etiqueta().toLowerCase(java.util.Locale.ROOT)
					+ ": no hay nada que corregir.");
		}
		if (nuevo == vigente.estado) {
			throw new ReglaNegocioException("Una corrección cambia el estado (para la hora de llegada, corrige primero a "
					+ "otro estado).");
		}
		MarcaAsistencia m = base(vigente.registroId, vigente.seccionId, vigente.fecha, vigente.alumnoId,
				vigente.matriculaId, nuevo, horaLlegada, sesionId);
		m.reemplazaId = Objects.requireNonNull(vigente.getId(), "id de la marca vigente");
		return m;
	}

	private static MarcaAsistencia base(Long registroId, Long seccionId, LocalDate fecha, Long alumnoId,
			Long matriculaId, EstadoAsistencia estado, LocalTime horaLlegada, Long sesionId) {
		MarcaAsistencia m = new MarcaAsistencia();
		m.registroId = Objects.requireNonNull(registroId, "registroId");
		m.seccionId = Objects.requireNonNull(seccionId, "seccionId");
		m.fecha = Objects.requireNonNull(fecha, "fecha");
		m.alumnoId = Objects.requireNonNull(alumnoId, "alumnoId");
		m.matriculaId = Objects.requireNonNull(matriculaId, "matriculaId");
		m.estado = Objects.requireNonNull(estado, "estado");
		m.horaLlegada = horaLlegada(estado, horaLlegada);
		m.fuente = FuenteAsistencia.DOCENTE;
		m.sesionId = Objects.requireNonNull(sesionId, "sesionId");
		return m;
	}

	/** La hora de llegada solo en TARDE (CHECK), al minuto. */
	private static LocalTime horaLlegada(EstadoAsistencia estado, LocalTime hora) {
		return estado == EstadoAsistencia.TARDE && hora != null ? hora.truncatedTo(ChronoUnit.MINUTES) : null;
	}

	static String motivoValido(String motivo) {
		String limpio = Normalizador.limpiar(motivo);
		if (limpio == null || limpio.length() < MIN_MOTIVO || limpio.length() > MAX_MOTIVO) {
			throw new ReglaNegocioException("Escribe el motivo de la corrección, en " + MIN_MOTIVO + " a " + MAX_MOTIVO
					+ " caracteres.");
		}
		return limpio;
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("La asistencia es de solo inserción: una marca no se edita; se corrige con otra.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La asistencia es de solo inserción: una marca no se borra.");
	}

	public Long getRegistroId() {
		return registroId;
	}

	public Long getSeccionId() {
		return seccionId;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public Long getMatriculaId() {
		return matriculaId;
	}

	public EstadoAsistencia getEstado() {
		return estado;
	}

	public LocalTime getHoraLlegada() {
		return horaLlegada;
	}

	public FuenteAsistencia getFuente() {
		return fuente;
	}

	public OrigenMarca getOrigen() {
		return origen;
	}

	public boolean esPrimera() {
		return Boolean.TRUE.equals(primera);
	}

	public Long getReemplazaId() {
		return reemplazaId;
	}

	public Long getSolicitudId() {
		return solicitudId;
	}

	public String getMotivo() {
		return motivo;
	}

	public MotivoJustificacion getMotivoJustificacion() {
		return motivoJustificacion;
	}

	public String getNotaJustificacion() {
		return notaJustificacion;
	}

	public Long getApoderadoId() {
		return apoderadoId;
	}

	public Long getFamiliaId() {
		return familiaId;
	}

	public Long getSesionId() {
		return sesionId;
	}
}
