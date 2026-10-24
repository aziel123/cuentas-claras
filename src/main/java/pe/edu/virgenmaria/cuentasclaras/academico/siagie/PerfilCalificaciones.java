package pe.edu.virgenmaria.cuentasclaras.academico.siagie;

import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sprint 10, tanda 3 (sección 3.10, S5, decisión 181): el perfil de columnas de la exportación de calificaciones, versionado
 * DENTRO del jar ({@code src/main/resources/siagie/perfiles/{nombre}.json}), no editable por el colegio. Las columnas del
 * alumno salen del catálogo cerrado {@link CampoCalificaciones}; por competencia van el nivel y, si {@code conclusion}, la
 * conclusión descriptiva del periodo.
 */
public record PerfilCalificaciones(String nombre, List<Columna> columnas, boolean conclusion) {

	public static final String PROVISIONAL = "SIAGIE_CALIFICACIONES_PROVISIONAL";

	public record Columna(String encabezado, CampoCalificaciones campo, Map<String, String> valores) {

		public Columna {
			Objects.requireNonNull(encabezado, "encabezado");
			Objects.requireNonNull(campo, "campo");
			valores = valores == null ? Map.of() : Map.copyOf(valores);
		}

		public String traducir(String valor) {
			return valor == null ? "" : valores.getOrDefault(valor, valor);
		}
	}

	public PerfilCalificaciones {
		Objects.requireNonNull(nombre, "nombre");
		columnas = List.copyOf(columnas);
	}

	/** Lee un perfil del jar (solo letras, dígitos y «_» en el nombre: nada de rutas). */
	public static PerfilCalificaciones leer(String nombre) {
		if (nombre == null || !nombre.matches("[A-Z0-9_]{3,40}")) {
			throw new IllegalArgumentException("Perfil de calificaciones no válido: " + nombre);
		}
		try (InputStream entrada = new ClassPathResource("siagie/perfiles/" + nombre + ".json").getInputStream()) {
			return JsonMapper.builder().build().readValue(entrada, PerfilCalificaciones.class);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Falta el perfil de calificaciones " + nombre, e);
		}
	}

	public boolean esProvisional() {
		return nombre.contains("PROVISIONAL");
	}
}
