package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.TableroPromotoria;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ComoUsuario;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rediseño (tablero del inicio): Promotoría recibe las cifras y los gráficos SVG dibujados en el servidor, cada uno con
 * su título, su resumen y su tabla de datos; Caja no recibe ni el tablero ni sus montos. Y la página no trae estilos ni
 * scripts en línea (la CSP los bloquearía).
 */
@PruebaIntegracion
class TableroInicioWebTest {

	/** Un atributo style, un bloque style o un script sin src (lo que la CSP {@code 'self'} no deja ejecutar). */
	private static final Pattern EN_LINEA = Pattern.compile("(?i)<[^>]+\\sstyle\\s*=|<style[\\s>]|<script(?![^>]*\\ssrc=)"
			+ "|\\son[a-z]+\\s*=\\s*\"");

	@Autowired
	private MockMvc mvc;

	@Test
	@ComoUsuario(roles = Rol.PROMOTOR, nombreCompleto = "Rosa Huamán")
	void promotoriaVeLasCifrasYLosGraficosConSuTablaDeDatos() throws Exception {
		String html = mvc.perform(get("/inicio"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("tablero", org.hamcrest.Matchers.instanceOf(TableroPromotoria.Tablero.class)))
				.andExpect(content().string(containsString("data-tablero-kpis")))
				.andExpect(content().string(containsString("Cobrado hoy")))
				.andExpect(content().string(containsString("Pendientes por aprobar")))
				.andExpect(content().string(containsString("data-tablero-graficos")))
				.andExpect(content().string(containsString("id=\"grafico-diaria\"")))
				.andExpect(content().string(containsString("id=\"grafico-morosidad\"")))
				.andExpect(content().string(containsString("id=\"grafico-medios\"")))
				.andExpect(content().string(containsString("Cobranza de los últimos 30 días")))
				.andReturn().getResponse().getContentAsString();

		// La serie diaria: un SVG con su <title> y <desc>, 30 barras (con su <title>) y 30 filas en su tabla.
		assertThat(html).contains("<svg class=\"grafico-svg barras-v\"", "<desc>", "Ver los datos en una tabla");
		String diaria = seccion(html, "grafico-diaria");
		assertThat(contar(diaria, "<rect class=\"grafico-barra serie-1\"")).isEqualTo(30);
		assertThat(contar(diaria, "<th scope=\"row\">")).isEqualTo(30);
		assertThat(diaria).contains("role=\"img\"", "aria-labelledby=\"grafico-diaria-titulo grafico-diaria-resumen\"");
		// La geometría va en atributos SVG en %, nunca en style.
		assertThat(diaria).containsPattern("<rect class=\"grafico-barra serie-1\" x=\"[0-9.]+%\"\\s+y=\"[0-9.]+%\"\\s+"
				+ "width=\"[0-9.]+%\"\\s+height=\"[0-9.]+%\"");
	}

	@Test
	@ComoUsuario(roles = Rol.PROMOTOR)
	void elInicioDePromotoriaNoTraeEstilosNiScriptsEnLinea() throws Exception {
		String html = mvc.perform(get("/inicio")).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();

		assertThat(html).contains("data-tablero-graficos");
		assertThat(EN_LINEA.matcher(html).find()).as("estilo o script en línea en el inicio de Promotoría").isFalse();
	}

	@Test
	@ComoUsuario(roles = Rol.CAJA, nombreCompleto = "Lucía Ramos")
	void cajaNoRecibeElTableroNiSusMontos() throws Exception {
		mvc.perform(get("/inicio"))
				.andExpect(status().isOk())
				.andExpect(model().attributeDoesNotExist("tablero"))
				.andExpect(content().string(not(containsString("data-tablero-kpis"))))
				.andExpect(content().string(not(containsString("data-tablero-graficos"))))
				.andExpect(content().string(not(containsString("grafico-diaria"))))
				.andExpect(content().string(not(containsString("Morosidad por grado"))))
				.andExpect(content().string(not(containsString("Medios de pago de"))));
	}

	@Test
	@ComoUsuario(roles = Rol.CAJA)
	void cajaNoPuedeLlamarAlServicioDelTablero(@Autowired TableroPromotoria tablero) {
		org.assertj.core.api.Assertions.assertThatThrownBy(tablero::tablero)
				.isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
	}

	private static String seccion(String html, String id) {
		int inicio = html.indexOf("id=\"" + id + "\"");
		assertThat(inicio).as("sección " + id).isPositive();
		int fin = html.indexOf("</section>", inicio);
		return html.substring(inicio, fin);
	}

	private static int contar(String texto, String buscado) {
		int n = 0;
		for (int i = texto.indexOf(buscado); i >= 0; i = texto.indexOf(buscado, i + 1)) {
			n++;
		}
		return n;
	}
}
