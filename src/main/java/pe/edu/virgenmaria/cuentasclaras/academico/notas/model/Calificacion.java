package pe.edu.virgenmaria.cuentasclaras.academico.notas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 10, tanda 2 (secciones 3.5 y 3.6): una nota de un alumno en una competencia y un periodo. SOLO INSERCIÓN, en cadena
 * lineal: cambiar es agregar una fila que reemplaza a la vigente ({@code reemplazaId}); la vigente es la que nadie
 * reemplaza. Nada cambia ni se borra ({@code updatable = false}, sin GRANT de UPDATE ni DELETE: 1142). La conclusión va con
 * su SHA-256 (la bitácora y la solicitud guardan la huella, nunca el texto: puede tener datos sensibles). La matrícula es la
 * del momento (ACTIVA o RESERVADA: C1).
 */
@Entity
@Table(name = "calificacion")
public class Calificacion extends BaseEntity {

	public static final int MIN_CONCLUSION = 10;

	@Column(name = "anio_escolar_id", nullable = false, updatable = false)
	private Long anioEscolarId;

	@Column(name = "periodo_id", nullable = false, updatable = false)
	private Long periodoId;

	@Column(name = "seccion_id", nullable = false, updatable = false)
	private Long seccionId;

	@Column(name = "matricula_id", nullable = false, updatable = false)
	private Long matriculaId;

	@Column(name = "alumno_id", nullable = false, updatable = false)
	private Long alumnoId;

	@Column(name = "competencia_id", nullable = false, updatable = false)
	private Long competenciaId;

	@Enumerated(EnumType.STRING)
	@Column(name = "nivel_logro", updatable = false, length = 2)
	private NivelLogro nivelLogro;

	@Column(updatable = false, length = 1000)
	private String conclusion;

	@Column(name = "conclusion_sha256", updatable = false, length = 64)
	private String conclusionSha256;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private OrigenCalificacion origen;

	@Column(updatable = false)
	private Boolean inicial;

	@Column(name = "reemplaza_id", updatable = false)
	private Long reemplazaId;

	@Column(name = "lote_id", updatable = false)
	private Long loteId;

	@Column(name = "solicitud_id", updatable = false)
	private Long solicitudId;

	@Column(name = "registrado_bd", updatable = false)
	private LocalDateTime registradoBd;

	protected Calificacion() {
		// requerido por JPA
	}

	/** Los datos de quién, dónde y en qué competencia (comunes a todo origen). */
	public record Donde(Long anioEscolarId, Long periodoId, Long seccionId, Long matriculaId, Long alumnoId,
			Long competenciaId) {
	}

	/** La primera nota de la competencia en el periodo (con un lote del periodo abierto, o tardía con su solicitud). */
	public static Calificacion inicial(Donde d, NivelLogro nivel, String conclusion, Long loteId, boolean tardia,
			LocalDateTime ahora) {
		Calificacion c = base(d, nivel, conclusion, ahora);
		c.origen = tardia ? OrigenCalificacion.REGISTRO_TARDIO : OrigenCalificacion.REGISTRO;
		c.inicial = Boolean.TRUE;
		c.loteId = Objects.requireNonNull(loteId, "loteId");
		if (nivel == null) {
			throw new IllegalArgumentException("La primera nota tiene un nivel de logro");
		}
		return c;
	}

	/** Un cambio con el periodo abierto: reemplaza la vigente (puede dejarla «sin nota»). */
	public static Calificacion cambio(Calificacion vigente, NivelLogro nivel, String conclusion, Long loteId,
			LocalDateTime ahora) {
		return cambio(vigente, vigente.getSeccionId(), nivel, conclusion, loteId, ahora);
	}

	/**
	 * QA del sprint 10: el cambio con el periodo abierto en la sección del lote. Si el alumno se trasladó a mitad de periodo,
	 * la nota vigente es de su sección anterior y el cambio queda en la nueva (la del lote y la de su matrícula: así lo exige
	 * trg_calificacion_nace); la cadena sigue siendo la de la matrícula.
	 */
	public static Calificacion cambio(Calificacion vigente, Long seccionId, NivelLogro nivel, String conclusion, Long loteId,
			LocalDateTime ahora) {
		Donde antes = vigente.donde();
		Calificacion c = base(new Donde(antes.anioEscolarId(), antes.periodoId(), Objects.requireNonNull(seccionId, "seccion"),
				antes.matriculaId(), antes.alumnoId(), antes.competenciaId()), nivel, conclusion, ahora);
		c.origen = OrigenCalificacion.CAMBIO_ABIERTO;
		c.reemplazaId = Objects.requireNonNull(vigente.getId(), "vigente");
		c.loteId = Objects.requireNonNull(loteId, "loteId");
		return c;
	}

	/** La corrección aprobada (periodo cerrado): reemplaza la vigente con lo que dice la solicitud. */
	public static Calificacion correccion(Calificacion vigente, NivelLogro nivel, String conclusion, Long solicitudId,
			LocalDateTime ahora) {
		Calificacion c = base(vigente.donde(), nivel, conclusion, ahora);
		c.origen = OrigenCalificacion.CORRECCION_APROBADA;
		c.reemplazaId = Objects.requireNonNull(vigente.getId(), "vigente");
		c.solicitudId = Objects.requireNonNull(solicitudId, "solicitudId");
		return c;
	}

	private static Calificacion base(Donde d, NivelLogro nivel, String conclusion, LocalDateTime ahora) {
		Calificacion c = new Calificacion();
		c.anioEscolarId = Objects.requireNonNull(d.anioEscolarId(), "anio");
		c.periodoId = Objects.requireNonNull(d.periodoId(), "periodo");
		c.seccionId = Objects.requireNonNull(d.seccionId(), "seccion");
		c.matriculaId = Objects.requireNonNull(d.matriculaId(), "matricula");
		c.alumnoId = Objects.requireNonNull(d.alumnoId(), "alumno");
		c.competenciaId = Objects.requireNonNull(d.competenciaId(), "competencia");
		c.nivelLogro = nivel;
		if (conclusion != null && nivel == null) {
			throw new IllegalArgumentException("«Sin nota» no lleva conclusión");
		}
		c.conclusion = conclusion;
		c.conclusionSha256 = conclusion == null ? null : HuellaConclusion.de(conclusion);
		c.registradoBd = ahora;
		return c;
	}

	public Donde donde() {
		return new Donde(anioEscolarId, periodoId, seccionId, matriculaId, alumnoId, competenciaId);
	}

	/** Si esta nota es igual a lo pedido (mismo nivel y misma conclusión): entonces no se guarda una fila nueva. */
	public boolean igualA(NivelLogro nivel, String conclusionPedida) {
		return nivelLogro == nivel && Objects.equals(conclusion, conclusionPedida);
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("Una nota no se cambia: se reemplaza con otra fila.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Una nota no se borra.");
	}

	public Long getAnioEscolarId() {
		return anioEscolarId;
	}

	public Long getPeriodoId() {
		return periodoId;
	}

	public Long getSeccionId() {
		return seccionId;
	}

	public Long getMatriculaId() {
		return matriculaId;
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public Long getCompetenciaId() {
		return competenciaId;
	}

	public NivelLogro getNivelLogro() {
		return nivelLogro;
	}

	public String getConclusion() {
		return conclusion;
	}

	public String getConclusionSha256() {
		return conclusionSha256;
	}

	public OrigenCalificacion getOrigen() {
		return origen;
	}

	public Long getReemplazaId() {
		return reemplazaId;
	}

	public Long getLoteId() {
		return loteId;
	}

	public Long getSolicitudId() {
		return solicitudId;
	}
}
