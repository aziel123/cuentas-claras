package pe.edu.virgenmaria.cuentasclaras.plataforma.fin;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 11, tanda 2 (AP2-4): 5 intentos de DNI o código anulan el enlace; cada intento fallido deja su evento (y se
 * guarda aunque se muestre el error). Un operador manda un enlace nuevo y el anterior deja de servir.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class EntregaIntentosTest extends BaseFinColegio {

	@Test
	void cincoIntentosAnulanElEnlaceYElOperadorMandaOtro() {
		long colegio = colegioEnProduccion("Colegio Intentos", EscenarioPlataforma.RUC_C, "titular.intentos", "45678951",
				"987650051");
		suspender(colegio);
		String token = token(colegio);
		entrega.confirmarDni(token, "45678951");
		for (int i = 1; i <= 4; i++) {
			assertThatThrownBy(() -> entrega.confirmarCodigo(token, "000000")).isInstanceOf(ReglaNegocioException.class)
					.hasMessageContaining("Te quedan");
		}
		assertThatThrownBy(() -> entrega.confirmarDni(token, "99999999")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("anulado");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE colegio_id = ? AND accion = "
				+ "'EXPORTACION_INTENTO_FALLIDO'", Long.class, colegio)).isEqualTo(5L);
		assertThat(exportacion(colegio)).containsEntry("intentos_fallidos", 5);
		assertThat(exportacion(colegio).get("token_hash")).isNull();
		assertThatThrownBy(() -> entrega.ver(token)).isInstanceOf(RecursoNoEncontradoException.class);

		// El operador manda un enlace nuevo: el anterior ya no sirve; el nuevo sí.
		como(ana);
		Long id = ((Number) exportacion(colegio).get("id")).longValue();
		fin.reenviarEntrega(colegio, id);
		String nuevo = token(colegio);
		assertThat(nuevo).isNotEqualTo(token);
		assertThat(entrega.ver(nuevo).colegio()).isEqualTo("Colegio Intentos");
		assertThatThrownBy(() -> entrega.ver(token)).isInstanceOf(RecursoNoEncontradoException.class);
	}
}
