package pe.edu.virgenmaria.cuentasclaras.comun.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La configuración de producción no da a la aplicación más de lo que necesita.
 */
class ConfiguracionYamlTest {

	@Test
	void enProduccionLaAplicacionNoMigraNiRecibeLasCredencialesDelMigrador() throws IOException {
		Properties prod = cargar("application-prod.yaml");
		String texto = new ClassPathResource("application-prod.yaml").getContentAsString(StandardCharsets.UTF_8);

		assertThat(prod.getProperty("spring.flyway.enabled")).isEqualTo("false");
		assertThat(prod.stringPropertyNames()).noneMatch(p -> p.startsWith("spring.flyway.user")
				|| p.startsWith("spring.flyway.password"));
		assertThat(texto).doesNotContain("DB_MIGRADOR");
	}

	@Test
	void enProduccionSoloSeCreeLaIpQueEnviaUnProxyDeConfianza() throws IOException {
		Properties prod = cargar("application-prod.yaml");

		assertThat(prod.getProperty("server.forward-headers-strategy")).isEqualTo("native");
		assertThat(prod.getProperty("server.tomcat.remoteip.internal-proxies")).startsWith("${CC_PROXIES_INTERNOS:");
	}

	@Test
	void noHayPerfilPorDefectoNiInterruptorParaSaltarseLosPermisos() throws IOException {
		assertThat(cargar("application.yaml").getProperty("spring.profiles.default")).isNull();
		for (String archivo : new String[] { "application.yaml", "application-prod.yaml", "application-dev.yaml" }) {
			assertThat(cargar(archivo).stringPropertyNames()).noneMatch(p -> p.contains("exigir-permisos"));
		}
	}

	private static Properties cargar(String archivo) {
		YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
		yaml.setResources(new ClassPathResource(archivo));
		return yaml.getObject();
	}
}
