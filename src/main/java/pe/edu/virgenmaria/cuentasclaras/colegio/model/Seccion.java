package pe.edu.virgenmaria.cuentasclaras.colegio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Sección de un grado en un año («5.° Primaria A»). No se borra: se desactiva y deja de ofrecerse para matricular.
 * El año y el grado no cambian.
 */
@Entity
@Table(name = "seccion")
public class Seccion extends BaseEntity {

	public static final int MAX_NOMBRE = 30;

	private static final Pattern PATRON_NOMBRE = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N} '.\\-]*$");

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "anio_escolar_id", nullable = false, updatable = false)
	private AnioEscolar anioEscolar;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private Grado grado;

	@Column(nullable = false, updatable = false, length = MAX_NOMBRE)
	private String nombre;

	@Column(nullable = false)
	private boolean activa = true;

	protected Seccion() {
		// requerido por JPA
	}

	public static Seccion nueva(AnioEscolar anioEscolar, Grado grado, String nombre) {
		Objects.requireNonNull(anioEscolar, "anioEscolar");
		if (grado == null) {
			throw new ReglaNegocioException("Elige el grado.");
		}
		Seccion seccion = new Seccion();
		seccion.anioEscolar = anioEscolar;
		seccion.grado = grado;
		seccion.nombre = nombreValido(nombre);
		return seccion;
	}

	/** Nombre limpio: «a» → «A», « Celeste » → «Celeste». Letras, números, espacios, punto y guion; hasta 30. */
	public static String nombreValido(String nombre) {
		String limpio = Normalizador.limpiar(nombre);
		if (limpio == null) {
			throw new ReglaNegocioException("Escribe el nombre de la sección, por ejemplo A.");
		}
		if (limpio.length() > MAX_NOMBRE || !PATRON_NOMBRE.matcher(limpio).matches()) {
			throw new ReglaNegocioException("El nombre de la sección puede tener hasta " + MAX_NOMBRE
					+ " letras o números, por ejemplo A, B o Celeste.");
		}
		return limpio.length() == 1 ? limpio.toUpperCase(java.util.Locale.ROOT) : limpio;
	}

	public void desactivar() {
		if (!activa) {
			throw new ReglaNegocioException("La sección " + etiqueta() + " ya está desactivada.");
		}
		activa = false;
	}

	/** «5.° Primaria A». */
	public String etiqueta() {
		return grado.etiqueta() + " " + nombre;
	}

	public Nivel nivel() {
		return grado.nivel();
	}

	public AnioEscolar getAnioEscolar() {
		return anioEscolar;
	}

	public Grado getGrado() {
		return grado;
	}

	public String getNombre() {
		return nombre;
	}

	public boolean isActiva() {
		return activa;
	}
}
