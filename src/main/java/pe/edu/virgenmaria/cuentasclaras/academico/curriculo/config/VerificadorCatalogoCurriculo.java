package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.AreaCurricular;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.CurriculoVersion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.AreaCurricularRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.CompetenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.CurriculoVersionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.EscalaLogroRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 1 (sección 3.2, E13 y M10): el catálogo nacional de la base es el del jar. Calcula la huella de cada
 * versión ({@link HuellaCatalogo}) y la compara con {@code src/main/resources/curriculo/{codigo}.sha256}, <b>en las dos
 * direcciones</b>: cada versión de la base tiene su archivo y cada archivo, su versión (una versión metida en la base sin
 * archivo, o un archivo sin su migración, también es una diferencia). En prod y en el piloto, si algo no coincide, la
 * aplicación NO arranca («El catálogo CNEB-2016.1 no coincide con el del jar»), como con los triggers; en dev y en las
 * pruebas, avisa en el log. Además lo revisa {@code RevisionCatalogo} a diario (05:00), con la alerta a Promotoría.
 * <p>
 * Lee con JPA (el catálogo no tiene colegio: no lo filtra {@code @TenantId}); nunca escribe.
 */
@Component
public class VerificadorCatalogoCurriculo implements InitializingBean {

	static final String ARCHIVOS = "classpath*:curriculo/*.sha256";

	private static final Pattern HEX = Pattern.compile("^[0-9a-f]{64}$");

	private static final Logger LOG = LoggerFactory.getLogger(VerificadorCatalogoCurriculo.class);

	private final CurriculoVersionRepository versiones;

	private final AreaCurricularRepository areas;

	private final CompetenciaRepository competencias;

	private final EscalaLogroRepository escala;

	private final Environment entorno;

	private final ResourcePatternResolver recursos;

	@Autowired
	public VerificadorCatalogoCurriculo(CurriculoVersionRepository versiones, AreaCurricularRepository areas,
			CompetenciaRepository competencias, EscalaLogroRepository escala, Environment entorno) {
		this(versiones, areas, competencias, escala, entorno, new PathMatchingResourcePatternResolver());
	}

	/** Con otro lector de los archivos {@code curriculo/*.sha256} (las pruebas de las dos direcciones). */
	public VerificadorCatalogoCurriculo(CurriculoVersionRepository versiones, AreaCurricularRepository areas,
			CompetenciaRepository competencias, EscalaLogroRepository escala, Environment entorno,
			ResourcePatternResolver recursos) {
		this.versiones = versiones;
		this.areas = areas;
		this.competencias = competencias;
		this.escala = escala;
		this.entorno = entorno;
		this.recursos = recursos;
	}

	@Override
	public void afterPropertiesSet() {
		exigir(diferencias());
	}

	/** Las diferencias entre la base y el jar (vacía si coinciden). */
	@Transactional(readOnly = true)
	public List<String> diferencias() {
		return comparar(huellasDeLaBase(), huellasDelJar());
	}

	/** En prod y en el piloto, con diferencias, lanza; en los demás perfiles, avisa. */
	public void exigir(List<String> diferencias) {
		if (diferencias.isEmpty()) {
			LOG.info("Catálogo del currículo verificado: las versiones de la base coinciden con las del jar.");
			return;
		}
		String mensaje = String.join(" ", diferencias);
		if (entorno.acceptsProfiles(Profiles.of("prod", "piloto"))) {
			throw new IllegalStateException(mensaje + " Un DBA cambió el catálogo o falta una migración: restaura las filas "
					+ "de la migración (una corrección es una versión nueva) antes de arrancar.");
		}
		LOG.warn("{} (en prod la aplicación no arrancaría).", mensaje);
	}

	/** Las dos direcciones: versión sin archivo, archivo sin versión y huella distinta. */
	public static List<String> comparar(Map<String, String> base, Map<String, String> jar) {
		List<String> diferencias = new ArrayList<>();
		base.forEach((codigo, huella) -> {
			String esperada = jar.get(codigo);
			if (esperada == null) {
				diferencias.add("La versión " + codigo + " del catálogo está en la base y no tiene su archivo "
						+ "curriculo/" + codigo + ".sha256 en el jar.");
			}
			else if (!esperada.equals(huella)) {
				diferencias.add("El catálogo " + codigo + " no coincide con el del jar.");
			}
		});
		jar.keySet().stream().filter(codigo -> !base.containsKey(codigo)).forEach(codigo -> diferencias.add(
				"El archivo curriculo/" + codigo + ".sha256 del jar no tiene su versión en la base (falta su migración)."));
		return diferencias;
	}

	/** {código: huella} de cada versión de la base. */
	Map<String, String> huellasDeLaBase() {
		Map<String, String> huellas = new TreeMap<>();
		for (CurriculoVersion v : versiones.findAllByOrderByCodigoAsc()) {
			huellas.put(v.getCodigo(), HuellaCatalogo.de(filasAreas(v.getId()), filasCompetencias(v.getId()),
					filasEscala(v.getId())));
		}
		return huellas;
	}

	/** {código: huella} de cada {@code curriculo/*.sha256} del jar. */
	Map<String, String> huellasDelJar() {
		Map<String, String> huellas = new TreeMap<>();
		try {
			for (Resource archivo : recursos.getResources(ARCHIVOS)) {
				String nombre = archivo.getFilename();
				if (nombre == null || !nombre.endsWith(".sha256")) {
					continue;
				}
				try (InputStream in = archivo.getInputStream()) {
					String huella = new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim();
					if (!HEX.matcher(huella).matches()) {
						throw new IllegalStateException("El archivo curriculo/" + nombre + " no tiene una huella SHA-256");
					}
					huellas.put(nombre.substring(0, nombre.length() - ".sha256".length()), huella);
				}
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("No se pudieron leer las huellas del catálogo del jar", e);
		}
		return huellas;
	}

	List<HuellaCatalogo.FilaArea> filasAreas(Long versionId) {
		return areas.findByVersionIdOrderByNivelAscOrdenAsc(versionId).stream().map(a -> new HuellaCatalogo.FilaArea(
				a.getNivel().name(), a.getCodigo(), a.getNombre(), a.getOrden(), a.isTransversal(), a.isExonerable()))
				.toList();
	}

	List<HuellaCatalogo.FilaCompetencia> filasCompetencias(Long versionId) {
		Map<Long, String> codigos = areas.findByVersionIdOrderByNivelAscOrdenAsc(versionId).stream()
				.collect(Collectors.toMap(AreaCurricular::getId, AreaCurricular::getCodigo));
		return competencias.findByVersionIdOrderByCodigoAsc(versionId).stream().map(c -> new HuellaCatalogo.FilaCompetencia(
				c.getCodigo(), codigos.getOrDefault(c.getAreaId(), "?"), c.getNombre(), c.getOrden(), c.getSoloGrados()))
				.toList();
	}

	List<HuellaCatalogo.FilaEscala> filasEscala(Long versionId) {
		return escala.findByVersionIdOrderByNivelAscOrdenAsc(versionId).stream().map(e -> new HuellaCatalogo.FilaEscala(
				e.getNivel().name(), e.getValor(), e.getNombre(), e.getDescripcion(), e.getOrden(), e.isExigeConclusion()))
				.toList();
	}
}
