package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 1 (sección 3.3 y 9; E30 y E31): los DTO del paquete {@code academico.dto} (los que ve el docente y los
 * de Docentes y secciones) no tienen campos de documento, nacimiento, contacto, apoderado ni dinero. Se leen TODOS los
 * archivos del paquete: un DTO nuevo entra solo. Lo mismo valdrá para {@code comunicados.dto} (tanda 3).
 */
class DtoDocenteSinDatosTest {

	static final List<String> PROHIBIDOS = List.of("documento", "dni", "nacimiento", "telefono", "celular", "correo",
			"apoderado", "familia", "monto", "deuda", "saldo", "pago", "cuota", "pension", "moros");

	private static final String PAQUETE = "pe.edu.virgenmaria.cuentasclaras.academico.dto";

	@Test
	void ningunCampoDeLosDtoAcademicosEsDeDocumentosContactosODinero() throws Exception {
		List<Class<?>> dtos = clasesDelPaquete();
		assertThat(dtos).as("se leyeron los DTO del paquete").hasSizeGreaterThanOrEqualTo(10)
				.allMatch(Class::isRecord);
		List<String> encontrados = new ArrayList<>();
		for (Class<?> dto : dtos) {
			for (RecordComponent c : dto.getRecordComponents()) {
				String nombre = c.getName().toLowerCase(Locale.ROOT);
				PROHIBIDOS.stream().filter(nombre::contains).forEach(p -> encontrados.add(dto.getSimpleName() + "."
						+ c.getName() + " (" + p + ")"));
			}
		}
		assertThat(encontrados).as("campos prohibidos en los DTO académicos").isEmpty();
	}

	@Test
	void elDetectorReconoceUnCampoProhibido() {
		record Inventado(String nombres, String numeroDocumento, java.math.BigDecimal deudaVencida) {
		}
		assertThat(Stream.of(Inventado.class.getRecordComponents()).map(c -> c.getName().toLowerCase(Locale.ROOT))
				.filter(n -> PROHIBIDOS.stream().anyMatch(n::contains))).containsExactly("numerodocumento", "deudavencida");
	}

	private static List<Class<?>> clasesDelPaquete() throws IOException, ClassNotFoundException {
		Path carpeta = Path.of("src/main/java", PAQUETE.replace('.', '/'));
		List<Class<?>> clases = new ArrayList<>();
		try (Stream<Path> archivos = Files.list(carpeta)) {
			for (Path archivo : archivos.filter(a -> a.toString().endsWith(".java")).sorted().toList()) {
				String nombre = archivo.getFileName().toString().replace(".java", "");
				clases.add(Class.forName(PAQUETE + "." + nombre));
			}
		}
		return clases;
	}
}
