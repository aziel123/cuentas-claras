package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * Fila de {@code configuracion_colegio} (correcciones del sprint 6, QA-S6-6): de UN colegio, sin {@code @TenantId} (el
 * operador la escribe para otro colegio desde la plataforma). {@code resumen_correo_externo} y
 * {@code huella_correo_externo} son el correo del contador (o de un tercero de confianza) de ESE colegio que recibe su
 * resumen diario y su huella: el correo externo de control.
 * <p>
 * Sprint 8, tanda 1 (H10): ya no la escribe solo el DBA: la registra el operador de la plataforma con la conexión de
 * {@code cc_sistema} y la firma de su sesión ({@code configuracion_colegio:{colegio}:{clave}:{version}}; en MySQL,
 * trg_configuracion_colegio_registro y trg_configuracion_colegio_cambio). cc_app sigue sin GRANT (1142). El colegio y la
 * clave no cambian; nunca se borra.
 */
@Entity
@Table(name = "configuracion_colegio")
public class ConfiguracionColegio {

	/** El correo del contador del colegio que también recibe el resumen diario (decisión 69). */
	public static final String RESUMEN_CORREO_EXTERNO = "resumen_correo_externo";

	/**
	 * Sprint 7, tanda 2 (H5): el correo del contador de ESE colegio que también recibe la huella diaria de la bitácora
	 * (antes era una fila de {@code configuracion_bd}, de toda la base). V25 la copió solo si había un único colegio.
	 */
	public static final String HUELLA_CORREO_EXTERNO = "huella_correo_externo";

	public static final Set<String> CLAVES = Set.of(RESUMEN_CORREO_EXTERNO, HUELLA_CORREO_EXTERNO);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "colegio_id", nullable = false, updatable = false)
	private Long colegioId;

	@Column(nullable = false, updatable = false, length = 40)
	private String clave;

	@Column(nullable = false, length = 100)
	private String valor;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private LocalDateTime creadoEn;

	@Column(name = "creado_por", nullable = false, updatable = false, length = 60)
	private String creadoPor;

	@Column(name = "actualizado_por", length = 60)
	private String actualizadoPor;

	@Column(name = "actualizado_en")
	private LocalDateTime actualizadoEn;

	@Version
	@Column(nullable = false)
	private Long version;

	protected ConfiguracionColegio() {
		// requerido por JPA
	}

	/** Sprint 8, tanda 1: una fila nueva, escrita por el operador (con cc_sistema y su firma). */
	public static ConfiguracionColegio nueva(Long colegioId, String clave, String valor, String por, LocalDateTime ahora) {
		if (!CLAVES.contains(clave)) {
			throw new ReglaNegocioException("La clave de configuración «" + clave + "» no existe.");
		}
		ConfiguracionColegio c = new ConfiguracionColegio();
		c.colegioId = Objects.requireNonNull(colegioId, "colegioId");
		c.clave = clave;
		c.valor = valorValido(valor);
		c.creadoPor = Objects.requireNonNull(por, "por");
		c.creadoEn = Objects.requireNonNull(ahora, "ahora");
		c.actualizadoPor = por;
		c.actualizadoEn = ahora;
		return c;
	}

	/** Cambia el valor (el operador, con su firma de la versión actual). */
	public void cambiar(String valor, String por, LocalDateTime ahora) {
		this.valor = valorValido(valor);
		this.actualizadoPor = Objects.requireNonNull(por, "por");
		this.actualizadoEn = Objects.requireNonNull(ahora, "ahora");
	}

	private static String valorValido(String valor) {
		String texto = valor == null ? "" : valor.strip();
		if (texto.isEmpty() || texto.length() > 100) {
			throw new ReglaNegocioException("Escribe el correo externo de control (hasta 100 caracteres).");
		}
		return texto;
	}

	@PreRemove
	void impedirBorrado() {
		throw new UnsupportedOperationException("La configuración del colegio no se borra");
	}

	public Long getId() {
		return id;
	}

	public Long getColegioId() {
		return colegioId;
	}

	public String getClave() {
		return clave;
	}

	public String getValor() {
		return valor;
	}

	public String getCreadoPor() {
		return creadoPor;
	}

	public String getActualizadoPor() {
		return actualizadoPor;
	}

	public LocalDateTime getActualizadoEn() {
		return actualizadoEn;
	}

	public Long getVersion() {
		return version;
	}
}
