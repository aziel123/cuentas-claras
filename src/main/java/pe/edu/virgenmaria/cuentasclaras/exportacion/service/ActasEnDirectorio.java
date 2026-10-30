package pe.edu.virgenmaria.cuentasclaras.exportacion.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Correcciones del sprint 11 (A1): las actas externas como las deja {@code exportar-acta.sh} ({@code acta-{colegio}-{id}.json}
 * con su {@code .sha256}) en {@code cuentasclaras.supresion.actas-dir} ({@code CC_DIR_ACTAS}, la copia local del depósito).
 */
@Component
public class ActasEnDirectorio implements FuenteActasExternas {

	private static final Pattern ACTA = Pattern.compile("acta-([0-9]+)-([0-9]+)[.]json");

	private final String directorio;

	public ActasEnDirectorio(@Value("${cuentasclaras.supresion.actas-dir:}") String directorio) {
		this.directorio = directorio == null ? "" : directorio.strip();
	}

	@Override
	public String origen() {
		return directorio;
	}

	@Override
	public List<ActaExterna> actas() {
		if (directorio.isEmpty()) {
			return List.of();
		}
		Path dir = Path.of(directorio);
		if (!Files.isDirectory(dir)) {
			throw new IllegalStateException("No existe el directorio de las actas de supresión (CC_DIR_ACTAS): " + directorio);
		}
		try (Stream<Path> archivos = Files.list(dir)) {
			return archivos.map(p -> ACTA.matcher(p.getFileName().toString())).filter(Matcher::matches)
					.map(m -> new ActaExterna(Long.parseLong(m.group(1)), Long.parseLong(m.group(2)))).toList();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
