package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 3 (A9, B5): en el contenedor, {@code /tmp} es tmpfs CON tamaño (el analizador de PDF escribe ahí sus
 * archivos de trabajo: un tope de memoria no basta) y la aplicación usa hasta el 55 % de la memoria del contenedor para
 * dejar lugar al proceso hijo del analizador.
 */
class TmpfsConTamanoTest {

	@ParameterizedTest
	@ValueSource(strings = { "deploy/compose.prod.yml", "deploy/compose.demo.yml" })
	void tmpfsConTamanoYLaAplicacionAl55(String archivo) throws Exception {
		String compose = Files.readString(Path.of(archivo));
		assertThat(compose).containsPattern("/tmp:size=\\d+m");
		assertThat(compose).contains("-XX:MaxRAMPercentage=55").doesNotContain("MaxRAMPercentage=75");
		assertThat(compose).contains("mem_limit:");
	}
}
