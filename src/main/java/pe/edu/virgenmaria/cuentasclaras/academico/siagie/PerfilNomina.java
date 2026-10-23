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
 * Sprint 9, tanda 3 (sección 3.11, decisión 165): un perfil de columnas de la nómina, versionado DENTRO del jar
 * ({@code src/main/resources/siagie/perfiles/{nombre}.json}), no editable por el colegio: el formato es del MINEDU y
 * cambiarlo es una entrega revisada. Cada columna es un {@link CampoNomina} del catálogo cerrado, con la traducción de sus
 * valores si hace falta ({@code M → Hombre}).
 */
public record PerfilNomina(String nombre, String hoja, List<Columna> columnas) {

	public static final String PROVISIONAL = "SIAGIE_MATRICULA_PROVISIONAL";

	public record Columna(String encabezado, CampoNomina campo, Map<String, String> valores) {

		public Columna {
			Objects.requireNonNull(encabezado, "encabezado");
			Objects.requireNonNull(campo, "campo");
			valores = valores == null ? Map.of() : Map.copyOf(valores);
		}

		public String traducir(String valor) {
			return valor == null ? "" : valores.getOrDefault(valor, valor);
		}
	}

	public PerfilNomina {
		Objects.requireNonNull(nombre, "nombre");
		Objects.requireNonNull(hoja, "hoja");
		columnas = List.copyOf(columnas);
	}

	/** Lee un perfil del jar (solo letras, dígitos y «_» en el nombre: nada de rutas). */
	public static PerfilNomina leer(String nombre) {
		if (nombre == null || !nombre.matches("[A-Z0-9_]{3,40}")) {
			throw new IllegalArgumentException("Perfil de nómina no válido: " + nombre);
		}
		try (InputStream entrada = new ClassPathResource("siagie/perfiles/" + nombre + ".json").getInputStream()) {
			return JsonMapper.builder().build().readValue(entrada, PerfilNomina.class);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Falta el perfil de nómina " + nombre, e);
		}
	}

	public boolean esProvisional() {
		return nombre.contains("PROVISIONAL");
	}
}
