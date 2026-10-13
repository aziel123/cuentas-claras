package pe.edu.virgenmaria.cuentasclaras.comun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code cuentasclaras.plataforma} (sprint 8, tanda 1; secciones 3.1 a 3.3).
 * <ul>
 *   <li>{@code instalacion}: {@code DEDICADA} (un colegio, como hoy) o {@code COMPARTIDA} (varios colegios en una base).
 *       La verificación de los conectores por instalación llega en la tanda 2.</li>
 *   <li>{@code operadores}: la lista {@code usuario:correo,usuario2:correo2} del despliegue ({@code CC_OPERADORES},
 *       decisión 112). Al arrancar, {@code InicializadorOperadores} crea al que falta (con su enlace de activación al
 *       correo) y desactiva al que ya no está.</li>
 *   <li>{@code maxColegios} y {@code maxAlumnos}: el tope de una instalación compartida (decisión 138).</li>
 * </ul>
 */
@ConfigurationProperties("cuentasclaras.plataforma")
public record PropiedadesPlataforma(@DefaultValue("DEDICADA") String instalacion, @DefaultValue("") String operadores,
		@DefaultValue("25") int maxColegios, @DefaultValue("8000") int maxAlumnos) {

	public static final String COMPARTIDA = "COMPARTIDA";

	public static final String DEDICADA = "DEDICADA";

	/** Un operador de la lista: su nombre de usuario (ya normalizado) y su correo. */
	public record Operador(String nombreUsuario, String correo) {
	}

	public PropiedadesPlataforma {
		instalacion = instalacion == null ? DEDICADA : instalacion.strip().toUpperCase(Locale.ROOT);
		if (!instalacion.equals(COMPARTIDA) && !instalacion.equals(DEDICADA)) {
			throw new IllegalArgumentException("cuentasclaras.plataforma.instalacion (CC_INSTALACION) debe ser COMPARTIDA "
					+ "o DEDICADA");
		}
		operadores = operadores == null ? "" : operadores.strip();
		if (maxColegios < 1 || maxAlumnos < 1) {
			throw new IllegalArgumentException("cuentasclaras.plataforma.max-colegios y max-alumnos deben ser positivos");
		}
		listaOperadores(operadores);
	}

	public boolean compartida() {
		return COMPARTIDA.equals(instalacion);
	}

	/** Los operadores de {@code CC_OPERADORES}, en orden y sin repetidos. */
	public List<Operador> listaOperadores() {
		return listaOperadores(operadores);
	}

	private static List<Operador> listaOperadores(String lista) {
		List<Operador> resultado = new ArrayList<>();
		if (lista == null || lista.isBlank()) {
			return resultado;
		}
		for (String parte : lista.split(",")) {
			String entrada = parte.strip();
			if (entrada.isEmpty()) {
				continue;
			}
			int separador = entrada.indexOf(':');
			if (separador <= 0 || separador == entrada.length() - 1) {
				throw new IllegalArgumentException("CC_OPERADORES lleva usuario:correo separados por comas; no se entiende «"
						+ entrada + "»");
			}
			String usuario = entrada.substring(0, separador).strip().toLowerCase(Locale.ROOT);
			String correo = entrada.substring(separador + 1).strip();
			if (!usuario.matches("[a-z0-9._-]{3,60}") || !correo.contains("@") || correo.length() > 150) {
				throw new IllegalArgumentException("CC_OPERADORES: el usuario «" + usuario + "» debe tener de 3 a 60 "
						+ "caracteres (minúsculas, números, punto, guion) y un correo válido");
			}
			if (resultado.stream().anyMatch(o -> o.nombreUsuario().equals(usuario))) {
				throw new IllegalArgumentException("CC_OPERADORES repite al usuario «" + usuario + "»");
			}
			resultado.add(new Operador(usuario, correo));
		}
		return resultado;
	}
}
