package pe.edu.virgenmaria.cuentasclaras.colegio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 11, tanda 1 (sección 3.3 y 3.13): un cambio del estado de un colegio (BAJA, SUSPENSION, REACTIVACION) o de su
 * facultad del titular (BLOQUEO_FACULTAD, DESBLOQUEO_FACULTAD). Lo PROPONE un operador y lo APRUEBA OTRO, cada uno con la
 * firma de su sesión en el colegio de plataforma; lo APLICA {@code sistema.plataforma} en su fecha (la suspensión, a los 30
 * días); se RECHAZA (otro operador) o se ANULA (antes de aplicar). El titular puede OBJETAR una baja o una suspensión y la
 * objeción la resuelve un operador que no propuso (AP-10); sin resolver, no se aplica.
 * <p>
 * Es del colegio afectado ({@code colegio_id}, {@code @TenantId}); lo escriben solo los operadores y el proceso, con
 * {@code cc_sistema} (en {@code plataforma}: lo exige ArchUnit). Vive en {@code colegio.model} y no en {@code plataforma}
 * porque el colegio lo lee (la franja del personal y el aviso del portal) y nadie depende de la plataforma. Las horas de la
 * aprobación, de la fecha efectiva, de la aplicación y de la objeción las pone la base en MySQL
 * (trg_cambio_estado_colegio_estado, con el reloj real); en H2 las pone la aplicación con las mismas reglas. Solo se
 * escriben las columnas que cambian ({@code @DynamicUpdate}): la base rechaza que cambie una aprobación ya registrada.
 * Nada se borra.
 */
@Entity
@Table(name = "cambio_estado_colegio")
@DynamicUpdate
public class CambioEstadoColegio extends BaseEntity {

	public static final int MOTIVO_MINIMO = 20;

	public static final int MOTIVO_MAXIMO = 500;

	/** Días de aviso de la suspensión (cláusula 7 de los términos; la base los fija: decisión 194). */
	public static final int DIAS_AVISO_SUSPENSION = 30;

	/** Días de la baja de un colegio con alguna cuenta activada (AP-6; la base los fija). */
	public static final int DIAS_BAJA_CON_CUENTAS = 7;

	@Column(name = "plataforma_id", nullable = false, updatable = false)
	private Long plataformaId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private TipoCambioEstado tipo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 8)
	private AmbitoCambioEstado ambito;

	@Enumerated(EnumType.STRING)
	@Column(name = "motivo_tipo", nullable = false, updatable = false, length = 16)
	private MotivoCambioEstado motivoTipo;

	@Column(nullable = false, updatable = false, length = MOTIVO_MAXIMO)
	private String motivo;

	@Column(name = "referencia_aviso", updatable = false, length = 120)
	private String referenciaAviso;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado_origen", nullable = false, updatable = false, length = 12)
	private EstadoColegio estadoOrigen;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado_destino", nullable = false, updatable = false, length = 12)
	private EstadoColegio estadoDestino;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EstadoCambioEstado estado = EstadoCambioEstado.PROPUESTO;

	/** TRUE en PROPUESTO y APROBADO (uno pendiente por colegio y ámbito); si no, {@code null}. */
	@Column
	private Boolean pendiente = Boolean.TRUE;

	@Column(name = "propuesto_por", nullable = false, updatable = false, length = 60)
	private String propuestoPor;

	@Column(name = "operador_id", nullable = false, updatable = false)
	private Long operadorId;

	@Column(name = "aprobado_por", length = 60)
	private String aprobadoPor;

	@Column(name = "aprobador_id")
	private Long aprobadorId;

	@Column(name = "aprobado_en", insertable = false)
	private LocalDateTime aprobadoEn;

	@Column(name = "efectiva_desde", insertable = false)
	private LocalDateTime efectivaDesde;

	@Column(name = "aplicado_en", insertable = false)
	private LocalDateTime aplicadoEn;

	@Column(name = "resuelto_por", length = 60)
	private String resueltoPor;

	@Column(name = "resuelto_en")
	private LocalDateTime resueltoEn;

	@Column(length = MOTIVO_MAXIMO)
	private String objecion;

	@Column(name = "objetado_por", length = 60)
	private String objetadoPor;

	@Column(name = "objetado_en")
	private LocalDateTime objetadoEn;

	@Column(name = "objecion_respuesta", length = MOTIVO_MAXIMO)
	private String objecionRespuesta;

	@Column(name = "objecion_resuelta_por", length = 60)
	private String objecionResueltaPor;

	@Column(name = "objecion_resuelta_en")
	private LocalDateTime objecionResueltaEn;

	/** Sprint 11, tanda 2 (3.10 y AP2-2): la exportación que habilita el CIERRE o la BAJA de un colegio con datos. */
	@Column(name = "exportacion_id", updatable = false)
	private Long exportacionId;

	protected CambioEstadoColegio() {
		// requerido por JPA
	}

	/**
	 * Un operador propone. {@code estadoActual} es el del colegio (origen del bloqueo o desbloqueo de la facultad; para los
	 * demás tipos debe ser su origen).
	 */
	public static CambioEstadoColegio propuesto(TipoCambioEstado tipo, MotivoCambioEstado motivoTipo, String motivo,
			String referenciaAviso, EstadoColegio estadoActual, Long plataformaId, Long operadorId, String operador) {
		Objects.requireNonNull(tipo, "tipo");
		Objects.requireNonNull(estadoActual, "estadoActual");
		if (motivoTipo == null || !tipo.motivos().contains(motivoTipo)) {
			throw new ReglaNegocioException("Elige un motivo de la lista para «" + tipo.etiqueta() + "».");
		}
		String texto = exigirTexto(motivo, "el motivo");
		String referencia = referenciaAviso == null || referenciaAviso.isBlank() ? null : referenciaAviso.strip();
		if (tipo == TipoCambioEstado.SUSPENSION && (referencia == null || referencia.length() < 5 || referencia.length() > 120)) {
			throw new ReglaNegocioException("La suspensión exige la referencia del aviso escrito al representante legal (el "
					+ "número de la carta o la fecha del correo, de 5 a 120 caracteres): sin aviso escrito no hay suspensión.");
		}
		EstadoColegio origen = tipo.deFacultad() ? estadoActual : tipo.origen();
		EstadoColegio destino = tipo.deFacultad() ? estadoActual : tipo.destino();
		if (estadoActual != origen) {
			throw new ReglaNegocioException("«" + tipo.etiqueta() + "» se propone a un colegio "
					+ origen.etiqueta().toLowerCase(java.util.Locale.ROOT) + " y este está "
					+ estadoActual.etiqueta().toLowerCase(java.util.Locale.ROOT) + ".");
		}
		if (tipo.deFacultad() && !estadoActual.opera()) {
			throw new ReglaNegocioException("La facultad del titular solo existe en un colegio en preparación o en producción.");
		}
		CambioEstadoColegio c = new CambioEstadoColegio();
		c.tipo = tipo;
		c.ambito = tipo.ambito();
		c.motivoTipo = motivoTipo;
		c.motivo = texto;
		c.referenciaAviso = referencia;
		c.estadoOrigen = origen;
		c.estadoDestino = destino;
		c.plataformaId = Objects.requireNonNull(plataformaId, "plataformaId");
		c.operadorId = Objects.requireNonNull(operadorId, "operadorId");
		c.propuestoPor = Objects.requireNonNull(operador, "operador");
		return c;
	}

	/** Sprint 11, tanda 2: la exportación que lo habilita (obligatoria en el CIERRE; en la BAJA, si el colegio tiene datos). */
	public CambioEstadoColegio conExportacion(Long exportacionId) {
		this.exportacionId = exportacionId;
		if (tipo == TipoCambioEstado.CIERRE && exportacionId == null) {
			throw new ReglaNegocioException("El cierre exige la exportación de los datos del colegio.");
		}
		return this;
	}

	public Long getExportacionId() {
		return exportacionId;
	}

	/**
	 * OTRO operador aprueba. La fecha efectiva la calcula la base en MySQL; aquí, con las mismas reglas: la suspensión, a
	 * las 00:00 del día siguiente a los 30 días; la baja, a los 7 días si alguna cuenta del colegio se activó; lo demás, ya.
	 */
	public void aprobar(String operador, Long aprobadorId, LocalDateTime ahora, boolean conCuentasActivadas) {
		exigir(EstadoCambioEstado.PROPUESTO, "aprobar");
		if (Objects.equals(operador, propuestoPor)) {
			throw new ReglaNegocioException("Tú propusiste este cambio: lo aprueba otro operador, con su propia sesión.");
		}
		this.estado = EstadoCambioEstado.APROBADO;
		this.aprobadoPor = Objects.requireNonNull(operador, "operador");
		this.aprobadorId = Objects.requireNonNull(aprobadorId, "aprobadorId");
		this.aprobadoEn = Objects.requireNonNull(ahora, "ahora");
		this.efectivaDesde = switch (tipo) {
			case SUSPENSION -> ahora.toLocalDate().plusDays(DIAS_AVISO_SUSPENSION + 1L).atStartOfDay();
			case BAJA -> conCuentasActivadas ? ahora.plusDays(DIAS_BAJA_CON_CUENTAS) : ahora;
			default -> ahora;
		};
	}

	/** Lo aplica {@code sistema.plataforma} en su fecha, sin objeción abierta. */
	public void aplicar(LocalDateTime ahora) {
		exigir(EstadoCambioEstado.APROBADO, "aplicar");
		if (efectivaDesde == null || efectivaDesde.isAfter(ahora)) {
			throw new ReglaNegocioException("Este cambio se aplica desde el " + efectivaDesde + ".");
		}
		if (objecionAbierta()) {
			throw new ReglaNegocioException("El titular objetó este cambio y la objeción no está resuelta: no se aplica.");
		}
		this.estado = EstadoCambioEstado.APLICADO;
		this.aplicadoEn = ahora;
		this.pendiente = null;
	}

	/** OTRO operador rechaza una propuesta. */
	public void rechazar(String operador, LocalDateTime ahora) {
		exigir(EstadoCambioEstado.PROPUESTO, "rechazar");
		if (Objects.equals(operador, propuestoPor)) {
			throw new ReglaNegocioException("Tú propusiste este cambio: lo rechaza otro operador. Si ya no hace falta, anúlalo.");
		}
		resolver(EstadoCambioEstado.RECHAZADO, operador, ahora);
	}

	/** Cualquier operador anula, antes de aplicar (por ejemplo, porque el colegio pagó). */
	public void anular(String operador, LocalDateTime ahora) {
		if (!estado.pendiente()) {
			throw new ReglaNegocioException("Este cambio ya está " + estado.etiqueta().toLowerCase(java.util.Locale.ROOT)
					+ ": no se anula.");
		}
		resolver(EstadoCambioEstado.ANULADO, operador, ahora);
	}

	/** AP-10: el titular objeta una baja o una suspensión pendiente, una vez. */
	public void objetar(String titular, String texto, LocalDateTime ahora) {
		if (!tipo.objetable()) {
			throw new ReglaNegocioException("Solo se objeta una baja o una suspensión.");
		}
		if (!estado.pendiente()) {
			throw new ReglaNegocioException("Este cambio ya está " + estado.etiqueta().toLowerCase(java.util.Locale.ROOT)
					+ ": ya no se objeta.");
		}
		if (objecion != null) {
			throw new ReglaNegocioException("Ya objetaste este cambio: la objeción la resuelve un operador.");
		}
		this.objecion = exigirTexto(texto, "la objeción");
		this.objetadoPor = Objects.requireNonNull(titular, "titular");
		this.objetadoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** AP-10: un operador que no propuso resuelve la objeción, con su respuesta escrita. */
	public void resolverObjecion(String operador, String respuesta, LocalDateTime ahora) {
		if (objecion == null) {
			throw new ReglaNegocioException("Este cambio no tiene una objeción.");
		}
		if (objecionResueltaPor != null) {
			throw new ReglaNegocioException("La objeción ya está resuelta.");
		}
		if (!estado.pendiente()) {
			throw new ReglaNegocioException("Este cambio ya está resuelto.");
		}
		if (Objects.equals(operador, propuestoPor)) {
			throw new ReglaNegocioException("Tú propusiste este cambio: la objeción la resuelve otro operador.");
		}
		this.objecionRespuesta = exigirTexto(respuesta, "la respuesta a la objeción");
		this.objecionResueltaPor = operador;
		this.objecionResueltaEn = Objects.requireNonNull(ahora, "ahora");
	}

	public boolean objecionAbierta() {
		return objecion != null && objecionResueltaEn == null;
	}

	private void resolver(EstadoCambioEstado destino, String operador, LocalDateTime ahora) {
		this.estado = destino;
		this.resueltoPor = Objects.requireNonNull(operador, "operador");
		this.resueltoEn = Objects.requireNonNull(ahora, "ahora");
		this.pendiente = null;
	}

	private void exigir(EstadoCambioEstado esperado, String accion) {
		if (estado != esperado) {
			throw new ReglaNegocioException("No se puede " + accion + " un cambio "
					+ estado.etiqueta().toLowerCase(java.util.Locale.ROOT) + ".");
		}
	}

	private static String exigirTexto(String valor, String que) {
		String texto = valor == null ? "" : valor.strip();
		if (texto.length() < MOTIVO_MINIMO || texto.length() > MOTIVO_MAXIMO) {
			throw new ReglaNegocioException("Escribe " + que + " (de " + MOTIVO_MINIMO + " a " + MOTIVO_MAXIMO
					+ " caracteres): queda en la bitácora del colegio y de la plataforma.");
		}
		return texto;
	}

	@PreRemove
	void impedirBorrado() {
		throw new UnsupportedOperationException("Un cambio de estado del colegio no se borra");
	}

	public Long getPlataformaId() {
		return plataformaId;
	}

	public TipoCambioEstado getTipo() {
		return tipo;
	}

	public AmbitoCambioEstado getAmbito() {
		return ambito;
	}

	public MotivoCambioEstado getMotivoTipo() {
		return motivoTipo;
	}

	public String getMotivo() {
		return motivo;
	}

	public String getReferenciaAviso() {
		return referenciaAviso;
	}

	public EstadoColegio getEstadoOrigen() {
		return estadoOrigen;
	}

	public EstadoColegio getEstadoDestino() {
		return estadoDestino;
	}

	public EstadoCambioEstado getEstado() {
		return estado;
	}

	public String getPropuestoPor() {
		return propuestoPor;
	}

	public Long getOperadorId() {
		return operadorId;
	}

	public String getAprobadoPor() {
		return aprobadoPor;
	}

	public LocalDateTime getAprobadoEn() {
		return aprobadoEn;
	}

	public LocalDateTime getEfectivaDesde() {
		return efectivaDesde;
	}

	public LocalDateTime getAplicadoEn() {
		return aplicadoEn;
	}

	public String getResueltoPor() {
		return resueltoPor;
	}

	public LocalDateTime getResueltoEn() {
		return resueltoEn;
	}

	public String getObjecion() {
		return objecion;
	}

	public String getObjetadoPor() {
		return objetadoPor;
	}

	public LocalDateTime getObjetadoEn() {
		return objetadoEn;
	}

	public String getObjecionRespuesta() {
		return objecionRespuesta;
	}

	public String getObjecionResueltaPor() {
		return objecionResueltaPor;
	}
}
