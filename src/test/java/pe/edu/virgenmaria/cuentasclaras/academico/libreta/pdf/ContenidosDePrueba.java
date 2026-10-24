package pe.edu.virgenmaria.cuentasclaras.academico.libreta.pdf;

import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.MotivoLibreta;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.TipoCodigo;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.ContenidoLibreta;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 10, tanda 3: contenidos de libreta FICTICIOS para probar el generador sin base (y para las libretas de muestra de
 * {@code docs/ux/libretas-muestra/}). Los nombres de áreas y competencias son los del catálogo CNEB-2016.1; los alumnos,
 * docentes y conclusiones son inventados.
 */
public final class ContenidosDePrueba {

	static final List<ContenidoLibreta.Escala> ESCALA = List.of(
			new ContenidoLibreta.Escala("AD", "Logro destacado", "El estudiante evidencia un nivel superior a lo esperado "
					+ "respecto a la competencia: demuestra aprendizajes que van más allá del nivel esperado."),
			new ContenidoLibreta.Escala("A", "Logro esperado", "El estudiante evidencia el nivel esperado respecto a la "
					+ "competencia, demostrando manejo satisfactorio en todas las tareas propuestas y en el tiempo programado."),
			new ContenidoLibreta.Escala("B", "En proceso", "El estudiante está próximo o cerca al nivel esperado respecto a la "
					+ "competencia, para lo cual requiere acompañamiento durante un tiempo razonable para lograrlo."),
			new ContenidoLibreta.Escala("C", "En inicio", "El estudiante muestra un progreso mínimo en una competencia de "
					+ "acuerdo al nivel esperado. Evidencia con frecuencia dificultades en el desarrollo de las tareas."));

	private ContenidosDePrueba() {
	}

	/** 5.° A de Primaria, II bimestre (con el I), con conclusiones en B, una exoneración y la asistencia. */
	public static ContenidoLibreta primaria() {
		List<ContenidoLibreta.Periodo> periodos = List.of(new ContenidoLibreta.Periodo(1, "I bimestre"),
				new ContenidoLibreta.Periodo(2, "II bimestre"));
		List<ContenidoLibreta.Area> areas = new ArrayList<>();
		areas.add(area("PS", "Personal Social", false, new String[][] {
				{ "PRI-PS-1", "Construye su identidad", "A", "A" },
				{ "PRI-PS-2", "Convive y participa democráticamente en la búsqueda del bien común", "A", "AD" },
				{ "PRI-PS-3", "Construye interpretaciones históricas", "B", "A" },
				{ "PRI-PS-4", "Gestiona responsablemente el espacio y el ambiente", "A", "A" },
				{ "PRI-PS-5", "Gestiona responsablemente los recursos económicos", "A", "A" } }));
		areas.add(area("EF", "Educación Física", false, new String[][] {
				{ "PRI-EF-1", "Se desenvuelve de manera autónoma a través de su motricidad", "A", "EXO" },
				{ "PRI-EF-2", "Asume una vida saludable", "A", "EXO" },
				{ "PRI-EF-3", "Interactúa a través de sus habilidades sociomotrices", "AD", "EXO" } }));
		areas.add(area("COM", "Comunicación", false, new String[][] {
				{ "PRI-COM-1", "Se comunica oralmente en su lengua materna", "A", "A" },
				{ "PRI-COM-2", "Lee diversos tipos de textos escritos en su lengua materna", "B", "B" },
				{ "PRI-COM-3", "Escribe diversos tipos de textos en su lengua materna", "A", "A" } }));
		areas.add(area("AYC", "Arte y Cultura", false, new String[][] {
				{ "PRI-AYC-1", "Aprecia de manera crítica manifestaciones artístico-culturales", "A", "A" },
				{ "PRI-AYC-2", "Crea proyectos desde los lenguajes artísticos", "AD", "AD" } }));
		areas.add(area("ING", "Inglés como lengua extranjera", false, new String[][] {
				{ "PRI-ING-1", "Se comunica oralmente en inglés como lengua extranjera", "B", "A" },
				{ "PRI-ING-2", "Lee diversos tipos de textos escritos en inglés como lengua extranjera", "B", "B" },
				{ "PRI-ING-3", "Escribe diversos tipos de textos en inglés como lengua extranjera", "C", "B" } }));
		areas.add(area("MAT", "Matemática", false, new String[][] {
				{ "PRI-MAT-1", "Resuelve problemas de cantidad", "A", "AD" },
				{ "PRI-MAT-2", "Resuelve problemas de regularidad, equivalencia y cambio", "B", "A" },
				{ "PRI-MAT-3", "Resuelve problemas de forma, movimiento y localización", "A", "A" },
				{ "PRI-MAT-4", "Resuelve problemas de gestión de datos e incertidumbre", "A", "B" } }));
		areas.add(area("CYT", "Ciencia y Tecnología", false, new String[][] {
				{ "PRI-CYT-1", "Indaga mediante métodos científicos para construir sus conocimientos", "A", "A" },
				{ "PRI-CYT-2", "Explica el mundo físico basándose en conocimientos sobre los seres vivos, materia y energía, "
						+ "biodiversidad, Tierra y universo", "A", "A" },
				{ "PRI-CYT-3", "Diseña y construye soluciones tecnológicas para resolver problemas de su entorno", "B", "A" } }));
		areas.add(area("REL", "Educación Religiosa", false, new String[][] {
				{ "PRI-REL-1", "Construye su identidad como persona humana, amada por Dios, digna, libre y trascendente, "
						+ "comprendiendo la doctrina de su propia religión, abierto al diálogo con las que le son cercanas", "A", "A" },
				{ "PRI-REL-2", "Asume la experiencia del encuentro personal y comunitario con Dios en su proyecto de vida en "
						+ "coherencia con su creencia religiosa", "A", "A" } }));
		areas.add(area("TRV", "Competencias transversales", true, new String[][] {
				{ "PRI-TRV-1", "Se desenvuelve en entornos virtuales generados por las TIC", "A", "A" },
				{ "PRI-TRV-2", "Gestiona su aprendizaje de manera autónoma", "B", "A" } }));
		return new ContenidoLibreta("Colegio Virgen María", "Primaria", "5.° Primaria", "A", 2026, 101L, 202L, 2,
				"II bimestre", false, new LibretaEmitida.Cabecera("Quispe Huamán, Mateo", "07123456789012",
						TipoCodigo.CODIGO_ESTUDIANTE, "0123456", "DRE Lima Metropolitana", "UGEL 03", "Ana Torres Ríos",
						"Carlos Mendoza Paredes"), periodos, areas, List.of(
								new ContenidoLibreta.Asistencia(1, "I bimestre", 1, 0, 2),
								new ContenidoLibreta.Asistencia(2, "II bimestre", 0, 1, 0)), ESCALA);
	}

	/** 5 años de Inicial, I bimestre: la escala de Inicial no lleva AD y la libreta se entrega en la entrevista. */
	public static ContenidoLibreta inicial() {
		List<ContenidoLibreta.Periodo> periodos = List.of(new ContenidoLibreta.Periodo(1, "I bimestre"));
		List<ContenidoLibreta.Area> areas = new ArrayList<>();
		areas.add(area1("PS", "Personal Social", false, new String[][] {
				{ "INI-PS-1", "Construye su identidad", "A" },
				{ "INI-PS-2", "Convive y participa democráticamente en la búsqueda del bien común", "A" } }));
		areas.add(area1("PSI", "Psicomotriz", false, new String[][] {
				{ "INI-PSI-1", "Se desenvuelve de manera autónoma a través de su motricidad", "A" } }));
		areas.add(area1("COM", "Comunicación", false, new String[][] {
				{ "INI-COM-1", "Se comunica oralmente en su lengua materna", "A" },
				{ "INI-COM-2", "Lee diversos tipos de textos escritos en su lengua materna", "B" },
				{ "INI-COM-3", "Escribe diversos tipos de textos en su lengua materna", "B" },
				{ "INI-COM-4", "Crea proyectos desde los lenguajes artísticos", "A" } }));
		areas.add(area1("MAT", "Matemática", false, new String[][] {
				{ "INI-MAT-1", "Resuelve problemas de cantidad", "A" },
				{ "INI-MAT-2", "Resuelve problemas de forma, movimiento y localización", "A" } }));
		areas.add(area1("CYT", "Ciencia y Tecnología", false, new String[][] {
				{ "INI-CYT-1", "Indaga mediante métodos científicos para construir sus conocimientos", "A" } }));
		areas.add(area1("TRV", "Competencias transversales", true, new String[][] {
				{ "INI-TRV-1", "Se desenvuelve en entornos virtuales generados por las TIC", "A" },
				{ "INI-TRV-2", "Gestiona su aprendizaje de manera autónoma", "B" } }));
		return new ContenidoLibreta("Colegio Virgen María", "Inicial", "5 años", "Girasoles", 2026, 103L, 201L, 1,
				"I bimestre", false, new LibretaEmitida.Cabecera("Rojas Paz, Ariana", "81234567", TipoCodigo.DNI,
						"0123457", "DRE Lima Metropolitana", "UGEL 03", "Elena Ramos Vela", "Carlos Mendoza Paredes"),
				periodos, areas, List.of(new ContenidoLibreta.Asistencia(1, "I bimestre", 2, 0, 1)), ESCALA.subList(1, 4));
	}

	public static GeneradorLibretaPdf.Version version(int numero) {
		return new GeneradorLibretaPdf.Version("LIB-1234-1a2b3c4d5e6f7a8b", numero, LocalDateTime.of(2026, 7, 20, 9, 5),
				numero == 1 ? MotivoLibreta.CIERRE : MotivoLibreta.CORRECCION,
				numero == 1 ? null : "Matemática · Resuelve problemas de gestión de datos e incertidumbre: C → B");
	}

	/** El mismo contenido con otra conclusión en la primera competencia con nota del periodo (E37). */
	public static ContenidoLibreta conConclusion(ContenidoLibreta c, String conclusion) {
		List<ContenidoLibreta.Area> areas = new ArrayList<>();
		boolean puesta = false;
		for (ContenidoLibreta.Area a : c.areas()) {
			List<ContenidoLibreta.Competencia> comps = new ArrayList<>();
			for (ContenidoLibreta.Competencia k : a.competencias()) {
				if (!puesta && !a.transversal()) {
					List<ContenidoLibreta.Nota> notas = new ArrayList<>(k.notas());
					ContenidoLibreta.Nota n = notas.remove(notas.size() - 1);
					notas.add(new ContenidoLibreta.Nota(n.periodo(), "B", false, conclusion, "0".repeat(64)));
					comps.add(new ContenidoLibreta.Competencia(k.codigo(), k.nombre(), notas, k.anual()));
					puesta = true;
				}
				else {
					comps.add(k);
				}
			}
			areas.add(new ContenidoLibreta.Area(a.codigo(), a.nombre(), a.transversal(), comps));
		}
		return new ContenidoLibreta(c.colegio(), c.nivel(), c.grado(), c.seccion(), c.anio(), c.alumnoId(), c.periodoId(),
				c.periodoNumero(), c.periodoNombre(), c.ultimoPeriodo(), c.cabecera(), c.periodos(), areas, c.asistencia(),
				c.escala());
	}

	private static ContenidoLibreta.Area area(String codigo, String nombre, boolean transversal, String[][] filas) {
		List<ContenidoLibreta.Competencia> comps = new ArrayList<>();
		for (String[] f : filas) {
			comps.add(new ContenidoLibreta.Competencia(f[0], f[1], List.of(nota(1, f[2], null), nota(2, f[3],
					conclusion(f[3], f[1]))), null));
		}
		return new ContenidoLibreta.Area(codigo, nombre, transversal, comps);
	}

	private static ContenidoLibreta.Area area1(String codigo, String nombre, boolean transversal, String[][] filas) {
		List<ContenidoLibreta.Competencia> comps = new ArrayList<>();
		for (String[] f : filas) {
			comps.add(new ContenidoLibreta.Competencia(f[0], f[1], List.of(nota(1, f[2], conclusion(f[2], f[1]))), null));
		}
		return new ContenidoLibreta.Area(codigo, nombre, transversal, comps);
	}

	private static ContenidoLibreta.Nota nota(int periodo, String nivel, String conclusion) {
		boolean exo = "EXO".equals(nivel);
		return new ContenidoLibreta.Nota(periodo, exo ? null : nivel, exo, conclusion, conclusion == null ? null
				: "0".repeat(64));
	}

	private static String conclusion(String nivel, String competencia) {
		if (!"B".equals(nivel) && !"C".equals(nivel)) {
			return null;
		}
		return "Avanza en «" + competencia.toLowerCase(java.util.Locale.ROOT) + "»: reconoce lo que aprendió y necesita "
				+ "practicar con ejemplos de su vida diaria. Se recomienda leer en casa 15 minutos cada día.";
	}
}
