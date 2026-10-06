package pe.edu.virgenmaria.cuentasclaras.recaudacion.formato;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.config.PropiedadesRecaudacion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El archivo de ejemplo de {@code docs/ux/} (para probar la carga en desarrollo con {@code ./mvnw spring-boot:run}) se lee
 * sin errores con el formato genérico y su pie cuadra.
 */
class EjemploRecaudacionTest {

	@Test
	void elArchivoDeEjemploSeLeeSinErrores() throws IOException {
		Path ejemplo = Path.of("docs/ux/ejemplo-recaudacion.csv");
		LecturaRecaudacion lectura = new FormatoGenericoCsv(PropiedadesRecaudacion.porDefecto())
				.leer(ejemplo.getFileName().toString(), Files.readAllBytes(ejemplo), LocalDate.of(2030, 1, 1));

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.filas()).hasSizeGreaterThanOrEqualTo(5);
		assertThat(lectura.cantidadDeclarada()).isEqualTo(lectura.filas().size());
		assertThat(lectura.totalDeclarado()).isNotNull();
	}
}
