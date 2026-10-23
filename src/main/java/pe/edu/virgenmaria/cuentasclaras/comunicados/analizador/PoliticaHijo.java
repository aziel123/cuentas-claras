package pe.edu.virgenmaria.cuentasclaras.comunicados.analizador;

import java.io.FilePermission;
import java.net.SocketPermission;
import java.net.URLPermission;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.Permission;
import java.util.List;

/**
 * Sprint 9, tanda 3 (B5, sección 6 del brief): lo único que puede hacer el proceso hijo que analiza un PDF. Leer el jar
 * (o las clases del classpath), {@code java.home} y su directorio de trabajo; escribir y borrar solo en su directorio;
 * NINGUNA conexión de red (ni {@code SocketPermission} ni {@code URLPermission}), no lanzar procesos y no cambiar esta
 * política. Lo demás (propiedades, reflexión de las bibliotecas) se permite: el hijo no tiene credenciales (entorno
 * vacío) ni nada que leer fuera de su directorio.
 * <p>
 * Java 21 todavía tiene {@code SecurityManager} (con {@code -Djava.security.manager=allow}); desaparece en Java 24: al
 * subir de versión, el hijo pasa a un contenedor aparte con {@code network_mode: none} (riesgo de la tanda).
 */
@SuppressWarnings("removal")
final class PoliticaHijo extends SecurityManager {

	private final Path directorio;

	private final List<Path> lectura;

	PoliticaHijo(Path directorio, List<Path> lectura) {
		this.directorio = directorio.toAbsolutePath().normalize();
		this.lectura = lectura.stream().map(p -> p.toAbsolutePath().normalize()).toList();
	}

	@Override
	public void checkPermission(Permission permiso) {
		revisar(permiso);
	}

	@Override
	public void checkPermission(Permission permiso, Object contexto) {
		revisar(permiso);
	}

	private void revisar(Permission permiso) {
		if (permiso instanceof SocketPermission || permiso instanceof URLPermission) {
			throw new SecurityException("El analizador de PDF no abre conexiones de red.");
		}
		if (permiso instanceof RuntimePermission r && "setSecurityManager".equals(r.getName())) {
			throw new SecurityException("La política del analizador no se cambia.");
		}
		if (permiso instanceof FilePermission f) {
			revisarArchivo(f);
		}
	}

	private void revisarArchivo(FilePermission permiso) {
		String acciones = permiso.getActions();
		if (acciones.contains("execute")) {
			throw new SecurityException("El analizador de PDF no lanza procesos.");
		}
		Path ruta = ruta(permiso.getName());
		if (ruta == null) {
			throw new SecurityException("Ruta no permitida para el analizador de PDF.");
		}
		boolean escribe = acciones.contains("write") || acciones.contains("delete");
		if (ruta.startsWith(directorio)) {
			return;
		}
		if (!escribe && (lectura.stream().anyMatch(ruta::startsWith) || esAleatorio(ruta))) {
			return;
		}
		throw new SecurityException("El analizador de PDF solo lee su jar, java.home y su directorio, y solo escribe en "
				+ "su directorio.");
	}

	private static boolean esAleatorio(Path ruta) {
		String texto = ruta.toString().replace('\\', '/');
		return texto.equals("/dev/random") || texto.equals("/dev/urandom");
	}

	private static Path ruta(String nombre) {
		if (nombre == null || nombre.isBlank() || "<<ALL FILES>>".equals(nombre)) {
			return null;
		}
		String limpio = nombre.endsWith("-") || nombre.endsWith("*") ? nombre.substring(0, nombre.length() - 1) : nombre;
		try {
			return Path.of(limpio.isEmpty() ? "." : limpio).toAbsolutePath().normalize();
		}
		catch (InvalidPathException e) {
			return null;
		}
	}
}
