package pe.edu.virgenmaria.cuentasclaras.comun.demo;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * Sprint 8, tanda 3 (sección 9 del diseño): en el perfil {@code demo} la clave HMAC de la bitácora, la de los secretos de
 * los conectores y el secreto de los avisos de la pasarela simulada se generan AL AZAR en cada arranque (la base es H2 en
 * memoria y se borra cada noche: no hay nada que conservar). Así ninguna de esas claves vive en un archivo, en el entorno
 * ni en la imagen; {@code VerificadorConfiguracion} rechaza que lleguen del entorno.
 * <p>
 * Corre después de leer la configuración (los perfiles ya se conocen) y antes de que se cree cualquier bean. Fuera del
 * perfil {@code demo} no hace nada. Se registra en {@code META-INF/spring.factories}.
 */
public class SecretosAleatoriosDemo implements EnvironmentPostProcessor, Ordered {

	/** El nombre de la fuente de propiedades (la primera: gana sobre los archivos y el entorno). */
	public static final String FUENTE = "secretosAleatoriosDemo";

	private final SecureRandom azar = new SecureRandom();

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment entorno, SpringApplication aplicacion) {
		if (!Arrays.asList(entorno.getActiveProfiles()).contains("demo") || entorno.getPropertySources().contains(FUENTE)) {
			return;
		}
		entorno.getPropertySources().addFirst(new MapPropertySource(FUENTE, Map.of(
				"cuentasclaras.auditoria.clave-hmac", aleatoria(48),
				"cuentasclaras.conectores.clave", aleatoria(32),
				"cuentasclaras.conectores.clave-version", "1",
				"cuentasclaras.pasarela.simulada.secreto-aviso", aleatoria(32))));
	}

	private String aleatoria(int bytes) {
		byte[] valor = new byte[bytes];
		azar.nextBytes(valor);
		return Base64.getEncoder().encodeToString(valor);
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}
}
