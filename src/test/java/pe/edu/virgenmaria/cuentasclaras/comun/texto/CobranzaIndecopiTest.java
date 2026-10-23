package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correcciones del sprint 9 (INDECOPI y QA-S9-5) con la decisión del usuario de DOS niveles: la cobranza clara se rechaza
 * (deuda, mensualidad, pensión, mora, «S/» con un monto en cifras o en letras, condicionar un examen, obligaciones
 * económicas, pagos, Yape o Plin); las palabras ambiguas (cancelar, «al día con», abono, saldo, regularizar, aporte) no se
 * rechazan: mandan el comunicado a aprobación.
 */
class CobranzaIndecopiTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"los alumnos que no estén al día con la mensualidad no podrán rendir el examen bimestral",
			"Recuerden la mensualidad de noviembre.", "Cumplan sus obligaciones económicas.",
			"Sus compromisos economicos vencen.", "No podrá rendir el examen.", "no podrán dar examen",
			"Traigan S/ 300.", "Traigan S/ trescientos.", "Son S/. cien.", "s/ veintiocho por alumno", "S/ mil quinientos",
			"Tiene intereses de mora.", "Está en mora.", "Pago por Yape 987654321" })
	void debeRechazarLaCobranzaClara(String texto) {
		assertThat(ContenidoSinCobranza.hablaDeCobranza(texto)).as(texto).isTrue();
		assertThat(ContenidoSinCobranza.motivo(texto)).as(texto).isPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Debemos cancelar la reunión del viernes.", "Estén al día con las tareas.",
			"Traigan abono para el biohuerto.", "Revisen el saldo de horas del taller.", "Regularicen la inscripción.",
			"Deben regularizar la matrícula del club.", "Se cancela la salida por lluvia: cancelación confirmada.",
			"El aporte de cada sección para el paseo." })
	void lasAmbiguasNoSeRechazanPeroVanAAprobacion(String texto) {
		assertThat(ContenidoSinCobranza.hablaDeCobranza(texto)).as(texto).isFalse();
		assertThat(ContenidoSinCobranza.ambigua(texto)).as(texto).isPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Av. Lima s/n, Miraflores", "Reunión de padres/madres", "Calle Los Olivos s/n",
			"Los alumnos rendirán el examen bimestral el lunes.", "Saldaña, Abanto y Abondano ganaron el concurso.",
			"La familia Mora ganó el concurso.", "El Día del Logro es a las 9:30." })
	void loQueNoEsCobranzaNiAmbiguoSaleDirecto(String texto) {
		assertThat(ContenidoSinCobranza.hablaDeCobranza(texto)).as(texto).isFalse();
		assertThat(ContenidoSinCobranza.ambigua(texto)).as(texto).isEmpty();
	}
}
