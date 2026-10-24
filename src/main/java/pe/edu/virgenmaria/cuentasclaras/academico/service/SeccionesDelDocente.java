package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.api.SeccionesDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionArea;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionAreaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 9, tanda 1 (sección 3.3): qué secciones ve el docente en sesión. Solo las de sus asignaciones VIGENTES, en años
 * que no están cerrados y con el rol DOCENTE en su sesión: una propuesta (sin aprobar), rechazada o terminada no da acceso
 * (E3 y E32). Una sección ajena, o de otro colegio ({@code @TenantId}), responde 404.
 */
@Service
@PreAuthorize("hasAnyRole('DIRECTOR','DOCENTE')")
public class SeccionesDelDocente implements SeccionesDocente {

	private final AsignacionDocenteRepository asignaciones;

	private final SeccionRepository secciones;

	private final AsignacionAreaRepository areas;

	private final pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.ConfiguracionEvaluacionRepository
			configuraciones;

	private final pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Catalogo catalogo;

	private final Clock reloj;

	public SeccionesDelDocente(AsignacionDocenteRepository asignaciones, SeccionRepository secciones,
			AsignacionAreaRepository areas,
			pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.ConfiguracionEvaluacionRepository configuraciones,
			pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Catalogo catalogo, Clock reloj) {
		this.asignaciones = asignaciones;
		this.secciones = secciones;
		this.areas = areas;
		this.configuraciones = configuraciones;
		this.catalogo = catalogo;
		this.reloj = reloj;
	}

	/** Las asignaciones VIGENTES del docente que dan acceso hoy (A7: sin las coberturas vencidas). */
	private List<AsignacionDocente> vigentesDe(Long usuarioId) {
		LocalDate hoy = LocalDate.now(reloj);
		return asignaciones.findByUsuarioIdAndEstadoOrderBySeccionIdAsc(usuarioId, EstadoAsignacion.VIGENTE).stream()
				.filter(a -> a.daAcceso(hoy)).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<SeccionDocente> mias() {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		if (!persona.roles().contains(Rol.DOCENTE)) {
			return List.of();
		}
		Map<Long, List<AsignacionDocente>> porSeccion = new LinkedHashMap<>();
		vigentesDe(persona.usuarioId())
				.forEach(a -> porSeccion.computeIfAbsent(a.getSeccionId(), id -> new ArrayList<>()).add(a));
		List<SeccionDocente> mias = new ArrayList<>();
		porSeccion.forEach((seccionId, delaSeccion) -> secciones.findById(seccionId)
				.filter(s -> !s.getAnioEscolar().cerrado())
				.ifPresent(s -> mias.add(new SeccionDocente(s.getId(), s.etiqueta(), s.getAnioEscolar().getAnio(),
						funcion(delaSeccion), delaSeccion.stream().anyMatch(a -> a.getTipo() == TipoAsignacion.TUTOR)))));
		mias.sort(Comparator.comparing(SeccionDocente::anio).reversed().thenComparing(SeccionDocente::seccion));
		return List.copyOf(mias);
	}

	@Override
	@Transactional(readOnly = true)
	public void exigir(long seccionId, boolean conAuxiliar) {
		vigentesEn(seccionId, conAuxiliar).orElseThrow(SeccionesDelDocente::noEncontrada);
	}

	/** La sección y lo que el docente en sesión hace en ella, o vacío si no tiene una asignación vigente allí. */
	@Transactional(readOnly = true)
	public Optional<Map.Entry<Seccion, String>> vigentesEn(long seccionId, boolean conAuxiliar) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		if (!persona.roles().contains(Rol.DOCENTE)) {
			return Optional.empty();
		}
		Optional<Seccion> seccion = secciones.findById(seccionId).filter(s -> !s.getAnioEscolar().cerrado());
		if (seccion.isEmpty()) {
			return Optional.empty();
		}
		List<AsignacionDocente> suyas = vigentesDe(persona.usuarioId()).stream()
				.filter(a -> a.getSeccionId().equals(seccionId))
				.filter(a -> conAuxiliar || a.getTipo() != TipoAsignacion.AUXILIAR).toList();
		return suyas.isEmpty() ? Optional.empty() : Optional.of(Map.entry(seccion.get(), funcion(suyas)));
	}

	@Override
	@Transactional(readOnly = true)
	@PreAuthorize("hasAnyRole('DIRECTOR','PROMOTOR')")
	public boolean sigueAsignado(long usuarioId, long seccionId) {
		return secciones.findById(seccionId).filter(s -> !s.getAnioEscolar().cerrado()).isPresent()
				&& vigentesDe(usuarioId).stream().anyMatch(a -> a.getSeccionId().equals(seccionId)
						&& a.getTipo() != TipoAsignacion.AUXILIAR);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Long> areasDe(long seccionId) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		Optional<Seccion> seccion = secciones.findById(seccionId).filter(s -> !s.getAnioEscolar().cerrado());
		if (!persona.roles().contains(Rol.DOCENTE) || seccion.isEmpty()) {
			return List.of();
		}
		List<AsignacionDocente> suyas = vigentesDe(persona.usuarioId()).stream()
				.filter(a -> a.getSeccionId().equals(seccionId) && a.getTipo() != TipoAsignacion.AUXILIAR
						&& a.getHasta() == null).toList();
		if (suyas.isEmpty()) {
			return List.of();
		}
		java.util.Set<Long> ids = new java.util.TreeSet<>();
		areas.findByAsignacionIdInOrderByAreaIdAsc(suyas.stream().map(AsignacionDocente::getId).toList())
				.forEach(a -> ids.add(a.getAreaId()));
		// Sprint 10, tanda 2 (S2, decisión 175): las competencias transversales las califica el TUTOR (la misma regla que
		// cc_docente_de_area en MySQL): su área transversal del nivel, en la versión del año.
		if (suyas.stream().anyMatch(a -> a.getTipo() == TipoAsignacion.TUTOR)) {
			configuraciones.findByAnioEscolarId(seccion.get().getAnioEscolar().getId())
					.ifPresent(c -> catalogo.areasDe(c.getCurriculoVersionId(), seccion.get().nivel()).stream()
							.filter(pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo::transversal)
							.forEach(t -> ids.add(t.id())));
		}
		return List.copyOf(ids);
	}

	/**
	 * Sprint 10, tanda 2 (sección 4 del brief): si el docente en sesión tiene en la sección una asignación VIGENTE de tutor
	 * o de área sin cobertura pero SIN áreas del catálogo (las del sprint 9): marca asistencia, pero todavía no califica.
	 */
	@Override
	@Transactional(readOnly = true)
	public boolean sinAreasDelCatalogo(long seccionId) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		if (!persona.roles().contains(Rol.DOCENTE)) {
			return false;
		}
		return vigentesDe(persona.usuarioId()).stream()
				.filter(a -> a.getSeccionId().equals(seccionId) && a.getTipo() != TipoAsignacion.AUXILIAR
						&& a.getHasta() == null)
				.anyMatch(a -> !areas.existsByAsignacionId(a.getId()));
	}

	/** Sprint 9, tanda 2: si el docente en sesión es tutor(a) VIGENTE de la sección (ve las notas y el reporte). */
	@Transactional(readOnly = true)
	public boolean tutorDe(long seccionId) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		return persona.roles().contains(Rol.DOCENTE) && vigentesDe(persona.usuarioId()).stream()
				.anyMatch(a -> a.getSeccionId().equals(seccionId) && a.getTipo() == TipoAsignacion.TUTOR);
	}

	/**
	 * «Tutor(a)», «Docente de área: Matemática», «Auxiliar de educación» (varias, unidas con « · »). Sprint 10, tanda 3: con
	 * los nombres de las áreas del catálogo de la asignación (las del sprint 10 no tienen texto libre); sin ellas, el texto
	 * del sprint 9.
	 */
	private String funcion(List<AsignacionDocente> asignaciones) {
		Map<Long, List<Long>> delCatalogo = areas.findByAsignacionIdInOrderByAreaIdAsc(asignaciones.stream()
				.map(AsignacionDocente::getId).toList()).stream().collect(java.util.stream.Collectors.groupingBy(
						pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionArea::getAsignacionId,
						java.util.stream.Collectors.mapping(
								pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionArea::getAreaId,
								java.util.stream.Collectors.toList())));
		Map<Long, String> nombres = new java.util.HashMap<>();
		if (!delCatalogo.isEmpty()) {
			catalogo.areas(delCatalogo.values().stream().flatMap(List::stream).collect(java.util.stream.Collectors.toSet()))
					.forEach(x -> nombres.put(x.id(), x.nombre()));
		}
		return funcion(asignaciones, delCatalogo, nombres);
	}

	static String funcion(List<AsignacionDocente> asignaciones, Map<Long, List<Long>> delCatalogo,
			Map<Long, String> nombres) {
		return String.join(" · ", asignaciones.stream().sorted(Comparator.comparing(AsignacionDocente::getTipo))
				.map(a -> {
					// El tutor queda «Tutor(a)» (con todas las áreas del grado, la lista sería larga).
					List<Long> suyas = a.getTipo() == TipoAsignacion.TUTOR ? List.of()
							: delCatalogo.getOrDefault(a.getId(), List.of());
					String texto = suyas.isEmpty() ? a.getAreas() : String.join(", ", suyas.stream()
							.map(id -> nombres.getOrDefault(id, "área " + id)).toList());
					return texto == null ? a.getTipo().etiqueta() : a.getTipo().etiqueta() + ": " + texto;
				}).toList());
	}

	static RecursoNoEncontradoException noEncontrada() {
		return new RecursoNoEncontradoException("Sección no encontrada");
	}
}
