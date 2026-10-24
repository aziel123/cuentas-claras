package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.Descarga;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.LibretasColegioVista;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.CanalEntrega;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.EntregaLibreta;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.EntregaLibretaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.LibretaEmitidaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.service.SesionDeQuienEscribe;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 3 (secciones 3.7 y 3.13; M9, decisión 177 revisada): las libretas para el colegio. Dirección ve e imprime
 * las de cualquier sección; el tutor VIGENTE, solo las de su sección (otra: 404, lo exige también
 * trg_entrega_libreta_nace). Cada libreta impresa deja su constancia de entrega IMPRESA a nombre de quien imprime, con su
 * sesión; en Inicial, ENTREVISTA. Una sección, hasta {@code max-alumnos-por-archivo} libretas por archivo y una impresión a
 * la vez por colegio. Ni la pantalla ni el PDF muestran nada de cobranza.
 */
@Service
@PreAuthorize("hasAnyRole('DIRECTOR','DOCENTE')")
public class LibretasColegio {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	/** Una impresión de sección a la vez por colegio (sección 3.9). */
	private final Set<Long> imprimiendo = ConcurrentHashMap.newKeySet();

	private final LibretaEmitidaRepository libretas;

	private final EntregaLibretaRepository entregas;

	private final PeriodoEvaluacionRepository periodos;

	private final SeccionRepository secciones;

	private final AnioEscolarRepository anios;

	private final MatriculasAlCierre alCierre;

	private final AlumnoRepository alumnos;

	private final AsignacionDocenteRepository asignaciones;

	private final RegeneracionLibreta regeneracion;

	private final SesionDeQuienEscribe sesiones;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public LibretasColegio(LibretaEmitidaRepository libretas, EntregaLibretaRepository entregas,
			PeriodoEvaluacionRepository periodos, SeccionRepository secciones, AnioEscolarRepository anios,
			MatriculasAlCierre alCierre, AlumnoRepository alumnos, AsignacionDocenteRepository asignaciones,
			RegeneracionLibreta regeneracion, SesionDeQuienEscribe sesiones, AuditoriaService auditoria, Clock reloj) {
		this.libretas = libretas;
		this.entregas = entregas;
		this.periodos = periodos;
		this.secciones = secciones;
		this.anios = anios;
		this.alCierre = alCierre;
		this.alumnos = alumnos;
		this.asignaciones = asignaciones;
		this.regeneracion = regeneracion;
		this.sesiones = sesiones;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** La pantalla: las secciones que puede ver quien está en sesión y, si eligió una y un periodo, sus libretas vigentes. */
	@Transactional(readOnly = true)
	public LibretasColegioVista inicio(Long seccionId, Long periodoId) {
		UsuarioAutenticado persona = QuienEnSesionLibreta.actual();
		boolean direccion = persona.roles().contains(Rol.DIRECTOR);
		AnioEscolar anio = anios.findByEstado(EstadoAnioEscolar.EN_CURSO).orElse(null);
		if (anio == null) {
			return new LibretasColegioVista(List.of(), List.of(), null, null, null, null, false, List.of(), 0, direccion,
					regeneracion.maxPorArchivo());
		}
		List<Seccion> visibles = secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(anio.getId()).stream()
				.filter(s -> direccion || esTutor(persona, s.getId())).toList();
		List<PeriodoEvaluacion> cerrados = periodos.findByAnioEscolarIdOrderByNumeroAsc(anio.getId()).stream()
				.filter(p -> p.getEstado() == EstadoPeriodo.CERRADO).toList();
		List<LibretasColegioVista.Opcion> opcionesSeccion = visibles.stream()
				.map(s -> new LibretasColegioVista.Opcion(s.getId(), s.etiqueta())).toList();
		List<LibretasColegioVista.Opcion> opcionesPeriodo = cerrados.stream()
				.map(p -> new LibretasColegioVista.Opcion(p.getId(), p.getNombre())).toList();
		if (seccionId == null || periodoId == null) {
			return new LibretasColegioVista(opcionesSeccion, opcionesPeriodo, seccionId, periodoId, null, null, false,
					List.of(), 0, direccion, regeneracion.maxPorArchivo());
		}
		Seccion seccion = visibles.stream().filter(s -> s.getId().equals(seccionId)).findFirst()
				.orElseThrow(() -> new RecursoNoEncontradoException("Sección no encontrada"));
		PeriodoEvaluacion periodo = cerrados.stream().filter(p -> p.getId().equals(periodoId)).findFirst()
				.orElseThrow(() -> new RecursoNoEncontradoException("Periodo no encontrado"));
		List<LibretaEmitida> vigentes = libretas.vigentesDeSeccion(periodo.getId(), seccion.getId());
		Map<Long, Long> entregasPorLibreta = vigentes.isEmpty() ? Map.of()
				: entregas.findByLibretaIdInOrderByIdAsc(vigentes.stream().map(LibretaEmitida::getId).toList()).stream()
						.collect(Collectors.groupingBy(EntregaLibreta::getLibretaId, Collectors.counting()));
		List<LibretasColegioVista.Fila> filas = vigentes.stream()
				.sorted(java.util.Comparator.comparing(LibretaEmitida::cabecera,
						java.util.Comparator.comparing(LibretaEmitida.Cabecera::alumnoNombre)))
				.map(l -> new LibretasColegioVista.Fila(l.getId(), l.cabecera().alumnoNombre(), l.getNumeroVersion(),
						LibretasFamilia.emitida(l).format(FECHA), l.getMotivo().etiqueta(),
						entregasPorLibreta.getOrDefault(l.getId(), 0L).intValue()))
				.toList();
		Set<Long> conLibreta = vigentes.stream().map(LibretaEmitida::getMatriculaId).collect(Collectors.toSet());
		// Correcciones del sprint 10 (P-7): la libreta es de la sección del cierre; el trasladado después del cierre no falta
		// en su sección nueva (su libreta está en la anterior) y quien estaba al cierre sí cuenta.
		int faltan = (int) alCierre.deLaSeccion(periodo, seccion.getId()).stream()
				.filter(m -> !conLibreta.contains(m.getId())).count();
		return new LibretasColegioVista(opcionesSeccion, opcionesPeriodo, seccionId, periodoId, seccion.etiqueta(),
				periodo.getNombre(), seccion.nivel() == Nivel.INICIAL, filas, faltan, direccion,
				regeneracion.maxPorArchivo());
	}

	/** Imprime UNA libreta (Dirección, o el tutor de su sección: otra, 404) y deja su constancia de entrega. */
	@Transactional
	public Descarga imprimir(long libretaId) {
		UsuarioAutenticado persona = QuienEnSesionLibreta.actual();
		LibretaEmitida libreta = libretas.findById(libretaId).filter(l -> puedeVer(persona, l.getSeccionId()))
				.orElseThrow(() -> new RecursoNoEncontradoException("Libreta no encontrada"));
		Long familia = alumnos.findById(libreta.getAlumnoId()).map(a -> a.getFamilia().getId()).orElse(null);
		RegeneracionLibreta.Regenerada r = regeneracion.regenerar(libreta);
		if (!r.coincide()) {
			return Descarga.enRevision(regeneracion.plazo(libreta), libreta.getAlumnoId(), familia);
		}
		byte[] pdf = regeneracion.pdf(List.of(r));
		entregar(persona, List.of(libreta));
		return Descarga.pdf(pdf, "libreta-" + libreta.getId() + "-v" + libreta.getNumeroVersion() + ".pdf",
				libreta.getAlumnoId(), familia, 1, 0);
	}

	/**
	 * Imprime las libretas vigentes de una sección y un periodo (hasta {@code max-alumnos-por-archivo}), una impresión a la
	 * vez por colegio. Las que no coinciden con su huella no salen (quedan en revisión) y se cuentan.
	 */
	@Transactional
	public Descarga imprimirSeccion(long seccionId, long periodoId) {
		UsuarioAutenticado persona = QuienEnSesionLibreta.actual();
		Seccion seccion = secciones.findById(seccionId).filter(s -> puedeVer(persona, s.getId()))
				.orElseThrow(() -> new RecursoNoEncontradoException("Sección no encontrada"));
		PeriodoEvaluacion periodo = periodos.findById(periodoId)
				.filter(p -> p.getAnioEscolarId().equals(seccion.getAnioEscolar().getId()))
				.orElseThrow(() -> new RecursoNoEncontradoException("Periodo no encontrado"));
		List<LibretaEmitida> vigentes = libretas.vigentesDeSeccion(periodo.getId(), seccion.getId()).stream()
				.sorted(java.util.Comparator.comparing(l -> l.cabecera().alumnoNombre())).toList();
		if (vigentes.isEmpty()) {
			throw new ReglaNegocioException("Esa sección todavía no tiene libretas del " + periodo.getNombre() + ".");
		}
		if (vigentes.size() > regeneracion.maxPorArchivo()) {
			throw new ReglaNegocioException("La sección tiene más de " + regeneracion.maxPorArchivo() + " libretas: "
					+ "imprímelas de una en una.");
		}
		Long colegio = ContextoColegio.actual();
		if (!imprimiendo.add(colegio)) {
			throw new ReglaNegocioException("Ya se está imprimiendo una sección del colegio. Intente en unos minutos.");
		}
		try {
			List<RegeneracionLibreta.Regenerada> regeneradas = regeneracion.regenerar(vigentes);
			List<LibretaEmitida> buenas = regeneradas.stream().filter(RegeneracionLibreta.Regenerada::coincide)
					.map(RegeneracionLibreta.Regenerada::libreta).toList();
			int omitidas = vigentes.size() - buenas.size();
			if (buenas.isEmpty()) {
				throw new ReglaNegocioException("Ninguna libreta de la sección coincide con su huella: quedaron en "
						+ "revisión (Promotoría ya recibió la alerta).");
			}
			byte[] pdf = regeneracion.pdf(regeneradas);
			entregar(persona, buenas);
			auditoria.registrar(AccionAuditoria.LIBRETAS_IMPRESAS, "seccion", String.valueOf(seccionId), null,
					buenas.size() + " libreta(s)", persona.nombreCompleto() + " imprimió " + buenas.size() + " libreta(s) del "
							+ periodo.getNombre() + " de " + seccion.etiqueta() + (omitidas > 0 ? " (" + omitidas
									+ " en revisión por su huella)" : "") + ".");
			String nombre = "libretas-" + seccion.getGrado().name().toLowerCase(Locale.ROOT) + "-"
					+ seccion.getNombre().replaceAll("[^A-Za-z0-9]", "") + "-p" + periodo.getNumero() + ".pdf";
			return Descarga.pdf(pdf, nombre, null, null, buenas.size(), omitidas);
		}
		finally {
			imprimiendo.remove(colegio);
		}
	}

	private void entregar(UsuarioAutenticado persona, List<LibretaEmitida> lista) {
		Long sesion = sesiones.de(persona);
		LocalDateTime ahora = LocalDateTime.now(reloj);
		Map<Long, Seccion> porSeccion = lista.stream().map(LibretaEmitida::getSeccionId).distinct()
				.map(id -> secciones.findById(id).orElseThrow()).collect(Collectors.toMap(Seccion::getId,
						Function.identity()));
		for (LibretaEmitida l : lista) {
			CanalEntrega canal = porSeccion.get(l.getSeccionId()).nivel() == Nivel.INICIAL ? CanalEntrega.ENTREVISTA
					: CanalEntrega.IMPRESA;
			entregas.saveAndFlush(EntregaLibreta.porElColegio(l.getId(), canal, sesion, ahora));
		}
	}

	private boolean puedeVer(UsuarioAutenticado persona, Long seccionId) {
		return persona.roles().contains(Rol.DIRECTOR) || esTutor(persona, seccionId);
	}

	/** M9: el tutor VIGENTE de la sección (la misma regla de trg_entrega_libreta_nace). */
	private boolean esTutor(UsuarioAutenticado persona, Long seccionId) {
		LocalDate hoy = LocalDate.now(reloj);
		return persona.roles().contains(Rol.DOCENTE) && asignaciones.findFirstByUsuarioIdAndSeccionIdAndTipoAndEstado(
				persona.usuarioId(), seccionId, TipoAsignacion.TUTOR, EstadoAsignacion.VIGENTE)
				.filter(a -> a.daAcceso(hoy)).isPresent();
	}
}
