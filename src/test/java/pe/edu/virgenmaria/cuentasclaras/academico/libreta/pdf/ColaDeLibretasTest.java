package pe.edu.virgenmaria.cuentasclaras.academico.libreta.pdf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.PruebaLibretas;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sprint 10, tanda 3 (E38, sección 3.9): con los 2 hilos del generador ocupados y su cola de 8 llena, la descarga de una
 * libreta responde 503 con «intente en unos minutos» (la aplicación no se queda sin hilos) y no deja constancia de entrega.
 * Al liberarse, la descarga sale.
 */
class ColaDeLibretasTest extends PruebaLibretas {

	@Autowired
	private GeneradorLibretaPdf generador;

	@Test
	void colaLlenaResponde503ConMensaje() throws Exception {
		cerrarYEmitir();
		Long id = libreta(mateo);
		CountDownLatch soltar = new CountDownLatch(1);
		CountDownLatch ocupados = new CountDownLatch(2);
		try {
			for (int i = 0; i < 10; i++) {
				generador.ocupar(() -> {
					ocupados.countDown();
					try {
						soltar.await(60, TimeUnit.SECONDS);
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				});
			}
			assertThat(ocupados.await(10, TimeUnit.SECONDS)).isTrue();
			MockHttpServletResponse r = mvc.perform(get("/familia/libretas/" + id + ".pdf").with(UsuariosDePrueba.como(
					rosaEnLinea()))).andReturn().getResponse();
			assertThat(r.getStatus()).isEqualTo(503);
			assertThat(new String(r.getContentAsByteArray(), StandardCharsets.UTF_8)).contains("Intente en unos minutos");
			assertThat(contar("SELECT COUNT(*) FROM entrega_libreta")).isZero();
		}
		finally {
			soltar.countDown();
		}
		Thread.sleep(200);
		assertThat(mvc.perform(get("/familia/libretas/" + id + ".pdf").with(UsuariosDePrueba.como(rosaEnLinea())))
				.andReturn().getResponse().getStatus()).isEqualTo(200);
	}
}
