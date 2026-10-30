package pe.edu.virgenmaria.cuentasclaras.plataforma.fin;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.FinDeColegio;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correcciones del sprint 11: QA-S11-02 (3 códigos de WhatsApp por hora y por exportación) y B1 (los intentos de cada
 * enlace vuelven a 0 con un enlace nuevo, pero el total de la exportación solo sube y a los 15 ya no se manda otro enlace).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CorreccionesSprint11EntregaTest extends BaseFinColegio {

	@Test
	void tresCodigosPorHoraComoMaximo() {
		long colegio = colegioEnProduccion("Colegio Tope", EscenarioPlataforma.RUC_A, "titular.tope", "45678931",
				"987650131");
		suspender(colegio);
		String token = token(colegio);
		for (int i = 0; i < 3; i++) {
			entrega.confirmarDni(token, "45678931");
		}
		assertThatThrownBy(() -> entrega.confirmarDni(token, "45678931")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("3 códigos");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND tipo = 'CODIGO_EXPORTACION'",
				Long.class, colegio)).isEqualTo(3L);
		// Pasada la hora, sale otro.
		reloj.mover(Duration.ofMinutes(61));
		entrega.confirmarDni(token, "45678931");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND tipo = 'CODIGO_EXPORTACION'",
				Long.class, colegio)).isEqualTo(4L);
	}

	@Test
	void elTotalDeIntentosSoloSubeYTopaLosEnlacesNuevos() {
		long colegio = colegioEnProduccion("Colegio Totales", EscenarioPlataforma.RUC_B, "titular.totales", "45678932",
				"987650132");
		suspender(colegio);
		Long id = ((Number) exportacion(colegio).get("id")).longValue();
		for (int ronda = 1; ronda <= 3; ronda++) {
			String token = token(colegio);
			for (int i = 0; i < 5; i++) {
				assertThatThrownBy(() -> entrega.confirmarDni(token, "11111111")).isInstanceOf(ReglaNegocioException.class);
			}
			assertThat(exportacion(colegio)).containsEntry("intentos_fallidos", 5).containsEntry("intentos_totales",
					5 * ronda);
			como(ana);
			if (ronda < 3) {
				fin.reenviarEntrega(colegio, id);
				// QA-S11-01: el enlace nuevo nace con sus 5 intentos; el total no baja.
				assertThat(exportacion(colegio)).containsEntry("intentos_fallidos", 0).containsEntry("intentos_totales",
						5 * ronda);
			}
		}
		assertThat(FinDeColegio.MAX_INTENTOS_TOTALES).isEqualTo(15);
		assertThatThrownBy(() -> fin.reenviarEntrega(colegio, id)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Pide otra exportación");
	}
}
