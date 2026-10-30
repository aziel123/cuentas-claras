package pe.edu.virgenmaria.cuentasclaras.colegio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Colegio cliente de la plataforma. Toda entidad de negocio referencia a un colegio mediante {@code colegioId}
 * (multi-colegio). No extiende {@code BaseEntity}: no es «de» un colegio.
 * <p>
 * Sprint 8, tanda 1 (V28): tiene {@link TipoColegio tipo} (la fila {@code PLATAFORMA} es donde viven los operadores),
 * {@link EstadoColegio estado} (en {@code PREPARACION} la base no deja registrar pagos), la ficha (razón social, RUC,
 * dirección, correo institucional, niveles, titular y contrato) y la aprobación del aviso de privacidad. {@code tipo},
 * {@code creadoPor} y {@code creadoEn} no cambian; nunca se borra. En MySQL, {@code trg_colegio_nace} y
 * {@code trg_colegio_cambio} vigilan quién lo crea y quién lo pasa a producción.
 */
@Entity
@Table(name = "colegio")
@EntityListeners(AuditingEntityListener.class)
// Solo las columnas que cambian: cada conexión de MySQL tiene GRANT sobre columnas distintas (el operador con cc_sistema,
// la Promotoría con cc_app) y un UPDATE de toda la fila daría 1143.
@org.hibernate.annotations.DynamicUpdate
public class Colegio {

	public static final int MAX_NOMBRE = 150;

	/** Correcciones del sprint 11 (A2): el retiro de la autorización de conservación espera 24 horas. */
	public static final int HORAS_RETIRO_CONSERVACION = 24;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = MAX_NOMBRE)
	private String nombre;

	@Column(length = 11)
	private String ruc;

	@Column(nullable = false, updatable = false)
	private boolean activo = true;

	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private LocalDateTime creadoEn;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 12)
	private TipoColegio tipo = TipoColegio.COLEGIO;

	/** {@code TRUE} solo en la fila PLATAFORMA (UNIQUE: una sola); {@code null} en los colegios. */
	@Column(name = "plataforma_unica", updatable = false)
	private Boolean plataformaUnica;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private EstadoColegio estado = EstadoColegio.PREPARACION;

	@Column(name = "razon_social", length = 150)
	private String razonSocial;

	@Column(length = 200)
	private String direccion;

	@Column(name = "correo_institucional", length = 150)
	private String correoInstitucional;

	/** Niveles que ofrece, en el orden del catálogo, separados por coma ({@code INICIAL,PRIMARIA}). */
	@Column(nullable = false, length = 40)
	private String niveles = "INICIAL,PRIMARIA,SECUNDARIA";

	@Column(name = "titular_nombre", length = 150)
	private String titularNombre;

	/** El DNI (o CE) del titular del contrato: lo confirma al activar su cuenta de Promotoría. */
	@Column(name = "titular_documento", length = 12)
	private String titularDocumento;

	@Column(name = "contrato_codigo", length = 30)
	private String contratoCodigo;

	@Column(name = "aviso_privacidad_version", length = 20)
	private String avisoPrivacidadVersion;

	@Column(name = "aviso_privacidad_aprobado_por", length = 60)
	private String avisoPrivacidadAprobadoPor;

	@Column(name = "aviso_privacidad_aprobado_en")
	private LocalDateTime avisoPrivacidadAprobadoEn;

	@Column(name = "produccion_desde")
	private LocalDateTime produccionDesde;

	@Column(name = "produccion_por", length = 60)
	private String produccionPor;

	/**
	 * Sprint 11, tanda 1 (V39): desde cuándo está en su estado. En MySQL la pone la base al cambiar el estado
	 * (trg_colegio_cambio, con el reloj real); en H2 la pone la aplicación con el mismo criterio.
	 */
	@Column(name = "estado_desde", nullable = false)
	private LocalDateTime estadoDesde;

	/** Sprint 11, tanda 1: el último cambio de estado aplicado ({@code cambio_estado_colegio}, dos operadores). */
	@Column(name = "cambio_estado_id")
	private Long cambioEstadoId;

	/** Sprint 11, tanda 1 (AP-11): cuándo se aplicó la suspensión (la pone la base; vuelve a {@code null} al reactivar). */
	@Column(name = "suspendido_en")
	private LocalDateTime suspendidoEn;

	/** Sprint 11, tanda 1 (AP-2): la facultad del titular, bloqueada por dos operadores. */
	@Column(name = "facultad_bloqueada", nullable = false)
	private boolean facultadBloqueada;

	/** Sprint 11, tanda 1 (AP-2): el BLOQUEO_FACULTAD o DESBLOQUEO_FACULTAD que la dejó como está. */
	@Column(name = "facultad_cambio_id")
	private Long facultadCambioId;

	/** Sprint 11, tanda 2 (AP-11 y AP2-9): el titular autorizó conservar los datos 12 meses (con su firma). */
	@Column(name = "conservacion_autorizada_por", length = 60)
	private String conservacionAutorizadaPor;

	/** La hora la pone la base (trg_colegio_cambio); en H2, la aplicación. */
	@Column(name = "conservacion_autorizada_en")
	private LocalDateTime conservacionAutorizadaEn;

	/** Sprint 11, tanda 2 (AP2-10): la retención legal (un operador la pone y otro la quita). */
	/** Correcciones del sprint 11 (A2, V42): el retiro pedido por el titular; se aplica a las 24 horas (la base). */
	@Column(name = "conservacion_retiro_por", length = 60)
	private String conservacionRetiroPor;

	@Column(name = "conservacion_retiro_pedido_en")
	private LocalDateTime conservacionRetiroPedidoEn;

	@Column(name = "conservacion_retiro_aplica_desde")
	private LocalDateTime conservacionRetiroAplicaDesde;

	@Column(name = "conservacion_codigo_mensaje_id")
	private Long conservacionCodigoMensajeId;

	@Column(name = "conservacion_codigo_sha256", length = 64)
	private String conservacionCodigoSha256;

	@Column(name = "retencion_legal_motivo", length = 500)
	private String retencionLegalMotivo;

	@Column(name = "retencion_legal_desde")
	private LocalDateTime retencionLegalDesde;

	@Column(name = "retencion_legal_por", length = 60)
	private String retencionLegalPor;

	/** Sprint 11, tanda 2 (AP2-7): el acta EJECUTADA de un colegio SUPRIMIDO (la anota el DBA). */
	@Column(name = "acta_supresion_id", insertable = false, updatable = false)
	private Long actaSupresionId;

	/** Columna generada: cuándo se cerró (el estado_desde del CERRADO). Nadie la escribe. */
	@Column(name = "cerrado_en", insertable = false, updatable = false)
	private LocalDateTime cerradoEn;

	/**
	 * Columna generada (AP-7 y AP-11): hasta cuándo se conservan los datos. Con la autorización del titular, la suspensión
	 * + 12 meses; sin ella, el cierre + 30 días; una BAJA, + 30 días. Nadie la escribe.
	 */
	@Column(name = "conservar_hasta", insertable = false, updatable = false)
	private java.time.LocalDate conservarHasta;

	@CreatedBy
	@Column(name = "creado_por", nullable = false, updatable = false, length = 60)
	private String creadoPor;

	@LastModifiedDate
	@Column(name = "actualizado_en", nullable = false)
	private LocalDateTime actualizadoEn;

	/**
	 * Correcciones del sprint 8 (S8-M5): quién cambió la ficha por última vez. trg_colegio_cambio exige su firma de la
	 * versión anterior ({@code colegio:{id}:FICHA:{version}}): el operador en preparación (antes de que el titular active
	 * su cuenta) o la Promotoría del colegio.
	 */
	@Column(name = "actualizado_por", length = 60)
	private String actualizadoPor;

	@Version
	@Column(nullable = false)
	private Long version;

	protected Colegio() {
		// requerido por JPA
	}

	/**
	 * Un colegio que ya opera (datos de demostración y pruebas): nace directamente en producción, puesto ahí por quien lo
	 * crea. En MySQL solo lo puede insertar el administrador (cc_app no tiene INSERT y cc_sistema solo crea en preparación).
	 */
	public Colegio(String nombre) {
		this.nombre = Objects.requireNonNull(nombre, "nombre");
		this.estado = EstadoColegio.PRODUCCION;
	}

	/**
	 * Sprint 8, tanda 1 (sección 3.3): el alta de un colegio nuevo por el operador de la plataforma. Nace en preparación con
	 * su ficha completa; la base exige que lo cree un operador activo (trg_colegio_nace).
	 */
	public static Colegio enPreparacion(String nombre, String razonSocial, String ruc, String direccion,
			String correoInstitucional, Set<Nivel> niveles, String titularNombre, String titularDocumento,
			String contratoCodigo) {
		Colegio c = new Colegio();
		c.nombre = exigir(nombre, "el nombre del colegio", MAX_NOMBRE);
		c.estado = EstadoColegio.PREPARACION;
		c.razonSocial = exigir(razonSocial, "la razón social", 150);
		c.ruc = Objects.requireNonNull(ruc, "ruc");
		c.direccion = exigir(direccion, "la dirección", 200);
		c.correoInstitucional = exigir(correoInstitucional, "el correo institucional", 150);
		c.niveles = textoNiveles(niveles);
		c.titularNombre = exigir(titularNombre, "el nombre del titular", 150);
		c.titularDocumento = Objects.requireNonNull(titularDocumento, "titularDocumento");
		c.contratoCodigo = contratoCodigo == null || contratoCodigo.isBlank() ? null : contratoCodigo.strip();
		return c;
	}

	/** Corregir la ficha (operador en preparación, Promotoría en cualquier estado). El RUC no cambia en producción. */
	public void corregirFicha(String nombre, String razonSocial, String direccion, String correoInstitucional) {
		this.nombre = exigir(nombre, "el nombre del colegio", MAX_NOMBRE);
		this.razonSocial = exigir(razonSocial, "la razón social", 150);
		this.direccion = exigir(direccion, "la dirección", 200);
		this.correoInstitucional = exigir(correoInstitucional, "el correo institucional", 150);
	}

	/** Correcciones del sprint 8 (S8-M5): quien firma el cambio de la ficha (operador o Promotoría). */
	public void firmarCambioDeFicha(String por) {
		this.actualizadoPor = Objects.requireNonNull(por, "por");
	}

	/** Solo en preparación (en producción el RUC no cambia: lo exige la base). */
	public void cambiarRuc(String ruc) {
		if (estado == EstadoColegio.PRODUCCION) {
			throw new ReglaNegocioException("Un colegio en producción no cambia su RUC: sus comprobantes ya se emitieron con él.");
		}
		this.ruc = Objects.requireNonNull(ruc, "ruc");
	}

	public void cambiarNiveles(Set<Nivel> niveles) {
		this.niveles = textoNiveles(niveles);
	}

	public void cambiarTitular(String titularNombre, String titularDocumento, String contratoCodigo) {
		this.titularNombre = exigir(titularNombre, "el nombre del titular", 150);
		this.titularDocumento = Objects.requireNonNull(titularDocumento, "titularDocumento");
		this.contratoCodigo = contratoCodigo == null || contratoCodigo.isBlank() ? null : contratoCodigo.strip();
	}

	/**
	 * PREPARACION → PRODUCCION, una sola vez y sin vuelta atrás (decisión 116). En MySQL, trg_colegio_cambio exige que
	 * {@code por} sea una Promotoría activa de este colegio con su firma, y que haya Dirección y Caja activas.
	 */
	public void pasarAProduccion(String por, LocalDateTime ahora) {
		if (tipo != TipoColegio.COLEGIO) {
			throw new ReglaNegocioException("La plataforma no pasa a producción.");
		}
		if (estado == EstadoColegio.PRODUCCION) {
			throw new ReglaNegocioException("El colegio ya está en producción.");
		}
		this.estado = EstadoColegio.PRODUCCION;
		this.produccionPor = Objects.requireNonNull(por, "por");
		this.produccionDesde = Objects.requireNonNull(ahora, "ahora");
		this.estadoDesde = ahora;
	}

	/**
	 * Sprint 11, tanda 1 (sección 3.3): aplica el cambio de estado APROBADO por dos operadores ({@code cambioId}). Solo
	 * cambia el estado, el enlace y las horas (que en MySQL pone la base: trg_colegio_cambio exige ese cambio aprobado, del
	 * mismo origen y destino, y vencido). Lo llama solo {@code sistema.plataforma}.
	 */
	public void aplicarCambioDeEstado(Long cambioId, EstadoColegio origen, EstadoColegio destino, LocalDateTime ahora) {
		if (tipo != TipoColegio.COLEGIO) {
			throw new ReglaNegocioException("La plataforma no cambia de estado.");
		}
		if (estado != origen) {
			throw new ReglaNegocioException("El colegio está " + estado.etiqueta().toLowerCase(java.util.Locale.ROOT)
					+ " y el cambio era desde «" + origen.etiqueta() + "».");
		}
		this.estado = Objects.requireNonNull(destino, "destino");
		this.cambioEstadoId = Objects.requireNonNull(cambioId, "cambioId");
		this.estadoDesde = Objects.requireNonNull(ahora, "ahora");
		if (destino == EstadoColegio.SUSPENDIDO) {
			this.suspendidoEn = ahora;
		}
		else if (origen == EstadoColegio.SUSPENDIDO && destino == EstadoColegio.PRODUCCION) {
			this.suspendidoEn = null;
		}
	}

	/**
	 * Sprint 11, tanda 2 (AP-11 y AP2-9): el titular autoriza conservar los datos 12 meses después de la suspensión. Solo
	 * mientras el colegio opera: desde la suspensión queda congelada (también en la base).
	 */
	public void autorizarConservacion(String titular, LocalDateTime ahora) {
		exigirConservacionEditable();
		if (conservacionAutorizadaPor != null) {
			throw new ReglaNegocioException("La conservación ya está autorizada.");
		}
		this.conservacionAutorizadaPor = Objects.requireNonNull(titular, "titular");
		this.conservacionAutorizadaEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** Correcciones (A2): el titular PIDE el retiro (con su firma y su código); se aplica a las 24 horas si nadie lo frena. */
	public void pedirRetiroConservacion(String titular, Long codigoMensajeId, String codigoSha256, LocalDateTime ahora) {
		exigirConservacionEditable();
		if (conservacionAutorizadaPor == null) {
			throw new ReglaNegocioException("La conservación no está autorizada.");
		}
		if (conservacionRetiroPor != null) {
			throw new ReglaNegocioException("Ya pediste retirar la autorización: se aplica el "
					+ conservacionRetiroAplicaDesde + " si nadie lo frena.");
		}
		this.conservacionRetiroPor = Objects.requireNonNull(titular, "titular");
		this.conservacionCodigoMensajeId = Objects.requireNonNull(codigoMensajeId, "codigoMensajeId");
		this.conservacionCodigoSha256 = Objects.requireNonNull(codigoSha256, "codigoSha256");
		this.conservacionRetiroPedidoEn = ahora;
		this.conservacionRetiroAplicaDesde = ahora.plusHours(HORAS_RETIRO_CONSERVACION);
	}

	/** Correcciones (A2): sistema.plataforma aplica el retiro vencido (24 horas). */
	public void aplicarRetiroConservacion(LocalDateTime ahora) {
		if (conservacionRetiroPor == null || conservacionRetiroAplicaDesde == null
				|| conservacionRetiroAplicaDesde.isAfter(ahora)) {
			throw new ReglaNegocioException("El retiro de la conservación todavía no se aplica.");
		}
		this.conservacionAutorizadaPor = null;
		this.conservacionAutorizadaEn = null;
		this.conservacionRetiroPor = null;
		this.conservacionRetiroPedidoEn = null;
		this.conservacionRetiroAplicaDesde = null;
	}

	/** Correcciones (A2): un operador frena el retiro pedido (la autorización sigue). */
	public void frenarRetiroConservacion(String operador) {
		if (conservacionRetiroPor == null) {
			throw new ReglaNegocioException("No hay un retiro de la conservación por frenar.");
		}
		this.conservacionRetiroPor = null;
		this.conservacionRetiroPedidoEn = null;
		this.conservacionRetiroAplicaDesde = null;
		this.actualizadoPor = Objects.requireNonNull(operador, "operador");
	}

	public String getConservacionRetiroPor() {
		return conservacionRetiroPor;
	}

	public LocalDateTime getConservacionRetiroAplicaDesde() {
		return conservacionRetiroAplicaDesde;
	}

	private void exigirConservacionEditable() {
		if (estado != EstadoColegio.PREPARACION && estado != EstadoColegio.PRODUCCION) {
			throw new ReglaNegocioException("Desde la suspensión, la autorización de conservación queda como estaba.");
		}
	}

	/** Sprint 11, tanda 2 (AP2-10): un operador pone la retención legal (un pedido escrito de una autoridad o un litigio). */
	public void ponerRetencionLegal(String motivo, String operador, LocalDateTime ahora) {
		String texto = motivo == null ? "" : motivo.strip();
		if (texto.length() < 20 || texto.length() > 500) {
			throw new ReglaNegocioException("Escribe el motivo de la retención legal (de 20 a 500 caracteres): qué autoridad "
					+ "o qué proceso lo pide y su referencia.");
		}
		if (retencionLegalPor != null) {
			throw new ReglaNegocioException("El colegio ya tiene una retención legal.");
		}
		this.retencionLegalMotivo = texto;
		this.retencionLegalPor = Objects.requireNonNull(operador, "operador");
		this.retencionLegalDesde = ahora;
	}

	/** La quita OTRO operador (con su firma; queda como actualizado_por). */
	public void quitarRetencionLegal(String operador) {
		if (retencionLegalPor == null) {
			throw new ReglaNegocioException("El colegio no tiene retención legal.");
		}
		if (Objects.equals(operador, retencionLegalPor)) {
			throw new ReglaNegocioException("Tú pusiste la retención legal: la quita otro operador, con su propia sesión.");
		}
		this.retencionLegalMotivo = null;
		this.retencionLegalPor = null;
		this.retencionLegalDesde = null;
		this.actualizadoPor = operador;
	}

	/** Sprint 11, tanda 1 (AP-2): bloquea o desbloquea la facultad del titular con su cambio aprobado por dos operadores. */
	public void cambiarFacultad(boolean bloqueada, Long cambioId) {
		if (this.facultadBloqueada == bloqueada) {
			throw new ReglaNegocioException(bloqueada ? "La facultad del titular ya está bloqueada."
					: "La facultad del titular no está bloqueada.");
		}
		this.facultadBloqueada = bloqueada;
		this.facultadCambioId = Objects.requireNonNull(cambioId, "cambioId");
	}

	/** Dirección aprobó la versión vigente del aviso de privacidad del colegio (sección 3.5, paso 11). */
	public void aprobarAvisoPrivacidad(String version, String por, LocalDateTime ahora) {
		this.avisoPrivacidadVersion = Objects.requireNonNull(version, "version");
		this.avisoPrivacidadAprobadoPor = Objects.requireNonNull(por, "por");
		this.avisoPrivacidadAprobadoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** Un colegio creado ya en producción (constructor de demostración) toma la hora y el autor de su alta. */
	@PrePersist
	void completarAlNacer() {
		if (estado == EstadoColegio.PRODUCCION && produccionDesde == null) {
			produccionDesde = actualizadoEn;
			produccionPor = creadoPor == null ? "sistema" : creadoPor;
		}
		if (estadoDesde == null) {
			estadoDesde = produccionDesde != null ? produccionDesde : actualizadoEn;
		}
	}

	@PreRemove
	void impedirBorrado() {
		throw new UnsupportedOperationException("Un colegio no se borra");
	}

	private static String exigir(String valor, String que, int maximo) {
		String texto = valor == null ? "" : valor.strip();
		if (texto.isEmpty() || texto.length() > maximo) {
			throw new ReglaNegocioException("Escribe " + que + " (hasta " + maximo + " caracteres).");
		}
		return texto;
	}

	private static String textoNiveles(Set<Nivel> niveles) {
		if (niveles == null || niveles.isEmpty()) {
			throw new ReglaNegocioException("Elige al menos un nivel (Inicial, Primaria o Secundaria).");
		}
		return EnumSet.copyOf(niveles).stream().map(Enum::name).collect(Collectors.joining(","));
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getRuc() {
		return ruc;
	}

	public boolean isActivo() {
		return activo;
	}

	public LocalDateTime getCreadoEn() {
		return creadoEn;
	}

	public TipoColegio getTipo() {
		return tipo;
	}

	public boolean esPlataforma() {
		return tipo == TipoColegio.PLATAFORMA;
	}

	public EstadoColegio getEstado() {
		return estado;
	}

	public boolean enProduccion() {
		return estado == EstadoColegio.PRODUCCION;
	}

	/**
	 * Sprint 11, tanda 2 (3.8 y 3.10): las familias entran a consultar en SUSPENDIDO y, en CERRADO, hasta
	 * {@code conservar_hasta} inclusive (la base lo exige igual: trg_sesion_usuario_nace). Después, nadie entra.
	 */
	public boolean consultaFamilias(java.time.LocalDate hoy) {
		if (estado == EstadoColegio.SUSPENDIDO) {
			return true;
		}
		return estado == EstadoColegio.CERRADO && conservarHasta != null && !hoy.isAfter(conservarHasta);
	}

	/** Sprint 11, tanda 1: el colegio opera (preparación o producción): su personal ingresa. */
	public boolean opera() {
		return estado.opera();
	}

	public LocalDateTime getEstadoDesde() {
		return estadoDesde;
	}

	public Long getCambioEstadoId() {
		return cambioEstadoId;
	}

	public LocalDateTime getSuspendidoEn() {
		return suspendidoEn;
	}

	public String getConservacionAutorizadaPor() {
		return conservacionAutorizadaPor;
	}

	public LocalDateTime getConservacionAutorizadaEn() {
		return conservacionAutorizadaEn;
	}

	public boolean conservacionAutorizada() {
		return conservacionAutorizadaPor != null;
	}

	public String getRetencionLegalMotivo() {
		return retencionLegalMotivo;
	}

	public LocalDateTime getRetencionLegalDesde() {
		return retencionLegalDesde;
	}

	public String getRetencionLegalPor() {
		return retencionLegalPor;
	}

	public boolean conRetencionLegal() {
		return retencionLegalPor != null;
	}

	public Long getActaSupresionId() {
		return actaSupresionId;
	}

	public LocalDateTime getCerradoEn() {
		return cerradoEn;
	}

	public java.time.LocalDate getConservarHasta() {
		return conservarHasta;
	}

	public boolean isFacultadBloqueada() {
		return facultadBloqueada;
	}

	public Long getFacultadCambioId() {
		return facultadCambioId;
	}

	public String getRazonSocial() {
		return razonSocial;
	}

	public String getDireccion() {
		return direccion;
	}

	public String getCorreoInstitucional() {
		return correoInstitucional;
	}

	/** Los niveles que ofrece, en el orden del catálogo. */
	public Set<Nivel> getNiveles() {
		Set<Nivel> conjunto = EnumSet.noneOf(Nivel.class);
		Arrays.stream(niveles.split(",")).map(String::strip).filter(n -> !n.isEmpty()).map(Nivel::valueOf)
				.forEach(conjunto::add);
		return conjunto;
	}

	public String getTitularNombre() {
		return titularNombre;
	}

	public String getTitularDocumento() {
		return titularDocumento;
	}

	public String getContratoCodigo() {
		return contratoCodigo;
	}

	public String getAvisoPrivacidadVersion() {
		return avisoPrivacidadVersion;
	}

	public String getAvisoPrivacidadAprobadoPor() {
		return avisoPrivacidadAprobadoPor;
	}

	public LocalDateTime getAvisoPrivacidadAprobadoEn() {
		return avisoPrivacidadAprobadoEn;
	}

	public LocalDateTime getProduccionDesde() {
		return produccionDesde;
	}

	public String getProduccionPor() {
		return produccionPor;
	}

	public String getCreadoPor() {
		return creadoPor;
	}

	public LocalDateTime getActualizadoEn() {
		return actualizadoEn;
	}

	public String getActualizadoPor() {
		return actualizadoPor;
	}

	public Long getVersion() {
		return version;
	}
}
