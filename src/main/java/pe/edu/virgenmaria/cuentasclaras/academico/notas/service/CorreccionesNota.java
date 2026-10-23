package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.api.SeccionesDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.CompetenciaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionAreaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.BorradorConclusion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.Calificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.NivelLogro;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.BorradorConclusionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.CalificacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.service.SesionDeQuienEscribe;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Sprint 10, tanda 2 (sección 3.6; E4, E5, E6, E23 y M8): pedir lo que cambia una nota con el periodo CERRADO. La
 * <b>corrección</b> ({@code CORRECCION_CALIFICACION}) la pide el docente del área en la sección o Dirección, con un motivo
 * de al menos 20 caracteres; la conclusión nueva va en {@code borrador_conclusion} (M8) y la solicitud lleva solo el nivel,
 * el id del borrador y su SHA-256. La <b>nota tardía</b> ({@code CALIFICACION_TARDIA}) la pide el docente del área para una
 * competencia que quedó sin nota. Las dos las aprueba OTRA persona de Dirección o Promotoría en la bandeja.
 */
@Service
@PreAuthorize("hasAnyRole('DIRECTOR','DOCENTE')")
public class CorreccionesNota {

	static final String ENTIDAD = "calificacion";

	static final int MIN_MOTIVO = 20;

	private final CalificacionRepository calificaciones;

	private final BorradorConclusionRepository borradores;

	private final PeriodoEvaluacionRepository periodos;

	private final MatriculaRepository matriculas;

	private final AsignacionDocenteRepository asignaciones;

	private final AsignacionAreaRepository asignacionAreas;

	private final ContextoNotas contexto;

	private final SeccionesDocente seccionesDocente;

	private final RegistroSolicitudes solicitudes;

	private final SesionDeQuienEscribe sesiones;

	private final AuditoriaService auditoria;

	public CorreccionesNota(CalificacionRepository calificaciones, BorradorConclusionRepository borradores,
			PeriodoEvaluacionRepository periodos, MatriculaRepository matriculas, AsignacionDocenteRepository asignaciones,
			AsignacionAreaRepository asignacionAreas, ContextoNotas contexto, SeccionesDocente seccionesDocente,
			RegistroSolicitudes solicitudes, SesionDeQuienEscribe sesiones, AuditoriaService auditoria) {
		this.calificaciones = calificaciones;
		this.borradores = borradores;
		this.periodos = periodos;
		this.matriculas = matriculas;
		this.asignaciones = asignaciones;
		this.asignacionAreas = asignacionAreas;
		this.contexto = contexto;
		this.seccionesDocente = seccionesDocente;
		this.solicitudes = solicitudes;
		this.sesiones = sesiones;
		this.auditoria = auditoria;
	}

	/**
	 * Pide corregir la nota vigente {@code calificacionId} (periodo CERRADO): a {@code nivel} (o «sin nota», {@code null}) y
	 * con la conclusión nueva, que va en su borrador. @return el id de la solicitud
	 */
	@Transactional
	public Long pedir(long calificacionId, String nivelPedido, String conclusionPedida, String motivo) {
		UsuarioAutenticado persona = Personas.actual();
		Calificacion vigente = calificaciones.findById(calificacionId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Nota no encontrada"));
		Seccion seccion = contexto.seccion(vigente.getSeccionId());
		ConfiguracionEvaluacion configuracion = contexto.configuracion(seccion);
		CompetenciaDelCatalogo competencia = competencia(seccion, configuracion, vigente.getCompetenciaId());
		boolean direccion = persona.roles().contains(Rol.DIRECTOR);
		if (!direccion && !seccionesDocente.areasDe(seccion.getId()).contains(competencia.areaId())) {
			throw new RecursoNoEncontradoException("Nota no encontrada");
		}
		if (calificaciones.existsByReemplazaId(vigente.getId())) {
			throw new ReglaNegocioException("Esa nota ya cambió: abre la hoja de nuevo para ver la vigente.");
		}
		PeriodoEvaluacion periodo = periodos.findById(vigente.getPeriodoId()).orElseThrow();
		if (periodo.getEstado() != EstadoPeriodo.CERRADO) {
			throw new ReglaNegocioException("El " + periodo.getNombre() + " está abierto: cambia la nota en la hoja.");
		}
		NivelLogro nivel = NivelLogro.de(nivelPedido);
		String conclusion = nivel == null || conclusionPedida == null || conclusionPedida.isBlank() ? null
				: conclusionPedida.strip();
		boolean exige = nivel != null && (configuracion.isConclusionSiempre() || contexto.catalogo()
				.escalaDe(configuracion.getCurriculoVersionId(), seccion.nivel()).stream()
				.anyMatch(v -> v.valor().equals(nivel.name()) && v.exigeConclusion()));
		if (exige && conclusion == null) {
			throw new ReglaNegocioException("La norma exige la conclusión descriptiva para " + nivel.name() + ".");
		}
		if (conclusion != null && (conclusion.length() < Calificacion.MIN_CONCLUSION
				|| conclusion.length() > configuracion.getConclusionMax())) {
			throw new ReglaNegocioException("La conclusión va de " + Calificacion.MIN_CONCLUSION + " a "
					+ configuracion.getConclusionMax() + " caracteres.");
		}
		if (vigente.igualA(nivel, conclusion)) {
			throw new ReglaNegocioException("La corrección cambia el nivel o la conclusión.");
		}
		String razon = motivo == null ? "" : motivo.strip();
		if (razon.length() < MIN_MOTIVO) {
			throw new ReglaNegocioException("Explica la corrección en al menos " + MIN_MOTIVO + " caracteres.");
		}
		Map<String, String> datos = new LinkedHashMap<>();
		if (nivel != null) {
			datos.put("nivel", nivel.name());
		}
		BorradorConclusion borrador = null;
		if (conclusion != null) {
			// M8: el texto va en su borrador (con la sesión de quien lo escribe); la solicitud, solo su id y su huella.
			borrador = borradores.saveAndFlush(BorradorConclusion.de(vigente.getId(), conclusion, sesiones.de(persona)));
			datos.put("borrador_id", borrador.getId().toString());
			datos.put("conclusion_sha256", borrador.getSha256());
		}
		Matricula matricula = matriculas.findById(vigente.getMatriculaId()).orElseThrow();
		String de = vigente.getNivelLogro() == null ? "sin nota" : vigente.getNivelLogro().name();
		String a = nivel == null ? "sin nota" : nivel.name();
		SolicitudCambio solicitud = solicitudes.crear(TipoSolicitud.CORRECCION_CALIFICACION, ENTIDAD, vigente.getId(),
				"Corregir " + competencia.codigo() + " de " + RegistroNotas.nombre(matricula) + " en " + seccion.etiqueta()
						+ " (" + periodo.getNombre() + "): " + de + " → " + a, datos, razon);
		auditoria.registrar(AccionAuditoria.CORRECCION_NOTA_PEDIDA, ENTIDAD, vigente.getId().toString(), de, a,
				persona.nombreCompleto() + " pidió corregir " + competencia.codigo() + " (matrícula "
						+ vigente.getMatriculaId() + ", " + periodo.getNombre() + "): de " + de + " a " + a
						+ (borrador == null ? "" : ", con la conclusión de huella " + borrador.getSha256().substring(0, 12))
						+ " (solicitud " + solicitud.getId() + "). La aprueba otra persona de Dirección o Promotoría.");
		return solicitud.getId();
	}

	/**
	 * Pide registrar en 48 horas, con el periodo CERRADO, las competencias de su área que quedaron sin nota (E23). La
	 * entidad es su asignación en la sección. @return el id de la solicitud
	 */
	@PreAuthorize("hasRole('DOCENTE')")
	@Transactional
	public Long pedirTardia(long seccionId, long areaId, long periodoId, String motivo) {
		UsuarioAutenticado persona = Personas.actual();
		Seccion seccion = contexto.seccion(seccionId);
		if (!seccionesDocente.areasDe(seccionId).contains(areaId)) {
			throw new RecursoNoEncontradoException("Área no encontrada");
		}
		ConfiguracionEvaluacion configuracion = contexto.configuracion(seccion);
		AreaDelCatalogo area = contexto.area(seccion, configuracion, areaId);
		PeriodoEvaluacion periodo = contexto.periodo(seccion, periodoId);
		if (periodo.getEstado() != EstadoPeriodo.CERRADO) {
			throw new ReglaNegocioException("El " + periodo.getNombre() + " está abierto: registra en la hoja.");
		}
		List<CompetenciaDelCatalogo> competencias = contexto.competencias(seccion, configuracion, areaId);
		List<Matricula> delaSeccion = contexto.matriculas(seccionId);
		Set<Long> exonerados = contexto.exonerados(delaSeccion.stream().map(Matricula::getId).toList(), areaId, periodo);
		long conNota = competencias.isEmpty() ? 0 : calificaciones.vigentes(periodo.getId(), seccionId,
				competencias.stream().map(CompetenciaDelCatalogo::id).toList()).stream()
				.filter(c -> c.getNivelLogro() != null).count();
		long esperadas = (long) competencias.size() * delaSeccion.stream().filter(m -> !exonerados.contains(m.getId()))
				.count();
		if (conNota >= esperadas) {
			throw new ReglaNegocioException("Todas las competencias de " + area.nombre() + " tienen nota: un cambio va por "
					+ "corrección.");
		}
		String razon = motivo == null ? "" : motivo.strip();
		if (razon.length() < MIN_MOTIVO) {
			throw new ReglaNegocioException("Explica por qué quedó sin registrar, en al menos " + MIN_MOTIVO + " caracteres.");
		}
		AsignacionDocente asignacion = suAsignacion(persona, seccionId, area);
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("periodo_id", periodo.getId().toString());
		datos.put("area_id", Long.toString(areaId));
		SolicitudCambio solicitud = solicitudes.crear(TipoSolicitud.CALIFICACION_TARDIA, TardiasAprobadas.ENTIDAD,
				asignacion.getId(), "Registrar notas pendientes de " + area.nombre() + " en " + seccion.etiqueta() + " ("
						+ periodo.getNombre() + ", " + (esperadas - conNota) + " sin nota)", datos, razon);
		auditoria.registrar(AccionAuditoria.CALIFICACION_TARDIA_PEDIDA, TardiasAprobadas.ENTIDAD,
				asignacion.getId().toString(), null, periodo.getId() + ":" + areaId, persona.nombreCompleto()
						+ " pidió registrar notas pendientes de " + area.nombre() + " en " + seccion.etiqueta() + " ("
						+ periodo.getNombre() + "; solicitud " + solicitud.getId() + "). Si la aprueba otra persona, tiene "
						+ TardiasAprobadas.HORAS + " horas.");
		return solicitud.getId();
	}

	/** La asignación vigente con la que ese docente califica el área (la de tutor, en las transversales). */
	private AsignacionDocente suAsignacion(UsuarioAutenticado persona, long seccionId, AreaDelCatalogo area) {
		return asignaciones.findByUsuarioIdAndEstadoOrderBySeccionIdAsc(persona.usuarioId(), EstadoAsignacion.VIGENTE)
				.stream().filter(a -> a.getSeccionId().equals(seccionId) && a.getHasta() == null
						&& a.getTipo() != TipoAsignacion.AUXILIAR)
				.filter(a -> area.transversal() ? a.getTipo() == TipoAsignacion.TUTOR
						: asignacionAreas.findByAsignacionIdOrderByAreaIdAsc(a.getId()).stream()
								.anyMatch(x -> Objects.equals(x.getAreaId(), area.id())))
				.findFirst().orElseThrow(() -> new RecursoNoEncontradoException("Área no encontrada"));
	}

	private CompetenciaDelCatalogo competencia(Seccion seccion, ConfiguracionEvaluacion configuracion, Long competenciaId) {
		return contexto.catalogo().competenciasDe(configuracion.getCurriculoVersionId(), seccion.getGrado()).stream()
				.filter(c -> c.id().equals(competenciaId)).findFirst()
				.orElseThrow(() -> new IllegalStateException("La competencia de la nota no es del grado"));
	}
}
