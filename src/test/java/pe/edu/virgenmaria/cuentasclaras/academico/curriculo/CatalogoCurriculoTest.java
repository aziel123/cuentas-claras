package pe.edu.virgenmaria.cuentasclaras.academico.curriculo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.CompetenciaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.VersionDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Catalogo;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10, tanda 1 (sección 3.2 y riesgo «Textos del catálogo» del brief): el catálogo CNEB-2016.1 que carga V35. 29 áreas
 * (7, 10 y 12 por nivel), 75 competencias (14, 30 y 31) y 12 valores de escala; la escala exige conclusión como la RVM
 * 048-2024 (Inicial en A, B y C; Primaria en B y C; Secundaria en C); {@code competenciasDe} respeta {@code solo_grados};
 * exonerables, solo Educación Física y Educación Religiosa. Los 75 nombres se comparan con la tabla de la sección 3.2 del
 * diseño, escrita aquí aparte de la migración: un error de tipeo publicado solo se corrige con una versión nueva.
 */
@PruebaIntegracion
class CatalogoCurriculoTest {

	@Autowired
	private Catalogo catalogo;

	@Autowired
	private JdbcTemplate jdbc;

	private Long version;

	@BeforeEach
	void preparar() {
		EscenarioCobranza.como(EscenarioCobranza.DIRECCION);
		version = catalogo.versiones().stream().filter(v -> v.codigo().equals("CNEB-2016.1")).findFirst()
				.map(VersionDelCatalogo::id).orElseThrow();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void tieneLasAreasCompetenciasYEscalaDeLaVersion() {
		assertThat(catalogo.areasDe(version, Nivel.INICIAL)).hasSize(7);
		assertThat(catalogo.areasDe(version, Nivel.PRIMARIA)).hasSize(10);
		assertThat(catalogo.areasDe(version, Nivel.SECUNDARIA)).hasSize(12);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM competencia WHERE version_id = ?", Integer.class, version))
				.isEqualTo(75);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM competencia WHERE version_id = ? AND codigo LIKE 'INI-%'",
				Integer.class, version)).isEqualTo(14);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM competencia WHERE version_id = ? AND codigo LIKE 'PRI-%'",
				Integer.class, version)).isEqualTo(30);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM competencia WHERE version_id = ? AND codigo LIKE 'SEC-%'",
				Integer.class, version)).isEqualTo(31);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM escala_logro WHERE version_id = ?", Integer.class, version))
				.isEqualTo(12);
		assertThat(catalogo.porDefecto()).map(VersionDelCatalogo::codigo).contains("CNEB-2016.1");
	}

	@Test
	void laEscalaExigeConclusionComoLaNorma() {
		for (String valor : List.of("AD", "A", "B", "C")) {
			assertThat(catalogo.exigeConclusion(version, Nivel.INICIAL, valor)).as("Inicial " + valor)
					.isEqualTo(!valor.equals("AD"));
			assertThat(catalogo.exigeConclusion(version, Nivel.PRIMARIA, valor)).as("Primaria " + valor)
					.isEqualTo(valor.equals("B") || valor.equals("C"));
			assertThat(catalogo.exigeConclusion(version, Nivel.SECUNDARIA, valor)).as("Secundaria " + valor)
					.isEqualTo(valor.equals("C"));
		}
		assertThat(catalogo.escalaDe(version, Nivel.PRIMARIA)).extracting(v -> v.valor() + " " + v.nombre())
				.containsExactly("AD Logro destacado", "A Logro esperado", "B En proceso", "C En inicio");
		assertThat(catalogo.escalaDe(version, Nivel.PRIMARIA).get(2).descripcion()).isEqualTo("Cuando el estudiante está "
				+ "próximo o cerca al nivel esperado respecto a la competencia, para lo cual requiere acompañamiento durante "
				+ "un tiempo razonable para lograrlo.");
	}

	@Test
	void cuatroAniosSinTransversalesNiCastellanoComoSegundaLenguaYCincoConEllas() {
		List<String> cuatro = catalogo.competenciasDe(version, Grado.INICIAL_4).stream()
				.map(CompetenciaDelCatalogo::codigo).toList();
		List<String> cinco = catalogo.competenciasDe(version, Grado.INICIAL_5).stream()
				.map(CompetenciaDelCatalogo::codigo).toList();
		assertThat(cuatro).hasSize(11).doesNotContain("INI-TRV-1", "INI-TRV-2", "INI-C2L-1");
		assertThat(cinco).hasSize(14).contains("INI-TRV-1", "INI-TRV-2", "INI-C2L-1");
		// En el orden de la libreta: área por área (Personal Social primero), cada una en su orden.
		assertThat(cinco).startsWith("INI-PS-1", "INI-PS-2", "INI-PS-3", "INI-PSI-1").endsWith("INI-TRV-1", "INI-TRV-2");
		assertThat(catalogo.competenciasDe(version, Grado.PRIMARIA_3)).hasSize(30);
		assertThat(catalogo.competenciasDe(version, Grado.SECUNDARIA_5)).hasSize(31);
	}

	@Test
	void soloEducacionFisicaYReligionSonExonerablesYLasTransversalesSonUnArea() {
		for (Nivel nivel : Nivel.values()) {
			List<AreaDelCatalogo> areas = catalogo.areasDe(version, nivel);
			assertThat(areas.stream().filter(AreaDelCatalogo::exonerable).map(AreaDelCatalogo::codigo))
					.as(nivel.name()).containsExactlyInAnyOrderElementsOf(nivel == Nivel.INICIAL ? List.of()
							: List.of("EF", "REL"));
			assertThat(areas.stream().filter(AreaDelCatalogo::transversal).map(AreaDelCatalogo::codigo))
					.as(nivel.name()).containsExactly("TRV");
			assertThat(areas.get(areas.size() - 1).codigo()).as("la transversal va al final").isEqualTo("TRV");
		}
	}

	/** Los 75 nombres, exactamente como la tabla de la sección 3.2 del diseño (con tildes). */
	@Test
	void losNombresSonLosDeLaNorma() {
		Map<String, String> enLaBase = jdbc.queryForList("SELECT codigo, nombre FROM competencia WHERE version_id = ?",
				version).stream().collect(Collectors.toMap(f -> (String) f.get("CODIGO"), f -> (String) f.get("NOMBRE")));
		assertThat(enLaBase).isEqualTo(esperados());
		Map<String, String> areas = jdbc.queryForList("SELECT CONCAT(nivel, '-', codigo) AS clave, nombre FROM "
				+ "area_curricular WHERE version_id = ?", version).stream().collect(Collectors.toMap(f -> (String) f
						.get("CLAVE"), f -> (String) f.get("NOMBRE")));
		assertThat(areas).containsEntry("PRIMARIA-EF", "Educación Física").containsEntry("PRIMARIA-REL",
				"Educación Religiosa").containsEntry("SECUNDARIA-DPCC", "Desarrollo Personal, Ciudadanía y Cívica")
				.containsEntry("SECUNDARIA-EPT", "Educación para el Trabajo").containsEntry("INICIAL-PSI", "Psicomotriz")
				.containsEntry("PRIMARIA-ING", "Inglés como lengua extranjera").containsEntry("PRIMARIA-AYC", "Arte y Cultura")
				.containsEntry("SECUNDARIA-CCSS", "Ciencias Sociales").containsEntry("INICIAL-TRV",
						"Competencias transversales");
	}

	private static Map<String, String> esperados() {
		String identidad = "Construye su identidad";
		String convive = "Convive y participa democráticamente en la búsqueda del bien común";
		String religion1 = "Construye su identidad como persona humana, amada por Dios, digna, libre y trascendente, "
				+ "comprendiendo la doctrina de su propia religión, abierto al diálogo con las que le son cercanas";
		String religion2 = "Asume la experiencia del encuentro personal y comunitario con Dios en su proyecto de vida en "
				+ "coherencia con su creencia religiosa";
		String motricidad = "Se desenvuelve de manera autónoma a través de su motricidad";
		String oral = "Se comunica oralmente en su lengua materna";
		String lee = "Lee diversos tipos de textos escritos en su lengua materna";
		String escribe = "Escribe diversos tipos de textos en su lengua materna";
		String crea = "Crea proyectos desde los lenguajes artísticos";
		String aprecia = "Aprecia de manera crítica manifestaciones artístico-culturales";
		String cantidad = "Resuelve problemas de cantidad";
		String regularidad = "Resuelve problemas de regularidad, equivalencia y cambio";
		String forma = "Resuelve problemas de forma, movimiento y localización";
		String datos = "Resuelve problemas de gestión de datos e incertidumbre";
		String indaga = "Indaga mediante métodos científicos para construir sus conocimientos";
		String explica = "Explica el mundo físico basándose en conocimientos sobre los seres vivos, materia y energía, "
				+ "biodiversidad, Tierra y universo";
		String disena = "Diseña y construye soluciones tecnológicas para resolver problemas de su entorno";
		String tic = "Se desenvuelve en entornos virtuales generados por las TIC";
		String autonomo = "Gestiona su aprendizaje de manera autónoma";
		String historia = "Construye interpretaciones históricas";
		String ambiente = "Gestiona responsablemente el espacio y el ambiente";
		String recursos = "Gestiona responsablemente los recursos económicos";
		String saludable = "Asume una vida saludable";
		String sociomotriz = "Interactúa a través de sus habilidades sociomotrices";
		String[] c2l = { "Se comunica oralmente en castellano como segunda lengua",
				"Lee diversos tipos de textos escritos en castellano como segunda lengua",
				"Escribe diversos tipos de textos en castellano como segunda lengua" };
		String[] ingles = { "Se comunica oralmente en inglés como lengua extranjera",
				"Lee diversos tipos de textos escritos en inglés como lengua extranjera",
				"Escribe diversos tipos de textos en inglés como lengua extranjera" };
		Map<String, String> m = new LinkedHashMap<>();
		// Inicial (14)
		m.put("INI-PS-1", identidad);
		m.put("INI-PS-2", convive);
		m.put("INI-PS-3", religion1);
		m.put("INI-PSI-1", motricidad);
		m.put("INI-COM-1", oral);
		m.put("INI-COM-2", lee);
		m.put("INI-COM-3", escribe);
		m.put("INI-COM-4", crea);
		m.put("INI-C2L-1", c2l[0]);
		m.put("INI-MAT-1", cantidad);
		m.put("INI-MAT-2", forma);
		m.put("INI-CYT-1", indaga);
		m.put("INI-TRV-1", tic);
		m.put("INI-TRV-2", autonomo);
		// Primaria (30) y Secundaria (31): las áreas comunes, con los mismos nombres.
		for (String nivel : List.of("PRI", "SEC")) {
			if (nivel.equals("PRI")) {
				m.put("PRI-PS-1", identidad);
				m.put("PRI-PS-2", convive);
				m.put("PRI-PS-3", historia);
				m.put("PRI-PS-4", ambiente);
				m.put("PRI-PS-5", recursos);
			}
			else {
				m.put("SEC-DPCC-1", identidad);
				m.put("SEC-DPCC-2", convive);
				m.put("SEC-CCSS-1", historia);
				m.put("SEC-CCSS-2", ambiente);
				m.put("SEC-CCSS-3", recursos);
				m.put("SEC-EPT-1", "Gestiona proyectos de emprendimiento económico o social");
			}
			m.put(nivel + "-EF-1", motricidad);
			m.put(nivel + "-EF-2", saludable);
			m.put(nivel + "-EF-3", sociomotriz);
			m.put(nivel + "-COM-1", oral);
			m.put(nivel + "-COM-2", lee);
			m.put(nivel + "-COM-3", escribe);
			m.put(nivel + "-AYC-1", aprecia);
			m.put(nivel + "-AYC-2", crea);
			for (int i = 0; i < 3; i++) {
				m.put(nivel + "-C2L-" + (i + 1), c2l[i]);
				m.put(nivel + "-ING-" + (i + 1), ingles[i]);
			}
			m.put(nivel + "-MAT-1", cantidad);
			m.put(nivel + "-MAT-2", regularidad);
			m.put(nivel + "-MAT-3", forma);
			m.put(nivel + "-MAT-4", datos);
			m.put(nivel + "-CYT-1", indaga);
			m.put(nivel + "-CYT-2", explica);
			m.put(nivel + "-CYT-3", disena);
			m.put(nivel + "-REL-1", religion1);
			m.put(nivel + "-REL-2", religion2);
			m.put(nivel + "-TRV-1", tic);
			m.put(nivel + "-TRV-2", autonomo);
		}
		assertThat(m).hasSize(75);
		return m;
	}
}
