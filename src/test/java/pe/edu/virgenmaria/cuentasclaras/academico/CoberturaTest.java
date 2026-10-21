package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.proceso.FinAsignaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 9, tanda 2 (A7, E56): la cobertura de un docente ausente tiene fecha de fin (hasta 5 días hábiles, nunca de
 * tutor); desde el día siguiente ya no da acceso y {@code sistema.academico} la termina.
 */
class CoberturaTest extends PruebaAsistencia {

	@Autowired
	private FinAsignaciones fin;

	private Long proponer(TipoAsignacion tipo, java.time.LocalDate hasta) {
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		try {
			return asignaciones.proponer(new ProponerAsignacionRequest(datos.seccion(), datos.otroDocente().getId(), tipo,
					null, "Cubre a la tutora con licencia médica", hasta));
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	void coberturaTerminaSola() {
		Long id = proponer(TipoAsignacion.AUXILIAR, EscenarioAsistencia.MARTES.plusDays(1));
		EscenarioAprobaciones.aprueba(direccion(), bandeja, jdbc, "asignacion_docente", id);
		SecurityContextHolder.clearContext();
		assertThat(como(otroDocente(), () -> asistencia.hoja(datos.seccion())).seccion()).isEqualTo("5.° Primaria A");
		// El jueves (después del miércoles «hasta») ya no da acceso, aunque siga VIGENTE.
		alas(2, 8, 0);
		assertThatThrownBy(() -> como(otroDocente(), () -> asistencia.hoja(datos.seccion())))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(fin.terminar(1L)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT estado FROM asignacion_docente WHERE id = ?", String.class, id))
				.isEqualTo("TERMINADA");
		assertThat(jdbc.queryForObject("SELECT terminada_por FROM asignacion_docente WHERE id = ?", String.class, id))
				.isEqualTo("sistema.academico");
	}

	@Test
	void coberturaDeMasDeCincoDiasHabilesSeRechaza() {
		assertThatThrownBy(() -> proponer(TipoAsignacion.AUXILIAR, EscenarioAsistencia.MARTES.plusDays(9)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("5 días hábiles");
		assertThatThrownBy(() -> proponer(TipoAsignacion.TUTOR, EscenarioAsistencia.MARTES.plusDays(1)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("tutor");
		assertThatThrownBy(() -> proponer(TipoAsignacion.DOCENTE, EscenarioAsistencia.MARTES.minusDays(1)))
				.isInstanceOf(ReglaNegocioException.class);
		// Hasta el martes siguiente (5 días hábiles) sí.
		assertThat(proponer(TipoAsignacion.DOCENTE, EscenarioAsistencia.MARTES.plusDays(7))).isPositive();
	}
}
