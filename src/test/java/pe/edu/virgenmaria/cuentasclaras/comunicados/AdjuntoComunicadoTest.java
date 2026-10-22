package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.AdjuntoValidado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.AdjuntosComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.HuellaComunicado;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sprint 9, tanda 3 (E22, E26, B5, B12): el PDF de un comunicado, con la aplicación completa y el proceso hijo real. El
 * texto del PDF pasa por el filtro; se guardan los dos SHA-256; cada intento queda en la bitácora; y diez bombas seguidas
 * no dejan la aplicación sin servicio (las páginas responden en menos de 2 s y ninguna subida pasa de 10 s).
 */
class AdjuntoComunicadoTest extends PruebaComunicados {

	@Autowired
	AdjuntosComunicado adjuntos;

	@Autowired
	MockMvc mvc;

	@Test
	void elTextoDelPdfPasaPorElFiltro() {
		byte[] pdf = PdfsDePrueba.conTexto(1, "Para el paseo: depositen el aporte al Yape del comité, 987654321, antes del "
				+ "viernes por la mañana sin falta.", d -> { });
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> adjuntos.validar("paseo.pdf", pdf)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("El texto del PDF");
		assertThat(jdbc.queryForObject("SELECT valor_nuevo FROM evento_auditoria WHERE accion = 'ADJUNTO_REVISADO'",
				String.class)).isEqualTo("RECHAZADO_POR_CONTENIDO");
	}

	@Test
	void seGuardanLosDosSha256YCadaIntentoQuedaEnLaBitacora() {
		byte[] original = PdfsDePrueba.normal();
		AdjuntoValidado validado = como(EscenarioCobranza.DIRECCION, () -> adjuntos.validar("programa.pdf", original));
		assertThat(validado.sha256Original()).isEqualTo(HuellaComunicado.sha256(original));
		assertThat(validado.sha256()).isEqualTo(HuellaComunicado.sha256(validado.saneado()))
				.isNotEqualTo(validado.sha256Original());
		assertThat(validado.requiereRevision()).isFalse();
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aTodoElColegio(EmisorRol.DIRECCION,
				"Programa del Día del Logro"), validado));
		Map<String, Object> fila = jdbc.queryForMap("SELECT a.sha256, a.sha256_original, a.paginas, a.requiere_revision "
				+ "FROM comunicado c JOIN comunicado_adjunto a ON a.id = c.adjunto_id WHERE c.id = ?", id);
		assertThat(fila.get("sha256")).isEqualTo(validado.sha256());
		assertThat(fila.get("sha256_original")).isEqualTo(validado.sha256Original());
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> adjuntos.validar("js.pdf",
				PdfsDePrueba.conJavaScript()))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("El PDF no se aceptó");
		assertThat(jdbc.queryForList("SELECT valor_nuevo FROM evento_auditoria WHERE accion = 'ADJUNTO_REVISADO' ORDER BY "
				+ "secuencia", String.class)).containsExactly("ACEPTADO", "RECHAZADO");
		assertThat(jdbc.queryForList("SELECT detalle FROM evento_auditoria WHERE accion = 'ADJUNTO_REVISADO'",
				String.class)).noneMatch(d -> d.contains("Día del Logro"));
	}

	/** A10, B5: diez PDF bomba seguidos; la aplicación sigue respondiendo y cada subida termina a tiempo. */
	@Test
	void diezBombasSeguidasNoDejanSinServicio() throws Exception {
		List<byte[]> bombas = List.of(PdfsDePrueba.bombaDeFlate(), PdfsDePrueba.bombaDeObjetos(),
				PdfsDePrueba.recursionProfunda());
		AtomicBoolean sigue = new AtomicBoolean(true);
		AtomicLong peor = new AtomicLong();
		List<Integer> estados = new ArrayList<>();
		Thread vigia = new Thread(() -> {
			while (sigue.get()) {
				long inicio = System.nanoTime();
				try {
					int estado = mvc.perform(get("/comunicados").with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
							.andReturn().getResponse().getStatus();
					synchronized (estados) {
						estados.add(estado);
					}
				}
				catch (Exception e) {
					synchronized (estados) {
						estados.add(-1);
					}
				}
				peor.accumulateAndGet((System.nanoTime() - inicio) / 1_000_000, Math::max);
				try {
					Thread.sleep(100);
				}
				catch (InterruptedException e) {
					return;
				}
			}
		}, "vigia");
		vigia.start();
		long peorSubida = 0;
		try {
			for (int i = 0; i < 10; i++) {
				byte[] bomba = bombas.get(i % bombas.size());
				long inicio = System.nanoTime();
				assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> adjuntos.validar("bomba.pdf", bomba)))
						.isInstanceOf(ReglaNegocioException.class);
				peorSubida = Math.max(peorSubida, (System.nanoTime() - inicio) / 1_000_000);
			}
		}
		finally {
			sigue.set(false);
			vigia.join(10_000);
		}
		assertThat(peorSubida).as("ninguna subida pasa de 10 s (ms)").isLessThan(10_000);
		assertThat(peor.get()).as("las páginas responden en menos de 2 s (ms)").isLessThan(2_000);
		synchronized (estados) {
			assertThat(estados).isNotEmpty().allMatch(e -> e == 200);
		}
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'ADJUNTO_REVISADO' AND valor_nuevo = "
				+ "'RECHAZADO'")).isEqualTo(10);
		// La undécima de la misma persona en la hora ya no se revisa (B12).
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> adjuntos.validar("otra.pdf",
				PdfsDePrueba.normal()))).hasMessageContaining("10 PDF en la última hora");
	}
}
