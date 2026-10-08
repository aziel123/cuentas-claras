package pe.edu.virgenmaria.cuentasclaras.auditoria.model;

import java.util.regex.Pattern;

/**
 * Quién hizo la acción y desde dónde.
 *
 * @param colegioId     colegio del actor; {@code null} si no se pudo identificar (p. ej. login de un usuario inexistente)
 * @param usuarioId     id del usuario; {@code null} para procesos del sistema o visitantes
 * @param nombreUsuario nombre de usuario, {@code sistema} o {@code anonimo}
 * @param roles         roles separados por coma, sin el prefijo {@code ROLE_}
 * @param ip            IP v4 o v6 literal; cualquier otro texto se guarda como {@value #IP_NO_VALIDA}
 */
public record Actor(Long colegioId, Long usuarioId, String nombreUsuario, String roles, String ip) {

	public static final String SISTEMA = "sistema";

	public static final String ANONIMO = "anonimo";

	public static final String IP_NO_VALIDA = "(ip no válida)";

	private static final Pattern IPV4 = Pattern.compile(
			"^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

	private static final Pattern GRUPO_IPV6 = Pattern.compile("^[0-9A-Fa-f]{1,4}$");

	public Actor {
		if (nombreUsuario == null || nombreUsuario.isBlank()) {
			throw new IllegalArgumentException("El actor de un evento de auditoría debe tener nombre");
		}
		ip = normalizarIp(ip);
	}

	/** Proceso sin usuario (arranque, tareas programadas) que actúa sobre un colegio. */
	public static Actor sistema(Long colegioId) {
		return new Actor(colegioId, null, SISTEMA, null, null);
	}

	/**
	 * La IP tal cual si es una dirección literal válida; si no, {@value #IP_NO_VALIDA}. No consulta DNS.
	 * Evita que un texto arbitrario (por ejemplo, una cabecera manipulada) termine en la bitácora.
	 */
	public static String normalizarIp(String ip) {
		if (ip == null || ip.isBlank()) {
			return null;
		}
		String texto = ip.strip();
		return esIpv4(texto) || esIpv6(texto) ? texto : IP_NO_VALIDA;
	}

	static boolean esIpv4(String texto) {
		return IPV4.matcher(texto).matches();
	}

	static boolean esIpv6(String texto) {
		if (texto.length() > 45 || !texto.contains(":") || texto.contains(":::")) {
			return false;
		}
		String sinZona = texto.contains("%") ? texto.substring(0, texto.indexOf('%')) : texto;
		if (texto.contains("%") && !texto.substring(texto.indexOf('%') + 1).matches("[0-9A-Za-z]{1,15}")) {
			return false;
		}
		int compresiones = sinZona.split("::", -1).length - 1;
		if (compresiones > 1) {
			return false;
		}
		String[] partes = sinZona.split(":", -1);
		int grupos = 0;
		for (int i = 0; i < partes.length; i++) {
			String parte = partes[i];
			if (parte.isEmpty()) {
				continue; // parte de "::" o del inicio/fin comprimido
			}
			if (i == partes.length - 1 && parte.contains(".")) {
				if (!esIpv4(parte)) {
					return false;
				}
				grupos += 2;
			}
			else if (GRUPO_IPV6.matcher(parte).matches()) {
				grupos++;
			}
			else {
				return false;
			}
		}
		if (sinZona.startsWith(":") && !sinZona.startsWith("::") || sinZona.endsWith(":") && !sinZona.endsWith("::")) {
			return false;
		}
		return compresiones == 1 ? grupos < 8 : grupos == 8;
	}
}
