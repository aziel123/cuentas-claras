package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correcciones del sprint 9 (auditoría del código, 2, INDECOPI; y QA-S9-5): la regla de cobranza (la misma de
 * trg_mensaje_nace y trg_comunicado_nace) también para la mensualidad, «al día con», las obligaciones económicas, el
 * saldo, los abonos, regularizar, «cancelar la/su», condicionar un examen y un monto en letras después de «S/».
 */
class CobranzaIndecopiTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"los alumnos que no estén al día con la mensualidad no podrán rendir el examen bimestral",
			"Recuerden la mensualidad de noviembre.", "Deben estar al dia con el colegio.",
			"Cumplan sus obligaciones económicas.", "Sus compromisos economicos vencen.", "Tiene un saldo pendiente.",
			"Pueden abonar en secretaría.", "Hagan su abono hoy.", "Abonen lo que falta.", "Deben regularizar su situación.", "Regularicen su situación.",
			"Por favor cancelar la matrícula.", "Deben cancelar su compromiso.", "No podrá rendir el examen.",
			"no podrán dar examen", "Traigan S/ trescientos.", "Son S/. cien.", "s/ veintiocho por alumno",
			"S/ mil quinientos" })
	void debeRechazarLoQuePideINDECOPI(String texto) {
		assertThat(ContenidoSinCobranza.hablaDeCobranza(texto)).as(texto).isTrue();
		assertThat(ContenidoSinCobranza.motivo(texto)).as(texto).isPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Av. Lima s/n, Miraflores", "Reunión de padres/madres", "Calle Los Olivos s/n",
			"Los alumnos rendirán el examen bimestral el lunes.",
			"La salida se canceló por la lluvia.", "Saldaña, Abanto y Abondano ganaron el concurso.",
			"El Día del Logro es a las 9:30." })
	void noDebeRechazarLoQueNoEsCobranza(String texto) {
		assertThat(ContenidoSinCobranza.hablaDeCobranza(texto)).as(texto).isFalse();
	}

	/**
	 * Falsos positivos conocidos y aceptados (la regla es amplia a propósito): el comunicado no sale y quien lo escribe lo
	 * redacta de otra forma; queda en la bitácora con la regla.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "Traigan abono para el biohuerto.", "Estén al día con las tareas.",
			"Debemos cancelar la reunión del viernes." })
	void falsosPositivosAceptados(String texto) {
		assertThat(ContenidoSinCobranza.hablaDeCobranza(texto)).as(texto).isTrue();
	}
}
