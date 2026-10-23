package pe.edu.virgenmaria.cuentasclaras.academico.curriculo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.config.HuellaCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.config.VerificadorCatalogoCurriculo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.AreaCurricularRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.CompetenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.CurriculoVersionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.EscalaLogroRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 10, tanda 1 (E13 y M10): la huella del catálogo de la base coincide con {@code curriculo/CNEB-2016.1.sha256}; si un
 * DBA cambia una competencia, prod NO arranca y dev solo avisa; y la verificación va en las dos direcciones (una versión de
 * la base sin su archivo, o un archivo sin su versión, también fallan).
 */
@PruebaIntegracion
class VerificadorCatalogoCurriculoTest {

	@Autowired
	private VerificadorCatalogoCurriculo verificador;

	@Autowired
	private CurriculoVersionRepository versiones;

	@Autowired
	private AreaCurricularRepository areas;

	@Autowired
	private CompetenciaRepository competencias;

	@Autowired
	private EscalaLogroRepository escala;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private JdbcTemplate jdbc;

	private VerificadorCatalogoCurriculo en(String perfil, Resource... extra) {
		MockEnvironment entorno = new MockEnvironment();
		entorno.setActiveProfiles(perfil);
		// Los archivos del jar y, para la prueba de las dos direcciones, algunos de más.
		PathMatchingResourcePatternResolver archivos = new PathMatchingResourcePatternResolver() {
			@Override
			public Resource[] getResources(String patron) throws IOException {
				List<Resource> todos = new ArrayList<>(Arrays.asList(super.getResources(patron)));
				if (patron.endsWith("curriculo/*.sha256")) {
					todos.addAll(Arrays.asList(extra));
				}
				return todos.toArray(Resource[]::new);
			}
		};
		return new VerificadorCatalogoCurriculo(versiones, areas, competencias, escala, entorno, archivos);
	}

	@Test
	void laHuellaCoincideConLaDelJar() throws IOException {
		assertThat(verificador.diferencias()).isEmpty();
		String archivo = Files.readString(Path.of("src/main/resources/curriculo/CNEB-2016.1.sha256"),
				StandardCharsets.US_ASCII).trim();
		assertThat(archivo).matches("[0-9a-f]{64}");
		assertThatCode(() -> en("prod").afterPropertiesSet()).doesNotThrowAnyException();
	}

	/** El contrato de la sección 6 del brief: la primera y la última fila de cada bloque, con la barra y el salto de línea. */
	@Test
	void laCadenaEsLaDelContrato() {
		String cadena = HuellaCatalogo.cadena(List.of(new HuellaCatalogo.FilaArea("PRIMARIA", "MAT", "Matemática", 7, false,
				false), new HuellaCatalogo.FilaArea("INICIAL", "PS", "Personal Social", 1, false, false)),
				List.of(new HuellaCatalogo.FilaCompetencia("PRI-MAT-1", "MAT", "Resuelve problemas de cantidad", 1, null),
						new HuellaCatalogo.FilaCompetencia("INI-TRV-1", "TRV", "Se desenvuelve", 1, "INICIAL_5")),
				List.of(new HuellaCatalogo.FilaEscala("PRIMARIA", "B", "En proceso", "Cuando…", 3, true)));
		assertThat(cadena).isEqualTo("INICIAL|PS|Personal Social|1|0|0\nPRIMARIA|MAT|Matemática|7|0|0\n"
				+ "INI-TRV-1|TRV|Se desenvuelve|1|INICIAL_5\nPRI-MAT-1|MAT|Resuelve problemas de cantidad|1|\n"
				+ "PRIMARIA|B|En proceso|Cuando…|3|1");
	}

	/** E13: un DBA cambia el nombre de una competencia: prod no arranca; dev solo avisa (en el log). */
	@Test
	void huellaDistintaNoArrancaEnProdYEnDevAvisa() {
		new TransactionTemplate(transacciones).executeWithoutResult(t -> {
			jdbc.update("UPDATE competencia SET nombre = 'Resuelve problemas de cantidades' WHERE codigo = 'PRI-MAT-1'");
			List<String> diferencias = verificador.diferencias();
			assertThat(diferencias).containsExactly("El catálogo CNEB-2016.1 no coincide con el del jar.");
			assertThatThrownBy(() -> en("prod").exigir(diferencias)).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("El catálogo CNEB-2016.1 no coincide con el del jar");
			assertThatThrownBy(() -> en("piloto").afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
			assertThatCode(() -> en("dev").afterPropertiesSet()).doesNotThrowAnyException();
			t.setRollbackOnly();
		});
		assertThat(verificador.diferencias()).isEmpty();
	}

	/** M10: una versión metida en la base sin su archivo en el jar también es una diferencia. */
	@Test
	void versionSinArchivoFalla() {
		new TransactionTemplate(transacciones).executeWithoutResult(t -> {
			jdbc.update("INSERT INTO curriculo_version (codigo, nombre, normas, vigente_desde, reemplaza_id, creado_en) "
					+ "SELECT 'CNEB-2016.9', 'Versión metida a mano', 'Ninguna norma', DATE '2027-01-01', id, "
					+ "CURRENT_TIMESTAMP FROM curriculo_version WHERE codigo = 'CNEB-2016.1'");
			List<String> diferencias = verificador.diferencias();
			assertThat(diferencias).singleElement().asString().contains("CNEB-2016.9").contains("no tiene su archivo");
			assertThatThrownBy(() -> en("prod").exigir(diferencias)).isInstanceOf(IllegalStateException.class);
			t.setRollbackOnly();
		});
	}

	/** M10: un archivo del jar sin su versión en la base (falta su migración) también es una diferencia. */
	@Test
	void archivoSinVersionFalla() {
		ByteArrayResource huerfano = new ByteArrayResource("0".repeat(64).getBytes(StandardCharsets.US_ASCII)) {
			@Override
			public String getFilename() {
				return "CNEB-2027.1.sha256";
			}
		};
		VerificadorCatalogoCurriculo conDeMas = en("prod", huerfano);
		assertThatThrownBy(conDeMas::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("curriculo/CNEB-2027.1.sha256").hasMessageContaining("no tiene su versión");
	}

	@Test
	void laComparacionVaEnLasDosDirecciones() {
		String h = "a".repeat(64);
		assertThat(VerificadorCatalogoCurriculo.comparar(java.util.Map.of("A", h), java.util.Map.of("A", h))).isEmpty();
		assertThat(VerificadorCatalogoCurriculo.comparar(java.util.Map.of("A", h, "B", h), java.util.Map.of("A", h)))
				.singleElement().asString().contains("B").contains("no tiene su archivo");
		assertThat(VerificadorCatalogoCurriculo.comparar(java.util.Map.of("A", h), java.util.Map.of("A", h, "C", h)))
				.singleElement().asString().contains("C.sha256").contains("no tiene su versión");
		assertThat(VerificadorCatalogoCurriculo.comparar(java.util.Map.of("A", h), java.util.Map.of("A", "b".repeat(64))))
				.containsExactly("El catálogo A no coincide con el del jar.");
	}
}
