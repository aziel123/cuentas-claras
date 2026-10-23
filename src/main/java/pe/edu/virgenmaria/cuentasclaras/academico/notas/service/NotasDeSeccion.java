package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.CompetenciaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.HojaNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotasSeccionVista;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.Calificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.CalificacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sprint 10, tanda 2 (sección 3.5): las notas de una sección, de solo lectura: el TUTOR vigente de la sección (lectura de
 * toda su sección) y Dirección (todas). Ningún otro docente: un docente de área ve solo su hoja (404 aquí). Cada consulta
 * queda en {@code acceso_dato_personal} (NOTAS_SECCION, con la cantidad de alumnos).
 */
@Service
@PreAuthorize("hasAnyRole('DIRECTOR','DOCENTE')")
public class NotasDeSeccion {

	private final ContextoNotas contexto;

	private final CalificacionRepository calificaciones;

	private final AsignacionDocenteRepository asignaciones;

	public NotasDeSeccion(ContextoNotas contexto, CalificacionRepository calificaciones,
			AsignacionDocenteRepository asignaciones) {
		this.contexto = contexto;
		this.calificaciones = calificaciones;
		this.asignaciones = asignaciones;
	}

	@Transactional(readOnly = true)
	public NotasSeccionVista vista(long seccionId, Long periodoId) {
		UsuarioAutenticado persona = Personas.actual();
		Seccion seccion = contexto.seccion(seccionId);
		boolean direccion = persona.roles().contains(Rol.DIRECTOR);
		boolean tutor = asignaciones.findByUsuarioIdAndEstadoOrderBySeccionIdAsc(persona.usuarioId(),
				EstadoAsignacion.VIGENTE).stream().anyMatch(a -> a.getSeccionId().equals(seccionId)
						&& a.getTipo() == TipoAsignacion.TUTOR);
		if (!direccion && !tutor) {
			throw new RecursoNoEncontradoException("Sección no encontrada");
		}
		ConfiguracionEvaluacion configuracion = contexto.configuracion(seccion);
		PeriodoEvaluacion periodo = contexto.periodo(seccion, periodoId);
		List<CompetenciaDelCatalogo> competencias = contexto.catalogo()
				.competenciasDe(configuracion.getCurriculoVersionId(), seccion.getGrado());
		List<Matricula> matriculas = contexto.matriculas(seccionId);
		Map<String, Calificacion> vigentes = new HashMap<>();
		if (!competencias.isEmpty() && !matriculas.isEmpty()) {
			calificaciones.vigentes(periodo.getId(), seccionId, competencias.stream().map(CompetenciaDelCatalogo::id)
					.toList()).forEach(c -> vigentes.put(c.getMatriculaId() + ":" + c.getCompetenciaId(), c));
		}
		List<NotasSeccionVista.AreaConNotas> areas = new ArrayList<>();
		for (AreaDelCatalogo area : contexto.catalogo().areasDe(configuracion.getCurriculoVersionId(), seccion.nivel())) {
			List<CompetenciaDelCatalogo> delArea = competencias.stream().filter(k -> k.areaId().equals(area.id())).toList();
			if (delArea.isEmpty()) {
				continue;
			}
			Set<Long> exonerados = contexto.exonerados(matriculas.stream().map(Matricula::getId).toList(), area.id(),
					periodo);
			List<HojaNotas.FilaAlumno> filas = new ArrayList<>();
			for (Matricula m : matriculas) {
				List<HojaNotas.Celda> celdas = delArea.stream().map(k -> {
					Calificacion v = vigentes.get(m.getId() + ":" + k.id());
					boolean conNota = v != null && v.getNivelLogro() != null;
					return new HojaNotas.Celda(k.id(), conNota ? v.getNivelLogro().name() : null,
							v == null ? null : v.getConclusion(), conNota);
				}).toList();
				Alumno a = m.getAlumno();
				filas.add(new HojaNotas.FilaAlumno(filas.size() + 1, m.getId(), RegistroNotas.apellidos(a), a.getNombres(),
						exonerados.contains(m.getId()), celdas));
			}
			areas.add(new NotasSeccionVista.AreaConNotas(area.id(), area.nombre(), delArea.stream()
					.map(k -> new HojaNotas.ColumnaCompetencia(k.id(), k.codigo(), k.nombre())).toList(), filas));
		}
		AccesoMostrado.filas(matriculas.size());
		return new NotasSeccionVista(seccion.getId(), seccion.etiqueta(), contexto.periodosDe(seccion).stream()
				.map(p -> new HojaNotas.OpcionPeriodo(p.getId(), p.getNombre(), p.getEstado().etiqueta())).toList(),
				periodo.getId(), periodo.getNombre(), areas);
	}
}
