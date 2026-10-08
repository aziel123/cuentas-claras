package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ConteoMatriculas;

import java.util.HashMap;
import java.util.Map;

/** Implementa el puerto de {@code colegio} con las matrículas activas (filtradas por colegio con {@code @TenantId}). */
@Component
public class ConteoMatriculasJpa implements ConteoMatriculas {

	private final MatriculaRepository matriculas;

	public ConteoMatriculasJpa(MatriculaRepository matriculas) {
		this.matriculas = matriculas;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, Long> porSeccion(Long anioEscolarId) {
		Map<Long, Long> conteo = new HashMap<>();
		for (Object[] fila : matriculas.contarActivasPorSeccion(anioEscolarId)) {
			conteo.put((Long) fila[0], ((Number) fila[1]).longValue());
		}
		return conteo;
	}
}
