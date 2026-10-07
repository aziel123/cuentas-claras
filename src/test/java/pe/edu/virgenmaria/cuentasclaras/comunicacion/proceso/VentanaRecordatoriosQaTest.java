package pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.FeriadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * QA sprint 5 (tanda 3, G15): ventana horaria de los recordatorios en el despacho. Dado un recordatorio PENDIENTE,
 * cuando el despacho lo toma a cierta hora, entonces sale solo de lunes a sábado, de 08:00 a 20:00 (Lima), nunca en
 * domingo ni feriado, y si no le toca, espera al inicio de la siguiente ventana. Sin colegio en el contexto, el
 * calendario usa solo los feriados nacionales.
 */
class VentanaRecordatoriosQaTest {

	private final DespachoMensajes despacho = new DespachoMensajes(null, null, null, null, null, null, null, null, null,
			null, null, null, new CalendarioHabil(mock(FeriadoRepository.class)));

	@Test
	void soloLosRecordatoriosYLasCuotasVencidasRespetanLaVentana() {
		assertThat(DespachoMensajes.esRecordatorio(TipoMensaje.RECORDATORIO_VENCIMIENTO)).isTrue();
		assertThat(DespachoMensajes.esRecordatorio(TipoMensaje.CUOTA_VENCIDA)).isTrue();
		// Los avisos antifraude salen a cualquier hora: el padre es el auditor.
		assertThat(DespachoMensajes.esRecordatorio(TipoMensaje.PAGO_REGISTRADO)).isFalse();
		assertThat(DespachoMensajes.esRecordatorio(TipoMensaje.PAGO_ANULADO)).isFalse();
	}

	@Test
	void debeSalirElSabadoHastaLas1959YNoALas2000() {
		assertThat(despacho.enVentana(LocalDateTime.of(2026, 10, 10, 8, 0))).isTrue();
		assertThat(despacho.enVentana(LocalDateTime.of(2026, 10, 10, 19, 59, 59))).isTrue();
		assertThat(despacho.enVentana(LocalDateTime.of(2026, 10, 10, 20, 0))).isFalse();
		assertThat(despacho.enVentana(LocalDateTime.of(2026, 10, 10, 7, 59, 59))).isFalse();
	}

	@Test
	void noDebeSalirEnDomingoNiEnFeriadoNacional() {
		assertThat(despacho.enVentana(LocalDateTime.of(2026, 10, 11, 10, 0))).isFalse(); // domingo
		assertThat(despacho.enVentana(LocalDateTime.of(2026, 12, 8, 10, 0))).isFalse(); // Inmaculada Concepción
		assertThat(despacho.enVentana(LocalDateTime.of(2027, 3, 25, 10, 0))).isFalse(); // Jueves Santo 2027
	}

	@Test
	void debePosponerAlMismoDiaALas0800SiEsDeMadrugada() {
		assertThat(despacho.siguienteVentana(LocalDateTime.of(2026, 10, 7, 3, 15)))
				.isEqualTo(LocalDateTime.of(2026, 10, 7, 8, 0));
	}

	@Test
	void debePosponerDelSabadoALaNocheAlLunesALas0800() {
		assertThat(despacho.siguienteVentana(LocalDateTime.of(2026, 10, 10, 20, 30)))
				.isEqualTo(LocalDateTime.of(2026, 10, 12, 8, 0));
	}

	@Test
	void debeSaltarLosDosFeriadosSeguidosDeDiciembre() {
		// Lunes 7 de diciembre de 2026 a las 21:00: martes 8 y miércoles 9 son feriados.
		assertThat(despacho.siguienteVentana(LocalDateTime.of(2026, 12, 7, 21, 0)))
				.isEqualTo(LocalDateTime.of(2026, 12, 10, 8, 0));
	}

	@Test
	void debeSaltarJuevesYViernesSanto2027() {
		// Miércoles 24 de marzo de 2027 a las 20:15: jueves 25 y viernes 26 son feriados; el sábado 27 sí admite.
		assertThat(despacho.siguienteVentana(LocalDateTime.of(2027, 3, 24, 20, 15)))
				.isEqualTo(LocalDateTime.of(2027, 3, 27, 8, 0));
	}

	@Test
	void debeSaltarElSabadoFeriadoYElDomingo() {
		// Viernes 30 de abril de 2027, 22:00: el sábado 1 de mayo es feriado y el domingo 2 no admite mensajes.
		assertThat(despacho.siguienteVentana(LocalDateTime.of(2027, 4, 30, 22, 0)))
				.isEqualTo(LocalDateTime.of(2027, 5, 3, 8, 0));
	}
}
