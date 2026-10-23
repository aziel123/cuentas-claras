package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.CompetenciaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.BorradorConclusion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.Calificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.NivelLogro;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.BorradorConclusionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.CalificacionRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Sprint 10, tanda 2 (sección 3.6; E4, E5, E6 y M8): aplica la {@code CORRECCION_CALIFICACION} aprobada en la bandeja (que ya
 * comprobó que quien aprueba es OTRA persona de Dirección o Promotoría y firmó). Inserta la nota {@code CORRECCION_APROBADA}
 * a nombre de quien aprueba con EXACTAMENTE lo aprobado: el nivel de la solicitud y el texto de su borrador (con la misma
 * huella). Quien registró la nota que se reemplaza tampoco aprueba (involucrados). La bitácora guarda las huellas, nunca
 * el texto. En MySQL lo exige trg_calificacion_nace.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorCorreccionCalificacion implements ManejadorSolicitud {

	private final CalificacionRepository calificaciones;

	private final BorradorConclusionRepository borradores;

	private final PeriodoEvaluacionRepository periodos;

	private final ContextoNotas contexto;

	private final DocentesDeArea docentes;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ManejadorCorreccionCalificacion(CalificacionRepository calificaciones, BorradorConclusionRepository borradores,
			PeriodoEvaluacionRepository periodos, ContextoNotas contexto, DocentesDeArea docentes,
			AuditoriaService auditoria, Clock reloj) {
		this.calificaciones = calificaciones;
		this.borradores = borradores;
		this.periodos = periodos;
		this.contexto = contexto;
		this.docentes = docentes;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.CORRECCION_CALIFICACION;
	}

	@Override
	public String entidad() {
		return CorreccionesNota.ENTIDAD;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Calificacion vigente = calificaciones.findById(Objects.requireNonNull(solicitud.getEntidadId(), "entidadId"))
				.orElseThrow(() -> new ReglaNegocioException("La nota de la solicitud ya no existe."));
		if (calificaciones.existsByReemplazaId(vigente.getId())) {
			throw new ReglaNegocioException("La nota cambió después de pedir la corrección: rechaza esta solicitud y que se "
					+ "pida de nuevo sobre la vigente.");
		}
		if (aprobador.equals(vigente.getCreadoPor())) {
			throw new ReglaNegocioException("Registraste la nota que se corrige: la corrección la aprueba otra persona.");
		}
		PeriodoEvaluacion periodo = periodos.findById(vigente.getPeriodoId()).orElseThrow();
		if (periodo.getEstado() != EstadoPeriodo.CERRADO) {
			throw new ReglaNegocioException("El periodo no está cerrado: la nota se cambia en la hoja.");
		}
		Seccion seccion = contexto.seccion(vigente.getSeccionId());
		AreaDelCatalogo area = areaDe(seccion, vigente);
		if (!docentes.califica(solicitud.getSolicitadoPor(), seccion.getId(), area)
				&& !docentes.tieneRol(solicitud.getSolicitadoPor(), Rol.DIRECTOR)) {
			throw new ReglaNegocioException("Quien pidió la corrección ya no califica " + area.nombre() + " en "
					+ seccion.etiqueta() + " ni es de Dirección: rechaza la solicitud.");
		}
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		NivelLogro nivel = NivelLogro.de(datos.get("nivel"));
		String conclusion = null;
		if (datos.get("borrador_id") != null) {
			BorradorConclusion borrador = borradores.findById(Long.valueOf(datos.get("borrador_id")))
					.filter(b -> b.getCalificacionId().equals(vigente.getId())
							&& b.getSha256().equals(datos.get("conclusion_sha256")))
					.orElseThrow(() -> new ReglaNegocioException("El borrador de la conclusión no es el de la solicitud: "
							+ "recházala."));
			conclusion = borrador.getTexto();
		}
		String de = vigente.getNivelLogro() == null ? "sin nota" : vigente.getNivelLogro().name();
		String a = nivel == null ? "sin nota" : nivel.name();
		Calificacion nueva = calificaciones.saveAndFlush(Calificacion.correccion(vigente, nivel, conclusion,
				solicitud.getId(), LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS)));
		auditoria.registrar(AccionAuditoria.CALIFICACION_CORREGIDA, CorreccionesNota.ENTIDAD, nueva.getId().toString(), de,
				a, "Aprobó la corrección de la nota " + vigente.getId() + " (matrícula " + vigente.getMatriculaId() + ", "
						+ area.nombre() + ", " + periodo.getNombre() + "): de " + de + " a " + a + "; conclusión "
						+ huella(vigente.getConclusionSha256()) + " → " + huella(nueva.getConclusionSha256()) + ". Pidió "
						+ solicitud.getSolicitadoPor() + ", registró la anterior " + vigente.getCreadoPor() + " y aprobó "
						+ aprobador + " (solicitud " + solicitud.getId() + ").");
	}

	/** E5: quien registró la nota que se reemplaza tampoco la aprueba (la bandeja lo suma a quien la pidió). */
	@Override
	public Set<String> involucrados(SolicitudCambio solicitud) {
		return calificaciones.findById(solicitud.getEntidadId()).map(c -> Set.of(c.getCreadoPor())).orElse(Set.of());
	}

	/**
	 * Lo que ve quien aprueba: la nota de hoy y la propuesta, con el texto del borrador (M8: solo aquí, nunca en la
	 * bitácora). La bandeja deja el acceso en {@code acceso_dato_personal} (NOTAS_SECCION).
	 */
	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		List<String> lineas = new ArrayList<>();
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		calificaciones.findById(solicitud.getEntidadId()).ifPresent(c -> {
			Seccion seccion = contexto.seccion(c.getSeccionId());
			lineas.add("Sección: " + seccion.etiqueta());
			periodos.findById(c.getPeriodoId()).ifPresent(p -> lineas.add("Periodo: " + p.getNombre()));
			competencia(seccion, c).ifPresent(k -> lineas.add("Competencia: " + k.codigo() + " · " + k.nombre()));
			lineas.add("Hoy figura: " + (c.getNivelLogro() == null ? "sin nota" : c.getNivelLogro().name())
					+ " (registró " + c.getCreadoPor() + ")");
			if (c.getConclusion() != null) {
				lineas.add("Conclusión de hoy: " + c.getConclusion());
			}
		});
		lineas.add("Quedaría: " + (datos.get("nivel") == null ? "sin nota" : datos.get("nivel")));
		if (datos.get("borrador_id") != null) {
			borradores.findById(Long.valueOf(datos.get("borrador_id")))
					.ifPresent(b -> lineas.add("Conclusión propuesta: " + b.getTexto()));
		}
		return lineas;
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return "Al aprobar, la nota cambia a tu nombre y la familia la verá corregida en la libreta. Confirma con el docente "
				+ "(o con su registro) que de verdad fue así.";
	}

	private AreaDelCatalogo areaDe(Seccion seccion, Calificacion c) {
		ConfiguracionEvaluacion configuracion = contexto.configuracion(seccion);
		CompetenciaDelCatalogo k = competencia(seccion, c)
				.orElseThrow(() -> new IllegalStateException("La competencia de la nota no es del grado"));
		return contexto.area(seccion, configuracion, k.areaId());
	}

	private java.util.Optional<CompetenciaDelCatalogo> competencia(Seccion seccion, Calificacion c) {
		ConfiguracionEvaluacion configuracion = contexto.configuracion(seccion);
		return contexto.catalogo().competenciasDe(configuracion.getCurriculoVersionId(), seccion.getGrado()).stream()
				.filter(k -> k.id().equals(c.getCompetenciaId())).findFirst();
	}

	static String huella(String sha) {
		return sha == null ? "(sin conclusión)" : sha.substring(0, 12);
	}
}
