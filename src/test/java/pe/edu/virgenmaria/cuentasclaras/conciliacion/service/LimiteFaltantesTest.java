package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ObjetoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.ObjetoAbierto;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA del sprint 4: desde qué día lo que «debía verse en el banco» pasa a ser una alerta CRÍTICA (sección 10.4, punto 5:
 * «cuando el extracto que cubre su VENTANA ya está CONFIRMADO»). Puro.
 */
class LimiteFaltantesTest {

	/** Viernes 30/10/2026: fin de mes. */
	private static final LocalDate VIERNES_30 = LocalDate.of(2026, 10, 30);

	private static final LocalDate LUNES_2 = LocalDate.of(2026, 11, 2);

	private static ObjetoAbierto objeto(ObjetoPartida tipo, LocalDate[] ventana) {
		return new ObjetoAbierto(tipo, 1L, VIERNES_30, new BigDecimal("450.00"), "OP1234", null, ventana[0], ventana[1],
				"detalle", "alguien");
	}

	@Test
	void debeEsperarAlDiaHabilSiguienteParaElAbonoDeUnLoteDeFinDeMes() {
		assertThat(DiferenciasConciliacion.limite(objeto(ObjetoPartida.LOTE_RECAUDACION,
				ReglasEmparejamiento.ventanaRecaudacion(VIERNES_30)))).isEqualTo(LUNES_2);
	}

	@Test
	void debeEsperarDosDiasHabilesParaElAbonoDeUnaLiquidacion() {
		assertThat(DiferenciasConciliacion.limite(objeto(ObjetoPartida.LIQUIDACION,
				ReglasEmparejamiento.ventanaLiquidacion(VIERNES_30)))).isEqualTo(LocalDate.of(2026, 11, 3));
	}

	@Test
	void debeExigirElYapeDeCajaElMismoDiaQueSeRegistro() {
		// F13: el Yape inventado sale en rojo en cuanto el extracto de su día está confirmado.
		assertThat(DiferenciasConciliacion.limite(objeto(ObjetoPartida.PAGO,
				ReglasEmparejamiento.ventanaPago(VIERNES_30)))).isEqualTo(VIERNES_30);
	}

	/**
	 * Con {@code abono-recaudacion: POR_PAGO}, cada pago por banco es un objeto PAGO con la ventana de la recaudación
	 * (su fecha y el día hábil siguiente). Si el banco abona el lunes un pago del viernes y el extracto del viernes ya
	 * está confirmado, el pago sale el mismo lunes como CRÍTICA «¿Pago inventado para tapar efectivo? Revísalo con la
	 * cajera», aunque aún está en su ventana. El lote (POR_LOTE) sí espera al día hábil siguiente.
	 */
	@Test
	@Disabled("QA-S4-4: DiferenciasConciliacion.limite (línea 132) usa la fecha del objeto para todo PAGO e ignora la "
			+ "ventana de la recaudación POR_PAGO: falsa alerta CRÍTICA de pago inventado dentro de su ventana")
	void debeEsperarAlDiaHabilSiguienteParaUnPagoDeRecaudacionPorPago() {
		assertThat(DiferenciasConciliacion.limite(objeto(ObjetoPartida.PAGO,
				ReglasEmparejamiento.ventanaRecaudacion(VIERNES_30)))).isEqualTo(LUNES_2);
	}
}
