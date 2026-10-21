package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AlumnoEnHoja;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.CorreccionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.HojaDelDia;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.HojaRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.MarcaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.OpcionSeccion;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.RegistroTardioRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ResultadoRegistro;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionesDeHoy;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.TardioAprobado;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConfiguracionAcademica;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MarcaAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.OrigenMarca;
import pe.edu.virgenmaria.cuentasclaras.academico.model.RegistroAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.MarcaAsistenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.RegistroAsistenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 9, tanda 2 (sección 3.4, decisiones 144 a 147): la asistencia del día la toma SOLO el docente, en sus secciones
 * (asignación VIGENTE de tutor, docente o auxiliar; Dirección no registra ni corrige marcas: E49).
 * <ul>
 *   <li>{@link #hoja}: los alumnos con matrícula ACTIVA en orden alfabético, todos en «Presente»; si ya se registró, las
 *       marcas vigentes. Queda en «Quién vio datos personales» como {@code LISTA_SECCION}.</li>
 *   <li>{@link #guardar}: UN POST con UNA firma ({@code asistencia:{seccion}:{fecha}:REGISTRADA}, A5) crea el registro de
 *       HOY y sus marcas, a nombre del docente. Otro día solo con su REGISTRO_ASISTENCIA_TARDIO aprobada (E1, E10).</li>
 *   <li>{@link #corregirHoy}: el mismo día, antes de la hora del segundo aviso (A2), con motivo y firma; nunca sobre una
 *       justificación de la familia ni su reversión, y nunca hacia FALTA_JUSTIFICADA (A1). Después, por solicitud.</li>
 *   <li>{@link #pedirCorreccion} y {@link #pedirRegistroTardio}: las aprueba OTRA persona de Dirección o Promotoría en la
 *       bandeja, con su firma ({@link ManejadorCorreccionAsistencia} y {@link ManejadorRegistroTardio}).</li>
 * </ul>
 * Nada de cobranza (INDECOPI: la hoja es la misma para todos). En MySQL lo exigen trg_registro_asistencia_nace y
 * trg_marca_asistencia_nace, con el día y la hora de la BASE.
 */
@Service
@PreAuthorize("hasRole('DOCENTE')")
public class ServicioAsistencia {

	static final String ENTIDAD_MARCA = "marca_asistencia";

	static final String ENTIDAD_SECCION = "seccion";

	/** Tope de la base para un registro tardío (días calendario). */
	static final int MAX_DIAS_TARDIO = 15;

	private final SeccionesDelDocente seccionesDelDocente;

	private final RegistroAsistenciaRepository registros;

	private final MarcaAsistenciaRepository marcas;

	private final MatriculaRepository matriculas;

	private final AlumnoRepository alumnos;

	private final SolicitudCambioRepository solicitudes;

	private final RegistroSolicitudes registroSolicitudes;

	private final ConfiguracionesAcademicas configuraciones;

	private final DiaDeClases diaDeClases;

	private final CalendarioHabil calendario;

	private final SesionDeQuienEscribe sesiones;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ServicioAsistencia(SeccionesDelDocente seccionesDelDocente, RegistroAsistenciaRepository registros,
			MarcaAsistenciaRepository marcas, MatriculaRepository matriculas, AlumnoRepository alumnos,
			SolicitudCambioRepository solicitudes, RegistroSolicitudes registroSolicitudes,
			ConfiguracionesAcademicas configuraciones, DiaDeClases diaDeClases, CalendarioHabil calendario,
			SesionDeQuienEscribe sesiones, FirmaSesion firma, AuditoriaService auditoria, Clock reloj) {
		this.seccionesDelDocente = seccionesDelDocente;
		this.registros = registros;
		this.marcas = marcas;
		this.matriculas = matriculas;
		this.alumnos = alumnos;
		this.solicitudes = solicitudes;
		this.registroSolicitudes = registroSolicitudes;
		this.configuraciones = configuraciones;
		this.diaDeClases = diaDeClases;
		this.calendario = calendario;
		this.sesiones = sesiones;
		this.firma = firma;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** Las secciones del docente para hoy, con si ya registró cada una. */
	@Transactional(readOnly = true)
	public SeccionesDeHoy deHoy() {
		LocalDate hoy = hoy();
		List<OpcionSeccion> opciones = new ArrayList<>();
		for (SeccionDocente s : seccionesDelDocente.mias()) {
			opciones.add(new OpcionSeccion(s.seccionId(), s.seccion(), registros.findBySeccionIdAndFecha(s.seccionId(), hoy)
					.isPresent()));
		}
		String sinClases = hoy.getDayOfWeek().getValue() >= 6 ? "Hoy no hay clases: es fin de semana."
				: calendario.esFeriado(hoy) ? "Hoy es feriado o día no laborable del colegio." : null;
		return new SeccionesDeHoy(hoy, DiaDeClases.texto(hoy), sinClases, opciones);
	}

	/** Los registros tardíos aprobados del docente que todavía no registró (para abrir esa hoja). */
	@Transactional(readOnly = true)
	public List<TardioAprobado> tardiosPorRegistrar() {
		String yo = PersonaEnSesion.actual().getUsername();
		LocalDate hoy = hoy();
		return solicitudes.findByTipoOrderByIdDesc(TipoSolicitud.REGISTRO_ASISTENCIA_TARDIO).stream()
				.filter(s -> s.getEstado() == EstadoSolicitud.APROBADA && yo.equals(s.getSolicitadoPor()))
				.filter(s -> fechaDe(s).map(f -> !f.isBefore(hoy.minusDays(MAX_DIAS_TARDIO))
						&& registros.findBySeccionIdAndFecha(s.getEntidadId(), f).isEmpty()).orElse(false))
				.map(s -> new TardioAprobado(s.getId(), s.getEntidadId(), s.getResumen())).toList();
	}

	/** La hoja de hoy de una sección del docente (404 si no es suya). */
	@Transactional(readOnly = true)
	public HojaDelDia hoja(long seccionId) {
		return hojaDe(seccionId, hoy(), null);
	}

	/** La hoja de un día pasado con su registro tardío aprobado (404 si no es suya o la solicitud no es de ella). */
	@Transactional(readOnly = true)
	public HojaDelDia hojaTardia(long seccionId, long solicitudId) {
		SolicitudCambio solicitud = tardioAprobado(seccionId, solicitudId);
		return hojaDe(seccionId, fechaDe(solicitud).orElseThrow(), solicitud.getId());
	}

	private HojaDelDia hojaDe(long seccionId, LocalDate fecha, Long solicitudId) {
		Map.Entry<Seccion, String> suya = seccionesDelDocente.vigentesEn(seccionId, true)
				.orElseThrow(SeccionesDelDocente::noEncontrada);
		Seccion seccion = suya.getKey();
		boolean tutor = seccionesDelDocente.tutorDe(seccionId);
		ConfiguracionAcademica configuracion = configuraciones.vigente();
		Optional<RegistroAsistencia> registro = registros.findBySeccionIdAndFecha(seccionId, fecha);
		List<AlumnoEnHoja> filas = new ArrayList<>();
		int[] cuenta = new int[4];
		boolean abierta = fecha.equals(hoy()) && ahora().toLocalTime().isBefore(configuracion.getHoraSegundoAviso());
		if (registro.isEmpty()) {
			List<Matricula> activas = activasDe(seccionId);
			for (Matricula m : activas) {
				Alumno a = m.getAlumno();
				filas.add(new AlumnoEnHoja(filas.size() + 1, a.getId(), apellidos(a), a.getNombres(),
						EstadoAsistencia.PRESENTE.name(), EstadoAsistencia.PRESENTE.etiqueta(),
						EstadoAsistencia.PRESENTE.variante(), null, null, null, false, false, null));
			}
			cuenta[0] = filas.size();
		}
		else {
			List<MarcaAsistencia> vigentes = marcas.vigentesDelRegistro(registro.get().getId());
			Map<Long, Alumno> porId = alumnos.findAllById(vigentes.stream().map(MarcaAsistencia::getAlumnoId).toList())
					.stream().collect(Collectors.toMap(Alumno::getId, Function.identity()));
			List<MarcaAsistencia> ordenadas = new ArrayList<>(vigentes);
			ordenadas.sort(Comparator.comparing((MarcaAsistencia m) -> clave(porId.get(m.getAlumnoId()))));
			for (MarcaAsistencia m : ordenadas) {
				Alumno a = porId.get(m.getAlumnoId());
				boolean corregible = abierta && m.getOrigen() != OrigenMarca.JUSTIFICACION
						&& m.getOrigen() != OrigenMarca.REVERSION_JUSTIFICACION;
				filas.add(new AlumnoEnHoja(filas.size() + 1, m.getAlumnoId(), a == null ? "—" : apellidos(a),
						a == null ? "—" : a.getNombres(), m.getEstado().name(), m.getEstado().etiqueta(),
						m.getEstado().variante(), m.getHoraLlegada(), m.getId(), m.getOrigen().etiqueta(), corregible,
						m.getEstado() == EstadoAsistencia.FALTA_JUSTIFICADA,
						tutor ? m.getNotaJustificacion() : null));
				cuenta[m.getEstado().ordinal()]++;
			}
		}
		AccesoMostrado.filas(filas.size());
		String sinClases = diaDeClases.sinClases(seccion, fecha).orElse(null);
		return new HojaDelDia(seccion.getId(), seccion.etiqueta(), fecha, DiaDeClases.texto(fecha), registro.isPresent(),
				abierta, configuracion.getHoraSegundoAviso(), sinClases, solicitudId, filas, cuenta[0], cuenta[2],
				cuenta[1], cuenta[3]);
	}

	/**
	 * Guarda la hoja de hoy (o la del día pasado de su solicitud aprobada): UN registro, UNA firma y una marca por alumno
	 * con matrícula ACTIVA, a nombre del docente en sesión. Si ya se registró, solo agrega a quienes faltaban (alumnos
	 * matriculados después), y solo si lo registró el mismo docente.
	 */
	@Transactional
	public ResultadoRegistro guardar(long seccionId, HojaRequest pedido) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		Seccion seccion = seccionesDelDocente.vigentesEn(seccionId, true).orElseThrow(SeccionesDelDocente::noEncontrada)
				.getKey();
		SolicitudCambio tardio = pedido.solicitudId() == null ? null : tardioAprobado(seccionId, pedido.solicitudId());
		LocalDate fecha = tardio == null ? hoy() : fechaDe(tardio).orElseThrow();
		diaDeClases.sinClases(seccion, fecha).ifPresent(motivo -> {
			throw new ReglaNegocioException(motivo);
		});
		Map<Long, Matricula> activas = new LinkedHashMap<>();
		activasDe(seccionId).forEach(m -> activas.put(m.getAlumno().getId(), m));
		Map<Long, MarcaPedida> pedidas = new LinkedHashMap<>();
		for (MarcaPedida p : pedido.marcas()) {
			if (p == null || p.alumnoId() == null || !activas.containsKey(p.alumnoId())) {
				throw new ReglaNegocioException("La hoja tiene un alumno que no está matriculado en " + seccion.etiqueta()
						+ ": vuelve a abrirla.");
			}
			if (pedidas.put(p.alumnoId(), p) != null) {
				throw new ReglaNegocioException("La hoja repite un alumno: vuelve a abrirla.");
			}
		}
		Long sesion = sesiones.de(persona);
		Optional<RegistroAsistencia> existente = registros.findBySeccionIdAndFecha(seccionId, fecha);
		RegistroAsistencia registro;
		Set<Long> yaMarcados = new HashSet<>();
		if (existente.isPresent()) {
			registro = existente.get();
			if (!registro.getRegistradoPor().equals(persona.getUsername())) {
				throw new ReglaNegocioException("La asistencia de " + seccion.etiqueta() + " ya la registró otra persona: "
						+ "corrige marca por marca.");
			}
			marcas.findByRegistroIdOrderByIdAsc(registro.getId()).forEach(m -> yaMarcados.add(m.getAlumnoId()));
			if (yaMarcados.containsAll(activas.keySet())) {
				throw new ReglaNegocioException("La asistencia de " + seccion.etiqueta() + " ya está registrada: corrige "
						+ "marca por marca.");
			}
			if (!fecha.equals(hoy()) && tardio == null) {
				throw new ReglaNegocioException("Solo se completa la asistencia del mismo día.");
			}
		}
		else {
			if (!pedidas.keySet().equals(activas.keySet())) {
				throw new ReglaNegocioException("La hoja no tiene a todos los alumnos de " + seccion.etiqueta()
						+ " (alguien se matriculó o se retiró mientras la llenabas): vuelve a abrirla.");
			}
			// A5: UNA firma por registro (un POST), antes del INSERT que la exige (trg_registro_asistencia_nace).
			firma.firmar(ClaveFirma.asistenciaRegistrada(seccion.getId(), fecha));
			registro = registros.saveAndFlush(tardio == null
					? RegistroAsistencia.delDia(seccion.getAnioEscolar().getId(), seccion.getId(), fecha,
							persona.getUsername(), sesion, activas.size())
					: RegistroAsistencia.tardio(seccion.getAnioEscolar().getId(), seccion.getId(), fecha, tardio.getId(),
							persona.getUsername(), sesion, activas.size()));
		}
		int[] cuenta = new int[4];
		for (Map.Entry<Long, Matricula> activa : activas.entrySet()) {
			if (yaMarcados.contains(activa.getKey())) {
				continue;
			}
			MarcaPedida p = pedidas.get(activa.getKey());
			EstadoAsistencia estado = p == null ? EstadoAsistencia.PRESENTE : p.estado();
			marcas.saveAndFlush(MarcaAsistencia.inicial(registro, activa.getKey(), activa.getValue().getId(), estado,
					p == null ? null : p.horaLlegada(), sesion));
			cuenta[estado.ordinal()]++;
		}
		ResultadoRegistro resultado = new ResultadoRegistro(registro.getId(), cuenta[0], cuenta[2], cuenta[1]);
		auditoria.registrar(AccionAuditoria.ASISTENCIA_REGISTRADA, "registro_asistencia", registro.getId().toString(), null,
				resultado.resumen(), persona.nombreCompleto() + " registró la asistencia de " + seccion.etiqueta() + " del "
						+ fecha + (tardio == null ? "" : " (día pasado, solicitud " + tardio.getId() + ")") + ": "
						+ resultado.resumen() + ".");
		return resultado;
	}

	/**
	 * Corrige una marca del MISMO día (antes de la hora del segundo aviso, A2), con motivo y la firma de la sesión. Si el
	 * aviso a las familias ya salió, la familia recibe la corrección y el evento queda resaltado (E5).
	 */
	@Transactional
	public void corregirHoy(long marcaId, CorreccionRequest pedido) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		MarcaAsistencia vigente = vigenteDeMiSeccion(marcaId);
		ConfiguracionAcademica configuracion = configuraciones.vigente();
		LocalDateTime ahora = ahora();
		if (!vigente.getFecha().equals(ahora.toLocalDate())) {
			throw new ReglaNegocioException("Solo se corrige directo la asistencia de hoy: para otro día, pide la "
					+ "corrección (la aprueba Dirección).");
		}
		if (!ahora.toLocalTime().isBefore(configuracion.getHoraSegundoAviso())) {
			throw new ReglaNegocioException("Desde las " + configuracion.getHoraSegundoAviso() + " la corrección del día ya "
					+ "salió a las familias: pide la corrección (la aprueba Dirección).");
		}
		if (vigente.getOrigen() == OrigenMarca.JUSTIFICACION || vigente.getOrigen() == OrigenMarca.REVERSION_JUSTIFICACION) {
			throw new ReglaNegocioException("Esta marca la justificó la familia (o la revirtió Dirección): solo cambia con "
					+ "una corrección aprobada por Dirección.");
		}
		MarcaAsistencia nueva = MarcaAsistencia.correccion(vigente, pedido.estado(), pedido.horaLlegada(), pedido.motivo(),
				null, sesiones.de(persona));
		firma.firmar(ClaveFirma.marcaCorregida(vigente.getId()));
		marcas.saveAndFlush(nueva);
		boolean despuesDelAviso = !ahora.toLocalTime().isBefore(configuracion.getHoraAviso());
		auditoria.registrar(despuesDelAviso ? AccionAuditoria.ASISTENCIA_CORREGIDA_DESPUES_DEL_AVISO
				: AccionAuditoria.ASISTENCIA_CORREGIDA, ENTIDAD_MARCA, nueva.getId().toString(), vigente.getEstado().name(),
				nueva.getEstado().name(), persona.nombreCompleto() + " corrigió la marca " + vigente.getId() + " del "
						+ vigente.getFecha() + " (alumno " + vigente.getAlumnoId() + "): de " + vigente.getEstado().etiqueta()
						+ " a " + nueva.getEstado().etiqueta() + ". Motivo: " + nueva.getMotivo() + "."
						+ (despuesDelAviso ? " Fue después del aviso: la familia recibe la corrección." : ""));
	}

	/** Pide la corrección de otro día (o después del cierre, o de una justificación): la aprueba Dirección (E6, E52). */
	@Transactional
	public Long pedirCorreccion(long marcaId, CorreccionRequest pedido) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		MarcaAsistencia vigente = vigenteDeMiSeccion(marcaId);
		if (pedido.estado() == null || pedido.estado() == vigente.getEstado()) {
			throw new ReglaNegocioException("La corrección cambia el estado de la marca.");
		}
		ConfiguracionAcademica configuracion = configuraciones.vigente();
		LocalDateTime ahora = ahora();
		boolean directo = vigente.getFecha().equals(ahora.toLocalDate())
				&& ahora.toLocalTime().isBefore(configuracion.getHoraSegundoAviso())
				&& vigente.getOrigen() != OrigenMarca.JUSTIFICACION
				&& vigente.getOrigen() != OrigenMarca.REVERSION_JUSTIFICACION
				&& pedido.estado() != EstadoAsistencia.FALTA_JUSTIFICADA;
		if (directo) {
			throw new ReglaNegocioException("Esta marca la puedes corregir directo hoy, antes de las "
					+ configuracion.getHoraSegundoAviso() + ".");
		}
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("estadoNuevo", pedido.estado().name());
		if (pedido.estado() == EstadoAsistencia.TARDE && pedido.horaLlegada() != null) {
			datos.put("horaLlegada", pedido.horaLlegada().truncatedTo(ChronoUnit.MINUTES).toString());
		}
		datos.put("seccion", vigente.getSeccionId().toString());
		datos.put("fecha", vigente.getFecha().toString());
		SolicitudCambio solicitud = registroSolicitudes.crear(TipoSolicitud.CORRECCION_ASISTENCIA, ENTIDAD_MARCA,
				vigente.getId(), "Corregir la asistencia del " + vigente.getFecha() + " de "
						+ seccionesDelDocente.vigentesEn(vigente.getSeccionId(), true).map(e -> e.getKey().etiqueta())
								.orElse("la sección") + ": de " + vigente.getEstado().etiqueta() + " a "
						+ pedido.estado().etiqueta(), datos, pedido.motivo());
		auditoria.registrar(AccionAuditoria.ASISTENCIA_SOLICITADA, ENTIDAD_MARCA, vigente.getId().toString(),
				vigente.getEstado().name(), pedido.estado().name(), persona.nombreCompleto() + " pidió corregir la marca "
						+ vigente.getId() + " (solicitud " + solicitud.getId() + "). La aprueba otra persona de Dirección.");
		return solicitud.getId();
	}

	/** Pide registrar un día pasado (se anotó en papel): hasta los días hábiles del colegio (5) y 15 calendario. */
	@Transactional
	public Long pedirRegistroTardio(long seccionId, RegistroTardioRequest pedido) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		Seccion seccion = seccionesDelDocente.vigentesEn(seccionId, true).orElseThrow(SeccionesDelDocente::noEncontrada)
				.getKey();
		LocalDate hoy = hoy();
		LocalDate fecha = Objects.requireNonNull(pedido.fecha(), "fecha");
		ConfiguracionAcademica configuracion = configuraciones.vigente();
		if (!fecha.isBefore(hoy)) {
			throw new ReglaNegocioException("El registro tardío es de un día pasado: la de hoy se registra en la hoja.");
		}
		if (fecha.isBefore(hoy.minusDays(MAX_DIAS_TARDIO))
				|| calendario.habilesEntre(fecha, hoy) > configuracion.getDiasRegistroTardio()) {
			throw new ReglaNegocioException("Solo se registra un día de los últimos "
					+ configuracion.getDiasRegistroTardio() + " días hábiles.");
		}
		diaDeClases.sinClases(seccion, fecha).ifPresent(motivo -> {
			throw new ReglaNegocioException(motivo);
		});
		if (registros.findBySeccionIdAndFecha(seccionId, fecha).isPresent()) {
			throw new ReglaNegocioException("La asistencia de " + seccion.etiqueta() + " del " + fecha + " ya está "
					+ "registrada: pide la corrección de cada marca.");
		}
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("fecha", fecha.toString());
		SolicitudCambio solicitud = registroSolicitudes.crear(TipoSolicitud.REGISTRO_ASISTENCIA_TARDIO, ENTIDAD_SECCION,
				seccion.getId(), "Registrar la asistencia de " + seccion.etiqueta() + " del " + DiaDeClases.texto(fecha)
						+ " (" + fecha + ")", datos, pedido.motivo());
		auditoria.registrar(AccionAuditoria.ASISTENCIA_SOLICITADA, ENTIDAD_SECCION, seccion.getId().toString(), null,
				fecha.toString(), persona.nombreCompleto() + " pidió registrar la asistencia de " + seccion.etiqueta()
						+ " del " + fecha + " (solicitud " + solicitud.getId() + "). La aprueba otra persona de Dirección.");
		return solicitud.getId();
	}

	/** La marca vigente de una sección del docente (404 si no es suya, de otro colegio o ya la reemplazó otra). */
	private MarcaAsistencia vigenteDeMiSeccion(long marcaId) {
		MarcaAsistencia marca = marcas.findById(marcaId).orElseThrow(ServicioAsistencia::marcaNoEncontrada);
		seccionesDelDocente.vigentesEn(marca.getSeccionId(), true).orElseThrow(ServicioAsistencia::marcaNoEncontrada);
		if (marcas.existsByReemplazaId(marca.getId())) {
			throw new ReglaNegocioException("Esa marca ya se corrigió: abre la hoja de nuevo para ver la vigente.");
		}
		return marca;
	}

	private SolicitudCambio tardioAprobado(long seccionId, long solicitudId) {
		seccionesDelDocente.vigentesEn(seccionId, true).orElseThrow(SeccionesDelDocente::noEncontrada);
		SolicitudCambio solicitud = solicitudes.findById(solicitudId)
				.filter(s -> s.getTipo() == TipoSolicitud.REGISTRO_ASISTENCIA_TARDIO
						&& ENTIDAD_SECCION.equals(s.getEntidad()) && Objects.equals(s.getEntidadId(), seccionId)
						&& PersonaEnSesion.actual().getUsername().equals(s.getSolicitadoPor()))
				.orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
		if (solicitud.getEstado() != EstadoSolicitud.APROBADA) {
			throw new ReglaNegocioException("El registro de ese día todavía no está aprobado (o se rechazó).");
		}
		LocalDate fecha = fechaDe(solicitud).orElseThrow(() -> new ReglaNegocioException("La solicitud no tiene fecha."));
		if (fecha.isBefore(hoy().minusDays(MAX_DIAS_TARDIO))) {
			throw new ReglaNegocioException("Pasaron más de " + MAX_DIAS_TARDIO + " días: ese día ya no se registra.");
		}
		return solicitud;
	}

	static Optional<LocalDate> fechaDe(SolicitudCambio solicitud) {
		String fecha = DatosSolicitud.leer(solicitud.getDatos()).get("fecha");
		try {
			return Optional.ofNullable(fecha).map(LocalDate::parse);
		}
		catch (java.time.format.DateTimeParseException e) {
			return Optional.empty();
		}
	}

	private List<Matricula> activasDe(long seccionId) {
		return matriculas.deLaSeccionEnOrden(seccionId, EnumSet.of(EstadoMatricula.ACTIVA));
	}

	static String apellidos(Alumno a) {
		return a.getApellidoMaterno() == null || a.getApellidoMaterno().isBlank() ? a.getApellidoPaterno()
				: a.getApellidoPaterno() + " " + a.getApellidoMaterno();
	}

	private static String clave(Alumno a) {
		return a == null ? "~" : (a.getApellidoPaterno() + " " + Objects.toString(a.getApellidoMaterno(), "") + " "
				+ a.getNombres()).toLowerCase(java.util.Locale.ROOT);
	}

	static RecursoNoEncontradoException marcaNoEncontrada() {
		return new RecursoNoEncontradoException("Marca no encontrada");
	}

	private LocalDate hoy() {
		return LocalDate.now(reloj);
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}
}
