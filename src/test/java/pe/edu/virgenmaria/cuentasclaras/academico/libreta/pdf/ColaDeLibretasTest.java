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
			// Llena los 2 hilos y la cola de 8 hasta que el generador rechace. En la suite completa puede quedar una tarea de
			// otra prueba en curso: se espera a que los 2 hilos corran tareas de esta prueba (bloqueadas) y se vuelve a llenar
			// la cola, que desde ahí solo cambia con lo que esta prueba agrega.
			Runnable bloqueada = () -> {
				ocupados.countDown();
				try {
					soltar.await(60, TimeUnit.SECONDS);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			};
			assertThat(llenar(bloqueada)).as("la cola se llenó").isTrue();
			assertThat(ocupados.await(30, TimeUnit.SECONDS)).isTrue();
			assertThat(llenar(bloqueada)).as("la cola sigue llena").isTrue();
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

	/** Agrega tareas hasta que el generador rechace. @return si rechazó (la cola quedó llena) */
	private boolean llenar(Runnable tarea) {
		for (int i = 0; i < 20; i++) {
			try {
				generador.ocupar(tarea);
			}
			catch (GeneradorLibretaPdf.ColaLlenaException e) {
				return true;
			}
		}
		return false;
	}
}
