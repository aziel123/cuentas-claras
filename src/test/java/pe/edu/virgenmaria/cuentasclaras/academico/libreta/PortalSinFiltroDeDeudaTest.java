package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pe.edu.virgenmaria.cuentasclaras.CuentasClarasApplication;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.web.PortalLibretasController;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sprint 10, tanda 3 (E28, B14; INDECOPI y Ley 29571): nada en el camino de una familia hasta su libreta puede leer la
 * deuda. No solo el servicio y el controlador (ArchUnit: academico no depende de cobranza): tampoco
 * <ol>
 *   <li>los {@code HandlerInterceptor} que corren en {@code /familia/libretas/**},</li>
 *   <li>los filtros de servlet de la aplicación,</li>
 *   <li>los {@code @ControllerAdvice} que aplican al controlador del portal de libretas (ni sus {@code @ModelAttribute}),</li>
 *   <li>los fragmentos del layout de las familias (la página de una familia que debe no muestra montos ni la deuda),</li>
 *   <li>la redirección después del ingreso ({@code AuthenticationSuccessHandler}).</li>
 * </ol>
 * Las dependencias se revisan con ArchUnit también de forma TRANSITIVA: un filtro que llamara a un servicio que lee cuotas
 * fallaría aquí.
 */
class PortalSinFiltroDeDeudaTest extends PruebaLibretas {

	/** Lo que sabe quién debe: los módulos del dinero y el portal de las familias (que depende de cobranza). */
	private static final List<String> DEL_DINERO = List.of(".cobranza.", ".caja.", ".comprobantes.", ".pasarela.",
			".recaudacion.", ".conciliacion.", ".panel.", ".familias.", ".matricula.");

	private static final JavaClasses CLASES = new ClassFileImporter().withImportOption(ImportOption.Predefined
			.DO_NOT_INCLUDE_TESTS).importPackagesOf(CuentasClarasApplication.class);

	@Autowired
	private ApplicationContext contexto;

	@Autowired
	@org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mapeos;

	@Autowired
	private ServicioPlanesPension planes;

	private static List<String> leeDeuda(Class<?> clase) {
		JavaClass importada = CLASES.get(clase.getName());
		List<String> problemas = new ArrayList<>();
		importada.getTransitiveDependenciesFromSelf().forEach(d -> {
			String destino = d.getTargetClass().getName();
			if (destino.startsWith("pe.edu.virgenmaria.cuentasclaras") && DEL_DINERO.stream().anyMatch(destino::contains)) {
				problemas.add(clase.getSimpleName() + " → " + destino);
			}
		});
		return problemas;
	}

	/** B14 (1): los interceptores de MVC que corren en /familia/libretas no leen la deuda. */
	@Test
	void losInterceptoresNoLeenLaDeuda() throws Exception {
		for (String ruta : List.of("/familia/libretas", "/familia/libretas/1.pdf")) {
			HandlerExecutionChain cadena = mapeos.getHandler(new MockHttpServletRequest("GET", ruta));
			assertThat(cadena).as(ruta).isNotNull();
			for (HandlerInterceptor i : cadena.getInterceptorList()) {
				Class<?> clase = org.springframework.aop.support.AopUtils.getTargetClass(i);
				if (clase.getName().startsWith("pe.edu.virgenmaria.cuentasclaras")) {
					assertThat(leeDeuda(clase)).as(ruta).isEmpty();
				}
			}
		}
	}

	/** B14 (2): ningún filtro de servlet de la aplicación lee la deuda. */
	@Test
	void losFiltrosNoLeenLaDeuda() {
		List<String> problemas = new ArrayList<>();
		contexto.getBeansOfType(Filter.class).values().stream()
				.map(f -> org.springframework.aop.support.AopUtils.getTargetClass(f))
				.filter(c -> c.getName().startsWith("pe.edu.virgenmaria.cuentasclaras")).forEach(c -> problemas.addAll(leeDeuda(c)));
		CLASES.stream().filter(c -> c.isAssignableTo(Filter.class) && !c.isInterface())
				.forEach(c -> problemas.addAll(leeDeuda(c.reflect())));
		assertThat(problemas).isEmpty();
	}

	/** B14 (3): los @ControllerAdvice que aplican al portal de libretas (y sus @ModelAttribute) no leen la deuda. */
	@Test
	void losControllerAdviceYSusModelAttributeNoLeenLaDeuda() {
		List<String> problemas = new ArrayList<>();
		int aplican = 0;
		for (ControllerAdviceBean advice : ControllerAdviceBean.findAnnotatedBeans(contexto)) {
			if (!advice.isApplicableToBeanType(PortalLibretasController.class)) {
				continue;
			}
			aplican++;
			problemas.addAll(leeDeuda(advice.getBeanType()));
		}
		assertThat(aplican).as("hay advices globales (marca, sesión, franja, errores)").isPositive();
		// Toda clase con @ModelAttribute que no sea un controlador propio (los advices) tampoco.
		CLASES.stream().filter(c -> c.isAnnotatedWith(ControllerAdvice.class) || c.isAnnotatedWith(RestControllerAdvice.class))
				.filter(c -> c.getMethods().stream().anyMatch(m -> m.isAnnotatedWith(ModelAttribute.class)))
				.forEach(c -> problemas.addAll(leeDeuda(c.reflect())));
		assertThat(problemas).isEmpty();
	}

	/** B14 (5): la redirección después del ingreso no depende de la deuda. */
	@Test
	void laRedireccionDespuesDelIngresoNoLeeLaDeuda() {
		List<String> problemas = new ArrayList<>();
		contexto.getBeansOfType(AuthenticationSuccessHandler.class).values().stream()
				.map(h -> org.springframework.aop.support.AopUtils.getTargetClass(h))
				.filter(c -> c.getName().startsWith("pe.edu.virgenmaria.cuentasclaras")).forEach(c -> problemas.addAll(leeDeuda(c)));
		CLASES.stream().filter(c -> c.isAssignableTo(AuthenticationSuccessHandler.class) && !c.isInterface())
				.forEach(c -> problemas.addAll(leeDeuda(c.reflect())));
		assertThat(problemas).isEmpty();
	}

	/**
	 * B14 (4): los fragmentos del layout de las familias. La página de libretas de una familia con cuotas vencidas no
	 * muestra montos, «deuda» ni un aviso de pago: lo mismo que la de una familia al día.
	 */
	@Test
	void elLayoutDeLaFamiliaQueDebeNoMuestraLaDeuda() throws Exception {
		EscenarioCobranza.planAprobado(planes, escuela.anio2026(), 2026, Nivel.PRIMARIA, "350.00", "300.00",
				LocalDate.of(2026, 3, 1));
		SecurityContextHolder.clearContext();
		jdbc.update("UPDATE cuota SET estado = 'PAGADA', monto_pagado = monto WHERE alumno_id = (SELECT alumno_id FROM "
				+ "matricula WHERE id = ?)", lucas);
		cerrarYEmitir();
		assertThat(contar("SELECT COUNT(*) FROM cuota WHERE alumno_id = (SELECT alumno_id FROM matricula WHERE id = ?) AND "
				+ "estado <> 'PAGADA' AND fecha_vencimiento < ?", mateo, LocalDate.of(2026, 5, 4))).isPositive();
		String deRosa = mvc.perform(get("/familia/libretas").with(UsuariosDePrueba.como(rosaEnLinea()))).andReturn()
				.getResponse().getContentAsString();
		String dePedro = mvc.perform(get("/familia/libretas").with(UsuariosDePrueba.como(pedroEnLinea()))).andReturn()
				.getResponse().getContentAsString();
		for (String pagina : List.of(deRosa, dePedro)) {
			assertThat(pagina).contains("Libretas").contains("Descargar PDF").doesNotContain("S/")
					.doesNotContainIgnoringCase("deuda").doesNotContainIgnoringCase("vencid")
					.doesNotContainIgnoringCase("pagar ahora");
		}
		// La misma familia antes y después de pagar todo: la página (con su layout) es la misma. Nada de lo que se muestra
		// depende de la deuda (la barra inferior de las familias es la misma para todas).
		jdbc.update("UPDATE cuota SET estado = 'PAGADA', monto_pagado = monto");
		String deRosaAlDia = mvc.perform(get("/familia/libretas").with(UsuariosDePrueba.como(rosaEnLinea()))).andReturn()
				.getResponse().getContentAsString();
		assertThat(sinSecretos(deRosaAlDia)).isEqualTo(sinSecretos(deRosa));
		// Las dos ven las mismas secciones del layout (la de Rosa no tiene un aviso de deuda que la de Pedro no tenga).
		Set<String> clasesRosa = Set.copyOf(clases(deRosa));
		Set<String> clasesPedro = Set.copyOf(clases(dePedro));
		assertThat(clasesRosa).isEqualTo(clasesPedro);
	}

	/** Sin lo que cambia en cada petición (el token CSRF y el nonce de la CSP). */
	private static String sinSecretos(String html) {
		return html.replaceAll("nonce=\"[^\"]*\"", "").replaceAll("value=\"[0-9a-zA-Z_-]{20,}\"", "")
				.replaceAll("content=\"[0-9a-zA-Z_-]{20,}\"", "");
	}

	private static List<String> clases(String html) {
		List<String> lista = new ArrayList<>();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("class=\"([^\"]+)\"").matcher(html);
		while (m.find()) {
			lista.add(m.group(1));
		}
		return lista;
	}
}
