package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.core.MethodParameter;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Las rutas de los controladores de la aplicación (sprint 7, tanda 3), para las pruebas que las recorren todas: CSRF en
 * todo POST e IDOR en toda ruta con id. Lee los mapeos reales de Spring MVC (una ruta nueva entra sola) y dice qué roles
 * la pueden pedir según {@link ModuloApp} (la primera regla que coincide gana, como en {@code ConfiguracionSeguridad}).
 */
public final class RutasDeLaAplicacion {

	/** Una ruta: método HTTP, patrón (como lo reporta Spring) y el método del controlador. */
	public record Ruta(String metodo, String patron, HandlerMethod manejador) {

		/** {@code "GET /alumnos/{id:\\d+}"}: la clave del catálogo. */
		public String clave() {
			return metodo + " " + patron;
		}

		/** Las variables de la ruta, en orden ({@code id}, {@code lineaId}...). */
		public List<String> variables() {
			List<String> nombres = new ArrayList<>();
			Matcher m = VARIABLE.matcher(patron);
			while (m.find()) {
				nombres.add(m.group(1));
			}
			return nombres;
		}

		/**
		 * Parámetros {@code @RequestParam} que pueden nombrar un registro: de tipo {@code Long} (o una lista de
		 * {@code Long}), como {@code familiaId}, {@code anioId} o {@code familia}.
		 */
		public List<String> parametrosConId() {
			List<String> nombres = new ArrayList<>();
			for (MethodParameter p : manejador.getMethodParameters()) {
				RequestParam param = p.getParameterAnnotation(RequestParam.class);
				if (param == null || p.hasParameterAnnotation(PathVariable.class)) {
					continue;
				}
				boolean id = p.getParameterType() == Long.class || p.getParameterType() == long.class
						|| (List.class.isAssignableFrom(p.getParameterType())
								&& p.getGenericParameterType().getTypeName().contains("java.lang.Long"));
				if (id) {
					String nombre = !param.name().isEmpty() ? param.name() : !param.value().isEmpty() ? param.value()
							: p.getParameterName();
					nombres.add(nombre);
				}
			}
			return nombres;
		}

		/**
		 * Campos de los formularios ({@code record} enlazados con {@code @Valid} o {@code @ModelAttribute}) que pueden
		 * nombrar un registro: de tipo {@code Long} o una colección de {@code Long} ({@code familiaId}, {@code cuotaIds}).
		 */
		public List<String> camposConId() {
			List<String> nombres = new ArrayList<>();
			for (MethodParameter p : manejador.getMethodParameters()) {
				Class<?> tipo = p.getParameterType();
				if (!tipo.isRecord() || p.hasParameterAnnotation(RequestParam.class)
						|| p.hasParameterAnnotation(PathVariable.class)) {
					continue;
				}
				for (java.lang.reflect.RecordComponent c : tipo.getRecordComponents()) {
					boolean id = c.getType() == Long.class || c.getType() == long.class
							|| (java.util.Collection.class.isAssignableFrom(c.getType())
									&& c.getGenericType().getTypeName().contains("java.lang.Long"));
					if (id) {
						nombres.add(c.getName());
					}
				}
			}
			return nombres;
		}

		/** La ruta con cada variable reemplazada por su valor (las que falten, por 1). */
		public String concreta(Map<String, String> valores) {
			Matcher m = VARIABLE.matcher(patron);
			StringBuilder ruta = new StringBuilder();
			while (m.find()) {
				m.appendReplacement(ruta, Matcher.quoteReplacement(valores.getOrDefault(m.group(1), "1")));
			}
			m.appendTail(ruta);
			return ruta.toString();
		}
	}

	private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z]+)(?::(?:[^{}]|\\{[^{}]*})*)?}");

	private static final AntPathMatcher COINCIDE = new AntPathMatcher();

	private RutasDeLaAplicacion() {
	}

	/** Todas las rutas de los controladores, una por método HTTP y patrón (sin método declarado: GET y POST). */
	public static List<Ruta> todas(RequestMappingHandlerMapping mapeos) {
		List<Ruta> rutas = new ArrayList<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapeos.getHandlerMethods().entrySet()) {
			Set<RequestMethod> metodos = e.getKey().getMethodsCondition().getMethods();
			List<String> nombres = metodos.isEmpty() ? List.of("GET", "POST")
					: metodos.stream().map(Enum::name).toList();
			for (String patron : e.getKey().getPatternValues()) {
				for (String metodo : nombres) {
					rutas.add(new Ruta(metodo, patron, e.getValue()));
				}
			}
		}
		rutas.sort(Comparator.comparing(Ruta::clave));
		return rutas;
	}

	/** Si la ruta concreta es pública (no exige sesión). */
	public static boolean publica(String ruta) {
		return Arrays.stream(ModuloApp.RUTAS_PUBLICAS).anyMatch(p -> COINCIDE.match(p, ruta))
				|| COINCIDE.match(ModuloApp.RUTAS_WEBHOOK, ruta);
	}

	/** El módulo que decide el permiso de la ruta concreta (la primera regla que coincide). */
	public static Optional<ModuloApp> modulo(String ruta) {
		return Arrays.stream(ModuloApp.values())
				.filter(m -> Arrays.stream(m.patrones()).anyMatch(p -> COINCIDE.match(p, ruta))).findFirst();
	}

	/** Los roles que pueden pedir la ruta concreta (vacío si es pública o no la cubre ningún módulo). */
	public static List<Rol> roles(String ruta) {
		if (ModuloApp.RUTA_CAMBIAR_CLAVE.equals(ruta)) {
			return List.of(Rol.values());
		}
		return modulo(ruta).map(m -> m.roles().stream().sorted().toList()).orElse(List.of());
	}

	/** Ayuda para armar mapas de valores en orden. */
	public static Map<String, String> valores(String... pares) {
		Map<String, String> mapa = new LinkedHashMap<>();
		for (int i = 0; i + 1 < pares.length; i += 2) {
			mapa.put(pares[i], pares[i + 1]);
		}
		return mapa;
	}
}
