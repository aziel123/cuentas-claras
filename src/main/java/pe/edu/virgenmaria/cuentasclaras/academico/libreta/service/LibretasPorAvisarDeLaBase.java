package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.api.LibretasPorAvisar;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.LibretaEmitidaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 3: las libretas por avisar ({@link LibretasPorAvisar}) de las emitidas desde una hora, por familia y
 * periodo. La vigente de cada hijo decide si el aviso es «disponible» o «corregida»; la clave del aviso es la libreta más
 * nueva de la familia en el periodo (una versión nueva, un aviso nuevo).
 */
@Service
@PreAuthorize("hasRole('SISTEMA_LIBRETAS')")
public class LibretasPorAvisarDeLaBase implements LibretasPorAvisar {

	private final LibretaEmitidaRepository libretas;

	private final AlumnoRepository alumnos;

	private final PeriodoEvaluacionRepository periodos;

	public LibretasPorAvisarDeLaBase(LibretaEmitidaRepository libretas, AlumnoRepository alumnos,
			PeriodoEvaluacionRepository periodos) {
		this.libretas = libretas;
		this.alumnos = alumnos;
		this.periodos = periodos;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PorAvisar> emitidasDesde(LocalDateTime desde) {
		List<LibretaEmitida> recientes = libretas.findByCreadoEnGreaterThanEqualOrderByIdAsc(desde);
		if (recientes.isEmpty()) {
			return List.of();
		}
		Map<Long, Alumno> porAlumno = alumnos.findAllById(recientes.stream().map(LibretaEmitida::getAlumnoId).distinct()
				.toList()).stream().collect(Collectors.toMap(Alumno::getId, Function.identity()));
		// Familia y periodo con libretas recientes; por cada uno, todas las libretas de esos hijos en ese periodo.
		Map<String, List<LibretaEmitida>> grupos = new LinkedHashMap<>();
		for (LibretaEmitida l : recientes) {
			Alumno a = porAlumno.get(l.getAlumnoId());
			if (a == null) {
				continue;
			}
			grupos.computeIfAbsent(a.getFamilia().getId() + ":" + l.getPeriodoId(), k -> new ArrayList<>()).add(l);
		}
		List<PorAvisar> resultado = new ArrayList<>();
		grupos.forEach((clave, deLaFamilia) -> {
			long familia = Long.parseLong(clave.substring(0, clave.indexOf(':')));
			long periodo = deLaFamilia.get(0).getPeriodoId();
			Map<Long, LibretaEmitida> vigentes = new LinkedHashMap<>();
			for (LibretaEmitida l : deLaFamilia) {
				libretas.findFirstByPeriodoIdAndMatriculaIdOrderByNumeroVersionDesc(periodo, l.getMatriculaId())
						.ifPresent(v -> vigentes.put(v.getMatriculaId(), v));
			}
			List<String> nombres = vigentes.values().stream()
					.map(v -> primerNombre(porAlumno.get(v.getAlumnoId()))).distinct().toList();
			boolean corregida = vigentes.values().stream().anyMatch(v -> v.getNumeroVersion() > 1);
			long ultima = vigentes.values().stream().mapToLong(LibretaEmitida::getId).max().orElse(0);
			String nombrePeriodo = periodos.findById(periodo).map(PeriodoEvaluacion::getNombre).orElse("periodo");
			resultado.add(new PorAvisar(familia, periodo, nombrePeriodo, nombres, corregida, ultima));
		});
		return resultado;
	}

	private static String primerNombre(Alumno a) {
		String nombres = a.getNombres().strip();
		int espacio = nombres.indexOf(' ');
		return espacio > 0 ? nombres.substring(0, espacio) : nombres;
	}
}
