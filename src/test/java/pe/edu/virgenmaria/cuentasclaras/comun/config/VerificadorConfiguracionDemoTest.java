package pe.edu.virgenmaria.cuentasclaras.comun.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 8, tanda 3 (sección 8.3, E20 y E21): la demo nunca envía un mensaje real, nunca cobra y nunca toca una base real.
 * {@code VerificadorConfiguracion} no la deja arrancar con otra base, con un proveedor real, con un secreto en el entorno,
 * combinada con prod, piloto o dev, ni sin la franja DEMO. La configuración de {@code application-demo.yaml} (con las
 * claves que se generan al azar al arrancar) sí arranca.
 */
class VerificadorConfiguracionDemoTest {

	private static final String[] DEMO = { "demo" };

	/** application.yaml + application-demo.yaml, con las claves al azar de SecretosAleatoriosDemo. */
	private static Map<String, String> configuracionDeLaDemo() {
		Map<String, String> propiedades = new HashMap<>();
		for (String archivo : new String[] { "application.yaml", "application-demo.yaml" }) {
			YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
			yaml.setResources(new ClassPathResource(archivo));
			Properties leidas = yaml.getObject();
			leidas.stringPropertyNames().forEach(n -> propiedades.put(n, resolver(leidas.getProperty(n))));
		}
		propiedades.put("cuentasclaras.auditoria.clave-hmac", "Q2xhdmUtYWxlYXRvcmlhLWRlLWxhLWRlbW8tcGFyYS1sYS1wcnVlYmEtMDE=");
		propiedades.put("cuentasclaras.conectores.clave", "ZGVtby1kZW1vLWRlbW8tZGVtby1kZW1vLWRlbW8tMDE=");
		return propiedades;
	}

	/** {@code ${VARIABLE:valor}} sin la variable en el entorno: el valor por defecto. */
	private static String resolver(String valor) {
		if (valor != null && valor.startsWith("${") && valor.endsWith("}")) {
			int dos = valor.indexOf(':');
			return dos < 0 ? null : valor.substring(dos + 1, valor.length() - 1);
		}
		return valor;
	}

	@Test
	void conLaConfiguracionDeLaDemoArranca() {
		assertThatCode(() -> VerificadorConfiguracion.verificar(DEMO, configuracionDeLaDemo())).doesNotThrowAnyException();
	}

	@Test
	void conMySqlNoArranca() {
		Map<String, String> config = configuracionDeLaDemo();
		config.put("spring.datasource.url", "jdbc:mysql://10.0.0.5:3306/cuentasclaras?sslMode=VERIFY_CA");

		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(DEMO, config)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("H2 en memoria");
		config.put("spring.datasource.url", "jdbc:h2:file:/datos/demo");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(DEMO, config)).hasMessageContaining("H2 en memoria");
	}

	@Test
	void conUnProveedorRealNoArranca() {
		for (String[] real : new String[][] { { "cuentasclaras.comprobantes.proveedor", "NUBEFACT" },
				{ "cuentasclaras.pasarela.proveedor", "CULQI" },
				{ "cuentasclaras.mensajeria.whatsapp.proveedor", "WHATSAPP_CLOUD" },
				{ "cuentasclaras.mensajeria.correo.proveedor", "SMTP" }, { "spring.mail.host", "smtp.ejemplo.pe" } }) {
			Map<String, String> config = configuracionDeLaDemo();
			config.put(real[0], real[1]);
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(DEMO, config)).as(real[0])
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("simulados");
		}
	}

	@Test
	void conUnSecretoEnElEntornoNoArranca() {
		for (String variable : new String[] { "DB_URL", "DB_SISTEMA_CLAVE", "NUBEFACT_TOKEN", "WHATSAPP_TOKEN",
				"PASARELA_LLAVE_SECRETA", "SPRING_MAIL_HOST", "CC_CLAVE_CONECTORES", "AUDITORIA_CLAVE_HMAC",
				"RESPALDO_DB_CLAVE", "RESPALDO_AGE_DESTINATARIOS" }) {
			Map<String, String> config = configuracionDeLaDemo();
			config.put(variable, "valor-de-una-instalacion-real");
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(DEMO, config)).as(variable)
					.isInstanceOf(IllegalStateException.class).hasMessageContaining(variable);
		}
	}

	@Test
	void combinadaConProdPilotoODevNoArranca() {
		for (String otro : new String[] { "prod", "piloto", "dev" }) {
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "demo", otro }, configuracionDeLaDemo()))
					.as(otro).isInstanceOf(IllegalStateException.class).hasMessageContaining("no se combina");
		}
	}

	@Test
	void sinLaFranjaDemoNoArranca() {
		Map<String, String> config = configuracionDeLaDemo();
		config.put("cuentasclaras.entorno.nombre", "PILOTO");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(DEMO, config)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("DEMO");
		config.remove("cuentasclaras.entorno.nombre");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(DEMO, config)).hasMessageContaining("DEMO");
	}

	@Test
	void laConfiguracionDeLaDemoNoTraeClavesNiSecretos() {
		Map<String, String> yaml = new HashMap<>();
		YamlPropertiesFactoryBean lector = new YamlPropertiesFactoryBean();
		lector.setResources(new ClassPathResource("application-demo.yaml"));
		Properties demo = lector.getObject();
		demo.stringPropertyNames().forEach(n -> yaml.put(n, demo.getProperty(n)));

		assertThat(yaml).doesNotContainKeys("cuentasclaras.auditoria.clave-hmac", "cuentasclaras.conectores.clave",
				"cuentasclaras.pasarela.simulada.secreto-aviso");
		assertThat(yaml.get("spring.datasource.url")).startsWith("jdbc:h2:mem:");
		assertThat(yaml.get("cuentasclaras.entorno.nombre")).isEqualTo("DEMO");
	}
}
