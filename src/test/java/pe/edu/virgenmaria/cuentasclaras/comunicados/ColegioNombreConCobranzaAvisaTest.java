package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.AvisosComunicadosFamilias;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 3 (B6): la regla de cobranza ya no corre sobre el nombre del colegio (trg_mensaje_nace la aplica a lo
 * que va después), pero el alta y la corrección de la ficha avisan si el nombre calza; y un nombre de persona que calza
 * sale genérico en los avisos.
 */
class ColegioNombreConCobranzaAvisaTest {

	@Test
	void unNombreQueCalzaSeAvisa() {
		assertThat(ContenidoSinCobranza.advertenciaNombreColegio("Colegio Cobranza del Norte")).isPresent().get()
				.asString().contains("menciona cobros");
		assertThat(ContenidoSinCobranza.advertenciaNombreColegio("Colegio Virgen María")).isEmpty();
		assertThat(ContenidoSinCobranza.advertenciaNombreColegio("Colegio San Pagán")).isEmpty();
	}

	@Test
	void elNombreDelColegioNoSeRevisaEnLosAvisos() {
		assertThat(AvisosComunicadosFamilias.emisorCorto("Dirección del Colegio Cobranza del Norte")).isEqualTo("Dirección");
		assertThat(AvisosComunicadosFamilias.emisorCorto("Promotoría del Colegio Yape")).isEqualTo("Promotoría");
		assertThat(AvisosComunicadosFamilias.emisorCorto("Prof. Plin T., Matemática, 5.° Primaria B")).isEqualTo("su docente");
	}
}
