package pe.edu.virgenmaria.cuentasclaras.arquitectura;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Sprint 10, tanda 3 (M11, sección 3.13): la LISTA BLANCA de PDFBox para el generador de la libreta. Solo escribe: un
 * documento nuevo, páginas A4, texto con la fuente del jar, rectángulos, líneas y el logo ya saneado. Una regla por
 * paquetes dejaba usar cualquier clase (imágenes desde archivos, unir PDF, formularios, acciones); esta solo admite:
 * <ul>
 *   <li>{@code PDDocument}: el constructor sin argumentos (o con la caché de memoria de {@code MemoryUsageSetting}),
 *       {@code addPage}, {@code save}, {@code close} y {@code getDocumentInformation};</li>
 *   <li>{@code PDPage}, {@code PDRectangle}, {@code PDPageContentStream} (con su {@code AppendMode}), {@code PDFont},
 *       {@code PDDocumentInformation}, {@code MemoryUsageSetting} y {@code RandomAccessStreamCache};</li>
 *   <li>{@code PDType0Font.load(PDDocument, InputStream, boolean)} y {@code LosslessFactory.createFromImage};</li>
 *   <li>{@code PDImageXObject} solo como tipo (y su ancho y alto), nunca {@code createFrom*}.</li>
 * </ul>
 * Prohibidos expresamente: {@code PDImageXObject.createFrom*}, {@code PDFMergerUtility}, {@code importPage},
 * {@code PDAcroForm}, los paquetes {@code interactive.action} e {@code interactive.annotation} y {@code Loader}.
 */
final class ListaBlancaPdfbox {

	private static final String P = "org.apache.pdfbox.";

	/** Las clases permitidas y, si la clase se limita a algunos miembros, cuáles ({@code null}: todos). */
	private static final Map<String, Set<String>> PERMITIDAS = Map.ofEntries(
			Map.entry(P + "pdmodel.PDDocument", Set.of("<init>()", "<init>(org.apache.pdfbox.io.RandomAccessStreamCache$"
					+ "StreamCacheCreateFunction)", "addPage", "save", "close", "getDocumentInformation")),
			Map.entry(P + "pdmodel.PDPage", Set.of()),
			Map.entry(P + "pdmodel.common.PDRectangle", Set.of()),
			Map.entry(P + "pdmodel.PDPageContentStream", Set.of()),
			Map.entry(P + "pdmodel.PDPageContentStream$AppendMode", Set.of()),
			Map.entry(P + "pdmodel.font.PDType0Font", Set.of("load(org.apache.pdfbox.pdmodel.PDDocument, java.io.InputStream, "
					+ "boolean)")),
			Map.entry(P + "pdmodel.font.PDFont", Set.of()),
			Map.entry(P + "pdmodel.graphics.image.LosslessFactory", Set.of("createFromImage")),
			Map.entry(P + "pdmodel.graphics.image.PDImageXObject", Set.of("getWidth", "getHeight")),
			Map.entry(P + "pdmodel.PDDocumentInformation", Set.of()),
			Map.entry(P + "io.MemoryUsageSetting", Set.of()),
			Map.entry(P + "io.RandomAccessStreamCache", Set.of()),
			Map.entry(P + "io.RandomAccessStreamCache$StreamCacheCreateFunction", Set.of()));

	/** Las clases de PDFBox con miembros limitados (las demás permitidas admiten todos sus miembros). */
	private static final Set<String> LIMITADAS = Set.of(P + "pdmodel.PDDocument", P + "pdmodel.font.PDType0Font",
			P + "pdmodel.graphics.image.LosslessFactory", P + "pdmodel.graphics.image.PDImageXObject");

	private static final List<String> PROHIBIDOS = List.of(P + "Loader", P + "multipdf.", P + "pdmodel.interactive.form.",
			P + "pdmodel.interactive.action.", P + "pdmodel.interactive.annotation.");

	private ListaBlancaPdfbox() {
	}

	static ArchRule regla(String paquete) {
		return classes().that().resideInAPackage(paquete).should(new ArchCondition<JavaClass>(
				"usar de PDFBox solo la lista blanca de escritura (M11)") {
			@Override
			public void check(JavaClass clase, ConditionEvents eventos) {
				for (Dependency d : clase.getDirectDependenciesFromSelf()) {
					String destino = d.getTargetClass().getName();
					if (!esPdfbox(destino)) {
						continue;
					}
					if (prohibido(destino) || !PERMITIDAS.containsKey(destino)) {
						eventos.add(SimpleConditionEvent.violated(d, d.getDescription() + ": " + destino
								+ " no está en la lista blanca de PDFBox"));
					}
				}
				for (JavaAccess<?> acceso : clase.getAccessesFromSelf()) {
					String duenio = acceso.getTargetOwner().getName();
					if (!LIMITADAS.contains(duenio)) {
						continue;
					}
					String nombre = acceso.getName();
					String firma = nombre + "(" + String.join(", ", acceso instanceof JavaCodeUnitAccess<?> llamada
							? llamada.getTarget().getRawParameterTypes().stream().map(JavaClass::getName).toList()
							: List.of()) + ")";
					Set<String> permitidos = PERMITIDAS.get(duenio);
					if (!permitidos.contains(nombre) && !permitidos.contains(firma)) {
						eventos.add(SimpleConditionEvent.violated(acceso, acceso.getDescription() + ": " + duenio + "."
								+ firma + " no está en la lista blanca de PDFBox"));
					}
				}
			}
		}).allowEmptyShould(true)
				.because("la libreta solo escribe un PDF: ni lo abre, ni une PDF, ni agrega formularios, acciones o anotaciones");
	}

	private static boolean esPdfbox(String nombre) {
		return nombre.startsWith(P) || nombre.startsWith("org.apache.fontbox.");
	}

	private static boolean prohibido(String nombre) {
		return PROHIBIDOS.stream().anyMatch(nombre::startsWith);
	}
}
