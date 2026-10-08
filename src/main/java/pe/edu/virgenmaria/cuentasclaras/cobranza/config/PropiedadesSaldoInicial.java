package pe.edu.virgenmaria.cuentasclaras.cobranza.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.LoteSaldoInicial;

import java.util.List;

/**
 * {@code cuentasclaras.saldo-inicial.conceptos-otros}: los únicos «otros conceptos» que el reglamento permite cargar
 * como saldo inicial. Ninguno puede parecer una pensión, una matrícula o un mes (la aplicación no arranca).
 */
@ConfigurationProperties("cuentasclaras.saldo-inicial")
public record PropiedadesSaldoInicial(
		@DefaultValue({ "Taller de verano", "Uniforme escolar", "Materiales educativos", "Excursión" })
		List<String> conceptosOtros) {

	public PropiedadesSaldoInicial {
		if (conceptosOtros == null || conceptosOtros.isEmpty() || conceptosOtros.size() > 20) {
			throw new IllegalArgumentException("cuentasclaras.saldo-inicial.conceptos-otros: de 1 a 20 conceptos");
		}
		for (String concepto : conceptosOtros) {
			if (concepto == null || concepto.isBlank() || concepto.length() > 80 || LoteSaldoInicial.pareceCuota(concepto)) {
				throw new IllegalArgumentException("cuentasclaras.saldo-inicial.conceptos-otros: «" + concepto
						+ "» no es válido (vacío, largo o parece una pensión o matrícula)");
			}
		}
		conceptosOtros = List.copyOf(conceptosOtros);
	}
}
