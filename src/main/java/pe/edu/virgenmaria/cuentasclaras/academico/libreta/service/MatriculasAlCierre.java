package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.HistorialMatricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.HistorialMatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Correcciones del sprint 10 (hallazgo 8 y P-1, P-6 y P-7 del QA): qué matrículas reciben la libreta de un periodo y en qué
 * sección. Es la sección y el estado que la matrícula tenía a su {@code cerrado_en} (la primera fila de
 * {@code matricula_historial} posterior al cierre dice cómo estaba; sin filas, como está hoy), no lo de la pasada de
 * emisión: quien se retira o se traslada después del cierre recibe la libreta de la sección donde estaba, y en su sección
 * nueva no figura como faltante. Una matrícula creada después del cierre no tiene libreta de ese periodo (P-6). Lo mismo
 * exige trg_libreta_emitida_nace. No lee cobranza (ArchUnit): la deuda nunca cuenta.
 */
@Component
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class MatriculasAlCierre {

	static final Set<EstadoMatricula> RECIBEN_LIBRETA = Set.of(EstadoMatricula.ACTIVA, EstadoMatricula.RESERVADA);

	/** Una matrícula con la sección y el estado que tenía al cierre. */
	public record AlCierre(Matricula matricula, Long seccionId, EstadoMatricula estado) {
	}

	private final MatriculaRepository matriculas;

	private final HistorialMatriculaRepository historial;

	public MatriculasAlCierre(MatriculaRepository matriculas, HistorialMatriculaRepository historial) {
		this.matriculas = matriculas;
		this.historial = historial;
	}

	/** Las del año del periodo que estaban activas o reservadas a su cierre, en orden alfabético. */
	public List<AlCierre> recibenLibreta(PeriodoEvaluacion periodo) {
		LocalDateTime cierre = periodo.getCerradoEn();
		if (cierre == null) {
			return List.of();
		}
		List<Matricula> delAnio = matriculas.delAnioEnOrden(periodo.getAnioEscolarId()).stream()
				.filter(m -> m.getCreadoEn() == null || !m.getCreadoEn().isAfter(cierre)).toList();
		Map<Long, HistorialMatricula> primerCambio = new HashMap<>();
		if (!delAnio.isEmpty()) {
			historial.findByMatriculaIdInAndCambiadoBdGreaterThanOrderByCambiadoBdAscIdAsc(
					delAnio.stream().map(Matricula::getId).toList(), cierre)
					.forEach(h -> primerCambio.putIfAbsent(h.getMatriculaId(), h));
		}
		List<AlCierre> resultado = new ArrayList<>();
		for (Matricula m : delAnio) {
			HistorialMatricula h = primerCambio.get(m.getId());
			AlCierre a = h == null ? new AlCierre(m, m.getSeccion().getId(), m.getEstado())
					: new AlCierre(m, h.getSeccionAnteriorId(), h.getEstadoAnterior());
			if (RECIBEN_LIBRETA.contains(a.estado())) {
				resultado.add(a);
			}
		}
		return resultado;
	}

	/** Las que reciben la libreta del periodo en esa sección (la de su cierre). */
	public List<Matricula> deLaSeccion(PeriodoEvaluacion periodo, long seccionId) {
		return recibenLibreta(periodo).stream().filter(a -> a.seccionId().equals(seccionId)).map(AlCierre::matricula)
				.toList();
	}
}
