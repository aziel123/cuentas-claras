package pe.edu.virgenmaria.cuentasclaras.academico.libreta.model;

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
 * Sprint 10, tanda 3 (secciones 3.7 y 3.13; decisión 170): una libreta emitida es una FOTO inmutable, no un archivo. Guarda
 * las marcas de agua (hasta qué nota, qué marca de asistencia y qué exoneración entra), la cabecera congelada (A3: si mañana
 * cambia el nombre, el tutor o la UGEL, esta libreta no cambia) y la huella del contenido; el PDF se regenera igual siempre
 * y se verifica contra la huella al descargarlo. SOLO INSERCIÓN y SOLO {@code sistema.libretas} (cc_sistema; cc_app no
 * tiene GRANT: E14). Una corrección o una reemisión es otra fila (la versión siguiente), nunca un cambio.
 */
@Entity
@Table(name = "libreta_emitida")
public class LibretaEmitida extends BaseEntity {

	public static final int MAX_CAMBIOS = 1000;

	/** Correcciones del sprint 10 (V38, hallazgo 2): el largo de la columna de los niveles canónicos. */
	public static final int MAX_NIVELES = 6000;

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

	@Column(name = "numero_version", nullable = false, updatable = false)
	private Integer numeroVersion;

	@Column(name = "reemplaza_id", updatable = false)
	private Long reemplazaId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 12)
	private MotivoLibreta motivo;

	@Column(name = "solicitud_id", updatable = false)
	private Long solicitudId;

	@Column(name = "hasta_calificacion_id", nullable = false, updatable = false)
	private Long hastaCalificacionId;

	@Column(name = "hasta_marca_id", nullable = false, updatable = false)
	private Long hastaMarcaId;

	@Column(name = "hasta_exoneracion_id", nullable = false, updatable = false)
	private Long hastaExoneracionId;

	@Column(name = "alumno_nombre", nullable = false, updatable = false, length = 190)
	private String alumnoNombre;

	@Column(name = "alumno_codigo", nullable = false, updatable = false, length = 20)
	private String alumnoCodigo;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_codigo", nullable = false, updatable = false, length = 18)
	private TipoCodigo tipoCodigo;

	@Column(name = "codigo_modular", updatable = false, length = 9)
	private String codigoModular;

	@Column(updatable = false, length = 80)
	private String dre;

	@Column(updatable = false, length = 80)
	private String ugel;

	@Column(name = "tutor_nombre", updatable = false, length = 190)
	private String tutorNombre;

	@Column(name = "director_nombre", nullable = false, updatable = false, length = 190)
	private String directorNombre;

	@Column(name = "huella_contenido", nullable = false, updatable = false, length = 64)
	private String huellaContenido;

	@Column(updatable = false, length = MAX_CAMBIOS)
	private String cambios;

	@Column(name = "registrado_bd", updatable = false)
	private LocalDateTime registradoBd;

	/**
	 * Correcciones del sprint 10 (V38, hallazgo 2): los niveles emitidos en su forma canónica ({@code FotoLibreta.niveles}).
	 * La reemisión vuelve a emitir estos mismos (trg_libreta_emitida_nace) y la bandeja muestra en qué difiere la base hoy.
	 * NULL en las libretas emitidas antes de V38.
	 */
	@Column(updatable = false, length = MAX_NIVELES)
	private String niveles;

	protected LibretaEmitida() {
		// requerido por JPA
	}

	/** Dónde: año, periodo, sección, matrícula y alumno. */
	public record Donde(Long anioEscolarId, Long periodoId, Long seccionId, Long matriculaId, Long alumnoId) {
	}

	/** Las marcas de agua de la foto (0 si no hay nada todavía). */
	public record Marcas(long calificacion, long marca, long exoneracion) {

		public Marcas {
			if (calificacion < 0 || marca < 0 || exoneracion < 0) {
				throw new IllegalArgumentException("Las marcas de agua no son negativas");
			}
		}

		/** Ninguna baja respecto de las de la versión anterior (lo exige también trg_libreta_emitida_nace). */
		public boolean noBajanDesde(Marcas anteriores) {
			return calificacion >= anteriores.calificacion && marca >= anteriores.marca
					&& exoneracion >= anteriores.exoneracion;
		}
	}

	/** La cabecera congelada (A3, M9): entra en la huella. */
	public record Cabecera(String alumnoNombre, String alumnoCodigo, TipoCodigo tipoCodigo, String codigoModular, String dre,
			String ugel, String tutorNombre, String directorNombre) {

		public Cabecera {
			Objects.requireNonNull(alumnoNombre, "alumnoNombre");
			Objects.requireNonNull(alumnoCodigo, "alumnoCodigo");
			Objects.requireNonNull(tipoCodigo, "tipoCodigo");
			Objects.requireNonNull(directorNombre, "directorNombre");
		}
	}

	/** La versión 1: lo informado al cierre. */
	public static LibretaEmitida alCierre(Donde d, Marcas marcas, Cabecera cabecera, String huella, LocalDateTime ahora) {
		LibretaEmitida l = base(d, marcas, cabecera, huella, ahora);
		l.numeroVersion = 1;
		l.motivo = MotivoLibreta.CIERRE;
		return l;
	}

	/** La versión siguiente por una corrección o nota tardía aprobada, con lo que cambió (sin conclusiones). */
	public static LibretaEmitida corregida(LibretaEmitida anterior, Marcas marcas, Cabecera cabecera, String huella,
			String cambios, LocalDateTime ahora) {
		LibretaEmitida l = siguiente(anterior, marcas, cabecera, huella, ahora);
		l.motivo = MotivoLibreta.CORRECCION;
		l.cambios = recortar(Objects.requireNonNull(cambios, "cambios"));
		return l;
	}

	/** La versión siguiente por una reemisión aprobada por Promotoría (A3, decisión 188). */
	public static LibretaEmitida reemitida(LibretaEmitida anterior, Marcas marcas, Cabecera cabecera, String huella,
			Long solicitudId, LocalDateTime ahora) {
		LibretaEmitida l = siguiente(anterior, marcas, cabecera, huella, ahora);
		l.motivo = MotivoLibreta.REEMISION;
		l.solicitudId = Objects.requireNonNull(solicitudId, "solicitudId");
		l.cambios = MotivoLibreta.TEXTO_REEMISION;
		return l;
	}

	private static LibretaEmitida siguiente(LibretaEmitida anterior, Marcas marcas, Cabecera cabecera, String huella,
			LocalDateTime ahora) {
		if (!marcas.noBajanDesde(anterior.marcas())) {
			throw new IllegalArgumentException("Las marcas de agua de una versión nueva no bajan");
		}
		LibretaEmitida l = base(anterior.donde(), marcas, cabecera, huella, ahora);
		l.numeroVersion = anterior.numeroVersion + 1;
		l.reemplazaId = Objects.requireNonNull(anterior.getId(), "anterior");
		return l;
	}

	private static LibretaEmitida base(Donde d, Marcas marcas, Cabecera cabecera, String huella, LocalDateTime ahora) {
		LibretaEmitida l = new LibretaEmitida();
		l.anioEscolarId = Objects.requireNonNull(d.anioEscolarId(), "anio");
		l.periodoId = Objects.requireNonNull(d.periodoId(), "periodo");
		l.seccionId = Objects.requireNonNull(d.seccionId(), "seccion");
		l.matriculaId = Objects.requireNonNull(d.matriculaId(), "matricula");
		l.alumnoId = Objects.requireNonNull(d.alumnoId(), "alumno");
		l.hastaCalificacionId = marcas.calificacion();
		l.hastaMarcaId = marcas.marca();
		l.hastaExoneracionId = marcas.exoneracion();
		l.alumnoNombre = cabecera.alumnoNombre();
		l.alumnoCodigo = cabecera.alumnoCodigo();
		l.tipoCodigo = cabecera.tipoCodigo();
		l.codigoModular = cabecera.codigoModular();
		l.dre = cabecera.dre();
		l.ugel = cabecera.ugel();
		l.tutorNombre = cabecera.tutorNombre();
		l.directorNombre = cabecera.directorNombre();
		l.huellaContenido = Objects.requireNonNull(huella, "huella");
		l.registradoBd = ahora;
		return l;
	}

	private static String recortar(String texto) {
		return texto.length() <= MAX_CAMBIOS ? texto : texto.substring(0, MAX_CAMBIOS - 1) + "…";
	}

	public Donde donde() {
		return new Donde(anioEscolarId, periodoId, seccionId, matriculaId, alumnoId);
	}

	public Marcas marcas() {
		return new Marcas(hastaCalificacionId, hastaMarcaId, hastaExoneracionId);
	}

	public Cabecera cabecera() {
		return new Cabecera(alumnoNombre, alumnoCodigo, tipoCodigo, codigoModular, dre, ugel, tutorNombre, directorNombre);
	}

	/** A4: el código de verificación impreso, {@code LIB-{id}-{16 primeros caracteres de la huella}}. */
	public String codigoVerificacion() {
		return "LIB-" + getId() + "-" + huellaContenido.substring(0, 16);
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("Una libreta emitida no se cambia: se emite la versión siguiente.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Una libreta emitida no se borra.");
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

	public int getNumeroVersion() {
		return numeroVersion;
	}

	public Long getReemplazaId() {
		return reemplazaId;
	}

	public MotivoLibreta getMotivo() {
		return motivo;
	}

	public Long getSolicitudId() {
		return solicitudId;
	}

	public String getHuellaContenido() {
		return huellaContenido;
	}

	public String getCambios() {
		return cambios;
	}

	public LocalDateTime getRegistradoBd() {
		return registradoBd;
	}

	public String getNiveles() {
		return niveles;
	}

	/** Fija los niveles canónicos ANTES de guardarla (una libreta guardada no cambia). */
	public LibretaEmitida conNiveles(String canonicos) {
		if (getId() != null) {
			throw new IllegalStateException("Una libreta emitida no se cambia: se emite la versión siguiente.");
		}
		Objects.requireNonNull(canonicos, "niveles");
		if (canonicos.length() > MAX_NIVELES) {
			throw new IllegalStateException("Los niveles de la libreta pasan de " + MAX_NIVELES + " caracteres.");
		}
		this.niveles = canonicos;
		return this;
	}
}
