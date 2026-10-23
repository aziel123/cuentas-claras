package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.ConfiguracionEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Catalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AreasDelAnio;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AsignacionSinAreas;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AsignacionVista;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AsignacionesVista;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.OpcionAcademica;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ResultadoCopia;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionConAsignaciones;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionArea;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionAreaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
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
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sprint 9, tanda 1 (sección 3.2, decisiones 139 y 140): las asignaciones de docentes, tutores y auxiliares a las secciones.
 * Dar acceso a los datos de menores lo deciden dos personas; quitarlo, una:
 * <ul>
 *   <li><b>proponer</b>: Administración o Dirección, con motivo. Nace PROPUESTA con su solicitud {@code ASIGNACION_DOCENTE};
 *       la aprueba OTRA persona de Dirección o Promotoría con su firma en la bandeja ({@link ManejadorAsignacionDocente}),
 *       nunca quien la propuso ni el docente asignado;</li>
 *   <li><b>proponer las del año anterior</b>: una propuesta (y su solicitud) por cada asignación del año anterior cuya
 *       sección tiene una igual este año (mismo grado y nombre) y cuyo docente sigue activo con el rol DOCENTE;</li>
 *   <li><b>terminar</b>: Dirección o Promotoría, con motivo y la firma de su sesión ({@code asignacion_docente:{id}:TERMINADA}).
 *       Terminar no borra nada: lo que registró ese docente sigue a su nombre.</li>
 * </ul>
 * Todo queda en la bitácora. En MySQL lo exigen trg_asignacion_docente_nace y trg_asignacion_docente_estado.
 * <p>
 * Sprint 10, tanda 1 (sección 3.4, A2, E24 y E25): la propuesta lleva las ÁREAS DEL CATÁLOGO que calificará el docente,
 * del nivel de la sección y de la versión del currículo del año (sin la transversal: la califica el tutor). Se insertan en
 * la MISMA transacción, después de la asignación y ANTES de su solicitud, cuyo {@code datos} lleva {@code "areas"} con los
 * ids ordenados: la base compara esa lista con las áreas al aprobarla. Nadie las agrega después. Una cobertura y el auxiliar
 * no llevan áreas. Cambiar las áreas de una asignación vigente es proponer otra igual con las nuevas: al aprobarla, el
 * manejador termina la anterior con la firma de quien aprueba.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class Asignaciones {

	static final String ENTIDAD = "asignacion_docente";

	/** Sprint 9, tanda 2 (A7): la cobertura de un ausente, como máximo 5 días hábiles. */
	static final int MAX_HABILES_COBERTURA = 5;

	private static final Set<EstadoAsignacion> ABIERTAS = EnumSet.of(EstadoAsignacion.PROPUESTA, EstadoAsignacion.VIGENTE);

	private final AsignacionDocenteRepository asignaciones;

	private final SeccionRepository secciones;

	private final AnioEscolarRepository anios;

	private final UsuarioRepository usuarios;

	private final RegistroSolicitudes solicitudes;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	private final CalendarioHabil calendario;

	private final AsignacionAreaRepository areasAsignadas;

	private final ConfiguracionEvaluacionRepository configuraciones;

	private final Catalogo catalogo;

	public Asignaciones(AsignacionDocenteRepository asignaciones, SeccionRepository secciones, AnioEscolarRepository anios,
			UsuarioRepository usuarios, RegistroSolicitudes solicitudes, FirmaSesion firma, AuditoriaService auditoria,
			Clock reloj, CalendarioHabil calendario, AsignacionAreaRepository areasAsignadas,
			ConfiguracionEvaluacionRepository configuraciones, Catalogo catalogo) {
		this.calendario = calendario;
		this.areasAsignadas = areasAsignadas;
		this.configuraciones = configuraciones;
		this.catalogo = catalogo;
		this.asignaciones = asignaciones;
		this.secciones = secciones;
		this.anios = anios;
		this.usuarios = usuarios;
		this.solicitudes = solicitudes;
		this.firma = firma;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** La pantalla del año (por defecto, el año en curso o el último que no está cerrado). */
	@Transactional(readOnly = true)
	public AsignacionesVista vista(Long anioId) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		List<AnioEscolar> todos = anios.findAllByOrderByAnioDesc();
		Optional<AnioEscolar> elegido = anioId != null ? todos.stream().filter(a -> a.getId().equals(anioId)).findFirst()
				: anios.findByEstado(EstadoAnioEscolar.EN_CURSO)
						.or(() -> todos.stream().filter(a -> !a.cerrado()).findFirst())
						.or(() -> todos.stream().findFirst());
		if (anioId != null && elegido.isEmpty()) {
			throw new RecursoNoEncontradoException("Año escolar no encontrado");
		}
		boolean puedeProponer = persona.roles().contains(Rol.DIRECTOR) || persona.roles().contains(Rol.ADMINISTRACION);
		boolean puedeTerminar = persona.roles().contains(Rol.DIRECTOR) || persona.roles().contains(Rol.PROMOTOR);
		List<OpcionAcademica> opciones = todos.stream().map(a -> new OpcionAcademica(a.getId(), a.getAnio() + " · "
				+ a.getEstado().name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT))).toList();
		if (elegido.isEmpty()) {
			return new AsignacionesVista(opciones, null, null, false, List.of(), List.of(), puedeProponer, puedeTerminar,
					false, List.of(), List.of(), false);
		}
		AnioEscolar anio = elegido.get();
		List<AsignacionDocente> delAnio = asignaciones.findByAnioEscolarIdOrderBySeccionIdAscIdAsc(anio.getId());
		Map<Long, List<Long>> areasPorAsignacion = areasDe(delAnio);
		Map<Long, String> nombresDeAreas = catalogo.areas(areasPorAsignacion.values().stream().flatMap(List::stream)
				.collect(Collectors.toSet())).stream().collect(Collectors.toMap(AreaDelCatalogo::id, AreaDelCatalogo::nombre));
		Map<Long, Optional<Usuario>> personas = new LinkedHashMap<>();
		Map<Long, List<AsignacionVista>> porSeccion = new LinkedHashMap<>();
		Map<Long, Seccion> seccionesDelAnio = new LinkedHashMap<>();
		List<Seccion> todasLasSecciones = secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(anio.getId());
		todasLasSecciones.forEach(s -> seccionesDelAnio.put(s.getId(), s));
		List<AsignacionSinAreas> sinAreas = new ArrayList<>();
		for (AsignacionDocente a : delAnio) {
			Optional<Usuario> docente = personas.computeIfAbsent(a.getUsuarioId(), usuarios::findById);
			List<Long> suyas = areasPorAsignacion.getOrDefault(a.getId(), List.of());
			String textoAreas = suyas.isEmpty() ? a.getAreas()
					: suyas.stream().map(id -> nombresDeAreas.getOrDefault(id, "área " + id)).collect(Collectors.joining(", "));
			porSeccion.computeIfAbsent(a.getSeccionId(), id -> new ArrayList<>()).add(new AsignacionVista(a.getId(),
					docente.map(Usuario::getNombreCompleto).orElse("—"), docente.map(Usuario::getNombreUsuario).orElse("—"),
					a.getTipo().etiqueta(), textoAreas, a.getEstado().etiqueta(), a.getEstado().variante(), a.esVigente(),
					a.getPropuestoPor(), a.getResueltoPor(), a.getResueltoEn(), a.getTerminadaPor(), a.getMotivoFin()));
			// «Asignaciones sin áreas del catálogo» (las vigentes del sprint 9): marcan asistencia, pero no califican.
			if (a.esVigente() && a.getTipo() != TipoAsignacion.AUXILIAR && a.getHasta() == null && suyas.isEmpty()) {
				Seccion s = seccionesDelAnio.get(a.getSeccionId());
				sinAreas.add(new AsignacionSinAreas(a.getId(), docente.map(Usuario::getNombreCompleto).orElse("—"),
						a.getTipo().etiqueta(), s == null ? "—" : s.etiqueta(), a.getAreas(), a.getSeccionId()));
			}
		}
		Optional<ConfiguracionEvaluacion> configuracion = configuraciones.findByAnioEscolarId(anio.getId());
		List<AreasDelAnio> areasDelAnio = configuracion.map(c -> java.util.Arrays.stream(Nivel.values())
				.map(n -> new AreasDelAnio(n, n.etiqueta(), catalogo.areasDe(c.getCurriculoVersionId(), n).stream()
						.filter(x -> !x.transversal()).toList()))
				.filter(g -> !g.areas().isEmpty()).toList()).orElse(List.of());
		List<SeccionConAsignaciones> conAsignaciones = todasLasSecciones
				.stream().map(s -> new SeccionConAsignaciones(s.getId(), s.etiqueta(), s.isActiva(),
						ordenadas(porSeccion.getOrDefault(s.getId(), List.of())))).toList();
		List<OpcionAcademica> docentes = usuarios.activosConRol(Rol.DOCENTE).stream()
				.filter(u -> u.getApoderadoId() == null && !combinaConCobranza(u))
				.sorted(Comparator.comparing(Usuario::getNombreCompleto, String.CASE_INSENSITIVE_ORDER))
				.map(u -> new OpcionAcademica(u.getId(), u.getNombreCompleto() + " (" + u.getNombreUsuario() + ")")).toList();
		boolean anterior = anios.findByAnio(anio.getAnio() - 1).map(a -> !asignaciones
				.findByAnioEscolarIdAndEstadoInOrderBySeccionIdAscIdAsc(a.getId(), copiables()).isEmpty()).orElse(false);
		return new AsignacionesVista(opciones, anio.getId(), anio.getAnio(), anio.cerrado(), conAsignaciones, docentes,
				puedeProponer, puedeTerminar, anterior, areasDelAnio, sinAreas, configuracion.isPresent());
	}

	/** Las áreas del catálogo (ids, ordenados) de cada asignación. */
	private Map<Long, List<Long>> areasDe(List<AsignacionDocente> lista) {
		if (lista.isEmpty()) {
			return Map.of();
		}
		return areasAsignadas.findByAsignacionIdInOrderByAreaIdAsc(lista.stream().map(AsignacionDocente::getId).toList())
				.stream().collect(Collectors.groupingBy(AsignacionArea::getAsignacionId,
						Collectors.mapping(AsignacionArea::getAreaId, Collectors.toList())));
	}

	/** Propone una asignación: queda PROPUESTA, con su solicitud para que otra persona la apruebe. @return su id */
	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional
	public Long proponer(ProponerAsignacionRequest pedido) {
		Seccion seccion = secciones.findById(pedido.seccionId())
				.orElseThrow(() -> new RecursoNoEncontradoException("Sección no encontrada"));
		Usuario docente = usuarios.findById(pedido.docenteId())
				.orElseThrow(() -> new RecursoNoEncontradoException("Docente no encontrado"));
		return proponerEn(seccion, docente, pedido.tipo(), pedido.areas(), pedido.motivo(), pedido.hasta(),
				pedido.areaIds(), Boolean.TRUE.equals(pedido.todasLasAreas()));
	}

	/**
	 * Propone para este año las asignaciones del año anterior (las vigentes y las que terminó el sistema al cerrar el año),
	 * en la sección con el mismo grado y nombre. Se salta las que ya existen, las de secciones que este año no hay y las de
	 * cuentas que ya no son docentes activos. Cada propuesta lleva su solicitud: la bandeja las aprueba una por una.
	 */
	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional
	public ResultadoCopia proponerLasDelAnioAnterior(Long anioId, String motivo) {
		AnioEscolar anio = anios.findById(anioId).orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado"));
		exigirAbierto(anio);
		AnioEscolar anterior = anios.findByAnio(anio.getAnio() - 1).orElseThrow(() -> new ReglaNegocioException(
				"No hay un año " + (anio.getAnio() - 1) + " del que copiar las asignaciones."));
		List<AsignacionDocente> previas = asignaciones.findByAnioEscolarIdAndEstadoInOrderBySeccionIdAscIdAsc(
				anterior.getId(), copiables()).stream().filter(a -> a.esVigente()
						|| (a.getTerminadaPor() != null && a.getTerminadaPor().startsWith("sistema."))).toList();
		if (previas.isEmpty()) {
			throw new ReglaNegocioException("El año " + anterior.getAnio() + " no tiene asignaciones que copiar.");
		}
		int propuestas = 0;
		int saltadas = 0;
		Set<String> vistas = new HashSet<>();
		for (AsignacionDocente previa : previas) {
			Optional<Seccion> origen = secciones.findById(previa.getSeccionId());
			Optional<Seccion> destino = origen.flatMap(o -> secciones.findByAnioEscolarIdAndGradoAndNombre(anio.getId(),
					o.getGrado(), o.getNombre())).filter(Seccion::isActiva);
			Optional<Usuario> docente = usuarios.findById(previa.getUsuarioId()).filter(this::esDocenteAsignable);
			String clave = destino.map(Seccion::getId).orElse(0L) + ":" + previa.getUsuarioId() + ":" + previa.getTipo();
			if (destino.isEmpty() || docente.isEmpty() || !vistas.add(clave)
					|| asignaciones.existsByUsuarioIdAndSeccionIdAndTipoAndEstadoIn(previa.getUsuarioId(),
							destino.get().getId(), previa.getTipo(), ABIERTAS)
					|| (previa.getTipo() == TipoAsignacion.TUTOR && tutorAbierto(destino.get().getId()))) {
				saltadas++;
				continue;
			}
			proponerEn(destino.get(), docente.get(), previa.getTipo(), previa.getAreas(), motivo, null,
					areasCopiables(previa, anterior, anio), false);
			propuestas++;
		}
		return new ResultadoCopia(propuestas, saltadas);
	}

	/** Termina una asignación vigente, con motivo y la firma de la sesión: el docente deja de ver la sección. */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	@Transactional
	public void terminar(Long asignacionId, String motivo) {
		AsignacionDocente asignacion = asignaciones.bloquear(asignacionId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Asignación no encontrada"));
		if (!asignacion.esVigente()) {
			throw new ReglaNegocioException("Solo se termina una asignación vigente (esta está "
					+ asignacion.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT) + ").");
		}
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		String texto = Normalizador.limpiar(motivo);
		// La firma de quien termina, antes del cambio (trg_asignacion_docente_estado la exige a su nombre).
		firma.firmar(ClaveFirma.asignacionTerminada(asignacion.getId()));
		asignacion.terminar(persona.getUsername(), texto, ahora());
		asignaciones.saveAndFlush(asignacion);
		auditoria.registrar(AccionAuditoria.ASIGNACION_DOCENTE_TERMINADA, ENTIDAD, asignacion.getId().toString(),
				EstadoAsignacion.VIGENTE.name(), EstadoAsignacion.TERMINADA.name(), persona.nombreCompleto()
						+ " terminó la asignación de " + descripcion(asignacion) + ". Motivo: " + asignacion.getMotivoFin()
						+ ". Desde ahora no ve la sección; lo que registró sigue a su nombre.");
	}

	private Long proponerEn(Seccion seccion, Usuario docente, TipoAsignacion tipo, String areas, String motivo,
			LocalDate hasta, List<Long> areaIds, boolean todasLasAreas) {
		AnioEscolar anio = seccion.getAnioEscolar();
		exigirAbierto(anio);
		exigirCoberturaValida(tipo, hasta);
		if (!seccion.isActiva()) {
			throw new ReglaNegocioException("La sección " + seccion.etiqueta() + " está desactivada.");
		}
		if (docente.getApoderadoId() != null || !docente.getRoles().contains(Rol.DOCENTE)) {
			throw new ReglaNegocioException("Solo se asigna una cuenta del personal con el rol Docente (dáselo en Usuarios).");
		}
		if (!docente.isActivo()) {
			throw new ReglaNegocioException("La cuenta de " + docente.getNombreCompleto() + " está desactivada.");
		}
		if (combinaConCobranza(docente)) {
			throw new ReglaNegocioException("La cuenta de " + docente.getNombreCompleto() + " combina Docente con Caja o "
					+ "Administración (ve quién debe): corrígela en Usuarios antes de asignarla.");
		}
		if (tipo == null) {
			throw new ReglaNegocioException("Elige qué hará en la sección: tutor(a), docente de área o auxiliar.");
		}
		if (asignaciones.existsByUsuarioIdAndSeccionIdAndTipoAndEstadoIn(docente.getId(), seccion.getId(), tipo,
				EnumSet.of(EstadoAsignacion.PROPUESTA))) {
			throw new ReglaNegocioException(docente.getNombreCompleto() + " ya es " + tipo.etiqueta().toLowerCase(
					java.util.Locale.ROOT) + " de " + seccion.etiqueta() + " por aprobarse: apruébala o recházala antes de "
					+ "proponer otra.");
		}
		List<AreaDelCatalogo> delCatalogo = areasValidas(seccion, tipo, hasta, areaIds, todasLasAreas);
		List<Long> idsDeAreas = delCatalogo.stream().map(AreaDelCatalogo::id).sorted().toList();
		// Sprint 10 (sección 3.4): una vigente con las mismas claves se reemplaza al aprobar esta, si cambian sus áreas.
		Optional<AsignacionDocente> vigente = asignaciones.findFirstByUsuarioIdAndSeccionIdAndTipoAndEstado(docente.getId(),
				seccion.getId(), tipo, EstadoAsignacion.VIGENTE);
		if (vigente.isPresent()) {
			List<Long> actuales = areasAsignadas.findByAsignacionIdOrderByAreaIdAsc(vigente.get().getId()).stream()
					.map(AsignacionArea::getAreaId).sorted().toList();
			if (actuales.equals(idsDeAreas) || hasta != null || vigente.get().getHasta() != null) {
				throw new ReglaNegocioException(docente.getNombreCompleto() + " ya es " + tipo.etiqueta().toLowerCase(
						java.util.Locale.ROOT) + " de " + seccion.etiqueta() + (actuales.equals(idsDeAreas)
								? " con esas mismas áreas." : ": una cobertura no reemplaza una asignación vigente."));
			}
		}
		if (tipo == TipoAsignacion.TUTOR && asignaciones.findFirstBySeccionIdAndTipoAndEstado(seccion.getId(),
				TipoAsignacion.TUTOR, EstadoAsignacion.VIGENTE).filter(t -> !docente.getId().equals(t.getUsuarioId()))
				.isPresent()) {
			throw new ReglaNegocioException(seccion.etiqueta() + " ya tiene tutor(a): termina esa asignación antes de "
					+ "proponer otra.");
		}
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		AsignacionDocente asignacion = asignaciones.saveAndFlush(AsignacionDocente.propuesta(anio.getId(), seccion.getId(),
				docente.getId(), tipo, delCatalogo.isEmpty() ? areas : null, persona.getUsername(), hasta));
		// A2: las áreas, en la MISMA transacción y ANTES de enlazar la solicitud (trg_asignacion_area_nace lo exige).
		for (Long area : idsDeAreas) {
			areasAsignadas.saveAndFlush(AsignacionArea.de(asignacion.getId(), area));
		}
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("seccion", seccion.getId().toString());
		datos.put("docente", docente.getId().toString());
		datos.put("tipo", tipo.name());
		if (hasta != null) {
			datos.put("hasta", hasta.toString());
		}
		if (!idsDeAreas.isEmpty()) {
			// A2: la base compara esta lista con las áreas de la asignación al pasarla a VIGENTE.
			datos.put("areas", idsDeAreas.stream().map(String::valueOf).collect(Collectors.joining(",")));
		}
		String textoAreas = delCatalogo.isEmpty() ? asignacion.getAreas()
				: delCatalogo.stream().map(AreaDelCatalogo::nombre).collect(Collectors.joining(", "));
		SolicitudCambio solicitud = solicitudes.crear(TipoSolicitud.ASIGNACION_DOCENTE, ENTIDAD, asignacion.getId(),
				resumen(docente, tipo, textoAreas, seccion), datos, motivo);
		asignacion.enlazarSolicitud(solicitud.getId());
		asignaciones.saveAndFlush(asignacion);
		auditoria.registrar(AccionAuditoria.ASIGNACION_DOCENTE_PROPUESTA, ENTIDAD, asignacion.getId().toString(), null,
				EstadoAsignacion.PROPUESTA.name(), persona.nombreCompleto() + " propuso a " + docente.getNombreCompleto()
						+ " (" + docente.getNombreUsuario() + ") como " + tipo.etiqueta().toLowerCase(java.util.Locale.ROOT)
						+ " de " + seccion.etiqueta() + " " + anio.getAnio() + (textoAreas == null ? ""
								: " (" + textoAreas + ")") + (hasta == null ? "" : ", como cobertura hasta el "
										+ hasta) + (vigente.isPresent() ? ", en reemplazo de su asignación vigente "
												+ vigente.get().getId() : "") + ". Motivo: " + solicitud.getMotivo()
						+ ". Debe aprobarla otra persona de Dirección o Promotoría, que no sea el docente.");
		return asignacion.getId();
	}

	/**
	 * Las áreas pedidas, validadas: del catálogo de la versión del año (configuración de evaluación), del nivel de la
	 * sección y no transversales; ninguna para el auxiliar ni para una cobertura (E25). «Todas las áreas del grado», solo para
	 * el tutor de Inicial o Primaria.
	 */
	private List<AreaDelCatalogo> areasValidas(Seccion seccion, TipoAsignacion tipo, LocalDate hasta, List<Long> areaIds,
			boolean todasLasAreas) {
		List<Long> pedidas = areaIds == null ? List.of() : areaIds;
		if (pedidas.isEmpty() && !todasLasAreas) {
			return List.of();
		}
		if (tipo == TipoAsignacion.AUXILIAR || hasta != null) {
			throw new ReglaNegocioException("El auxiliar y una cobertura no califican: no llevan áreas.");
		}
		ConfiguracionEvaluacion configuracion = configuraciones.findByAnioEscolarId(seccion.getAnioEscolar().getId())
				.orElseThrow(() -> new ReglaNegocioException("Para dar áreas, Dirección guarda primero la evaluación del año "
						+ seccion.getAnioEscolar().getAnio() + " (con la versión del currículo)."));
		Nivel nivel = seccion.nivel();
		List<AreaDelCatalogo> delNivel = catalogo.areasDe(configuracion.getCurriculoVersionId(), nivel).stream()
				.filter(a -> !a.transversal()).toList();
		if (todasLasAreas) {
			if (tipo != TipoAsignacion.TUTOR || nivel == Nivel.SECUNDARIA) {
				throw new ReglaNegocioException("«Todas las áreas del grado» es para el tutor(a) de Inicial o Primaria.");
			}
			return delNivel;
		}
		Set<Long> validas = delNivel.stream().map(AreaDelCatalogo::id).collect(Collectors.toSet());
		if (!validas.containsAll(pedidas)) {
			throw new ReglaNegocioException("Elige áreas de " + nivel.etiqueta() + " del currículo del año (las "
					+ "competencias transversales las califica el tutor y no se asignan).");
		}
		return delNivel.stream().filter(a -> pedidas.contains(a.id())).toList();
	}

	/** Al copiar las del año anterior: sus áreas, si el año nuevo usa la misma versión del currículo (si no, ninguna). */
	private List<Long> areasCopiables(AsignacionDocente previa, AnioEscolar anterior, AnioEscolar nuevo) {
		if (previa.getTipo() == TipoAsignacion.AUXILIAR || previa.getHasta() != null) {
			return List.of();
		}
		Optional<Long> versionAnterior = configuraciones.findByAnioEscolarId(anterior.getId())
				.map(ConfiguracionEvaluacion::getCurriculoVersionId);
		Optional<Long> versionNueva = configuraciones.findByAnioEscolarId(nuevo.getId())
				.map(ConfiguracionEvaluacion::getCurriculoVersionId);
		if (versionAnterior.isEmpty() || !versionAnterior.equals(versionNueva)) {
			return List.of();
		}
		return areasAsignadas.findByAsignacionIdOrderByAreaIdAsc(previa.getId()).stream().map(AsignacionArea::getAreaId)
				.toList();
	}

	static String resumen(Usuario docente, TipoAsignacion tipo, String areas, Seccion seccion) {
		return docente.getNombreCompleto() + " (" + docente.getNombreUsuario() + ") como "
				+ tipo.etiqueta().toLowerCase(java.util.Locale.ROOT) + (areas == null ? "" : " (" + areas + ")") + " de "
				+ seccion.etiqueta() + " " + seccion.getAnioEscolar().getAnio();
	}

	private String descripcion(AsignacionDocente a) {
		String docente = usuarios.findById(a.getUsuarioId()).map(u -> u.getNombreCompleto() + " (" + u.getNombreUsuario()
				+ ")").orElse("la cuenta " + a.getUsuarioId());
		String seccion = secciones.findById(a.getSeccionId()).map(Seccion::etiqueta).orElse("la sección " + a.getSeccionId());
		return docente + " como " + a.getTipo().etiqueta().toLowerCase(java.util.Locale.ROOT) + " de " + seccion;
	}

	private boolean esDocenteAsignable(Usuario u) {
		return u.isActivo() && u.getApoderadoId() == null && u.getRoles().contains(Rol.DOCENTE) && !combinaConCobranza(u);
	}

	/** Una cuenta anterior a V32 con DOCENTE y CAJA o ADMINISTRACION: no se le asignan secciones (decisión 141). */
	static boolean combinaConCobranza(Usuario u) {
		return u.getRoles().contains(Rol.DOCENTE)
				&& (u.getRoles().contains(Rol.CAJA) || u.getRoles().contains(Rol.ADMINISTRACION));
	}

	private boolean tutorVigente(Long seccionId) {
		return asignaciones.findFirstBySeccionIdAndTipoAndEstado(seccionId, TipoAsignacion.TUTOR, EstadoAsignacion.VIGENTE)
				.isPresent();
	}

	private boolean tutorAbierto(Long seccionId) {
		return tutorVigente(seccionId) || asignaciones.findFirstBySeccionIdAndTipoAndEstado(seccionId, TipoAsignacion.TUTOR,
				EstadoAsignacion.PROPUESTA).isPresent();
	}

	/**
	 * Sprint 9, tanda 2 (A7, E56): una cobertura de un docente ausente dura como máximo {@value #MAX_HABILES_COBERTURA}
	 * días hábiles desde hoy (con feriados; la base acepta 10 calendario) y nunca es de tutor.
	 */
	private void exigirCoberturaValida(TipoAsignacion tipo, LocalDate hasta) {
		if (hasta == null) {
			return;
		}
		if (tipo == TipoAsignacion.TUTOR) {
			throw new ReglaNegocioException("Una cobertura no es de tutor(a): propón al docente o auxiliar que cubre.");
		}
		LocalDate hoy = LocalDate.now(reloj);
		if (hasta.isBefore(hoy)) {
			throw new ReglaNegocioException("La cobertura termina hoy o después.");
		}
		if (hasta.isAfter(hoy.plusDays(10)) || !calendario.dentroDeHabiles(hoy, hasta, MAX_HABILES_COBERTURA)) {
			throw new ReglaNegocioException("Una cobertura dura como máximo " + MAX_HABILES_COBERTURA + " días hábiles: "
					+ "para más tiempo, propón una asignación sin fecha de fin.");
		}
	}

	private static void exigirAbierto(AnioEscolar anio) {
		if (anio.cerrado()) {
			throw new ReglaNegocioException("El año " + anio.getAnio() + " está cerrado: ya no se asignan docentes.");
		}
	}

	private static Set<EstadoAsignacion> copiables() {
		return EnumSet.of(EstadoAsignacion.VIGENTE, EstadoAsignacion.TERMINADA);
	}

	private static List<AsignacionVista> ordenadas(List<AsignacionVista> lista) {
		List<AsignacionVista> copia = new ArrayList<>(lista);
		copia.sort(Comparator.comparing((AsignacionVista a) -> !a.vigente()).thenComparing(AsignacionVista::id));
		return copia;
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}
}
