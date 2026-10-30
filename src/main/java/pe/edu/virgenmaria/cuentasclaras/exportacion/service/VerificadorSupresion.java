package pe.edu.virgenmaria.cuentasclaras.exportacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sprint 11, tanda 2 (AP2-3 y AP2-11): al arrancar, la base no tiene nada de un colegio suprimido y tiene aplicada cada acta
 * ejecutada que vive FUERA de ella ({@code CC_DIR_ACTAS}, las que escribe {@code exportar-acta.sh}). Un respaldo restaurado
 * sin {@code reaplicar.sh} «resucita» los datos de un colegio: en prod y en el piloto la aplicación NO arranca; en dev y en
 * las pruebas, avisa en el log.
 * <ul>
 * <li>cada {@code acta-{colegio}-{id}.json} del directorio: ese colegio está SUPRIMIDO con esa acta;</li>
 * <li>cada colegio SUPRIMIDO: 0 filas en las tablas de {@code exportacion/tablas.txt} que se suprimen.</li>
 * </ul>
 */
@Component
public class VerificadorSupresion implements InitializingBean {

	/** Las tablas de la exportación que NO se suprimen (la evidencia: exclusiones.txt). */
	static final Set<String> NO_SE_SUPRIMEN = Set.of("evento_auditoria", "huella_bitacora", "huella_hora",
			"cambio_estado_colegio", "exportacion_colegio", "descarga_exportacion", "acta_supresion");

	private static final Pattern ACTA = Pattern.compile("acta-(\\d+)-(\\d+)\\.json");

	private static final Logger LOG = LoggerFactory.getLogger(VerificadorSupresion.class);

	private final ColegioRepository colegios;

	private final LectorTablas lector;

	private final Environment entorno;

	private final String actasDir;

	public VerificadorSupresion(ColegioRepository colegios, LectorTablas lector, Environment entorno,
			@Value("${cuentasclaras.supresion.actas-dir:}") String actasDir) {
		this.colegios = colegios;
		this.lector = lector;
		this.entorno = entorno;
		this.actasDir = actasDir;
	}

	@Override
	public void afterPropertiesSet() {
		exigir(problemas());
	}

	/** Lo que no está en orden (vacío si todo). */
	public List<String> problemas() {
		List<String> problemas = new ArrayList<>();
		Map<Long, Colegio> porId = colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO).stream()
				.collect(Collectors.toMap(Colegio::getId, Function.identity()));
		for (long[] acta : actasExternas()) {
			Colegio c = porId.get(acta[0]);
			if (c == null || c.getEstado() != EstadoColegio.SUPRIMIDO || !Objects.equals(c.getActaSupresionId(), acta[1])) {
				problemas.add("El acta de supresión " + acta[1] + " del colegio " + acta[0] + " está fuera de la base y la "
						+ "base no la tiene aplicada: corre reaplicar.sh antes de abrir la aplicación.");
			}
		}
		List<TablasExportacion.Tabla> tablas = TablasExportacion.cargar().stream()
				.filter(t -> !NO_SE_SUPRIMEN.contains(t.nombre())).toList();
		for (Colegio c : porId.values()) {
			if (c.getEstado() != EstadoColegio.SUPRIMIDO) {
				continue;
			}
			for (TablasExportacion.Tabla t : tablas) {
				long n = lector.contar(t, c.getId());
				if (n > 0) {
					problemas.add("El colegio suprimido " + c.getId() + " tiene " + n + " filas en " + t.nombre() + ".");
				}
			}
		}
		return problemas;
	}

	public void exigir(List<String> problemas) {
		if (problemas.isEmpty()) {
			LOG.info("Supresiones verificadas: ningún colegio suprimido tiene datos y las actas externas están aplicadas.");
			return;
		}
		String mensaje = String.join(" ", problemas);
		if (entorno.acceptsProfiles(Profiles.of("prod", "piloto"))) {
			throw new IllegalStateException(mensaje);
		}
		LOG.warn("{} (en prod la aplicación no arrancaría).", mensaje);
	}

	/** {colegio, acta} de cada archivo {@code acta-{colegio}-{id}.json} del directorio (vacío si no hay directorio). */
	List<long[]> actasExternas() {
		if (actasDir == null || actasDir.isBlank()) {
			return List.of();
		}
		Path dir = Path.of(actasDir);
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> archivos = Files.list(dir)) {
			return archivos.map(p -> ACTA.matcher(p.getFileName().toString())).filter(Matcher::matches)
					.map(m -> new long[] { Long.parseLong(m.group(1)), Long.parseLong(m.group(2)) }).toList();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
