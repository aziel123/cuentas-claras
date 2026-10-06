package pe.edu.virgenmaria.cuentasclaras.comun.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Se niega a arrancar con una configuración peligrosa, antes de que el servidor web acepte peticiones:
 * <ul>
 *   <li>sin perfil activo (no hay perfil por defecto: {@code dev} en local, {@code prod} en producción);</li>
 *   <li>con {@code prod} o {@code piloto} combinados con {@code dev} o {@code test} (o entre sí): con
 *       {@code prod,dev} antes contaba como desarrollo y no exigía la clave HMAC (sprint 4, hallazgo 8);</li>
 *   <li>fuera de {@code dev}/{@code test}, con una clave HMAC de auditoría de desarrollo o de pruebas;</li>
 *   <li>con la pasarela SIMULADA fuera de {@code dev}, {@code test} o {@code piloto} (en {@code prod}, nunca), o en el
 *       piloto con el secreto de avisos de desarrollo;</li>
 *   <li>con una pasarela real sin su llave secreta y las credenciales de sus avisos, con una llave de pruebas en
 *       {@code prod} o una llave de producción fuera de {@code prod} (desde dev nunca se cobra de verdad);</li>
 *   <li>con Nubefact sin token, con una ruta que no es {@code https} a un dominio de la lista cerrada (nadie apunta el
 *       «OSE» a un servidor propio que responda ACEPTADO) o, fuera de {@code prod}, sin la marca explícita.</li>
 * </ul>
 */
@Component
public class VerificadorConfiguracion implements InitializingBean {

	static final Set<String> PERFILES_DE_DESARROLLO = Set.of("dev", "test");

	/** Perfiles que usan una base real (MySQL con cc_app): no se combinan con los de desarrollo ni entre sí. */
	static final Set<String> PERFILES_DE_DESPLIEGUE = Set.of("prod", "piloto");

	/** Donde puede existir la pasarela simulada (el bean tiene el mismo {@code @Profile}). */
	static final Set<String> PERFILES_PASARELA_SIMULADA = Set.of("dev", "test", "piloto");

	/** Las claves de dev y test lo llevan en el texto; ninguna clave real debe llevarlo. */
	static final String MARCA_CLAVE_DE_DESARROLLO = "no-usar-en-produccion";

	static final String CLAVE_HMAC = "cuentasclaras.auditoria.clave-hmac";

	static final String PASARELA = "cuentasclaras.pasarela.proveedor";

	/** S4-M1: la marca del entorno de prueba (franja global); en el piloto, la simulada la exige. */
	static final String ENTORNO = "cuentasclaras.entorno.nombre";

	static final String SECRETO_SIMULADA = "cuentasclaras.pasarela.simulada.secreto-aviso";

	static final String CULQI_LLAVE_SECRETA = "cuentasclaras.pasarela.culqi.llave-secreta";

	static final String CULQI_WEBHOOK_USUARIO = "cuentasclaras.pasarela.culqi.webhook-usuario";

	static final String CULQI_WEBHOOK_CLAVE = "cuentasclaras.pasarela.culqi.webhook-clave";

	static final String COMPROBANTES = "cuentasclaras.comprobantes.proveedor";

	static final String REAL_FUERA_DE_PROD = "cuentasclaras.comprobantes.permitir-real-fuera-de-prod";

	static final String NUBEFACT_RUTA = "cuentasclaras.comprobantes.nubefact.ruta";

	static final String NUBEFACT_TOKEN = "cuentasclaras.comprobantes.nubefact.token";

	static final String NUBEFACT_DOMINIOS = "cuentasclaras.comprobantes.nubefact.dominios-permitidos";

	/** Prefijos de las llaves secretas de Culqi: {@code sk_live_} cobra de verdad; {@code sk_test_} no. */
	static final String LLAVE_PRODUCCION = "sk_live_";

	static final String LLAVE_PRUEBAS = "sk_test_";

	/** Lista cerrada por defecto si no se configura otra. */
	static final String DOMINIOS_NUBEFACT_POR_DEFECTO = "api.nubefact.com";

	private final Environment entorno;

	public VerificadorConfiguracion(Environment entorno) {
		this.entorno = entorno;
	}

	@Override
	public void afterPropertiesSet() {
		verificar(entorno.getActiveProfiles(), nombre -> leer(entorno, nombre));
	}

	/**
	 * Lo mismo, pero apenas está listo el entorno (lo registra {@code main}): así el mensaje sale antes de que
	 * cualquier otra pieza (por ejemplo Flyway con una base embebida) falle por la configuración incompleta.
	 */
	public static final class AlPrepararEntorno implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

		@Override
		public void onApplicationEvent(ApplicationEnvironmentPreparedEvent evento) {
			ConfigurableEnvironment entorno = evento.getEnvironment();
			verificar(entorno.getActiveProfiles(), nombre -> leer(entorno, nombre));
		}
	}

	private static String leer(Environment entorno, String nombre) {
		try {
			return entorno.getProperty(nombre);
		}
		catch (IllegalArgumentException e) {
			return null; // ${VARIABLE} sin definir
		}
	}

	/** Solo la clave HMAC (el resto de la configuración con sus valores por defecto). */
	static void verificar(String[] perfiles, String claveHmac) {
		verificar(perfiles, nombre -> CLAVE_HMAC.equals(nombre) ? claveHmac : null);
	}

	static void verificar(String[] perfiles, Map<String, String> propiedades) {
		verificar(perfiles, propiedades::get);
	}

	static void verificar(String[] perfiles, Function<String, String> propiedad) {
		if (perfiles == null || perfiles.length == 0) {
			throw new IllegalStateException("No hay perfil activo. Indica SPRING_PROFILES_ACTIVE: dev para desarrollo "
					+ "local (./mvnw spring-boot:run ya lo usa) o prod en producción.");
		}
		List<String> activos = Arrays.asList(perfiles);
		boolean desarrollo = activos.stream().anyMatch(PERFILES_DE_DESARROLLO::contains);
		long despliegue = activos.stream().filter(PERFILES_DE_DESPLIEGUE::contains).count();
		if (despliegue > 1 || (despliegue == 1 && desarrollo)) {
			throw new IllegalStateException("Los perfiles " + String.join(",", activos) + " no se combinan: prod y piloto "
					+ "usan la base real y nunca van con dev, test ni entre sí. Indica un solo perfil de despliegue.");
		}
		String claveHmac = propiedad.apply(CLAVE_HMAC);
		if (!desarrollo && (claveHmac == null || claveHmac.isBlank() || claveHmac.contains(MARCA_CLAVE_DE_DESARROLLO))) {
			throw new IllegalStateException("La clave HMAC de auditoría falta o es la de desarrollo. En producción "
					+ "define AUDITORIA_CLAVE_HMAC con la clave del gestor de secretos.");
		}
		boolean prod = activos.contains("prod");
		verificarPasarela(activos, prod, propiedad);
		verificarComprobantes(prod, propiedad);
	}

	private static void verificarPasarela(List<String> activos, boolean prod, Function<String, String> propiedad) {
		String proveedor = mayusculas(propiedad.apply(PASARELA));
		if (proveedor == null || proveedor.equals("NINGUNA")) {
			return;
		}
		if (proveedor.equals("SIMULADA")) {
			if (prod || activos.stream().noneMatch(PERFILES_PASARELA_SIMULADA::contains)) {
				throw new IllegalStateException("La pasarela SIMULADA marca pagos sin dinero real: solo existe en dev, test o "
						+ "piloto, nunca en producción. Configura cuentasclaras.pasarela.proveedor con la pasarela real o "
						+ "NINGUNA.");
			}
			if (activos.contains("piloto") && !PropiedadesEntorno.PILOTO.equalsIgnoreCase(
					propiedad.apply(ENTORNO) == null ? "" : propiedad.apply(ENTORNO).strip())) {
				throw new IllegalStateException("En el piloto, la pasarela SIMULADA exige la marca del entorno de prueba "
						+ "(cuentasclaras.entorno.nombre: PILOTO): todas las páginas muestran que los pagos simulados no son "
						+ "dinero real y no se aplican a cuotas.");
			}
			String secreto = propiedad.apply(SECRETO_SIMULADA);
			if (activos.contains("piloto") && (vacio(secreto) || secreto.contains(MARCA_CLAVE_DE_DESARROLLO))) {
				throw new IllegalStateException("En el piloto, el secreto de los avisos de la pasarela simulada falta o es el "
						+ "de desarrollo: define PASARELA_SIMULADA_SECRETO con un valor propio.");
			}
			return;
		}
		if (!proveedor.equals("CULQI")) {
			throw new IllegalStateException("La pasarela " + proveedor + " todavía no está integrada (falta su adaptador). "
					+ "Usa NINGUNA hasta tener el contrato y el adaptador probado.");
		}
		String llave = propiedad.apply(CULQI_LLAVE_SECRETA);
		if (vacio(llave) || vacio(propiedad.apply(CULQI_WEBHOOK_USUARIO)) || vacio(propiedad.apply(CULQI_WEBHOOK_CLAVE))) {
			throw new IllegalStateException("La pasarela " + proveedor + " necesita su llave secreta y las credenciales de "
					+ "sus avisos (PASARELA_LLAVE_SECRETA, PASARELA_WEBHOOK_USUARIO y PASARELA_WEBHOOK_CLAVE).");
		}
		if (prod && !llave.startsWith(LLAVE_PRODUCCION)) {
			throw new IllegalStateException("En producción la llave de la pasarela debe ser la de producción ("
					+ LLAVE_PRODUCCION + "...): con una llave de pruebas los pagos no serían reales.");
		}
		if (!prod && !llave.startsWith(LLAVE_PRUEBAS)) {
			throw new IllegalStateException("Fuera de producción la llave de la pasarela debe ser la de pruebas ("
					+ LLAVE_PRUEBAS + "...): desde un entorno de prueba nunca se cobra de verdad.");
		}
	}

	private static void verificarComprobantes(boolean prod, Function<String, String> propiedad) {
		String proveedor = mayusculas(propiedad.apply(COMPROBANTES));
		if (proveedor == null || proveedor.equals("SIMULADO")) {
			return;
		}
		if (!proveedor.equals("NUBEFACT")) {
			throw new IllegalStateException("El proveedor de comprobantes " + proveedor + " no existe: usa SIMULADO o "
					+ "NUBEFACT.");
		}
		String ruta = propiedad.apply(NUBEFACT_RUTA);
		if (vacio(ruta) || vacio(propiedad.apply(NUBEFACT_TOKEN))) {
			throw new IllegalStateException("Nubefact necesita la RUTA y el TOKEN de la cuenta del colegio (NUBEFACT_RUTA y "
					+ "NUBEFACT_TOKEN).");
		}
		if (!rutaPermitida(ruta, propiedad.apply(NUBEFACT_DOMINIOS))) {
			throw new IllegalStateException("La ruta de Nubefact debe ser https y de un dominio de la lista cerrada "
					+ dominios(propiedad.apply(NUBEFACT_DOMINIOS)) + ": nadie debe poder apuntar el OSE a un servidor propio "
					+ "que responda ACEPTADO.");
		}
		if (!prod && !"true".equalsIgnoreCase(String.valueOf(propiedad.apply(REAL_FUERA_DE_PROD)).strip())) {
			throw new IllegalStateException("Fuera de producción no se emiten comprobantes reales: para probar con la cuenta "
					+ "DEMO de Nubefact activa cuentasclaras.comprobantes.permitir-real-fuera-de-prod.");
		}
	}

	/** Ruta {@code https} cuyo host está en la lista cerrada (también la usa el emisor real antes de cada envío). */
	public static boolean rutaPermitida(String ruta, String dominiosPermitidos) {
		if (vacio(ruta)) {
			return false;
		}
		try {
			URI uri = URI.create(ruta.strip());
			return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
					&& dominios(dominiosPermitidos).contains(uri.getHost().toLowerCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static Set<String> dominios(String lista) {
		return Set.copyOf(Arrays.stream((vacio(lista) ? DOMINIOS_NUBEFACT_POR_DEFECTO : lista).split(","))
				.map(d -> d.strip().toLowerCase(Locale.ROOT)).filter(d -> !d.isEmpty()).toList());
	}

	private static boolean vacio(String texto) {
		return texto == null || texto.isBlank();
	}

	private static String mayusculas(String texto) {
		return vacio(texto) ? null : texto.strip().toUpperCase(Locale.ROOT);
	}
}
