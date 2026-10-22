package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.edu.virgenmaria.cuentasclaras.comunicados.PdfsDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicados.analizador.AnalizadorPdf;

import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 3 (B5, sección 6 del brief): el analizador corre en un proceso HIJO con sus límites, el entorno vacío y
 * una política sin red. Estas pruebas lanzan procesos reales (con el mismo comando y la misma política).
 */
class AnalizadorPdfTest {

	@TempDir
	Path base;

	private LanzadorAnalizador lanzador(Duration tiempo) {
		return new LanzadorAnalizador(base.resolve("cc-pdf"), tiempo);
	}

	private Path directorio(String nombre) throws Exception {
		return Files.createDirectories(base.resolve(nombre));
	}

	private String sonda(LanzadorAnalizador lanzador, Path dir, String... argumentos) throws Exception {
		List<String> args = new java.util.ArrayList<>();
		args.add(dir.toString());
		args.addAll(Arrays.asList(argumentos));
		LanzadorAnalizador.Ejecucion e = lanzador.ejecutar(lanzador.comando(dir, SondaHijo.class.getName(), args), dir);
		assertThat(e.vencido()).as("la sonda terminó a tiempo").isFalse();
		return Files.readString(dir.resolve("sonda.txt"), StandardCharsets.UTF_8);
	}

	@Test
	void elComandoLlevaSusLimites() throws Exception {
		List<String> comando = lanzador(Duration.ofSeconds(10)).comando(directorio("x"), AnalizadorPdf.class.getName(),
				List.of("x"));
		assertThat(LanzadorAnalizador.opcionesJvm(comando)).contains("-Xmx128m", "-Xss512k", "-XX:MaxMetaspaceSize=64m",
				"-XX:ReservedCodeCacheSize=32m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1",
				"-XX:+ExitOnOutOfMemoryError", "-Djava.security.manager=allow");
		assertThat(comando).anyMatch(a -> a.startsWith("-Djava.io.tmpdir="));
		assertThat(comando.get(0)).contains("java");
	}

	@Test
	void alVencerElTiempoSeMataPorPid() throws Exception {
		LanzadorAnalizador lanzador = lanzador(Duration.ofSeconds(3));
		Path dir = directorio("dormir");
		long inicio = System.nanoTime();
		LanzadorAnalizador.Ejecucion e = lanzador.ejecutar(lanzador.comando(dir, SondaHijo.class.getName(),
				List.of(dir.toString(), "dormir")), dir);
		assertThat(e.vencido()).isTrue();
		assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(15));
		assertThat(ProcessHandle.of(e.pid()).map(ProcessHandle::isAlive).orElse(false)).as("el hijo murió").isFalse();
		assertThat(dir.resolve("sonda.txt")).doesNotExist();
	}

	@Test
	void sinMemoriaTerminaConCodigo3() throws Exception {
		LanzadorAnalizador lanzador = lanzador(Duration.ofSeconds(30));
		Path dir = directorio("memoria");
		LanzadorAnalizador.Ejecucion e = lanzador.ejecutar(lanzador.comando(dir, SondaHijo.class.getName(),
				List.of(dir.toString(), "memoria")), dir);
		assertThat(e.vencido()).isFalse();
		assertThat(e.codigo()).isEqualTo(AnalizadorPdf.SIN_MEMORIA);
	}

	@Test
	void elHijoNoAbreConexiones() throws Exception {
		try (ServerSocket servidor = new ServerSocket(0)) {
			servidor.setSoTimeout(500);
			String resultado = sonda(lanzador(Duration.ofSeconds(30)), directorio("red"), "red",
					Integer.toString(servidor.getLocalPort()));
			assertThat(resultado).isEqualTo("red=bloqueada");
			try {
				servidor.accept().close();
				throw new AssertionError("el hijo llegó a conectarse");
			}
			catch (SocketTimeoutException esperado) {
				// nadie se conectó
			}
		}
	}

	@Test
	void elHijoNoVeLasVariablesDelPadre() throws Exception {
		String variables = sonda(lanzador(Duration.ofSeconds(30)), directorio("entorno"), "entorno");
		Set<String> vistas = variables.isBlank() ? Set.of() : Set.of(variables.split(","));
		// En Windows (solo desarrollo) la JVM necesita SystemRoot; en el contenedor el entorno está vacío.
		assertThat(vistas).isSubsetOf("SystemRoot");
		assertThat(System.getenv()).as("el padre sí tiene variables").isNotEmpty();
	}

	@Test
	void elHijoNoEscribeFueraDeSuDirectorio() throws Exception {
		Path fuera = base.resolve("fuera.txt");
		String resultado = sonda(lanzador(Duration.ofSeconds(30)), directorio("escribe"), "fuera", fuera.toString());
		assertThat(resultado).isEqualTo("fuera=bloqueado");
		assertThat(fuera).doesNotExist();
	}

	@Test
	void noQuedanArchivosEnTmp() {
		LanzadorAnalizador lanzador = lanzador(Duration.ofSeconds(30));
		LanzadorAnalizador.Salida salida = lanzador.analizar(PdfsDePrueba.normal(), Set.of("cuentas.colegio.pe"));
		assertThat(salida.aceptado()).as(salida.motivo()).isTrue();
		LanzadorAnalizador.Salida rechazo = lanzador.analizar(PdfsDePrueba.conJavaScript(), Set.of());
		assertThat(rechazo.aceptado()).isFalse();
		assertThat(lanzador.directoriosEnCurso()).isZero();
	}

	@Test
	void alArrancarSeBorranLosRestos() throws Exception {
		Path restos = Files.createDirectories(base.resolve("cc-pdf").resolve("resto-de-un-reinicio"));
		Files.writeString(restos.resolve("entrada.pdf"), "x");
		LanzadorAnalizador lanzador = lanzador(Duration.ofSeconds(10));
		lanzador.borrarRestos();
		assertThat(lanzador.directoriosEnCurso()).isZero();
	}
}
