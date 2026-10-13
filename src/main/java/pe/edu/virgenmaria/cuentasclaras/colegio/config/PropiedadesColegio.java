package pe.edu.virgenmaria.cuentasclaras.colegio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.LogoColegio;

/**
 * {@code cuentasclaras.colegio}: topes del logo del colegio (sprint 8, tanda 1, sección 3.7). El lado máximo nunca pasa
 * de {@value LogoColegio#MAX_LADO} px (lo exige la base) ni la subida de 1 MB. Un valor inválido impide arrancar.
 *
 * @param logoMaxSubida tamaño máximo del archivo que se sube (PNG o JPEG)
 * @param logoMaxLado lado máximo, en píxeles, al que se reescala el logo
 */
@ConfigurationProperties("cuentasclaras.colegio")
public record PropiedadesColegio(@DefaultValue("1MB") DataSize logoMaxSubida, @DefaultValue("400") int logoMaxLado) {

	public static final long MAX_SUBIDA = 1_048_576L;

	public PropiedadesColegio {
		if (logoMaxSubida == null || logoMaxSubida.toBytes() <= 0 || logoMaxSubida.toBytes() > MAX_SUBIDA) {
			throw new IllegalArgumentException("cuentasclaras.colegio.logo-max-subida: entre 1 byte y 1MB");
		}
		if (logoMaxLado < 16 || logoMaxLado > LogoColegio.MAX_LADO) {
			throw new IllegalArgumentException("cuentasclaras.colegio.logo-max-lado: entre 16 y " + LogoColegio.MAX_LADO);
		}
	}
}
