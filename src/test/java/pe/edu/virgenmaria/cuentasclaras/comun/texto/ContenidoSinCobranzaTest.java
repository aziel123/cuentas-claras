package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tandas 2 y 3 (secciones 3.13 y 7.3): la regla de cobranza de los avisos académicos y de los comunicados es
 * UNA sola: la de Java es la misma expresión que la de los REGEXP_LIKE de trg_mensaje_nace (sobre los parámetros sin el
 * nombre del colegio, B6) y de trg_comunicado_nace (título y texto). Nombres como «Pagán» o «Vencesla» no coinciden. B7:
 * «padres/madres» y «7.45 a.m.» pasan; «S/ 350», «s/.350» y «350.00» no.
 */
class ContenidoSinCobranzaTest {

	@Test
	void esLaMismaExpresionQueLaDeLosTriggers() throws IOException {
		String script = Files.readString(Path.of("scripts/mysql/03-triggers.sql"));
		// Los literales de la regla de cobranza (empiezan con la de «S/», B7) seguidos de la bandera 'i'.
		Matcher m = Pattern.compile("'(\\(\\^\\|\\[\\^a-z\\]\\)s/[^']+)',\\s*'i'\\)").matcher(script);
		List<String> encontradas = new ArrayList<>();
		while (m.find()) {
			// En el literal de MySQL la barra va duplicada ('\\b' es \b para ICU).
			encontradas.add(m.group(1).replace("\\\\", "\\"));
		}
		assertThat(encontradas).as("los REGEXP_LIKE de trg_mensaje_nace y trg_comunicado_nace").hasSize(2)
				.allMatch(ContenidoSinCobranza.EXPRESION::equals);
	}

	/**
	 * Decisión del usuario (dos niveles): la regla AMBIGUA de trg_comunicado_nace es la misma de Java, y cc_texto_plano usa
	 * la misma tabla de letras parecidas que FiltroContenido.
	 */
	@Test
	void laReglaAmbiguaYLasLetrasParecidasSonLasDeLaBase() throws IOException {
		String script = Files.readString(Path.of("scripts/mysql/03-triggers.sql"));
		Matcher m = Pattern.compile("'(\\\\\\\\bcancel\\([^']+)',\\s*'i'\\)").matcher(script);
		List<String> encontradas = new ArrayList<>();
		while (m.find()) {
			encontradas.add(m.group(1).replace("\\\\", "\\"));
		}
		assertThat(encontradas).as("el REGEXP_LIKE ambiguo de trg_comunicado_nace").hasSize(1)
				.allMatch(ContenidoSinCobranza.EXPRESION_AMBIGUA::equals);
		assertThat(script).contains("DEFAULT '" + pe.edu.virgenmaria.cuentasclaras.comunicados.service.FiltroContenido
				.ORIGEN_PARECIDAS).contains("DEFAULT '" + pe.edu.virgenmaria.cuentasclaras.comunicados.service
						.FiltroContenido.DESTINO_PARECIDAS);
	}

	@ParameterizedTest
	@ValueSource(strings = { "Mateo: falta. Debe S/ 350.00", "su deuda", "la pensión de marzo", "Pensión", "3 cuotas",
			"pago pendiente", "PAGAR hoy", "es moroso", "cobranza", "cuota vencida", "por Yape", "plin", "US$ 20",
			"350.00", "1,50 soles", "S/ 350", "s/.350", "s/350" })
	void loDeCobranzaCoincide(String texto) {
		assertThat(ContenidoSinCobranza.hablaDeCobranza(texto)).as(texto).isTrue();
		assertThat(ContenidoSinCobranza.motivo(texto)).as(texto).isPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Pagán: falta", "Vencesla: llegó tarde (08:12)", "Colegio San Martín", "martes 06/10/2026",
			"Mateo: falta. Lucía: llegó tarde (08:12)", "Prof. Ana T.", "falta justificada", "Cobo",
			"Reunión de padres/madres", "Llegó a las 7.45 a.m.", "La salida es a las 12.30 h", "a las 3.15 p.m." })
	void loAcademicoNoCoincide(String texto) {
		assertThat(ContenidoSinCobranza.hablaDeCobranza(texto)).as(texto).isFalse();
		assertThat(ContenidoSinCobranza.motivo(texto)).as(texto).isEmpty();
	}

	@Test
	void elMotivoEsClaro() {
		assertThat(ContenidoSinCobranza.motivo("Traer S/ 20")).contains("menciona un monto en soles");
		assertThat(ContenidoSinCobranza.motivo("Son 350.00")).contains("menciona un monto con decimales");
		assertThat(ContenidoSinCobranza.motivo("su deuda")).contains("menciona una deuda");
		assertThat(ContenidoSinCobranza.motivo("por Yape")).contains("menciona Yape o Plin");
	}
}
