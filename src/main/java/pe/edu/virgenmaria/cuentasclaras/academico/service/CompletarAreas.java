package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.ConfiguracionEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Catalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.CompletarAreasVista;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionAreaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sprint 10, tanda 2 (sección 4 del brief, riesgo de la tanda 1): «Completar áreas» de los docentes del sprint 9, cuya
 * asignación no tiene áreas del catálogo y por eso no califican. Arma la propuesta de REEMPLAZO ya llenada (mismas claves y
 * las áreas sugeridas desde el texto libre de V32, por nombre sin tildes) para una asignación o para todas las de una
 * sección. No es un atajo: cada una es la misma {@link Asignaciones#proponer} de la tanda 1 (las áreas antes de la
 * solicitud, A2) y la aprueba OTRA persona; al aprobarla, reemplaza a la vigente. Sin aprobar, el docente sigue sin calificar.
 */
@Service
@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
public class CompletarAreas {

	private final AsignacionDocenteRepository asignaciones;

	private final AsignacionAreaRepository areasAsignadas;

	private final SeccionRepository secciones;

	private final UsuarioRepository usuarios;

	private final ConfiguracionEvaluacionRepository configuraciones;

	private final Catalogo catalogo;

	private final Asignaciones propuestas;

	public CompletarAreas(AsignacionDocenteRepository asignaciones, AsignacionAreaRepository areasAsignadas,
			SeccionRepository secciones, UsuarioRepository usuarios, ConfiguracionEvaluacionRepository configuraciones,
			Catalogo catalogo, Asignaciones propuestas) {
		this.asignaciones = asignaciones;
		this.areasAsignadas = areasAsignadas;
		this.secciones = secciones;
		this.usuarios = usuarios;
		this.configuraciones = configuraciones;
		this.catalogo = catalogo;
		this.propuestas = propuestas;
	}

	/** Las propuestas llenadas: de una asignación ({@code asignacionId}) o de todas las de una sección ({@code seccionId}). */
	@Transactional(readOnly = true)
	public CompletarAreasVista vista(Long asignacionId, Long seccionId) {
		List<AsignacionDocente> elegidas;
		Long seccion;
		if (asignacionId != null) {
			AsignacionDocente a = asignaciones.findById(asignacionId).filter(this::sinAreas)
					.orElseThrow(() -> new RecursoNoEncontradoException("Asignación no encontrada"));
			elegidas = List.of(a);
			seccion = a.getSeccionId();
		}
		else if (seccionId != null) {
			Seccion s = secciones.findById(seccionId).orElseThrow(() -> new RecursoNoEncontradoException("Sección no encontrada"));
			elegidas = asignaciones.findByAnioEscolarIdOrderBySeccionIdAscIdAsc(s.getAnioEscolar().getId()).stream()
					.filter(a -> a.getSeccionId().equals(seccionId)).filter(this::sinAreas).toList();
			seccion = seccionId;
		}
		else {
			throw new RecursoNoEncontradoException("Asignación no encontrada");
		}
		List<CompletarAreasVista.Propuesta> lista = new ArrayList<>();
		for (AsignacionDocente a : elegidas) {
			Seccion s = secciones.findById(a.getSeccionId()).orElseThrow();
			List<AreaDelCatalogo> delNivel = areasDelNivel(s);
			Set<Long> sugeridas = Set.copyOf(sugerir(a.getAreas(), delNivel));
			lista.add(new CompletarAreasVista.Propuesta(a.getId(), s.etiqueta(), usuarios.findById(a.getUsuarioId())
					.map(Usuario::getNombreCompleto).orElse("—"), a.getTipo().etiqueta(), a.getAreas(), delNivel.stream()
							.map(x -> new CompletarAreasVista.OpcionArea(x.id(), x.nombre(), sugeridas.contains(x.id())))
							.toList()));
		}
		return new CompletarAreasVista(seccion, lista);
	}

	/**
	 * Propone el reemplazo de cada asignación con las áreas que la persona confirmó (una solicitud por asignación, para que
	 * otra persona las apruebe seguidas en la bandeja). @return las solicitudes creadas
	 */
	@Transactional
	public int proponer(Map<Long, List<Long>> areasPorAsignacion, String motivo) {
		if (areasPorAsignacion.isEmpty()) {
			throw new ReglaNegocioException("Elige al menos un área para alguna asignación.");
		}
		int creadas = 0;
		for (Map.Entry<Long, List<Long>> e : areasPorAsignacion.entrySet()) {
			AsignacionDocente a = asignaciones.findById(e.getKey()).filter(this::sinAreas)
					.orElseThrow(() -> new RecursoNoEncontradoException("Asignación no encontrada"));
			if (e.getValue() == null || e.getValue().isEmpty()) {
				continue;
			}
			propuestas.proponer(ProponerAsignacionRequest.conAreas(a.getSeccionId(), a.getUsuarioId(), a.getTipo(), motivo,
					e.getValue()));
			creadas++;
		}
		if (creadas == 0) {
			throw new ReglaNegocioException("Elige al menos un área para alguna asignación.");
		}
		return creadas;
	}

	/**
	 * Las áreas del nivel cuyo nombre (sin tildes ni mayúsculas) aparece en el texto libre del sprint 9 («Matemática, Ciencia
	 * y Tecnología» → MAT y CYT). Es solo una sugerencia: la persona la revisa y otra la aprueba (riesgo 7).
	 */
	static List<Long> sugerir(String texto, List<AreaDelCatalogo> delNivel) {
		if (texto == null || texto.isBlank()) {
			return List.of();
		}
		String plano = plano(texto);
		return delNivel.stream().filter(a -> plano.contains(plano(a.nombre())) || plano.matches("(?s).*\\b"
				+ java.util.regex.Pattern.quote(a.codigo().toLowerCase(Locale.ROOT)) + "\\b.*")).map(AreaDelCatalogo::id)
				.toList();
	}

	private static String plano(String texto) {
		return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
				.replaceAll("\\s+", " ").strip();
	}

	private List<AreaDelCatalogo> areasDelNivel(Seccion s) {
		ConfiguracionEvaluacion c = configuraciones.findByAnioEscolarId(s.getAnioEscolar().getId())
				.orElseThrow(() -> new ReglaNegocioException("Dirección todavía no guarda la evaluación del año: sin ella no "
						+ "hay áreas del catálogo."));
		return catalogo.areasDe(c.getCurriculoVersionId(), s.nivel()).stream().filter(a -> !a.transversal()).toList();
	}

	private boolean sinAreas(AsignacionDocente a) {
		return a.esVigente() && a.getHasta() == null && a.getTipo() != TipoAsignacion.AUXILIAR
				&& !areasAsignadas.existsByAsignacionId(a.getId());
	}
}
