package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Fila de {@code configuracion_bd}: la escribe SOLO el DBA (cc_app no tiene GRANT de escritura). La aplicación solo la
 * lee: {@code huella_correo_externo} (el correo del contador que recibe la huella diaria) y
 * {@code mensajeria_simulada} (solo en las bases de dev, test y piloto). No es de un colegio: no extiende BaseEntity.
 */
@Entity
@Immutable
@Table(name = "configuracion_bd")
public class ConfiguracionBd {

	/**
	 * Ya no se usa desde el sprint 7 (tanda 2): la huella va al correo de cada colegio ({@code configuracion_colegio}). En
	 * prod, el verificador avisa en el log si la fila sigue aquí.
	 */
	public static final String HUELLA_CORREO_EXTERNO = "huella_correo_externo";

	/** Sprint 6, tanda 2 (decisión 69): el correo del contador que también recibe el resumen diario (lo escribe el DBA). */
	public static final String RESUMEN_CORREO_EXTERNO = "resumen_correo_externo";

	public static final String MENSAJERIA_SIMULADA = "mensajeria_simulada";

	@Id
	@Column(length = 40)
	private String clave;

	@Column(nullable = false, length = 100)
	private String valor;

	@Column(name = "creado_en", nullable = false)
	private LocalDateTime creadoEn;

	protected ConfiguracionBd() {
		// requerido por JPA
	}

	public String getClave() {
		return clave;
	}

	public String getValor() {
		return valor;
	}
}
