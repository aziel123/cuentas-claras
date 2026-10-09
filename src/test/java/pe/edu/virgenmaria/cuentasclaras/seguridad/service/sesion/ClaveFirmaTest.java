package pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 7, tanda 2 (sección 3.4): la clave que firma la aplicación es la que arma el trigger con CONCAT. Si una de las
 * dos cambia, en MySQL ninguna aprobación de esa tabla pasaría: esta prueba lo detecta sin MySQL.
 */
class ClaveFirmaTest {

	/** El primer literal de cada CONCAT que 03-triggers.sql pasa a cc_firma_valida (también dentro de un CASE). */
	private static final Pattern PREFIJO = Pattern.compile("CONCAT\\('([a-z_]+:(?:[A-Z]+:)?)'");

	private static Set<String> prefijosDeLosTriggers() throws IOException {
		String script = Files.readString(Path.of("scripts/mysql/03-triggers.sql"), StandardCharsets.UTF_8);
		Set<String> prefijos = new TreeSet<>();
		int desde = 0;
		while ((desde = script.indexOf("cc_firma_valida(", desde)) >= 0) {
			int fin = script.indexOf("SIGNAL", desde);
			Matcher m = PREFIJO.matcher(script.substring(desde, fin < 0 ? script.length() : fin));
			while (m.find()) {
				prefijos.add(m.group(1));
			}
			desde++;
		}
		return prefijos;
	}

	@Test
	void lasClavesDeLaAplicacionSonLasDeLosTriggers() throws IOException {
		LocalDateTime momento = LocalDateTime.of(2026, 10, 5, 9, 30, 15, 123_456_789);
		Set<String> deLaAplicacion = new TreeSet<>();
		Stream.of(ClaveFirma.solicitud(1L, EstadoSolicitud.APROBADA), ClaveFirma.descuento(1L, EstadoSolicitud.APROBADA),
				ClaveFirma.cierreCaja(1L, EstadoSolicitud.APROBADA), ClaveFirma.planPension(1L),
				ClaveFirma.loteSaldoInicialConfirmado(1L), ClaveFirma.loteSaldoInicialDevuelto(1L, momento),
				ClaveFirma.extracto(1L, EstadoSolicitud.APROBADA), ClaveFirma.loteRecaudacion(1L, EstadoSolicitud.APROBADA),
				ClaveFirma.partida(1L, EstadoSolicitud.APROBADA), ClaveFirma.cierreMensual(1L, 1), ClaveFirma.feriado(1L),
				ClaveFirma.avisoFamilia(1L), ClaveFirma.renovacion(1L, EstadoSolicitud.APROBADA),
				ClaveFirma.verificacionPago(1L, 1), ClaveFirma.verificacionDeposito(1L, 1),
				ClaveFirma.llamadaControl(LocalDate.of(2026, 10, 5), 1L, 1), ClaveFirma.delegacionLlamada(LocalDate.of(2026, 10, 5)))
				.forEach(clave -> {
					// El CHECK de V25 (firma_operacion.clave) y el largo de la columna.
					assertThat(clave).matches("^[a-z_]+:[A-Za-z0-9:_-]+$").hasSizeLessThanOrEqualTo(120);
					Matcher m = Pattern.compile("^([a-z_]+:(?:PAGO:|DEPOSITO:)?)").matcher(clave);
					assertThat(m.find()).isTrue();
					deLaAplicacion.add(m.group(1));
				});

		assertThat(deLaAplicacion).isEqualTo(prefijosDeLosTriggers());
	}

	/** DATE_FORMAT(devuelto_en, '%Y%m%d%H%i%s%f'): al microsegundo, como DATETIME(6). */
	@Test
	void laDevolucionLlevaElMomentoAlMicrosegundo() {
		assertThat(ClaveFirma.loteSaldoInicialDevuelto(7L, LocalDateTime.of(2026, 10, 5, 9, 3, 5, 120_456_789)))
				.isEqualTo("lote_saldo_inicial:7:DEVUELTO:20261005090305120456");
		assertThat(ClaveFirma.llamadaControl(LocalDate.of(2026, 10, 5), 3L, 2)).isEqualTo("llamada_control:2026-10-05:3:2");
	}

	@Test
	void sinIdNoHayClave() {
		assertThatThrownBy(() -> ClaveFirma.planPension(null)).isInstanceOf(NullPointerException.class);
	}
}
