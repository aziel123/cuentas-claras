package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Los privilegios que {@code scripts/mysql/02-permisos-tablas.sql} (empaquetado en el jar) da a cada conexión de la
 * aplicación, y la comparación con lo que la base dice que tiene ({@code SHOW GRANTS}) (sprint 7, tanda 2; H6 y E9).
 * {@code 02} es la fuente única: cualquier privilegio que no salga de ahí (un GRANT dado a mano, un rol de más, un
 * privilegio global o sobre el esquema como TRIGGER, CREATE o ALTER) no deja arrancar prod.
 */
public final class PermisosEsperados {

	/** El esquema de la aplicación. */
	static final String ESQUEMA = "cuentasclaras";

	static final String ROL_NEGOCIO = "cc_negocio";

	static final String SISTEMA = "cc_sistema";

	/** Funciones DEFINER de 02 que las conexiones pueden ejecutar. */
	static final Set<String> FUNCIONES = Set.of("triggers_instalados", "huellas_objetos");

	private static final Pattern GRANT = Pattern.compile("(?is)^GRANT\\s+(.+?)\\s+ON\\s+(?:(FUNCTION|PROCEDURE)\\s+)?"
			+ "(\\S+)\\s+TO\\s+(.+)$");

	/** {@code GRANT `rol`@`%`, ... TO `usuario`@`%`} (sin ON): la concesión de un rol. */
	private static final Pattern ROLES = Pattern.compile("(?is)^GRANT\\s+((?:`[^`]+`@`[^`]+`\\s*,?\\s*)+)\\s+TO\\s+.+$");

	/** Por tabla, por privilegio: las columnas (vacío = toda la tabla). */
	private final Map<String, Map<String, Set<String>>> tablas = new LinkedHashMap<>();

	private final Set<String> esquema = new TreeSet<>();

	private PermisosEsperados() {
	}

	/** Lo que 02 da a {@code cc_negocio} más, si es {@code cc_sistema}, lo que le da directo. */
	public static PermisosEsperados de(String usuario) {
		return de(HuellasObjetosBd.leer(HuellasObjetosBd.PERMISOS), usuario);
	}

	static PermisosEsperados de(String script, String usuario) {
		PermisosEsperados esperados = new PermisosEsperados();
		for (String sentencia : HuellasObjetosBd.sentencias(script)) {
			Matcher m = GRANT.matcher(sentencia.trim());
			if (!m.matches() || m.group(2) != null) {
				continue;
			}
			Set<String> para = destinatarios(m.group(4));
			if (!para.contains(ROL_NEGOCIO) && !para.contains(usuario)) {
				continue;
			}
			esperados.agregar(objeto(m.group(3)), privilegios(m.group(1)));
		}
		// Fase 1 del despliegue (01-usuarios.sql, antes de 02): solo el SELECT general.
		esperados.esquema.add("SELECT");
		return esperados;
	}

	/**
	 * Las líneas de {@code SHOW GRANTS} (con los roles activos expandidos) que dan algo que 02 no da.
	 *
	 * @return la lista de problemas, vacía si todo está en 02
	 */
	public List<String> deMas(List<String> lineas) {
		List<String> problemas = new ArrayList<>();
		for (String linea : lineas) {
			String l = linea.trim();
			Matcher rol = ROLES.matcher(l);
			if (rol.matches()) {
				// GRANT `rol`@`%` TO ...: el único rol es cc_negocio.
				if (!destinatarios(rol.group(1)).equals(Set.of(ROL_NEGOCIO))) {
					problemas.add(l);
				}
				continue;
			}
			Matcher m = GRANT.matcher(l);
			if (!m.matches()) {
				problemas.add(l);
				continue;
			}
			String privilegios = m.group(1).trim();
			String rutina = m.group(2);
			String objeto = objeto(m.group(3));
			if (privilegios.toUpperCase(Locale.ROOT).startsWith("PROXY")) {
				problemas.add(l);
			}
			else if (objeto.equals("*.*")) {
				if (!privilegios.equalsIgnoreCase("USAGE")) {
					problemas.add(l);
				}
			}
			else if (rutina != null) {
				String[] partes = objeto.split("\\.", 2);
				if (!partes[0].equals(ESQUEMA) || !FUNCIONES.contains(partes[1])
						|| !privilegios.equalsIgnoreCase("EXECUTE")) {
					problemas.add(l);
				}
			}
			else {
				String[] partes = objeto.split("\\.", 2);
				if (!partes[0].equals(ESQUEMA)) {
					problemas.add(l);
				}
				else if (partes[1].equals("*")) {
					privilegios(privilegios).keySet().stream().filter(p -> !esquema.contains(p)).findFirst()
							.ifPresent(p -> problemas.add(l));
				}
				else if (!cubre(partes[1], privilegios(privilegios))) {
					problemas.add(l);
				}
			}
		}
		return problemas;
	}

	private boolean cubre(String tabla, Map<String, Set<String>> pedidos) {
		Map<String, Set<String>> dados = tablas.getOrDefault(tabla, Map.of());
		for (Map.Entry<String, Set<String>> p : pedidos.entrySet()) {
			if (esquema.contains(p.getKey())) {
				continue;
			}
			Set<String> columnas = dados.get(p.getKey());
			if (columnas == null) {
				return false;
			}
			if (!columnas.isEmpty() && (p.getValue().isEmpty() || !columnas.containsAll(p.getValue()))) {
				return false;
			}
		}
		return true;
	}

	private void agregar(String objeto, Map<String, Set<String>> privilegios) {
		String[] partes = objeto.split("\\.", 2);
		if (partes.length < 2 || !partes[0].equals(ESQUEMA)) {
			return;
		}
		if (partes[1].equals("*")) {
			esquema.addAll(privilegios.keySet());
			return;
		}
		Map<String, Set<String>> dados = tablas.computeIfAbsent(partes[1], t -> new LinkedHashMap<>());
		privilegios.forEach((privilegio, columnas) -> {
			Set<String> actuales = dados.get(privilegio);
			if (actuales == null) {
				dados.put(privilegio, new TreeSet<>(columnas));
			}
			else if (!actuales.isEmpty()) {
				if (columnas.isEmpty()) {
					actuales.clear();
				}
				else {
					actuales.addAll(columnas);
				}
			}
		});
	}

	/** {@code INSERT, UPDATE (a, b)} → {INSERT: [], UPDATE: [a, b]}. */
	static Map<String, Set<String>> privilegios(String texto) {
		Map<String, Set<String>> privilegios = new LinkedHashMap<>();
		int nivel = 0;
		StringBuilder actual = new StringBuilder();
		for (char c : (texto + ",").toCharArray()) {
			if (c == '(') {
				nivel++;
			}
			else if (c == ')') {
				nivel--;
			}
			if (c == ',' && nivel == 0) {
				String p = actual.toString().trim();
				actual.setLength(0);
				if (p.isEmpty()) {
					continue;
				}
				int abre = p.indexOf('(');
				String nombre = (abre < 0 ? p : p.substring(0, abre)).trim().replaceAll("\\s+", " ")
						.toUpperCase(Locale.ROOT);
				Set<String> columnas = new TreeSet<>();
				if (abre >= 0) {
					for (String col : p.substring(abre + 1, p.lastIndexOf(')')).split(",")) {
						columnas.add(limpiar(col));
					}
				}
				privilegios.merge(nombre, columnas, (a, b) -> {
					if (a.isEmpty() || b.isEmpty()) {
						return new TreeSet<>();
					}
					a.addAll(b);
					return a;
				});
				continue;
			}
			actual.append(c);
		}
		return privilegios;
	}

	/** {@code `cuentasclaras`.`pago`} o {@code cuentasclaras.pago} → {@code cuentasclaras.pago}. */
	private static String objeto(String texto) {
		return texto.trim().replace("`", "").toLowerCase(Locale.ROOT);
	}

	/** {@code 'cc_app'@'%', 'cc_sistema'@'%'} → {cc_app, cc_sistema}. */
	private static Set<String> destinatarios(String texto) {
		Set<String> nombres = new LinkedHashSet<>();
		for (String parte : texto.split(",")) {
			String nombre = parte.trim();
			int arroba = nombre.indexOf('@');
			if (arroba >= 0) {
				nombre = nombre.substring(0, arroba);
			}
			nombres.add(limpiar(nombre));
		}
		return nombres;
	}

	private static String limpiar(String texto) {
		return texto.trim().replace("`", "").replace("'", "").toLowerCase(Locale.ROOT);
	}
}
