package pe.edu.virgenmaria.cuentasclaras.conectores.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Sprint 8, tanda 2 (sección 3.6, V29): un conector del colegio (OSE, pasarela, cuenta bancaria o recaudación), de cambios
 * por versión. Cada cambio es una fila nueva PROPUESTO; con su CAMBIO_CONECTOR aprobada y firmada por otra persona, la
 * vigente pasa a REEMPLAZADO y la nueva a VIGENTE en la misma transacción. Nada se borra.
 * <ul>
 *   <li>Lo propuesto (tipo, proveedor, modo, configuración, secreto cifrado, su pista, la versión de la clave, la clave
 *       pública y quién lo propuso) no cambia nunca: {@code updatable = false} y GRANT por columna (1143). Lo comprueba
 *       {@code InmutabilidadConectoresTest} contra {@code 02-permisos-tablas.sql}.</li>
 *   <li>El secreto va cifrado con AES-256-GCM ({@code SecretosConectores}); la base nunca lo ve en claro y nadie lo vuelve
 *       a ver: la pantalla muestra solo la pista (los 4 últimos caracteres).</li>
 *   <li>{@code clavePublica} es un UUID opaco: la ruta del aviso de la pasarela ({@code /webhooks/pasarela/{clave}}) y
 *       parte del AAD del secreto (un secreto copiado a otra fila no se descifra).</li>
 * </ul>
 */
@Entity
@Table(name = "conector_colegio")
public class ConectorColegio extends BaseEntity {

	public static final int MAX_CONFIGURACION = 2000;

	public static final int MAX_SECRETO_CIFRADO = 2048;

	public static final int MAX_DETALLE_PRUEBA = 200;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private TipoConector tipo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ProveedorConector proveedor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private ModoConector modo;

	/** JSON sin secretos (un record por tipo en {@code conectores.dto}). */
	@Column(nullable = false, updatable = false, length = MAX_CONFIGURACION)
	private String configuracion;

	/** nonce (12 bytes) + texto cifrado + etiqueta GCM (16 bytes); {@code null} si el tipo no tiene secreto. */
	@JdbcTypeCode(SqlTypes.VARBINARY)
	@Column(name = "secreto_cifrado", updatable = false, length = MAX_SECRETO_CIFRADO)
	private byte[] secretoCifrado;

	@Column(name = "secreto_pista", updatable = false, length = 4)
	private String secretoPista;

	@Column(name = "clave_version", updatable = false)
	private Integer claveVersion;

	@Column(name = "clave_publica", nullable = false, updatable = false, length = 36)
	private String clavePublica;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private EstadoConector estado;

	/** TRUE solo el VIGENTE; NULL los demás: {@code uk_conector_colegio_vigente} asegura uno por colegio y tipo. */
	@Column
	private Boolean vigente;

	@Column(name = "solicitud_id")
	private Long solicitudId;

	@Column(name = "propuesto_por", nullable = false, updatable = false, length = 60)
	private String propuestoPor;

	@Column(name = "resuelto_por", length = 60)
	private String resueltoPor;

	@Column(name = "resuelto_en")
	private LocalDateTime resueltoEn;

	@Column(name = "prueba_ok")
	private Boolean pruebaOk;

	@Column(name = "prueba_en")
	private LocalDateTime pruebaEn;

	@Column(name = "prueba_detalle", length = MAX_DETALLE_PRUEBA)
	private String pruebaDetalle;

	protected ConectorColegio() {
		// requerido por JPA
	}

	/**
	 * Un conector propuesto. {@code clavePublica} la genera quien propone (con ella se arma el AAD del secreto, que se
	 * cifra antes de guardar la fila).
	 */
	public static ConectorColegio propuesto(TipoConector tipo, ProveedorConector proveedor, ModoConector modo,
			String configuracion, String clavePublica, byte[] secretoCifrado, String secretoPista, Integer claveVersion,
			String propuestoPor) {
		Objects.requireNonNull(tipo, "tipo");
		Objects.requireNonNull(proveedor, "proveedor");
		Objects.requireNonNull(modo, "modo");
		if (!tipo.proveedores().contains(proveedor)) {
			throw new ReglaNegocioException(proveedor.etiqueta() + " no es un proveedor de " + tipo.etiqueta() + ".");
		}
		if ((proveedor == ProveedorConector.SIMULADO) != (modo == ModoConector.SIMULADO)) {
			throw new ReglaNegocioException("El modo simulado va solo con el proveedor simulado.");
		}
		if (!tipo.conSecreto() && (modo != ModoConector.REAL || secretoCifrado != null)) {
			throw new ReglaNegocioException(tipo.etiqueta() + " no tiene modo de pruebas ni secreto.");
		}
		if (configuracion == null || configuracion.isBlank() || configuracion.length() > MAX_CONFIGURACION) {
			throw new IllegalArgumentException("La configuración del conector está vacía o es demasiado larga");
		}
		if ((secretoCifrado == null) != (claveVersion == null) || (secretoCifrado == null && secretoPista != null)) {
			throw new IllegalArgumentException("El secreto cifrado va con la versión de su clave");
		}
		if (secretoCifrado != null && (secretoCifrado.length > MAX_SECRETO_CIFRADO || claveVersion < 1)) {
			throw new IllegalArgumentException("Secreto cifrado demasiado largo o sin versión de clave");
		}
		ConectorColegio c = new ConectorColegio();
		c.tipo = tipo;
		c.proveedor = proveedor;
		c.modo = modo;
		c.configuracion = configuracion;
		c.clavePublica = UUID.fromString(Objects.requireNonNull(clavePublica, "clavePublica")).toString();
		c.secretoCifrado = secretoCifrado == null ? null : secretoCifrado.clone();
		c.secretoPista = secretoPista;
		c.claveVersion = claveVersion;
		c.estado = EstadoConector.PROPUESTO;
		c.vigente = null;
		c.propuestoPor = Objects.requireNonNull(propuestoPor, "propuestoPor");
		return c;
	}

	/** Enlaza su solicitud CAMBIO_CONECTOR (una sola vez; trg_conector_colegio_estado lo exige en MySQL). */
	public void enlazarSolicitud(Long solicitud) {
		exigir(EstadoConector.PROPUESTO, "enlazar su solicitud");
		if (solicitudId != null) {
			throw new IllegalStateException("El conector ya tiene su solicitud");
		}
		solicitudId = Objects.requireNonNull(solicitud, "solicitud");
	}

	/** El resultado de «Probar conexión» (solo un PROPUESTO). El detalle no lleva secretos ni datos personales. */
	public void registrarPrueba(boolean ok, String detalle, LocalDateTime ahora) {
		exigir(EstadoConector.PROPUESTO, "probar");
		pruebaOk = ok;
		pruebaEn = Objects.requireNonNull(ahora, "ahora");
		String texto = detalle == null || detalle.isBlank() ? (ok ? "Conexión correcta" : "La prueba falló") : detalle.strip();
		pruebaDetalle = texto.length() <= MAX_DETALLE_PRUEBA ? texto : texto.substring(0, MAX_DETALLE_PRUEBA - 1) + "…";
	}

	/** PROPUESTO → VIGENTE, aprobado por otra persona (la bandeja ya firmó y dejó la solicitud APROBADA). */
	public void entrarEnVigencia(String aprobador, LocalDateTime ahora) {
		exigir(EstadoConector.PROPUESTO, "poner en vigencia");
		if (!Boolean.TRUE.equals(pruebaOk)) {
			throw new ReglaNegocioException("Antes de aprobarlo, quien lo propuso debe probar la conexión con éxito.");
		}
		if (Objects.equals(aprobador, propuestoPor)) {
			throw new IllegalStateException("Quien propone no aprueba (lo valida la bandeja y la base)");
		}
		estado = EstadoConector.VIGENTE;
		vigente = Boolean.TRUE;
		resueltoPor = Objects.requireNonNull(aprobador, "aprobador");
		resueltoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** VIGENTE → REEMPLAZADO: otro del mismo tipo entra en su lugar en esta transacción. */
	public void reemplazar() {
		exigir(EstadoConector.VIGENTE, "reemplazar");
		estado = EstadoConector.REEMPLAZADO;
		vigente = null;
	}

	/** PROPUESTO → RECHAZADO, con la firma de quien rechaza. */
	public void rechazar(String por, LocalDateTime ahora) {
		exigir(EstadoConector.PROPUESTO, "rechazar");
		estado = EstadoConector.RECHAZADO;
		resueltoPor = Objects.requireNonNull(por, "por");
		resueltoEn = Objects.requireNonNull(ahora, "ahora");
	}

	private void exigir(EstadoConector esperado, String accion) {
		if (estado != esperado) {
			throw new ReglaNegocioException("No se puede " + accion + " un conector " + estado.etiqueta().toLowerCase()
					+ ".");
		}
	}

	public boolean conSecreto() {
		return secretoCifrado != null;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los conectores no se borran: se reemplazan o se rechazan.");
	}

	public TipoConector getTipo() {
		return tipo;
	}

	public ProveedorConector getProveedor() {
		return proveedor;
	}

	public ModoConector getModo() {
		return modo;
	}

	public String getConfiguracion() {
		return configuracion;
	}

	/** Copia del secreto cifrado (solo para {@code SecretosConectores}). */
	public byte[] getSecretoCifrado() {
		return secretoCifrado == null ? null : secretoCifrado.clone();
	}

	public String getSecretoPista() {
		return secretoPista;
	}

	public Integer getClaveVersion() {
		return claveVersion;
	}

	public String getClavePublica() {
		return clavePublica;
	}

	public EstadoConector getEstado() {
		return estado;
	}

	public boolean isVigente() {
		return estado == EstadoConector.VIGENTE;
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

	public Boolean getPruebaOk() {
		return pruebaOk;
	}

	public LocalDateTime getPruebaEn() {
		return pruebaEn;
	}

	public String getPruebaDetalle() {
		return pruebaDetalle;
	}
}
