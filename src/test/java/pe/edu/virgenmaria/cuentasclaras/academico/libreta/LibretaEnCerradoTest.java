package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Correcciones del sprint 11 (QA-S11-04): en un colegio CERRADO (modo consulta hasta {@code conservar_hasta}) la familia
 * que descarga una libreta que ya no coincide con su huella ve «en revisión», nunca un error: ese camino solo deja el
 * evento de la bitácora (ningún mensaje, que en CERRADO la base rechazaría salvo ACTIVACION_CUENTA) y no entrega el PDF.
 */
class LibretaEnCerradoTest extends PruebaLibretas {

	@Test
	void laFamiliaVeEnRevisionEnUnColegioCerrado() throws Exception {
		cerrarYEmitir();
		Long id = libreta(mateo);
		// El colegio 1 en CERRADO (como lo deja el cierre: la suspensión y el estado desde hoy; conservar_hasta, la base).
		jdbc.update("UPDATE colegio SET estado = 'CERRADO', estado_desde = CURRENT_TIMESTAMP, suspendido_en = "
				+ "CURRENT_TIMESTAMP WHERE id = 1");
		jdbc.update("UPDATE calificacion SET nivel_logro = 'C' WHERE id = ?", vigente(mateo, competencias("MAT").get(0)));
		hoyEs(LocalDate.of(2026, 5, 7));
		long mensajesAntes = contar("SELECT COUNT(*) FROM mensaje");

		MvcResult descarga = mvc.perform(get("/familia/libretas/" + id + ".pdf").with(UsuariosDePrueba.como(rosaEnLinea())))
				.andReturn();
		MockHttpServletResponse r = descarga.getResponse();
		assertThat(r.getStatus()).isEqualTo(303);
		assertThat(r.getRedirectedUrl()).isEqualTo("/familia/libretas");
		String pagina = mvc.perform(get(r.getRedirectedUrl()).flashAttrs(descarga.getFlashMap())
				.with(UsuariosDePrueba.como(rosaEnLinea()))).andReturn().getResponse().getContentAsString();
		assertThat(pagina).contains("Su libreta está en revisión");
		assertThat(contar("SELECT COUNT(*) FROM entrega_libreta")).isZero();
		assertThat((long) contar("SELECT COUNT(*) FROM mensaje")).isEqualTo(mensajesAntes);
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'LIBRETA_HUELLA_NO_COINCIDE'")).isEqualTo(1);
	}
}
