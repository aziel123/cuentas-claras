package pe.edu.virgenmaria.cuentasclaras.academico.notas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.AccesosDatosPersonales;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 10, tanda 2 (M8, riesgo 5): el texto de la conclusión propuesta vive en {@code borrador_conclusion}. Lo ven quien lo
 * escribió (al pedir) y quien aprueba (en la bandeja, con su registro en «Quién vio datos personales»); otro docente, no.
 */
class BorradorConclusionTest extends PruebaNotas {

	private static final String TEXTO = "Requiere apoyo con ajustes razonables en la lectura de enunciados largos.";

	@Autowired
	private AccesosDatosPersonales accesos;

	@Test
	void soloLoVenQuienLoEscribeYQuienAprueba() {
		anaPoneA();
		cerrarPeriodo();
		Long nota = vigente(mateo, competencias("MAT").get(0));
		Long solicitud = como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedir(nota, "B", TEXTO,
				MOTIVO_CORRECCION));

		SolicitudVista vista = como(EscenarioCobranza.PROMOTORIA, () -> bandeja.detalle(solicitud));
		assertThat(vista.detalle()).contains("Conclusión propuesta: " + TEXTO);
		assertThat(vista.resumen()).doesNotContain(TEXTO);
		int registradas = como(EscenarioCobranza.PROMOTORIA, () -> accesos.registrar(TipoAcceso.APROBACION_CONTACTO,
				new AccesoMostrado(Set.of(), null, 0, solicitud), "127.0.0.1"));
		assertThat(registradas).isLessThanOrEqualTo(1);

		assertThatThrownBy(() -> como(UsuariosDePrueba.autenticado(luis), () -> correcciones.formulario(nota)))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(como(UsuariosDePrueba.autenticado(ana), () -> correcciones.formulario(nota)).nivelActual())
				.isEqualTo("A");
	}
}
