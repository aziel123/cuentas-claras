package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.academico.api.AsignacionesPorCompletar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;

/**
 * Sprint 10, tanda 2: el número de asignaciones sin áreas del catálogo del año en curso del colegio en contexto (solo un
 * número: sin nombres ni alumnos), para la lista de puesta en marcha.
 */
@Component
class ConteoAsignacionesPorCompletar implements AsignacionesPorCompletar {

	private final AnioEscolarRepository anios;

	private final CalculoAvance calculo;

	ConteoAsignacionesPorCompletar(AnioEscolarRepository anios, CalculoAvance calculo) {
		this.anios = anios;
		this.calculo = calculo;
	}

	@Override
	public int sinAreasDelAnioEnCurso() {
		return anios.findByEstado(EstadoAnioEscolar.EN_CURSO).map(a -> calculo.sinAreas(a.getId()).size()).orElse(0);
	}
}
