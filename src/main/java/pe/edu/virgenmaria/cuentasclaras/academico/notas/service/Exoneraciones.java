package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.ExonerarRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.ExoneracionesVista;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.ExoneracionArea;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.ExoneracionAreaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 2 (sección 3.5; E21, E22 y A3): exonerar a un alumno de Religión o Educación Física (RVM 094-2020). Solo
 * Dirección, con su firma ({@code exoneracion_area:{matricula}:{area}:AUTORIZADA}) y el código del escrito del padre. Rige
 * DESDE el periodo ABIERTO: las notas de los periodos anteriores quedan. Desde ahí el área no se califica para ese alumno
 * (hoja de notas y trigger) y la libreta dirá «Exonerado» (tanda 3). En MySQL lo exige trg_exoneracion_area_nace.
 */
@Service
@PreAuthorize("hasRole('DIRECTOR')")
public class Exoneraciones {

	static final String ENTIDAD = "exoneracion_area";

	private final ExoneracionAreaRepository exoneraciones;

	private final MatriculaRepository matriculas;

	private final SeccionRepository secciones;

	private final AnioEscolarRepository anios;

	private final ContextoNotas contexto;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	public Exoneraciones(ExoneracionAreaRepository exoneraciones, MatriculaRepository matriculas, SeccionRepository secciones,
			AnioEscolarRepository anios, ContextoNotas contexto, FirmaSesion firma, AuditoriaService auditoria) {
		this.exoneraciones = exoneraciones;
		this.matriculas = matriculas;
		this.secciones = secciones;
		this.anios = anios;
		this.contexto = contexto;
		this.firma = firma;
		this.auditoria = auditoria;
	}

	/** La pantalla de una sección del año en curso (por defecto, la primera). */
	@Transactional(readOnly = true)
	public ExoneracionesVista vista(Long seccionId) {
		Optional<AnioEscolar> anio = anios.findByEstado(EstadoAnioEscolar.EN_CURSO);
		List<Seccion> delAnio = anio.map(a -> secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(a.getId()).stream()
				.filter(Seccion::isActiva).toList()).orElse(List.of());
		List<ExoneracionesVista.Opcion> opciones = delAnio.stream()
				.map(s -> new ExoneracionesVista.Opcion(s.getId(), s.etiqueta())).toList();
		if (delAnio.isEmpty()) {
			return new ExoneracionesVista(opciones, null, null, List.of(), List.of(), List.of(), null);
		}
		Seccion seccion = seccionId == null ? delAnio.getFirst() : delAnio.stream().filter(s -> s.getId().equals(seccionId))
				.findFirst().orElseThrow(() -> new RecursoNoEncontradoException("Sección no encontrada"));
		ConfiguracionEvaluacion configuracion = contexto.configuracion(seccion);
		List<AreaDelCatalogo> exonerables = exonerables(seccion, configuracion);
		Map<Long, String> nombresAreas = exonerables.stream()
				.collect(Collectors.toMap(AreaDelCatalogo::id, AreaDelCatalogo::nombre));
		List<Matricula> alumnos = contexto.matriculas(seccion.getId());
		Map<Long, Matricula> porId = alumnos.stream().collect(Collectors.toMap(Matricula::getId, Function.identity()));
		Map<Long, PeriodoEvaluacion> periodos = contexto.periodosDe(seccion).stream()
				.collect(Collectors.toMap(PeriodoEvaluacion::getId, Function.identity()));
		List<ExoneracionesVista.Registrada> registradas = alumnos.isEmpty() ? List.of()
				: exoneraciones.findByMatriculaIdIn(porId.keySet()).stream()
						.map(e -> new ExoneracionesVista.Registrada(RegistroNotas.nombre(porId.get(e.getMatriculaId())),
								nombresAreas.getOrDefault(e.getAreaId(), "área " + e.getAreaId()),
								Optional.ofNullable(periodos.get(e.getDesdePeriodoId())).map(PeriodoEvaluacion::getNombre)
										.orElse("—"), e.getDocumento(), e.getAutorizadoPor())).toList();
		AccesoMostrado.filas(alumnos.size());
		return new ExoneracionesVista(opciones, seccion.getId(), seccion.etiqueta(), alumnos.stream()
				.map(m -> new ExoneracionesVista.Opcion(m.getId(), RegistroNotas.apellidos(m.getAlumno()) + ", "
						+ m.getAlumno().getNombres())).toList(),
				exonerables.stream().map(a -> new ExoneracionesVista.Opcion(a.id(), a.nombre())).toList(), registradas,
				abierto(seccion).map(PeriodoEvaluacion::getNombre).orElse(null));
	}

	/** Registra la exoneración con la firma de Dirección, desde el periodo abierto. @return su id */
	@Transactional
	public Long exonerar(ExonerarRequest pedido) {
		UsuarioAutenticado persona = Personas.actual();
		Matricula matricula = matriculas.findById(pedido.matriculaId())
				.filter(m -> ContextoNotas.CON_NOTA.contains(m.getEstado()))
				.orElseThrow(() -> new RecursoNoEncontradoException("Alumno no encontrado"));
		Seccion seccion = matricula.getSeccion();
		ConfiguracionEvaluacion configuracion = contexto.configuracion(seccion);
		AreaDelCatalogo area = exonerables(seccion, configuracion).stream().filter(a -> a.id().equals(pedido.areaId()))
				.findFirst().orElseThrow(() -> new ReglaNegocioException("Solo Religión y Educación Física se exoneran."));
		PeriodoEvaluacion desde = abierto(seccion).orElseThrow(() -> new ReglaNegocioException("La exoneración rige desde "
				+ "un periodo abierto: abre el periodo primero (las notas de los anteriores quedan)."));
		if (exoneraciones.existsByMatriculaIdAndAreaId(matricula.getId(), area.id())) {
			throw new ReglaNegocioException(RegistroNotas.nombre(matricula) + " ya está exonerado de " + area.nombre() + ".");
		}
		ExoneracionArea nueva = ExoneracionArea.nueva(seccion.getAnioEscolar().getId(), matricula.getId(), area.id(),
				desde.getId(), pedido.documento(), pedido.motivo(), persona.getUsername());
		firma.firmar(ClaveFirma.exoneracionArea(matricula.getId(), area.id()));
		nueva = exoneraciones.saveAndFlush(nueva);
		auditoria.registrar(AccionAuditoria.AREA_EXONERADA, ENTIDAD, nueva.getId().toString(), null,
				matricula.getId() + ":" + area.id(), persona.nombreCompleto() + " exoneró de " + area.nombre()
						+ " a la matrícula " + matricula.getId() + " de " + seccion.etiqueta() + " desde el "
						+ desde.getNombre() + " (escrito " + nueva.getDocumento() + "). Las notas de los periodos anteriores "
						+ "quedan.");
		return nueva.getId();
	}

	private List<AreaDelCatalogo> exonerables(Seccion seccion, ConfiguracionEvaluacion configuracion) {
		return contexto.catalogo().areasDe(configuracion.getCurriculoVersionId(), seccion.nivel()).stream()
				.filter(AreaDelCatalogo::exonerable).toList();
	}

	private Optional<PeriodoEvaluacion> abierto(Seccion seccion) {
		return contexto.periodosDe(seccion).stream().filter(p -> p.getEstado() == EstadoPeriodo.ABIERTO).findFirst();
	}
}
