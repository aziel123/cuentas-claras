package pe.edu.virgenmaria.cuentasclaras.comun.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Se niega a arrancar con una configuración peligrosa, antes de que el servidor web acepte peticiones:
 * <ul>
 *   <li>sin perfil activo (no hay perfil por defecto: {@code dev} en local, {@code prod} en producción);</li>
 *   <li>fuera de {@code dev}/{@code test}, con una clave HMAC de auditoría de desarrollo o de pruebas.</li>
 * </ul>
 */
@Component
public class VerificadorConfiguracion implements InitializingBean {

	static final Set<String> PERFILES_DE_DESARROLLO = Set.of("dev", "test");

	/** Las claves de dev y test lo llevan en el texto; ninguna clave real debe llevarlo. */
	static final String MARCA_CLAVE_DE_DESARROLLO = "no-usar-en-produccion";

	private final Environment entorno;

	public VerificadorConfiguracion(Environment entorno) {
		this.entorno = entorno;
	}

	@Override
	public void afterPropertiesSet() {
		verificar(entorno.getActiveProfiles(), entorno.getProperty("cuentasclaras.auditoria.clave-hmac"));
	}

	/**
	 * Lo mismo, pero apenas está listo el entorno (lo registra {@code main}): así el mensaje sale antes de que
	 * cualquier otra pieza (por ejemplo Flyway con una base embebida) falle por la configuración incompleta.
	 */
	public static final class AlPrepararEntorno implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

		@Override
		public void onApplicationEvent(ApplicationEnvironmentPreparedEvent evento) {
			ConfigurableEnvironment entorno = evento.getEnvironment();
			String clave;
			try {
				clave = entorno.getProperty("cuentasclaras.auditoria.clave-hmac");
			}
			catch (IllegalArgumentException e) {
				clave = null; // ${AUDITORIA_CLAVE_HMAC} sin definir
			}
			verificar(entorno.getActiveProfiles(), clave);
		}
	}

	static void verificar(String[] perfiles, String claveHmac) {
		if (perfiles == null || perfiles.length == 0) {
			throw new IllegalStateException("No hay perfil activo. Indica SPRING_PROFILES_ACTIVE: dev para desarrollo "
					+ "local (./mvnw spring-boot:run ya lo usa) o prod en producción.");
		}
		boolean desarrollo = Arrays.stream(perfiles).anyMatch(PERFILES_DE_DESARROLLO::contains);
		if (!desarrollo && (claveHmac == null || claveHmac.isBlank() || claveHmac.contains(MARCA_CLAVE_DE_DESARROLLO))) {
			throw new IllegalStateException("La clave HMAC de auditoría falta o es la de desarrollo. En producción "
					+ "define AUDITORIA_CLAVE_HMAC con la clave del gestor de secretos.");
		}
	}
}
