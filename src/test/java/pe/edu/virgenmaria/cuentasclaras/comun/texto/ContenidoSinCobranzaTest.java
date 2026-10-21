package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 2 (secciones 3.13 y 7.3): la regla de cobranza de los avisos académicos es UNA sola: la de Java es la
 * misma expresión que la del REGEXP_LIKE de trg_mensaje_nace. Nombres como «Pagán» o «Vencesla» no coinciden.
 */
class ContenidoSinCobranzaTest {

	@Test
	void esLaMismaExpresionQueLaDelTrigger() throws IOException {
		String script = Files.readString(Path.of("scripts/mysql/03-triggers.sql"));
		Matcher m = Pattern.compile("REGEXP_LIKE\\(NEW\\.parametros,\\s*'([^']+)',\\s*'i'\\)").matcher(script);
		assertThat(m.find()).as("el REGEXP_LIKE de trg_mensaje_nace").isTrue();
		// En el literal de MySQL la barra va duplicada ('\\b' es \b para ICU).
		assertThat(m.group(1).replace("\\\\", "\\")).isEqualTo(ContenidoSinCobranza.EXPRESION);
	}

	@ParameterizedTest
	@ValueSource(strings = { "Mateo: falta. Debe S/ 350.00", "su deuda", "la pensión de marzo", "Pensión", "3 cuotas",
			"pago pendiente", "PAGAR hoy", "es moroso", "cobranza", "cuota vencida", "por Yape", "plin", "US$ 20",
			"350.00", "1,50 soles" })
	void loDeCobranzaCoincide(String texto) {
		assertThat(ContenidoSinCobranza.hablaDeCobranza(texto)).as(texto).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Pagán: falta", "Vencesla: llegó tarde (08:12)", "Colegio San Martín", "martes 06/10/2026",
			"Mateo: falta. Lucía: llegó tarde (08:12)", "Prof. Ana T.", "falta justificada", "Cobo" })
	void loAcademicoNoCoincide(String texto) {
		assertThat(ContenidoSinCobranza.hablaDeCobranza(texto)).as(texto).isFalse();
	}
}
