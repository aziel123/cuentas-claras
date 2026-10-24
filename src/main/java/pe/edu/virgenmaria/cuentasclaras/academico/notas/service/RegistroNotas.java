package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.api.SeccionesDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.CompetenciaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.ValorEscala;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.HojaNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotasRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.ResultadoNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.Calificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.HuellaConclusion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.LoteCalificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.NivelLogro;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.CalificacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.LoteCalificacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.service.SesionDeQuienEscribe;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.MisNotasVista;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 2 (sección 3.5): la hoja de notas del docente y su guardado. Solo el docente con esa área asignada en la
 * sección (el tutor, en las transversales: S2) la abre y la guarda; una sección o un área ajenas, o de otro colegio,
 * responden 404 (E2). Dirección y Promotoría no ponen notas (403), aunque las vean; el auxiliar y una cobertura, tampoco.
 * <p>
 * El guardado es <b>un POST, un lote y una firma</b> ({@code lote_calificacion:{periodo}:{seccion}:{area}:{secuencia}}): una
 * fila por celda que cambia algo, en la cadena de la nota vigente. Con el periodo ABIERTO, notas nuevas y cambios (también a
 * «sin nota»); con el periodo CERRADO, solo con la nota tardía aprobada (48 h, competencias sin nota): lo demás va por
 * corrección (E1). Reciben nota las matrículas ACTIVAS y RESERVADAS (C1). La conclusión es obligatoria cuando la exige la
 * escala del catálogo o el colegio, y nunca va a la bitácora (solo la huella del lote). En MySQL lo exigen
 * trg_lote_calificacion_nace y trg_calificacion_nace.
 */
@Service
@PreAuthorize("hasRole('DOCENTE')")
public class RegistroNotas {

	static final String ENTIDAD_LOTE = "lote_calificacion";

	private final ContextoNotas contexto;

	private final SeccionesDocente seccionesDocente;

	private final CalificacionRepository calificaciones;

	private final LoteCalificacionRepository lotes;

	private final TardiasAprobadas tardias;

	private final SesionDeQuienEscribe sesiones;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public RegistroNotas(ContextoNotas contexto, SeccionesDocente seccionesDocente, CalificacionRepository calificaciones,
			LoteCalificacionRepository lotes, TardiasAprobadas tardias, SesionDeQuienEscribe sesiones, FirmaSesion firma,
			AuditoriaService auditoria, Clock reloj) {
		this.contexto = contexto;
		this.seccionesDocente = seccionesDocente;
		this.calificaciones = calificaciones;
		this.lotes = lotes;
		this.tardias = tardias;
		this.sesiones = sesiones;
		this.firma = firma;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** Lo que califica el docente en sesión, dónde le faltan áreas del catálogo y sus notas tardías por registrar. */
	@Transactional(readOnly = true)
	public MisNotasVista mias() {
		List<MisNotasVista.AreaDeSeccion> areas = new ArrayList<>();
		List<String> sinAreas = new ArrayList<>();
		for (SeccionDocente s : seccionesDocente.mias()) {
			List<Long> suyas = seccionesDocente.areasDe(s.seccionId());
			if (suyas.isEmpty()) {
				if (seccionesDocente.sinAreasDelCatalogo(s.seccionId())) {
					sinAreas.add(s.seccion());
				}
				continue;
			}
			Seccion seccion = contexto.seccion(s.seccionId());
			ConfiguracionEvaluacion configuracion;
			try {
				configuracion = contexto.configuracion(seccion);
			}
			catch (ReglaNegocioException e) {
				continue;
			}
			contexto.catalogo().areasDe(configuracion.getCurriculoVersionId(), seccion.nivel()).stream()
					.filter(a -> suyas.contains(a.id()))
					.forEach(a -> areas.add(new MisNotasVista.AreaDeSeccion(seccion.getId(), seccion.etiqueta(), a.id(),
							a.nombre(), a.transversal())));
		}
		return new MisNotasVista(areas, sinAreas, tardias.porRegistrar());
	}

	/** La hoja de la sección, el área y el periodo (por defecto, el abierto); con {@code solicitudId}, la de la tardía. */
	@Transactional(readOnly = true)
	public HojaNotas hoja(long seccionId, long areaId, Long periodoId, Long solicitudId) {
		Seccion seccion = contexto.seccion(seccionId);
		if (!seccionesDocente.areasDe(seccionId).contains(areaId)) {
			if (seccionesDocente.sinAreasDelCatalogo(seccionId)) {
				return sinAreas(seccion, areaId);
			}
			throw new RecursoNoEncontradoException("Área no encontrada");
		}
		ConfiguracionEvaluacion configuracion = contexto.configuracion(seccion);
		AreaDelCatalogo area = contexto.area(seccion, configuracion, areaId);
		PeriodoEvaluacion periodo = contexto.periodo(seccion, periodoId);
		SolicitudCambio tardia = solicitudId == null ? null : tardias.de(solicitudId, seccion, areaId, periodo);
		List<CompetenciaDelCatalogo> competencias = contexto.competencias(seccion, configuracion, areaId);
		List<Matricula> matriculas = contexto.matriculas(seccionId);
		Set<Long> exonerados = contexto.exonerados(matriculas.stream().map(Matricula::getId).toList(), areaId, periodo);
		Map<String, Calificacion> vigentes = vigentes(periodo, matriculas.stream().map(Matricula::getId).toList(),
				competencias);
		List<HojaNotas.FilaAlumno> filas = new ArrayList<>();
		int sinNota = 0;
		for (Matricula m : matriculas) {
			List<HojaNotas.Celda> celdas = new ArrayList<>();
			for (CompetenciaDelCatalogo c : competencias) {
				Calificacion v = vigentes.get(m.getId() + ":" + c.id());
				boolean conNota = v != null && v.getNivelLogro() != null;
				if (!conNota && !exonerados.contains(m.getId())) {
					sinNota++;
				}
				celdas.add(new HojaNotas.Celda(c.id(), v == null ? null : v.getId(), v == null || v.getNivelLogro() == null ? null : v.getNivelLogro().name(),
						v == null ? null : v.getConclusion(), conNota));
			}
			Alumno a = m.getAlumno();
			filas.add(new HojaNotas.FilaAlumno(filas.size() + 1, m.getId(), apellidos(a), a.getNombres(),
					exonerados.contains(m.getId()), celdas));
		}
		AccesoMostrado.filas(filas.size());
		boolean abierto = periodo.getEstado() == EstadoPeriodo.ABIERTO && !periodo.getInicio().isAfter(hoy());
		boolean editable = tardia != null || abierto;
		String aviso = editable ? null : periodo.getEstado() == EstadoPeriodo.CERRADO ? "El " + periodo.getNombre()
				+ " está cerrado: un cambio va por corrección (la aprueba otra persona) y una competencia sin nota, por nota "
				+ "tardía." : "El " + periodo.getNombre() + " todavía no abre.";
		List<String> exigen = contexto.catalogo().escalaDe(configuracion.getCurriculoVersionId(), seccion.nivel()).stream()
				.filter(ValorEscala::exigeConclusion).map(ValorEscala::valor).toList();
		return new HojaNotas(seccion.getId(), seccion.etiqueta(), area.id(), area.nombre(), periodo.getId(),
				periodo.getNombre(), periodo.getEstado().etiqueta(), opciones(seccion), editable, tardia != null,
				tardia == null ? null : tardia.getId(), aviso, configuracion.getConclusionMax(),
				configuracion.isConclusionSiempre(), competencias.stream().map(c -> new HojaNotas.ColumnaCompetencia(c.id(),
						c.codigo(), c.nombre())).toList(), exigen, filas, sinNota);
	}

	/**
	 * Guarda la hoja: UN lote, UNA firma y una fila por celda que cambia algo, a nombre del docente en sesión. Sin cambios,
	 * no guarda ni firma. @return lo que guardó
	 */
	@Transactional
	public ResultadoNotas guardar(long seccionId, long areaId, long periodoId, NotasRequest pedido) {
		UsuarioAutenticado persona = Personas.actual();
		Seccion seccion = contexto.seccion(seccionId);
		if (!seccionesDocente.areasDe(seccionId).contains(areaId)) {
			throw new RecursoNoEncontradoException("Área no encontrada");
		}
		ConfiguracionEvaluacion configuracion = contexto.configuracion(seccion);
		AreaDelCatalogo area = contexto.area(seccion, configuracion, areaId);
		PeriodoEvaluacion periodo = contexto.periodo(seccion, periodoId);
		SolicitudCambio tardia = pedido.solicitudId() == null ? null
				: tardias.de(pedido.solicitudId(), seccion, areaId, periodo);
		if (tardia == null && (periodo.getEstado() != EstadoPeriodo.ABIERTO || periodo.getInicio().isAfter(hoy()))) {
			throw new ReglaNegocioException(periodo.getEstado() == EstadoPeriodo.CERRADO ? "El " + periodo.getNombre()
					+ " está cerrado: la nota va por solicitud (corrección o nota tardía)." : "El " + periodo.getNombre()
							+ " todavía no abre.");
		}
		List<CompetenciaDelCatalogo> competencias = contexto.competencias(seccion, configuracion, areaId);
		Map<Long, CompetenciaDelCatalogo> porId = competencias.stream()
				.collect(Collectors.toMap(CompetenciaDelCatalogo::id, c -> c));
		Map<Long, Matricula> matriculas = new LinkedHashMap<>();
		contexto.matriculas(seccionId).forEach(m -> matriculas.put(m.getId(), m));
		Set<Long> exonerados = contexto.exonerados(List.copyOf(matriculas.keySet()), areaId, periodo);
		Map<String, Calificacion> vigentes = vigentes(periodo, List.copyOf(matriculas.keySet()), competencias);
		Map<String, Boolean> exige = new HashMap<>();
		contexto.catalogo().escalaDe(configuracion.getCurriculoVersionId(), seccion.nivel())
				.forEach(v -> exige.put(v.valor(), v.exigeConclusion() || configuracion.isConclusionSiempre()));
		List<Pendiente> filas = new ArrayList<>();
		java.util.Set<String> vistas = new java.util.HashSet<>();
		for (NotaPedida p : pedido.notas()) {
			if (p == null || p.matriculaId() == null || !matriculas.containsKey(p.matriculaId())
					|| p.competenciaId() == null || !porId.containsKey(p.competenciaId())) {
				throw new ReglaNegocioException("La hoja tiene un alumno o una competencia que no son de " + seccion.etiqueta()
						+ " · " + area.nombre() + ": vuelve a abrirla.");
			}
			if (!vistas.add(p.matriculaId() + ":" + p.competenciaId())) {
				throw new ReglaNegocioException("La hoja repite una celda: vuelve a abrirla.");
			}
			NivelLogro nivel = NivelLogro.de(p.nivel());
			String conclusion = conclusionValida(p, nivel, exige, configuracion, porId.get(p.competenciaId()),
					matriculas.get(p.matriculaId()));
			Calificacion vigente = vigentes.get(p.matriculaId() + ":" + p.competenciaId());
			if (exonerados.contains(p.matriculaId())) {
				if (nivel != null) {
					throw new ReglaNegocioException(nombre(matriculas.get(p.matriculaId())) + " está exonerado de "
							+ area.nombre() + ": no se califica.");
				}
				continue;
			}
			if (vigente == null && nivel == null) {
				continue;
			}
			if (vigente != null && vigente.igualA(nivel, conclusion)) {
				continue;
			}
			if (tardia != null && vigente != null) {
				throw new ReglaNegocioException("La nota tardía es solo para competencias sin nota: "
						+ nombre(matriculas.get(p.matriculaId())) + " ya tiene nota en " + porId.get(p.competenciaId())
								.codigo() + " (un cambio va por corrección).");
			}
			filas.add(new Pendiente(matriculas.get(p.matriculaId()), p.competenciaId(), vigente, nivel, conclusion));
		}
		if (filas.isEmpty()) {
			throw new ReglaNegocioException("No hay notas nuevas ni cambios que guardar.");
		}
		Long sesion = sesiones.de(persona);
		int secuencia = lotes.ultimaSecuencia(periodo.getId(), seccionId, areaId) + 1;
		// Una firma por lote (un POST), antes del INSERT que la exige (trg_lote_calificacion_nace).
		firma.firmar(ClaveFirma.loteCalificacion(periodo.getId(), seccionId, areaId, secuencia));
		LocalDateTime ahora = ahora();
		LoteCalificacion lote = lotes.saveAndFlush(LoteCalificacion.nuevo(periodo.getId(), seccionId, areaId, secuencia,
				tardia == null ? null : tardia.getId(), sesion, filas.size(), ahora));
		int nuevas = 0;
		int cambios = 0;
		int sinNota = 0;
		List<String> huella = new ArrayList<>();
		for (Pendiente f : filas) {
			Calificacion nueva;
			if (f.vigente() == null) {
				nueva = Calificacion.inicial(new Calificacion.Donde(seccion.getAnioEscolar().getId(), periodo.getId(),
						seccionId, f.matricula().getId(), f.matricula().getAlumno().getId(), f.competencia()), f.nivel(),
						f.conclusion(), lote.getId(), tardia != null, ahora);
				nuevas++;
			}
			else {
				// Traslado a mitad de periodo: la vigente puede ser de la sección anterior; el cambio va en esta.
				nueva = Calificacion.cambio(f.vigente(), seccionId, f.nivel(), f.conclusion(), lote.getId(), ahora);
				if (f.nivel() == null) {
					sinNota++;
				}
				else {
					cambios++;
				}
			}
			calificaciones.save(nueva);
			huella.add(f.matricula().getId() + ":" + f.competencia() + ":" + (f.nivel() == null ? "-" : f.nivel().name())
					+ ":" + (nueva.getConclusionSha256() == null ? "" : nueva.getConclusionSha256()));
		}
		calificaciones.flush();
		ResultadoNotas resultado = new ResultadoNotas(lote.getId(), secuencia, nuevas, cambios, sinNota);
		String sha = HuellaConclusion.de(String.join("\n", huella));
		auditoria.registrar(tardia == null ? AccionAuditoria.NOTAS_GUARDADAS : AccionAuditoria.CALIFICACION_TARDIA_REGISTRADA,
				ENTIDAD_LOTE, lote.getId().toString(), null, resultado.filas() + " filas; huella " + sha,
				persona.nombreCompleto() + " guardó notas de " + area.nombre() + " en " + seccion.etiqueta() + " ("
						+ periodo.getNombre() + ", lote " + secuencia + "): " + resultado.resumen()
						+ (tardia == null ? "" : ". Nota tardía con la solicitud " + tardia.getId()) + ".");
		return resultado;
	}

	/** Valida la conclusión de una celda: obligatoria cuando la escala o el colegio la exigen, y de largo permitido. */
	private static String conclusionValida(NotaPedida p, NivelLogro nivel, Map<String, Boolean> exige,
			ConfiguracionEvaluacion configuracion, CompetenciaDelCatalogo competencia, Matricula matricula) {
		String texto = p.conclusion() == null || p.conclusion().isBlank() ? null : p.conclusion().strip();
		if (nivel == null) {
			return null;
		}
		if (texto == null && Boolean.TRUE.equals(exige.get(nivel.name()))) {
			throw new ReglaNegocioException("La norma exige la conclusión descriptiva para " + nivel.name() + " en "
					+ competencia.codigo() + " (" + nombre(matricula) + "): escribe qué logró y qué le recomiendas.");
		}
		if (texto != null && (texto.length() < Calificacion.MIN_CONCLUSION || texto.length() > configuracion
				.getConclusionMax())) {
			throw new ReglaNegocioException("La conclusión de " + nombre(matricula) + " en " + competencia.codigo()
					+ " va de " + Calificacion.MIN_CONCLUSION + " a " + configuracion.getConclusionMax() + " caracteres.");
		}
		return texto;
	}

	/**
	 * Las vigentes de las matrículas de la hoja (QA del sprint 10): por matrícula y no por sección, porque la cadena es de la
	 * matrícula. Con la sección, un alumno trasladado a mitad de periodo aparecía «sin nota» y su primera nota en la sección
	 * nueva chocaba con uk_calificacion_inicial: el guardado de toda la hoja fallaba.
	 */
	private Map<String, Calificacion> vigentes(PeriodoEvaluacion periodo, List<Long> matriculas,
			List<CompetenciaDelCatalogo> competencias) {
		if (competencias.isEmpty() || matriculas.isEmpty()) {
			return Map.of();
		}
		Map<String, Calificacion> vigentes = new HashMap<>();
		calificaciones.vigentesDeMatriculas(periodo.getId(), matriculas, competencias.stream()
				.map(CompetenciaDelCatalogo::id).toList())
				.forEach(c -> vigentes.put(c.getMatriculaId() + ":" + c.getCompetenciaId(), c));
		return vigentes;
	}

	/** La hoja de quien tiene la asignación en la sección pero sin áreas del catálogo: el aviso, sin guardar. */
	private HojaNotas sinAreas(Seccion seccion, long areaId) {
		return new HojaNotas(seccion.getId(), seccion.etiqueta(), areaId, null, null, null, null, List.of(), false, false,
				null, "Tu asignación en " + seccion.etiqueta() + " todavía no tiene áreas del catálogo. Pide a Dirección que "
						+ "la complete.", 0, false, List.of(), List.of(), List.of(), 0);
	}

	private List<HojaNotas.OpcionPeriodo> opciones(Seccion seccion) {
		return contexto.periodosDe(seccion).stream().map(p -> new HojaNotas.OpcionPeriodo(p.getId(), p.getNombre(),
				p.getEstado().etiqueta())).toList();
	}

	static String apellidos(Alumno a) {
		return a.getApellidoMaterno() == null || a.getApellidoMaterno().isBlank() ? a.getApellidoPaterno()
				: a.getApellidoPaterno() + " " + a.getApellidoMaterno();
	}

	static String nombre(Matricula m) {
		return m.getAlumno().getNombres() + " " + m.getAlumno().getApellidoPaterno();
	}

	private LocalDate hoy() {
		return LocalDate.now(reloj);
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}

	private record Pendiente(Matricula matricula, Long competencia, Calificacion vigente, NivelLogro nivel,
			String conclusion) {

		Pendiente {
			Objects.requireNonNull(matricula, "matricula");
		}
	}
}
