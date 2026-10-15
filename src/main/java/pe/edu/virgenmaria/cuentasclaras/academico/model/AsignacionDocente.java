package pe.edu.virgenmaria.cuentasclaras.academico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 9, tanda 1 (sección 3.2, V32): un docente, una sección, un año y un tipo. Es lo que da acceso a los nombres de los
 * alumnos de esa sección (y, en las tandas 2 y 3, a su asistencia y a escribirles comunicados), así que:
 * <ul>
 *   <li>nace PROPUESTA (la propone Administración o Dirección) y pasa a VIGENTE solo con su solicitud
 *       {@code ASIGNACION_DOCENTE} aprobada y firmada por OTRA persona de Dirección o Promotoría que no es el docente;</li>
 *   <li>la termina Dirección o Promotoría con su firma, o {@code sistema.academico} (año cerrado o cuenta desactivada);
 *       terminar no borra lo que ese docente registró;</li>
 *   <li>el docente, la sección, el año, el tipo, las áreas y quién la propuso no cambian ({@code updatable = false} y GRANT
 *       por columna: 1143; lo comprueba {@code InmutabilidadAcademicoTest}). Nada se borra.</li>
 * </ul>
 * Las banderas {@code pendiente}, {@code vigente} y {@code tutorVigente} valen TRUE o NULL: así los UNIQUE de V32 admiten
 * una sola propuesta pendiente y una sola vigente por docente, sección y tipo, y un solo tutor vigente por sección.
 */
@Entity
@Table(name = "asignacion_docente")
public class AsignacionDocente extends BaseEntity {

	public static final int MAX_AREAS = 120;

	public static final int MIN_MOTIVO_FIN = 10;

	public static final int MAX_MOTIVO_FIN = 300;

	@Column(name = "anio_escolar_id", nullable = false, updatable = false)
	private Long anioEscolarId;

	@Column(name = "seccion_id", nullable = false, updatable = false)
	private Long seccionId;

	/** La cuenta del docente (personal con rol DOCENTE). */
	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private TipoAsignacion tipo;

	@Column(updatable = false, length = MAX_AREAS)
	private String areas;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private EstadoAsignacion estado;

	@Column
	private Boolean pendiente;

	@Column
	private Boolean vigente;

	@Column(name = "tutor_vigente")
	private Boolean tutorVigente;

	@Column(name = "solicitud_id")
	private Long solicitudId;

	@Column(name = "propuesto_por", nullable = false, updatable = false, length = 60)
	private String propuestoPor;

	@Column(name = "resuelto_por", length = 60)
	private String resueltoPor;

	@Column(name = "resuelto_en")
	private LocalDateTime resueltoEn;

	@Column(name = "terminada_por", length = 60)
	private String terminadaPor;

	@Column(name = "terminada_en")
	private LocalDateTime terminadaEn;

	@Column(name = "motivo_fin", length = MAX_MOTIVO_FIN)
	private String motivoFin;

	protected AsignacionDocente() {
		// requerido por JPA
	}

	/** Una propuesta nueva, a nombre de quien la propone (que debe ser la persona en sesión: CHECK con creado_por). */
	public static AsignacionDocente propuesta(Long anioEscolarId, Long seccionId, Long usuarioId, TipoAsignacion tipo,
			String areas, String propuestoPor) {
		AsignacionDocente a = new AsignacionDocente();
		a.anioEscolarId = Objects.requireNonNull(anioEscolarId, "anioEscolarId");
		a.seccionId = Objects.requireNonNull(seccionId, "seccionId");
		a.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
		if (tipo == null) {
			throw new ReglaNegocioException("Elige qué hará en la sección: tutor(a), docente de área o auxiliar.");
		}
		a.tipo = tipo;
		a.areas = areasValidas(tipo, areas);
		a.propuestoPor = Objects.requireNonNull(propuestoPor, "propuestoPor");
		a.estado = EstadoAsignacion.PROPUESTA;
		a.pendiente = Boolean.TRUE;
		return a;
	}

	/** Las áreas («Matemática, Ciencia y Tecnología»): texto limpio de hasta 120; el auxiliar no tiene áreas. */
	public static String areasValidas(TipoAsignacion tipo, String areas) {
		String limpio = Normalizador.limpiar(areas);
		if (limpio == null) {
			return null;
		}
		if (tipo == TipoAsignacion.AUXILIAR) {
			throw new ReglaNegocioException("El auxiliar de educación no dicta áreas: deja ese campo vacío.");
		}
		if (limpio.length() > MAX_AREAS) {
			throw new ReglaNegocioException("Las áreas pueden tener hasta " + MAX_AREAS + " caracteres.");
		}
		return limpio;
	}

	/** Enlaza la solicitud que la aprueba (se crea después de la propuesta: la solicitud la nombra por su id). */
	public void enlazarSolicitud(Long id) {
		exigir(EstadoAsignacion.PROPUESTA, "enlazar su solicitud");
		if (solicitudId != null) {
			throw new IllegalStateException("La propuesta ya tiene su solicitud");
		}
		solicitudId = Objects.requireNonNull(id, "solicitudId");
	}

	/** Otra persona la aprobó con su firma: desde ahora el docente ve la sección. */
	public void aprobar(String aprobador, LocalDateTime ahora) {
		exigir(EstadoAsignacion.PROPUESTA, "aprobarla");
		Objects.requireNonNull(aprobador, "aprobador");
		if (aprobador.equals(propuestoPor)) {
			throw new IllegalStateException("Quien propone una asignación no la aprueba");
		}
		estado = EstadoAsignacion.VIGENTE;
		pendiente = null;
		vigente = Boolean.TRUE;
		tutorVigente = tipo == TipoAsignacion.TUTOR ? Boolean.TRUE : null;
		resueltoPor = aprobador;
		resueltoEn = Objects.requireNonNull(ahora, "ahora");
	}

	public void rechazar(String quien, LocalDateTime ahora) {
		exigir(EstadoAsignacion.PROPUESTA, "rechazarla");
		estado = EstadoAsignacion.RECHAZADA;
		pendiente = null;
		resueltoPor = Objects.requireNonNull(quien, "quien");
		resueltoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** Termina una vigente: el docente deja de ver la sección; lo que registró sigue a su nombre. */
	public void terminar(String quien, String motivo, LocalDateTime ahora) {
		exigir(EstadoAsignacion.VIGENTE, "terminarla");
		String limpio = Normalizador.limpiar(motivo);
		if (limpio == null || limpio.length() < MIN_MOTIVO_FIN || limpio.length() > MAX_MOTIVO_FIN) {
			throw new ReglaNegocioException("Escribe por qué termina la asignación, en " + MIN_MOTIVO_FIN + " a "
					+ MAX_MOTIVO_FIN + " caracteres.");
		}
		estado = EstadoAsignacion.TERMINADA;
		vigente = null;
		tutorVigente = null;
		terminadaPor = Objects.requireNonNull(quien, "quien");
		terminadaEn = Objects.requireNonNull(ahora, "ahora");
		motivoFin = limpio;
	}

	private void exigir(EstadoAsignacion esperado, String accion) {
		if (estado != esperado) {
			throw new ReglaNegocioException("No se puede " + accion + ": la asignación está "
					+ estado.etiqueta().toLowerCase(java.util.Locale.ROOT) + ".");
		}
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las asignaciones docentes no se borran: se terminan o se rechazan.");
	}

	public boolean esVigente() {
		return estado == EstadoAsignacion.VIGENTE;
	}

	public Long getAnioEscolarId() {
		return anioEscolarId;
	}

	public Long getSeccionId() {
		return seccionId;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public TipoAsignacion getTipo() {
		return tipo;
	}

	public String getAreas() {
		return areas;
	}

	public EstadoAsignacion getEstado() {
		return estado;
	}

	public Long getSolicitudId() {
		return solicitudId;
	}

	public String getPropuestoPor() {
		return propuestoPor;
	}

	public String getResueltoPor() {
		return resueltoPor;
	}

	public LocalDateTime getResueltoEn() {
		return resueltoEn;
	}

	public String getTerminadaPor() {
		return terminadaPor;
	}

	public LocalDateTime getTerminadaEn() {
		return terminadaEn;
	}

	public String getMotivoFin() {
		return motivoFin;
	}
}
