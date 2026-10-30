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
 * QA del sprint 11 (AP2-4): cada entrega de la exportación tiene sus propios 5 intentos. Hallazgo QA-S11-01: tras el
 * quinto intento fallido, el «Nuevo enlace» del operador nacía con 0 intentos (el contador no bajaba), así que un solo
 * error de tipeo del titular lo volvía a anular. Los intentos del enlace anterior siguen en la bitácora.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class QaSprint11EntregaTest extends BaseFinColegio {

	private static final String DNI = "45678961";

	@Test
	void debeDarCincoIntentosNuevosAlEnlaceQueMandaElOperadorTrasLaAnulacion() {
		long colegio = anularElEnlaceConCincoIntentos("Colegio QA Intentos", EscenarioPlataforma.RUC_C, "titular.qaint",
				"987650061");
		Long exportacion = ((Number) exportacion(colegio).get("id")).longValue();

		como(ana);
		fin.reenviarEntrega(colegio, exportacion);
		String nuevo = token(colegio);

		assertThat(entrega.ver(nuevo).intentosRestantes()).isEqualTo(5);
		assertThat(exportacion(colegio)).containsEntry("intentos_fallidos", 0);
		// Un error de tipeo ya no anula el enlace nuevo: le quedan 4.
		assertThatThrownBy(() -> entrega.confirmarDni(nuevo, "45678960")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Te quedan 4");
		assertThat(entrega.ver(nuevo).intentosRestantes()).isEqualTo(4);
	}

	@Test
	void debeEntregarLaExportacionConElEnlaceNuevoTrasUnErrorDeTipeo() {
		long colegio = anularElEnlaceConCincoIntentos("Colegio QA Tipeo", EscenarioPlataforma.RUC_D, "titular.qatip",
				"987650062");
		Long exportacion = ((Number) exportacion(colegio).get("id")).longValue();
		como(ana);
		fin.reenviarEntrega(colegio, exportacion);
		String nuevo = token(colegio);

		assertThatThrownBy(() -> entrega.confirmarDni(nuevo, "4567896")).isInstanceOf(ReglaNegocioException.class);
		entrega.confirmarDni(nuevo, DNI);
		String codigo = codigo(colegio);
		assertThat(entrega.confirmarCodigo(nuevo, codigo)).isNotBlank();
		entrega.descargar(nuevo, codigo, "203.0.113.7");
		assertThat(exportacion(colegio)).containsEntry("estado", "ENTREGADA");
	}

	@Test
	void debeConservarEnLaBitacoraLosIntentosDelEnlaceAnulado() {
		long colegio = anularElEnlaceConCincoIntentos("Colegio QA Bitacora", EscenarioPlataforma.RUC_A, "titular.qabit",
				"987650063");
		Long exportacion = ((Number) exportacion(colegio).get("id")).longValue();
		como(ana);
		fin.reenviarEntrega(colegio, exportacion);
		String nuevo = token(colegio);
		assertThatThrownBy(() -> entrega.confirmarDni(nuevo, "11111111")).isInstanceOf(ReglaNegocioException.class);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE colegio_id = ? AND accion = "
				+ "'EXPORTACION_INTENTO_FALLIDO'", Long.class, colegio)).isEqualTo(6L);
	}

	@Test
	void debeAnularTambienElEnlaceNuevoAlQuintoIntentoFallido() {
		long colegio = anularElEnlaceConCincoIntentos("Colegio QA Quinto", EscenarioPlataforma.RUC_B, "titular.qaqui",
				"987650064");
		Long exportacion = ((Number) exportacion(colegio).get("id")).longValue();
		como(ana);
		fin.reenviarEntrega(colegio, exportacion);
		String nuevo = token(colegio);
		for (int i = 1; i <= 4; i++) {
			assertThatThrownBy(() -> entrega.confirmarDni(nuevo, "11111111")).hasMessageContaining("Te quedan");
		}
		assertThatThrownBy(() -> entrega.confirmarDni(nuevo, "11111111")).hasMessageContaining("anulado");
		assertThatThrownBy(() -> entrega.ver(nuevo)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(exportacion(colegio)).containsEntry("intentos_fallidos", 5);
	}

	/** IDOR: el operador no reenvía (ni anula) la entrega de otro colegio cambiando el id de la exportación en la ruta. */
	@Test
	void debeRechazarElReenvioDeLaExportacionDeOtroColegio() {
		long a = colegioEnProduccion("Colegio QA Uno", EscenarioPlataforma.RUC_A, "titular.qauno", DNI, "987650065");
		long b = colegioEnProduccion("Colegio QA Dos", EscenarioPlataforma.RUC_B, "titular.qados", "45678962",
				"987650066");
		suspender(b);
		String tokenB = token(b);
		Long exportacionB = ((Number) exportacion(b).get("id")).longValue();
		long enlacesB = jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND tipo = "
				+ "'EXPORTACION_ENLACE'", Long.class, b);

		como(ana);
		assertThatThrownBy(() -> fin.reenviarEntrega(a, exportacionB)).isInstanceOf(RuntimeException.class);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'EXPORTACION_ENLACE' AND colegio_id IN "
				+ "(?, ?)", Long.class, a, b)).isEqualTo(enlacesB);
		assertThat(entrega.ver(tokenB).colegio()).isEqualTo("Colegio QA Dos");
	}

	/** Un colegio suspendido cuyo primer enlace quedó anulado con 5 DNI equivocados. */
	private long anularElEnlaceConCincoIntentos(String nombre, String ruc, String titular, String celular) {
		long colegio = colegioEnProduccion(nombre, ruc, titular, DNI, celular);
		suspender(colegio);
		String token = token(colegio);
		for (int i = 1; i <= 4; i++) {
			assertThatThrownBy(() -> entrega.confirmarDni(token, "99999999")).hasMessageContaining("Te quedan");
		}
		assertThatThrownBy(() -> entrega.confirmarDni(token, "99999999")).hasMessageContaining("anulado");
		assertThat(exportacion(colegio)).containsEntry("intentos_fallidos", 5);
		return colegio;
	}
}
