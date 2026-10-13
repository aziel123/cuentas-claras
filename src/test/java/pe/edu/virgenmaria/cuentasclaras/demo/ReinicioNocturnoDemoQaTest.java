package pe.edu.virgenmaria.cuentasclaras.demo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA del sprint 8 (sección 8.1, decisión 126): la demo se borra cada noche a las 03:00 de Lima. Lo que la borra es reiniciar
 * la aplicación: solo funciona si la base es H2 EN MEMORIA y el temporizador corre en la hora de Lima (la VM de Google Cloud
 * está en UTC por defecto: 03:00 UTC serían las 22:00 de Lima, en plena demostración nocturna).
 *
 * <p>Dado el despliegue de la demo, cuando llega la noche, entonces el temporizador reinicia la aplicación a las 03:00 de
 * Lima y la base no sobrevive al reinicio.
 */
class ReinicioNocturnoDemoQaTest {

	private static String leer(String ruta) throws IOException {
		return Files.readString(Path.of(ruta), StandardCharsets.UTF_8);
	}

	@Test
	void debeReiniciarLaDemoALasTresDeLaMadrugadaDeLima() throws IOException {
		String temporizador = leer("deploy/systemd/cc-demo-reinicio.timer");

		assertThat(temporizador).containsPattern("(?m)^OnCalendar=\\*-\\*-\\* 03:00:00 America/Lima$");
		assertThat(leer("deploy/systemd/cc-demo-reinicio.service")).contains("compose.demo.yml").contains("restart app");
	}

	@Test
	void debeUsarSoloUnaBaseEnMemoriaQueSeBorraAlReiniciar() throws IOException {
		String demo = leer("src/main/resources/application-demo.yaml");

		assertThat(demo).contains("jdbc:h2:mem:").doesNotContain("jdbc:h2:file:").doesNotContain("jdbc:mysql:");
		assertThat(leer("deploy/compose.demo.yml")).doesNotContainPattern("(?m)^\\s+-\\s+[^#\\n]*:/app/data")
				.contains("TZ: America/Lima");
	}
}
