package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicados.PdfsDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoAdjuntoRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 9, tanda 3 (A10, B5, B12): UN análisis a la vez, cola de 4 y, como máximo, 1 tarea por persona y 2 por colegio
 * en cola o en curso. Llena, «intente en unos minutos» sin lanzar el hijo. Y el límite de subidas por hora se cuenta en
 * la bitácora (no se reinicia con la aplicación). El lanzador se reemplaza por uno que espera, para controlar la cola.
 */
class ColaDelAnalizadorTest {

	private final LanzadorAnalizador lanzador = mock(LanzadorAnalizador.class);

	private final AuditoriaService auditoria = mock(AuditoriaService.class);

	private final PdfEjemplo ejemplo = mock(PdfEjemplo.class);

	private final CountDownLatch suelta = new CountDownLatch(1);

	private final AtomicInteger lanzados = new AtomicInteger();

	private final ExecutorService hilos = Executors.newFixedThreadPool(10);

	private AdjuntosComunicado adjuntos;

	private AdjuntosComunicado preparar() {
		when(lanzador.analizar(any(), any())).thenAnswer(i -> {
			lanzados.incrementAndGet();
			suelta.await(30, TimeUnit.SECONDS);
			Properties p = new Properties();
			p.setProperty("estado", "RECHAZADO");
			p.setProperty("motivo", "prueba");
			return new LanzadorAnalizador.Salida(2, p, null, "", false);
		});
		adjuntos = new AdjuntosComunicado(lanzador, new FiltroContenido("https://cuentas.colegio.pe", ""), auditoria,
				mock(ComunicadoAdjuntoRepository.class), ejemplo,
				Clock.fixed(Instant.parse("2026-10-06T13:00:00Z"), ZoneId.of("America/Lima")));
		return adjuntos;
	}

	@AfterEach
	void soltar() {
		suelta.countDown();
		hilos.shutdownNow();
		if (adjuntos != null) {
			adjuntos.cerrar();
		}
		SecurityContextHolder.clearContext();
	}

	private static UsuarioAutenticado persona(long id, long colegio) {
		return new UsuarioAutenticado(id, colegio, "persona" + id, "Persona " + id, null, true, false, false,
				EnumSet.of(Rol.DIRECTOR), null);
	}

	private CompletableFuture<String> subir(UsuarioAutenticado quien) {
		return CompletableFuture.supplyAsync(() -> {
			UsuariosDePrueba.iniciarSesion(quien);
			try {
				adjuntos.validar("comunicado.pdf", PdfsDePrueba.normal());
				return "aceptado";
			}
			catch (ReglaNegocioException e) {
				return e.getMessage();
			}
			finally {
				SecurityContextHolder.clearContext();
			}
		}, hilos);
	}

	private void esperarLanzados(int cuantos) throws InterruptedException {
		for (int i = 0; i < 100 && lanzados.get() < cuantos; i++) {
			Thread.sleep(50);
		}
	}

	@Test
	void colaLlenaPideIntentarMasTarde() throws Exception {
		preparar();
		List<CompletableFuture<String>> subidas = new ArrayList<>();
		// Una en curso y cuatro en cola, de cinco colegios distintos.
		for (int i = 1; i <= 5; i++) {
			subidas.add(subir(persona(i, i)));
			if (i == 1) {
				esperarLanzados(1);
			}
		}
		Thread.sleep(300);
		String sexta = subir(persona(6, 6)).get(10, TimeUnit.SECONDS);
		assertThat(sexta).isEqualTo(AdjuntosComunicado.COLA_LLENA);
		assertThat(lanzados.get()).as("solo uno a la vez").isEqualTo(1);
		suelta.countDown();
		for (CompletableFuture<String> s : subidas) {
			assertThat(s.get(30, TimeUnit.SECONDS)).startsWith("El PDF no se aceptó");
		}
	}

	@Test
	void unaTareaPorUsuarioYDosPorColegio() throws Exception {
		preparar();
		UsuarioAutenticado ana = persona(1, 1);
		CompletableFuture<String> primera = subir(ana);
		esperarLanzados(1);
		assertThat(subir(ana).get(10, TimeUnit.SECONDS)).contains("Ya estamos revisando un PDF tuyo");
		CompletableFuture<String> otraDelColegio = subir(persona(2, 1));
		Thread.sleep(300);
		assertThat(subir(persona(3, 1)).get(10, TimeUnit.SECONDS)).as("tercera del mismo colegio")
				.isEqualTo(AdjuntosComunicado.COLA_LLENA);
		assertThat(subir(persona(4, 2)).isDone()).as("otro colegio sí entra a la cola").isFalse();
		suelta.countDown();
		assertThat(primera.get(30, TimeUnit.SECONDS)).startsWith("El PDF no se aceptó");
		assertThat(otraDelColegio.get(30, TimeUnit.SECONDS)).startsWith("El PDF no se aceptó");
	}

	/** B12: más de 10 por persona o 30 por colegio en la última hora (contados en la bitácora): sin lanzar el hijo. */
	@Test
	void limiteDeSubidasDesdeLaBitacora() {
		preparar();
		UsuariosDePrueba.iniciarSesion(persona(1, 1));
		when(auditoria.contarDesdeDelUsuarioActual(any(), any())).thenReturn(10L);
		assertThatThrownBy(() -> adjuntos.validar("a.pdf", PdfsDePrueba.normal())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("10 PDF en la última hora");
		when(auditoria.contarDesdeDelUsuarioActual(any(), any())).thenReturn(3L);
		when(auditoria.contarDesde(any(), any())).thenReturn(30L);
		assertThatThrownBy(() -> adjuntos.validar("a.pdf", PdfsDePrueba.normal())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("30 PDF en la última hora");
		verify(lanzador, never()).analizar(any(), any());
	}

	@Test
	void antesDeLanzarRevisaTamanoYExtension() {
		preparar();
		UsuariosDePrueba.iniciarSesion(persona(1, 1));
		assertThatThrownBy(() -> adjuntos.validar("a.pdf", PdfsDePrueba.enorme())).hasMessageContaining("2 MB");
		assertThatThrownBy(() -> adjuntos.validar("a.docx", PdfsDePrueba.normal())).hasMessageContaining(".pdf");
		when(ejemplo.soloElDeEjemplo()).thenReturn(true);
		assertThatThrownBy(() -> adjuntos.validar("a.pdf", PdfsDePrueba.normal())).hasMessageContaining("PDF de ejemplo");
		verify(lanzador, never()).analizar(any(), any());
	}
}
