package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.CompetenciaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.ConfiguracionEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Catalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.ExoneracionArea;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.ExoneracionAreaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 2: lo que comparten la hoja, el guardado, la vista de la sección, las correcciones y el avance: la
 * sección y el periodo del mismo año (si no, 404), la configuración del año, el área del catálogo de la versión y del nivel
 * de la sección, sus competencias del grado y las matrículas que reciben nota (ACTIVA o RESERVADA: C1; nunca se mira el
 * estado de pago). Sin rol propio: lo usan servicios que ya exigieron el suyo.
 */
@Component
class ContextoNotas {

	/** C1: la matrícula RESERVADA (impaga) recibe nota igual que la ACTIVA. */
	static final Set<EstadoMatricula> CON_NOTA = EnumSet.of(EstadoMatricula.ACTIVA, EstadoMatricula.RESERVADA);

	private final SeccionRepository secciones;

	private final PeriodoEvaluacionRepository periodos;

	private final ConfiguracionEvaluacionRepository configuraciones;

	private final MatriculaRepository matriculas;

	private final ExoneracionAreaRepository exoneraciones;

	private final Catalogo catalogo;

	ContextoNotas(SeccionRepository secciones, PeriodoEvaluacionRepository periodos,
			ConfiguracionEvaluacionRepository configuraciones, MatriculaRepository matriculas,
			ExoneracionAreaRepository exoneraciones, Catalogo catalogo) {
		this.secciones = secciones;
		this.periodos = periodos;
		this.configuraciones = configuraciones;
		this.matriculas = matriculas;
		this.exoneraciones = exoneraciones;
		this.catalogo = catalogo;
	}

	Seccion seccion(long seccionId) {
		return secciones.findById(seccionId).orElseThrow(() -> new RecursoNoEncontradoException("Sección no encontrada"));
	}

	ConfiguracionEvaluacion configuracion(Seccion seccion) {
		return configuraciones.findByAnioEscolarId(seccion.getAnioEscolar().getId())
				.orElseThrow(() -> new ReglaNegocioException("Dirección todavía no guarda la evaluación del año "
						+ seccion.getAnioEscolar().getAnio() + ": aún no hay notas que registrar."));
	}

	List<PeriodoEvaluacion> periodosDe(Seccion seccion) {
		return periodos.findByAnioEscolarIdOrderByNumeroAsc(seccion.getAnioEscolar().getId());
	}

	/** El periodo pedido (del año de la sección: si no, 404) o, sin pedirlo, el último abierto (o el último). */
	PeriodoEvaluacion periodo(Seccion seccion, Long periodoId) {
		List<PeriodoEvaluacion> delAnio = periodosDe(seccion);
		if (periodoId != null) {
			return delAnio.stream().filter(p -> p.getId().equals(periodoId)).findFirst()
					.orElseThrow(() -> new RecursoNoEncontradoException("Periodo no encontrado"));
		}
		return delAnio.stream().filter(p -> p.getEstado() == EstadoPeriodo.ABIERTO)
				.max(Comparator.comparingInt(PeriodoEvaluacion::getNumero))
				.or(() -> delAnio.stream().filter(p -> p.getEstado() == EstadoPeriodo.CERRADO)
						.max(Comparator.comparingInt(PeriodoEvaluacion::getNumero)))
				.orElseThrow(() -> new ReglaNegocioException("El año todavía no tiene periodos abiertos."));
	}

	/** El área del catálogo, de la versión del año y del nivel de la sección (si no, 404). */
	AreaDelCatalogo area(Seccion seccion, ConfiguracionEvaluacion configuracion, long areaId) {
		return catalogo.areasDe(configuracion.getCurriculoVersionId(), seccion.nivel()).stream()
				.filter(a -> a.id().equals(areaId)).findFirst()
				.orElseThrow(() -> new RecursoNoEncontradoException("Área no encontrada"));
	}

	/** Las competencias del área que se califican en el grado de la sección (respeta {@code solo_grados}), en su orden. */
	List<CompetenciaDelCatalogo> competencias(Seccion seccion, ConfiguracionEvaluacion configuracion, long areaId) {
		return catalogo.competenciasDe(configuracion.getCurriculoVersionId(), seccion.getGrado()).stream()
				.filter(c -> c.areaId().equals(areaId)).toList();
	}

	/** Las matrículas que reciben nota en la sección (ACTIVA o RESERVADA, C1), en orden alfabético. */
	List<Matricula> matriculas(long seccionId) {
		return matriculas.deLaSeccionEnOrden(seccionId, CON_NOTA);
	}

	/**
	 * A3: las matrículas exoneradas del área en ese periodo: su exoneración rige DESDE su periodo (de número menor o igual);
	 * en los anteriores conservan sus notas.
	 */
	Set<Long> exonerados(List<Long> matriculaIds, long areaId, PeriodoEvaluacion periodo) {
		if (matriculaIds.isEmpty()) {
			return Set.of();
		}
		Map<Long, PeriodoEvaluacion> delAnio = periodos.findByAnioEscolarIdOrderByNumeroAsc(periodo.getAnioEscolarId())
				.stream().collect(Collectors.toMap(PeriodoEvaluacion::getId, Function.identity()));
		return exoneraciones.findByMatriculaIdIn(matriculaIds).stream().filter(e -> e.getAreaId().equals(areaId))
				.filter(e -> Optional.ofNullable(delAnio.get(e.getDesdePeriodoId()))
						.map(d -> d.getNumero() <= periodo.getNumero()).orElse(false))
				.map(ExoneracionArea::getMatriculaId).collect(Collectors.toSet());
	}

	Catalogo catalogo() {
		return catalogo;
	}
}
