package pe.edu.virgenmaria.cuentasclaras.exportacion.model;

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
 * Sprint 11, tanda 2 (sección 3.9; AP-12, AP2-2, AP2-4 y AP2-13): la exportación completa de los datos de un colegio. La
 * genera {@code sistema.exportacion} al aplicarse una SUSPENSION, antes de un CIERRE o a pedido escrito del colegio (con la
 * firma de un operador). El ZIP vive en {@code CC_DIR_EXPORTACIONES} 30 días; la fila queda. La clave del ZIP se guarda
 * cifrada con {@code CC_CLAVE_CONECTORES} hasta que el titular la ve; el enlace y el código de la entrega, solo como
 * SHA-256. En MySQL, trg_exportacion_colegio_nace y _cambio validan cada paso y ponen las fechas.
 */
@Entity
@Table(name = "exportacion_colegio")
@DynamicUpdate
public class ExportacionColegio extends BaseEntity {

	public static final int MAX_DESCARGAS = 3;

	public static final int MAX_INTENTOS = 5;

	public static final int DIAS_VIGENCIA = 30;

	@Column(name = "plataforma_id", nullable = false, updatable = false)
	private Long plataformaId;

	@Column(name = "cambio_estado_id", updatable = false)
	private Long cambioEstadoId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 12)
	private MotivoExportacion motivo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EstadoExportacion estado = EstadoExportacion.GENERANDO;

	@Column
	private Boolean vigente = Boolean.TRUE;

	@Column(length = 200)
	private String archivo;

	@Column
	private Integer archivos;

	@Column
	private Long filas;

	@Column
	private Long bytes;

	@Column(name = "sha256_zip", length = 64)
	private String sha256Zip;

	@Column(name = "sha256_manifiesto", length = 64)
	private String sha256Manifiesto;

	@Column(name = "clave_cifrada", length = 512)
	private byte[] claveCifrada;

	@Column(name = "clave_version")
	private Integer claveVersion;

	@Column(name = "clave_vista_en")
	private LocalDateTime claveVistaEn;

	@Column(name = "token_hash", length = 64)
	private String tokenHash;

	@Column(name = "token_vence_en")
	private LocalDateTime tokenVenceEn;

	@Column(name = "dni_confirmado_en")
	private LocalDateTime dniConfirmadoEn;

	@Column(name = "codigo_hash", length = 64)
	private String codigoHash;

	@Column(name = "codigo_vence_en")
	private LocalDateTime codigoVenceEn;

	@Column(name = "intentos_fallidos", nullable = false)
	private int intentosFallidos;

	@Column(nullable = false)
	private int descargas;

	@Column(name = "detalle_fallo", length = 500)
	private String detalleFallo;

	/** La pone la base en MySQL (trg_exportacion_colegio_cambio); en H2, la aplicación con el mismo criterio. */
	@Column(name = "generada_en")
	private LocalDateTime generadaEn;

	@Column(name = "vence_en")
	private LocalDateTime venceEn;

	@Column(name = "entregada_en")
	private LocalDateTime entregadaEn;

	protected ExportacionColegio() {
		// requerido por JPA
	}

	public static ExportacionColegio nueva(Long plataformaId, MotivoExportacion motivo, Long cambioEstadoId) {
		ExportacionColegio e = new ExportacionColegio();
		e.plataformaId = Objects.requireNonNull(plataformaId, "plataformaId");
		e.motivo = Objects.requireNonNull(motivo, "motivo");
		if ((motivo == MotivoExportacion.PEDIDO) != (cambioEstadoId == null)) {
			throw new IllegalArgumentException("La exportación a pedido no tiene cambio de estado; las demás, sí");
		}
		e.cambioEstadoId = cambioEstadoId;
		return e;
	}

	/** Terminó de generarse: sus huellas, el archivo y la clave cifrada. */
	public void lista(String archivo, int archivos, long filas, long bytes, String sha256Zip, String sha256Manifiesto,
			byte[] claveCifrada, int claveVersion, LocalDateTime ahora) {
		exigir(EstadoExportacion.GENERANDO);
		this.estado = EstadoExportacion.LISTA;
		this.archivo = Objects.requireNonNull(archivo, "archivo");
		this.archivos = archivos;
		this.filas = filas;
		this.bytes = bytes;
		this.sha256Zip = Objects.requireNonNull(sha256Zip, "sha256Zip");
		this.sha256Manifiesto = Objects.requireNonNull(sha256Manifiesto, "sha256Manifiesto");
		this.claveCifrada = Objects.requireNonNull(claveCifrada, "claveCifrada");
		this.claveVersion = claveVersion;
		this.generadaEn = ahora;
		this.venceEn = ahora.plusDays(DIAS_VIGENCIA);
	}

	/** AP2-13: una libreta no coincide con su huella (o el ZIP no se pudo generar): no se entrega nada. */
	public void fallida(String detalle) {
		exigir(EstadoExportacion.GENERANDO);
		this.estado = EstadoExportacion.FALLIDA;
		this.vigente = null;
		this.claveCifrada = null;
		this.detalleFallo = detalle.length() > 500 ? detalle.substring(0, 500) : detalle;
	}

	/** AP2-4: la entrega empieza: el enlace (solo su SHA-256) que va por correo al titular. */
	public void iniciarEntrega(String tokenHash, LocalDateTime venceEn) {
		exigirEntregable();
		this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
		this.tokenVenceEn = venceEn.isAfter(this.venceEn) ? this.venceEn : venceEn;
		this.dniConfirmadoEn = null;
		this.codigoHash = null;
		this.codigoVenceEn = null;
	}

	public void dniConfirmado(LocalDateTime ahora) {
		exigirEntregable();
		this.dniConfirmadoEn = ahora;
	}

	/** El código (solo su SHA-256) que va por WhatsApp, atado a esta exportación. */
	public void codigoEnviado(String codigoHash, LocalDateTime venceEn) {
		exigirEntregable();
		if (dniConfirmadoEn == null) {
			throw new ReglaNegocioException("Primero confirma tu DNI.");
		}
		this.codigoHash = Objects.requireNonNull(codigoHash, "codigoHash");
		this.codigoVenceEn = venceEn;
	}

	/** AP2-4: un intento fallido de DNI o código. Al quinto se anula el enlace: hay que pedir otra entrega. */
	public boolean intentoFallido() {
		this.intentosFallidos = Math.min(MAX_INTENTOS, intentosFallidos + 1);
		if (intentosFallidos >= MAX_INTENTOS) {
			this.tokenHash = null;
			this.codigoHash = null;
			this.dniConfirmadoEn = null;
			return true;
		}
		return false;
	}

	/** La clave se ve UNA vez: después la columna queda en NULL (si se pierde, se genera otra exportación). */
	public byte[] tomarClave(LocalDateTime ahora) {
		if (claveCifrada == null) {
			throw new ReglaNegocioException("La clave de esta exportación ya se vio una vez. Si la perdiste, pide otra "
					+ "exportación: se genera con una clave nueva.");
		}
		byte[] clave = claveCifrada;
		this.claveCifrada = null;
		this.claveVistaEn = ahora;
		return clave;
	}

	/** Una descarga más (hasta 3); la primera la deja ENTREGADA. */
	public void descargada(LocalDateTime ahora) {
		exigirEntregable();
		if (descargas >= MAX_DESCARGAS) {
			throw new ReglaNegocioException("Ya se descargó " + MAX_DESCARGAS + " veces: pide otra exportación.");
		}
		descargas++;
		if (estado == EstadoExportacion.LISTA) {
			estado = EstadoExportacion.ENTREGADA;
			vigente = null;
			entregadaEn = ahora;
		}
	}

	/** {@code LimpiezaExportaciones}: a los 30 días se borra el archivo y la fila queda VENCIDA (sin clave ni enlace). */
	public void vencer() {
		if (estado != EstadoExportacion.LISTA && estado != EstadoExportacion.ENTREGADA) {
			throw new ReglaNegocioException("Solo vence una exportación lista o entregada.");
		}
		estado = EstadoExportacion.VENCIDA;
		vigente = null;
		claveCifrada = null;
		tokenHash = null;
		codigoHash = null;
	}

	private void exigir(EstadoExportacion esperado) {
		if (estado != esperado) {
			throw new ReglaNegocioException("La exportación está " + estado.etiqueta().toLowerCase(java.util.Locale.ROOT) + ".");
		}
	}

	private void exigirEntregable() {
		if (estado != EstadoExportacion.LISTA && estado != EstadoExportacion.ENTREGADA) {
			throw new ReglaNegocioException("Esta exportación ya no se entrega (está "
					+ estado.etiqueta().toLowerCase(java.util.Locale.ROOT) + "): pide otra.");
		}
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las exportaciones no se borran: vencen.");
	}

	public Long getPlataformaId() {
		return plataformaId;
	}

	public Long getCambioEstadoId() {
		return cambioEstadoId;
	}

	public MotivoExportacion getMotivo() {
		return motivo;
	}

	public EstadoExportacion getEstado() {
		return estado;
	}

	public String getArchivo() {
		return archivo;
	}

	public Integer getArchivos() {
		return archivos;
	}

	public Long getFilas() {
		return filas;
	}

	public Long getBytes() {
		return bytes;
	}

	public String getSha256Zip() {
		return sha256Zip;
	}

	public String getSha256Manifiesto() {
		return sha256Manifiesto;
	}

	public boolean claveDisponible() {
		return claveCifrada != null;
	}

	public Integer getClaveVersion() {
		return claveVersion;
	}

	public LocalDateTime getClaveVistaEn() {
		return claveVistaEn;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public LocalDateTime getTokenVenceEn() {
		return tokenVenceEn;
	}

	public LocalDateTime getDniConfirmadoEn() {
		return dniConfirmadoEn;
	}

	public String getCodigoHash() {
		return codigoHash;
	}

	public LocalDateTime getCodigoVenceEn() {
		return codigoVenceEn;
	}

	public int getIntentosFallidos() {
		return intentosFallidos;
	}

	public int getDescargas() {
		return descargas;
	}

	public String getDetalleFallo() {
		return detalleFallo;
	}

	public LocalDateTime getGeneradaEn() {
		return generadaEn;
	}

	public LocalDateTime getVenceEn() {
		return venceEn;
	}

	public LocalDateTime getEntregadaEn() {
		return entregadaEn;
	}
}
