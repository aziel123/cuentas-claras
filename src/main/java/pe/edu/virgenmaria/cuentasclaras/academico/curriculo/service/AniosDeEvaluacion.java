package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service;

import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.OpcionAnio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** El año que muestran las pantallas de evaluación y periodos: el pedido o, sin pedirlo, el en curso o el último abierto. */
final class AniosDeEvaluacion {

	private AniosDeEvaluacion() {
	}

	static List<OpcionAnio> opciones(List<AnioEscolar> todos) {
		return todos.stream().map(a -> new OpcionAnio(a.getId(), a.getAnio() + " · "
				+ a.getEstado().name().replace('_', ' ').toLowerCase(Locale.ROOT))).toList();
	}

	/** @throws RecursoNoEncontradoException si se pidió un año que no es del colegio (404, como cualquier IDOR) */
	static Optional<AnioEscolar> elegir(List<AnioEscolar> todos, Long anioId) {
		if (anioId != null) {
			return Optional.of(todos.stream().filter(a -> a.getId().equals(anioId)).findFirst()
					.orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado")));
		}
		return todos.stream().filter(a -> a.getEstado() == EstadoAnioEscolar.EN_CURSO).findFirst()
				.or(() -> todos.stream().filter(a -> !a.cerrado()).findFirst())
				.or(() -> todos.stream().findFirst());
	}
}
