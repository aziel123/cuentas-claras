package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comunicados.analizador.AnalizadorPdf;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Sprint 9, tanda 3 (B5, sección 6 del brief): lanza el analizador de PDF ({@link AnalizadorPdf}) en un PROCESO HIJO y lee
 * lo que dejó. El padre nunca abre el PDF.
 * <ul>
 *   <li>Comando: {@code java -Xmx128m -Xss512k -XX:MaxMetaspaceSize=64m -XX:ReservedCodeCacheSize=32m -XX:+UseSerialGC
 *       -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir={dir} -Djava.security.manager=allow}; dentro
 *       del jar de Spring Boot, {@code -cp /app/app.jar -Dloader.main=…AnalizadorPdf PropertiesLauncher}; fuera del jar
 *       (pruebas, {@code dev}), el {@code java.class.path} del padre.</li>
 *   <li>Entorno VACÍO (sin {@code DB_*} ni {@code JAVA_TOOL_OPTIONS}): el hijo no tiene ninguna credencial. En Windows
 *       (solo desarrollo) queda {@code SystemRoot}, sin el cual la JVM no arranca.</li>
 *   <li>Intercambio por archivos en {@code {tmp}/cc-pdf/{uuid}/} (en el contenedor, tmpfs) con permisos 700; el
 *       directorio se borra SIEMPRE al terminar, y al arrancar se borran los restos.</li>
 *   <li>Tiempo: si a los {@code max-segundos-validacion} (10) no terminó, se matan sus descendientes y él mismo por su
 *       PID ({@code destroyForcibly}), nunca por nombre, con el PID en el log.</li>
 * </ul>
 */
@Component
public class LanzadorAnalizador {

	private static final Logger LOG = LoggerFactory.getLogger(LanzadorAnalizador.class);

	static final String LANZADOR_BOOT = "org.springframework.boot.loader.launch.PropertiesLauncher";

	private static final int MAX_RESULTADO = 16 * 1024;

	private final Path base;

	private final Duration tiempo;

	@org.springframework.beans.factory.annotation.Autowired
	public LanzadorAnalizador(@Value("${cuentasclaras.comunicados.adjunto.max-segundos-validacion:10}") long segundos) {
		this(Path.of(System.getProperty("java.io.tmpdir"), "cc-pdf"), Duration.ofSeconds(segundos));
	}

	LanzadorAnalizador(Path base, Duration tiempo) {
		this.base = base;
		this.tiempo = tiempo;
	}

	/** Al arrancar: borra lo que haya quedado de un análisis interrumpido (un reinicio a mitad de uno). */
	@PostConstruct
	void borrarRestos() {
		if (!Files.isDirectory(base)) {
			return;
		}
		try (Stream<Path> restos = Files.list(base)) {
			restos.forEach(LanzadorAnalizador::borrar);
		}
		catch (IOException e) {
			LOG.warn("No se pudieron borrar los restos del analizador de PDF: {}", e.getClass().getSimpleName());
		}
	}

	/** Lo que devolvió el hijo (el padre recalcula los SHA-256 de lo que guarda). */
	public record Salida(int codigo, Properties resultado, byte[] saneado, String texto, boolean vencido) {

		public boolean aceptado() {
			return codigo == AnalizadorPdf.ACEPTADO && "ACEPTADO".equals(resultado.getProperty("estado"));
		}

		public String motivo() {
			if (vencido) {
				return "tardó demasiado en revisarse (posible archivo bomba)";
			}
			return switch (codigo) {
				case AnalizadorPdf.RECHAZADO -> resultado.getProperty("motivo", "no se aceptó");
				case AnalizadorPdf.SIN_MEMORIA -> "necesitó demasiada memoria para revisarse (posible archivo bomba)";
				case AnalizadorPdf.ACEPTADO -> null;
				default -> "no se pudo revisar";
			};
		}
	}

	/** Analiza un PDF en un proceso hijo con los dominios a los que puede enlazar. Bloquea hasta que termina o vence. */
	public Salida analizar(byte[] pdf, Set<String> dominiosPermitidos) {
		Path directorio = crearDirectorio();
		try {
			Files.write(directorio.resolve(AnalizadorPdf.ENTRADA), pdf);
			List<String> comando = comando(directorio, AnalizadorPdf.class.getName(),
					List.of(directorio.toString(), dominiosPermitidos.isEmpty() ? "-" : String.join(",", dominiosPermitidos)));
			Ejecucion ejecucion = ejecutar(comando, directorio);
			Properties resultado = AnalizadorPdf.leerResultado(directorio, MAX_RESULTADO);
			byte[] saneado = null;
			String texto = "";
			if (ejecucion.codigo() == AnalizadorPdf.ACEPTADO) {
				Path archivo = directorio.resolve(AnalizadorPdf.SANEADO);
				if (Files.isRegularFile(archivo) && Files.size(archivo) <= AnalizadorPdf.MAX_BYTES) {
					saneado = Files.readAllBytes(archivo);
				}
				Path textos = directorio.resolve(AnalizadorPdf.TEXTO);
				if (Files.isRegularFile(textos) && Files.size(textos) <= AnalizadorPdf.MAX_TEXTO * 4L) {
					texto = Files.readString(textos, StandardCharsets.UTF_8);
				}
			}
			int codigo = ejecucion.codigo() == AnalizadorPdf.ACEPTADO && saneado == null ? AnalizadorPdf.NO_REVISADO
					: ejecucion.codigo();
			return new Salida(codigo, resultado, saneado, texto, ejecucion.vencido());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		finally {
			borrar(directorio);
		}
	}

	/** El resultado de correr un proceso hijo: su código de salida y si se lo mató por tiempo. */
	public record Ejecucion(int codigo, boolean vencido, long pid) {
	}

	/** Corre un comando hijo con el entorno vacío y lo mata por su PID si vence el tiempo. Público para las pruebas. */
	public Ejecucion ejecutar(List<String> comando, Path directorio) throws IOException {
		ProcessBuilder constructor = new ProcessBuilder(comando).directory(directorio.toFile())
				.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
		Map<String, String> entorno = constructor.environment();
		String raizWindows = entorno.get("SystemRoot");
		entorno.clear();
		if (esWindows() && raizWindows != null) {
			entorno.put("SystemRoot", raizWindows);
		}
		Process proceso = constructor.start();
		long pid = proceso.pid();
		try {
			if (proceso.waitFor(tiempo.toMillis(), TimeUnit.MILLISECONDS)) {
				return new Ejecucion(proceso.exitValue(), false, pid);
			}
			LOG.warn("El analizador de PDF (PID {}) pasó de {} s: se mata por su PID.", pid, tiempo.toSeconds());
			matar(proceso);
			return new Ejecucion(-1, true, pid);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			matar(proceso);
			return new Ejecucion(-1, true, pid);
		}
	}

	/** Mata al hijo y a sus descendientes POR SU PID (nunca por nombre). */
	private static void matar(Process proceso) {
		proceso.descendants().forEach(ProcessHandle::destroyForcibly);
		proceso.destroyForcibly();
		try {
			proceso.waitFor(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * El comando del hijo: la misma JVM, sus límites y la clase principal. Dentro del jar de Spring Boot se lanza con el
	 * {@code PropertiesLauncher} y {@code -Dloader.main} (las clases del jar anidado); fuera, con el classpath del padre.
	 */
	public List<String> comando(Path directorio, String clasePrincipal, List<String> argumentos) {
		List<String> comando = new ArrayList<>();
		comando.add(Path.of(System.getProperty("java.home"), "bin", esWindows() ? "java.exe" : "java").toString());
		comando.addAll(List.of("-Xmx128m", "-Xss512k", "-XX:MaxMetaspaceSize=64m", "-XX:ReservedCodeCacheSize=32m",
				"-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-XX:+ExitOnOutOfMemoryError", "-Xshare:auto",
				"-Djava.io.tmpdir=" + directorio, "-Djava.security.manager=allow", "-Djava.awt.headless=true",
				"-Dfile.encoding=UTF-8"));
		String classpath = System.getProperty("java.class.path", "");
		if (esJarDeSpringBoot(classpath)) {
			comando.addAll(List.of("-cp", classpath, "-Dloader.main=" + clasePrincipal, LANZADOR_BOOT));
		}
		else {
			// Fuera del jar (pruebas y dev) el classpath puede pasar el largo máximo de una línea de comando (Windows): va
			// en un archivo de argumentos (@archivo) dentro del directorio de trabajo.
			Path argumentosCp = directorio.resolve("classpath.args");
			try {
				Files.writeString(argumentosCp, "-cp \"" + classpath.replace("\\", "\\\\") + "\"",
						StandardCharsets.UTF_8);
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			comando.addAll(List.of("@" + argumentosCp, clasePrincipal));
		}
		comando.addAll(argumentos);
		return comando;
	}

	/** {@code java -jar /app/app.jar}: el classpath es un único jar con las clases de la aplicación dentro. */
	static boolean esJarDeSpringBoot(String classpath) {
		if (classpath.contains(File.pathSeparator) || !classpath.endsWith(".jar")) {
			return false;
		}
		try (var jar = new java.util.jar.JarFile(classpath)) {
			return jar.getEntry("BOOT-INF/classes/") != null || jar.getEntry("BOOT-INF/lib/") != null;
		}
		catch (IOException e) {
			return false;
		}
	}

	private Path crearDirectorio() {
		try {
			Files.createDirectories(base);
			Path directorio = base.resolve(UUID.randomUUID().toString());
			if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
				Files.createDirectory(directorio,
						PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
			}
			else {
				Files.createDirectory(directorio);
			}
			return directorio;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Cuántos directorios de trabajo hay ahora (las pruebas comprueban que no quede ninguno). */
	public long directoriosEnCurso() {
		if (!Files.isDirectory(base)) {
			return 0;
		}
		try (Stream<Path> todos = Files.list(base)) {
			return todos.count();
		}
		catch (IOException e) {
			return 0;
		}
	}

	private static void borrar(Path directorio) {
		if (!Files.exists(directorio)) {
			return;
		}
		try (Stream<Path> todo = Files.walk(directorio)) {
			todo.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				}
				catch (IOException e) {
					LOG.warn("No se pudo borrar un archivo del analizador de PDF: {}", e.getClass().getSimpleName());
				}
			});
		}
		catch (IOException e) {
			LOG.warn("No se pudo borrar el directorio del analizador de PDF: {}", e.getClass().getSimpleName());
		}
	}

	private static boolean esWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	/** Para las pruebas: los argumentos de la JVM del hijo, sin la ruta de java ni el classpath. */
	static List<String> opcionesJvm(List<String> comando) {
		int fin = comando.indexOf("-cp");
		if (fin < 0) {
			fin = (int) comando.stream().takeWhile(a -> !a.startsWith("@")).count();
		}
		return Arrays.asList(comando.subList(1, fin).toArray(String[]::new));
	}
}
