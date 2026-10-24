package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.CompetenciaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.ConfiguracionEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionArea;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.AvanceVista;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.Calificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.CalificacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionAreaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 2: el cálculo del avance de un periodo (sin alumnos en el resultado) y de las asignaciones sin áreas del
 * catálogo. Lo usan el avance de Dirección y Promotoría, el resumen del cierre y las alertas {@code NOTAS_PENDIENTES} y
 * {@code ASIGNACIONES_SIN_AREAS}. Sin {@code @PreAuthorize}: lo protegen quienes lo llaman.
 */
@Component
class CalculoAvance {

	private final SeccionRepository secciones;

	private final ConfiguracionEvaluacionRepository configuraciones;

	private final CalificacionRepository calificaciones;

	private final AsignacionDocenteRepository asignaciones;

	private final AsignacionAreaRepository areasAsignadas;

	private final UsuarioRepository usuarios;

	private final ContextoNotas contexto;

	CalculoAvance(SeccionRepository secciones, ConfiguracionEvaluacionRepository configuraciones,
			CalificacionRepository calificaciones, AsignacionDocenteRepository asignaciones,
			AsignacionAreaRepository areasAsignadas, UsuarioRepository usuarios, ContextoNotas contexto) {
		this.secciones = secciones;
		this.configuraciones = configuraciones;
		this.calificaciones = calificaciones;
		this.asignaciones = asignaciones;
		this.areasAsignadas = areasAsignadas;
		this.usuarios = usuarios;
		this.contexto = contexto;
	}

	/** Por sección activa del año y área del nivel: notas esperadas y con nivel en ese periodo. */
	List<AvanceVista.FilaAvance> filas(PeriodoEvaluacion periodo) {
		Optional<ConfiguracionEvaluacion> configuracion = configuraciones.findByAnioEscolarId(periodo.getAnioEscolarId());
		if (configuracion.isEmpty()) {
			return List.of();
		}
		Map<Long, List<AsignacionDocente>> vigentes = asignaciones
				.findByAnioEscolarIdOrderBySeccionIdAscIdAsc(periodo.getAnioEscolarId()).stream()
				.filter(a -> a.esVigente() && a.getHasta() == null && a.getTipo() != TipoAsignacion.AUXILIAR)
				.collect(Collectors.groupingBy(AsignacionDocente::getSeccionId, LinkedHashMap::new, Collectors.toList()));
		Map<Long, List<Long>> areasPorAsignacion = areasDe(vigentes.values().stream().flatMap(List::stream).toList());
		Map<Long, String> nombres = new HashMap<>();
		List<AvanceVista.FilaAvance> filas = new ArrayList<>();
		for (Seccion seccion : secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(periodo.getAnioEscolarId())) {
			if (!seccion.isActiva()) {
				continue;
			}
			ConfiguracionEvaluacion c = configuracion.get();
			List<CompetenciaDelCatalogo> competencias = contexto.catalogo()
					.competenciasDe(c.getCurriculoVersionId(), seccion.getGrado());
			List<Long> matriculas = contexto.matriculas(seccion.getId()).stream().map(Matricula::getId).toList();
			// QA del sprint 10: solo las notas de las matrículas que reciben nota hoy (no las de un retirado ni las que dejó un
			// trasladado en esta sección), y de cada área sin los exonerados: si no, el avance se inflaba y escondía
			// pendientes (el cierre y la alerta NOTAS_PENDIENTES decían «completo» con un alumno sin nota).
			List<Calificacion> deLasMatriculas = matriculas.isEmpty() || competencias.isEmpty() ? List.of()
					: calificaciones.vigentesDeMatriculas(periodo.getId(), matriculas,
							competencias.stream().map(CompetenciaDelCatalogo::id).toList()).stream()
							.filter(x -> x.getNivelLogro() != null).toList();
			List<AsignacionDocente> deLaSeccion = vigentes.getOrDefault(seccion.getId(), List.of());
			for (AreaDelCatalogo area : contexto.catalogo().areasDe(c.getCurriculoVersionId(), seccion.nivel())) {
				List<CompetenciaDelCatalogo> delArea = competencias.stream().filter(k -> k.areaId().equals(area.id()))
						.toList();
				if (delArea.isEmpty()) {
					continue;
				}
				Set<Long> exonerados = contexto.exonerados(matriculas, area.id(), periodo);
				int esperadas = delArea.size() * (matriculas.size() - exonerados.size());
				Set<Long> idsDelArea = delArea.stream().map(CompetenciaDelCatalogo::id).collect(Collectors.toSet());
				int conNota = (int) deLasMatriculas.stream().filter(x -> idsDelArea.contains(x.getCompetenciaId())
						&& !exonerados.contains(x.getMatriculaId())).count();
				Set<String> quienes = new TreeSet<>();
				for (AsignacionDocente a : deLaSeccion) {
					boolean califica = area.transversal() ? a.getTipo() == TipoAsignacion.TUTOR
							: areasPorAsignacion.getOrDefault(a.getId(), List.of()).contains(area.id());
					if (califica) {
						quienes.add(nombres.computeIfAbsent(a.getUsuarioId(), id -> usuarios.findById(id)
								.map(Usuario::getNombreCompleto).orElse("—")));
					}
				}
				filas.add(new AvanceVista.FilaAvance(seccion.getId(), seccion.etiqueta(), area.id(), area.nombre(),
						quienes.isEmpty() ? "Sin docente con esta área" : String.join(", ", quienes), esperadas,
						Math.min(conNota, esperadas)));
			}
		}
		return filas;
	}

	/** Las asignaciones vigentes de tutor o de área, sin cobertura, que no tienen áreas del catálogo (las del sprint 9). */
	List<AvanceVista.SinAreas> sinAreas(Long anioEscolarId) {
		List<AsignacionDocente> vigentes = asignaciones.findByAnioEscolarIdOrderBySeccionIdAscIdAsc(anioEscolarId).stream()
				.filter(a -> a.esVigente() && a.getHasta() == null && a.getTipo() != TipoAsignacion.AUXILIAR).toList();
		Map<Long, List<Long>> areas = areasDe(vigentes);
		Map<Long, Seccion> porId = new HashMap<>();
		secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(anioEscolarId).forEach(s -> porId.put(s.getId(), s));
		return vigentes.stream().filter(a -> !areas.containsKey(a.getId()))
				.map(a -> new AvanceVista.SinAreas(a.getId(), a.getSeccionId(),
						Optional.ofNullable(porId.get(a.getSeccionId())).map(Seccion::etiqueta).orElse("—"),
						usuarios.findById(a.getUsuarioId()).map(Usuario::getNombreCompleto).orElse("—"),
						a.getTipo().etiqueta()))
				.toList();
	}

	private Map<Long, List<Long>> areasDe(List<AsignacionDocente> lista) {
		if (lista.isEmpty()) {
			return Map.of();
		}
		return areasAsignadas.findByAsignacionIdInOrderByAreaIdAsc(lista.stream().map(AsignacionDocente::getId).toList())
				.stream().collect(Collectors.groupingBy(AsignacionArea::getAsignacionId,
						Collectors.mapping(AsignacionArea::getAreaId, Collectors.toList())));
	}
}
