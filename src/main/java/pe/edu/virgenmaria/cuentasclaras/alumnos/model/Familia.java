package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

/** Agrupa a los hermanos y a sus apoderados. No se borra. */
@Entity
@Table(name = "familia")
public class Familia extends BaseEntity {

	public static final int MAX_NOMBRE = 120;

	@Column(nullable = false, length = MAX_NOMBRE)
	private String nombre;

	protected Familia() {
		// requerido por JPA
	}

	public static Familia nueva(String nombre) {
		Familia familia = new Familia();
		familia.nombre = nombreValido(nombre);
		return familia;
	}

	/** «Familia Quispe Huamán» a partir de los apellidos. */
	public static String nombrePorDefecto(String apellidoPaterno, String apellidoMaterno) {
		return "Familia " + apellidoPaterno + (apellidoMaterno == null ? "" : " " + apellidoMaterno);
	}

	/** @return {@code true} si cambió */
	public boolean renombrar(String nuevo) {
		String valido = nombreValido(nuevo);
		if (valido.equals(nombre)) {
			return false;
		}
		nombre = valido;
		return true;
	}

	private static String nombreValido(String nombre) {
		String limpio = Normalizador.limpiar(nombre);
		if (limpio == null || limpio.length() > MAX_NOMBRE) {
			throw new DatoInvalidoException("nombre", "El nombre de la familia debe tener de 1 a " + MAX_NOMBRE
					+ " caracteres.");
		}
		if ("=+-@".indexOf(limpio.charAt(0)) >= 0) {
			throw new ReglaNegocioException("El nombre de la familia no puede empezar con =, +, - ni @.");
		}
		return limpio;
	}

	public String getNombre() {
		return nombre;
	}
}
