package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.Comunicado;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 9, tanda 3 (sección 3.8, B11): las familias con al menos un alumno con matrícula ACTIVA en el alcance de un
 * comunicado, con cuántos hijos tiene allí (la misma regla de {@code trg_comunicado_destinatario_nace}). Sin datos de
 * cobranza: el mismo comunicado le llega a una familia con deuda y a una al día (INDECOPI, E39).
 */
@Component
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class AlcanceDeFamilias {

	/** Un alcance: el tipo y, según el tipo, su nivel, grado o sección. */
	public record Alcance(AlcanceComunicado tipo, Nivel nivel, Grado grado, Long seccionId) {

		public static Alcance de(Comunicado c) {
			return new Alcance(c.getAlcance(), c.getNivel(), c.getGrado(), c.getSeccionId());
		}

		boolean entra(Seccion s) {
			return switch (tipo) {
				case COLEGIO -> true;
				case NIVEL -> s.getGrado().nivel() == nivel;
				case GRADO -> s.getGrado() == grado;
				case SECCION -> s.getId().equals(seccionId);
			};
		}
	}

	private final MatriculaRepository matriculas;

	public AlcanceDeFamilias(MatriculaRepository matriculas) {
		this.matriculas = matriculas;
	}

	/** Familia → cantidad de hijos en el alcance (en el orden de las matrículas). */
	public Map<Long, Integer> familias(Long anioId, Alcance alcance) {
		Map<Long, Integer> familias = new LinkedHashMap<>();
		for (Matricula m : matriculas(anioId, alcance)) {
			familias.merge(m.getAlumno().getFamilia().getId(), 1, Integer::sum);
		}
		return familias;
	}

	/** Las matrículas ACTIVAS del alcance (también para la lista de pendientes con el nombre del alumno). */
	public List<Matricula> matriculas(Long anioId, Alcance alcance) {
		List<Matricula> resultado = new ArrayList<>();
		for (Matricula m : matriculas.findByAnioEscolarIdAndEstado(anioId, EstadoMatricula.ACTIVA)) {
			if (alcance.entra(m.getSeccion())) {
				resultado.add(m);
			}
		}
		return resultado;
	}
}
