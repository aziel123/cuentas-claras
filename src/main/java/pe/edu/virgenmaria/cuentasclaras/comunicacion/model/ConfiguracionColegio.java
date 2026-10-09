package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Fila de {@code configuracion_colegio} (correcciones del sprint 6, QA-S6-6): como {@code configuracion_bd}, la escribe
 * SOLO el DBA (cc_app no tiene GRANT de escritura), pero es de UN colegio. La aplicación solo la lee, siempre con el
 * colegio actual: {@code resumen_correo_externo} es el correo del contador de ESE colegio que también recibe su resumen
 * diario. No extiende BaseEntity (no tiene autoría ni versión: la fila es del DBA).
 */
@Entity
@Immutable
@Table(name = "configuracion_colegio")
public class ConfiguracionColegio {

	/** El correo del contador del colegio que también recibe el resumen diario (decisión 69). */
	public static final String RESUMEN_CORREO_EXTERNO = "resumen_correo_externo";

	/**
	 * Sprint 7, tanda 2 (H5): el correo del contador de ESE colegio que también recibe la huella diaria de la bitácora
	 * (antes era una fila de {@code configuracion_bd}, de toda la base). V25 la copió solo si había un único colegio.
	 */
	public static final String HUELLA_CORREO_EXTERNO = "huella_correo_externo";

	@Id
	private Long id;

	@Column(name = "colegio_id", nullable = false)
	private Long colegioId;

	@Column(nullable = false, length = 40)
	private String clave;

	@Column(nullable = false, length = 100)
	private String valor;

	@Column(name = "creado_en", nullable = false)
	private LocalDateTime creadoEn;

	protected ConfiguracionColegio() {
		// requerido por JPA
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
}
