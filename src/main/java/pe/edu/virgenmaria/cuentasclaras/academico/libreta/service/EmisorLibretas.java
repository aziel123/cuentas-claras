package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.LibretaEmitidaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.Calificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.OrigenCalificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.CalificacionRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 3 (secciones 3.7, 3.8 y 3.13; sección 3 del brief): la emisión de libretas, que hace SOLO
 * {@code sistema.libretas} (cc_sistema: es la única conexión con INSERT en {@code libreta_emitida}).
 * <ul>
 *   <li><b>Versión 1</b>, por cada matrícula ACTIVA o RESERVADA (C1) de cada sección de un periodo CERRADO, con la foto de lo
 *       registrado hasta el cierre. La que debe y la que tiene la matrícula impaga la reciben igual (INDECOPI): este
 *       servicio no puede leer cobranza (ArchUnit).</li>
 *   <li><b>Versión siguiente</b> por cada corrección o nota tardía aprobada con id mayor que la marca de agua de la vigente
 *       (también las aprobadas en la tanda 2, antes de que existieran libretas: la primera pasada emite la 1 y la 2), con
 *       «qué cambió» y sin conclusiones.</li>
 *   <li><b>Reemisión</b> por una {@code REEMISION_LIBRETA} aprobada por Promotoría (A3).</li>
 * </ul>
 * Bitácora (decisión 191): UN evento {@code LIBRETAS_EMITIDAS} por periodo y sección, con la cantidad y la huella de la
 * lista, no uno por libreta (la cadena única de la bitácora serializa el final de cada escritura).
 */
@Service
@PreAuthorize("hasRole('SISTEMA_LIBRETAS')")
@Transactional(propagation = Propagation.MANDATORY)
public class EmisorLibretas {

	static final Set<OrigenCalificacion> CAMBIAN_LA_LIBRETA = EnumSet.of(OrigenCalificacion.CORRECCION_APROBADA,
			OrigenCalificacion.REGISTRO_TARDIO);

	static final List<EstadoMatricula> RECIBEN_LIBRETA = List.of(EstadoMatricula.ACTIVA, EstadoMatricula.RESERVADA);

	/** Una sección de un periodo cerrado con trabajo pendiente. */
	public record Pendiente(long periodoId, long seccionId) {
	}

	/** Lo que emitió una sección. */
	public record Resultado(int alCierre, int corregidas, int reemitidas) {

		public Resultado sumar(Resultado otro) {
			return new Resultado(alCierre + otro.alCierre, corregidas + otro.corregidas, reemitidas + otro.reemitidas);
		}

		public int total() {
			return alCierre + corregidas + reemitidas;
		}
	}

	private final PeriodoEvaluacionRepository periodos;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final MatriculaRepository matriculas;

	private final LibretaEmitidaRepository libretas;

	private final CalificacionRepository calificaciones;

	private final SolicitudCambioRepository solicitudes;

	private final FotoLibreta foto;

	private final AuditoriaService auditoria;

	public EmisorLibretas(PeriodoEvaluacionRepository periodos, AnioEscolarRepository anios, SeccionRepository secciones,
			MatriculaRepository matriculas, LibretaEmitidaRepository libretas, CalificacionRepository calificaciones,
			SolicitudCambioRepository solicitudes, FotoLibreta foto, AuditoriaService auditoria) {
		this.periodos = periodos;
		this.anios = anios;
		this.secciones = secciones;
		this.matriculas = matriculas;
		this.libretas = libretas;
		this.calificaciones = calificaciones;
		this.solicitudes = solicitudes;
		this.foto = foto;
		this.auditoria = auditoria;
	}

	/**
	 * Las secciones de los periodos CERRADOS (de años que no están cerrados) que tienen algo por emitir: una matrícula sin
	 * libreta, una corrección o nota tardía posterior a su libreta vigente o una reemisión aprobada.
	 */
	@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
	public List<Pendiente> pendientes() {
		Map<Long, AnioEscolar> abiertos = anios.findAll().stream().filter(a -> a.getEstado() != EstadoAnioEscolar.CERRADO)
				.collect(Collectors.toMap(AnioEscolar::getId, Function.identity()));
		Set<Pendiente> pendientes = new LinkedHashSet<>();
		for (PeriodoEvaluacion p : periodos.findByEstadoOrderByAnioEscolarIdAscNumeroAsc(EstadoPeriodo.CERRADO)) {
			if (!abiertos.containsKey(p.getAnioEscolarId()) || p.getCerradoEn() == null) {
				continue;
			}
			Map<Long, LibretaEmitida> vigentes = vigentesPorMatricula(p.getId());
			Map<Long, Long> corregidas = ultimaCorreccionPorMatricula(p.getId());
			for (Seccion s : secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(p.getAnioEscolarId())) {
				Set<Long> hoyEnLaSeccion = matriculas.deLaSeccionEnOrden(s.getId(), RECIBEN_LIBRETA).stream()
						.map(Matricula::getId).collect(Collectors.toSet());
				boolean falta = hoyEnLaSeccion.stream().anyMatch(m -> !vigentes.containsKey(m));
				// QA del sprint 10: como emitir(), solo las matrículas que siguen en la sección (la libreta de un retirado
				// con una corrección posterior no se reemite: sin este filtro la sección quedaba pendiente para siempre).
				boolean corregir = vigentes.values().stream().filter(l -> l.getSeccionId().equals(s.getId()))
						.filter(l -> hoyEnLaSeccion.contains(l.getMatriculaId()))
						.anyMatch(l -> corregidas.getOrDefault(l.getMatriculaId(), 0L) > l.marcas().calificacion());
				if (falta || corregir) {
					pendientes.add(new Pendiente(p.getId(), s.getId()));
				}
			}
		}
		for (SolicitudCambio s : reemisionesSinLibreta()) {
			libretas.findById(s.getEntidadId()).ifPresent(l -> pendientes.add(new Pendiente(l.getPeriodoId(),
					l.getSeccionId())));
		}
		return List.copyOf(pendientes);
	}

	/** Emite lo pendiente de una sección de un periodo cerrado (su propia transacción, la abre el proceso). */
	public Resultado emitir(long periodoId, long seccionId, LocalDateTime ahora) {
		PeriodoEvaluacion periodo = periodos.findById(periodoId)
				.filter(p -> p.getEstado() == EstadoPeriodo.CERRADO && p.getCerradoEn() != null)
				.orElseThrow(() -> new IllegalStateException("El periodo " + periodoId + " no está cerrado"));
		Seccion seccion = secciones.findById(seccionId)
				.filter(s -> s.getAnioEscolar().getId().equals(periodo.getAnioEscolarId()))
				.orElseThrow(() -> new IllegalStateException("La sección " + seccionId + " no es del año del periodo"));
		List<Matricula> activas = matriculas.deLaSeccionEnOrden(seccion.getId(), RECIBEN_LIBRETA);
		Map<Long, LibretaEmitida> vigentes = vigentesPorMatricula(periodoId);
		List<LibretaEmitida> deLaSeccion = vigentes.values().stream()
				.filter(l -> l.getSeccionId().equals(seccion.getId())).toList();
		Set<Long> todas = new LinkedHashSet<>();
		activas.forEach(m -> todas.add(m.getId()));
		deLaSeccion.forEach(l -> todas.add(l.getMatriculaId()));
		FotoLibreta.DatosSeccion datos = foto.cargar(periodoId, seccionId, todas);
		// 1. La versión 1 de las que faltan, con lo registrado hasta el cierre.
		List<Matricula> faltan = activas.stream().filter(m -> !vigentes.containsKey(m.getId())).toList();
		Map<Long, LibretaEmitida.Marcas> alCierre = foto.marcasAlCierre(datos,
				faltan.stream().map(Matricula::getId).toList());
		List<LibretaEmitida> nuevas = new ArrayList<>();
		for (Matricula m : faltan) {
			LibretaEmitida.Donde donde = new LibretaEmitida.Donde(periodo.getAnioEscolarId(), periodoId, seccionId, m.getId(),
					m.getAlumno().getId());
			LibretaEmitida.Cabecera cabecera = foto.cabecera(datos, m.getAlumno());
			LibretaEmitida.Marcas marcas = alCierre.get(m.getId());
			String huella = FotoLibreta.huella(foto.armar(datos, donde, marcas, cabecera));
			LibretaEmitida l = libretas.saveAndFlush(LibretaEmitida.alCierre(donde, marcas, cabecera, huella, ahora));
			vigentes.put(m.getId(), l);
			nuevas.add(l);
		}
		if (!nuevas.isEmpty()) {
			String lista = nuevas.stream().sorted(Comparator.comparing(LibretaEmitida::getId))
					.map(l -> l.getId() + ":" + l.getHuellaContenido()).collect(Collectors.joining("\n"));
			auditoria.registrar(AccionAuditoria.LIBRETAS_EMITIDAS, "seccion", String.valueOf(seccionId), null,
					nuevas.size() + " libreta(s) · " + FotoLibreta.sha256(lista), "Libretas del " + periodo.getNombre()
							+ " de " + seccion.etiqueta() + ": " + nuevas.size() + " emitida(s) al cierre (matrículas activas o "
							+ "reservadas; la deuda no cuenta).");
		}
		// 2. La versión siguiente de las que tienen una corrección o nota tardía aprobada después de su marca de agua.
		int corregidas = 0;
		Map<Long, Long> ultimaCorreccion = ultimaCorreccionPorMatricula(periodoId);
		// Solo las matrículas que siguen ACTIVAS o RESERVADAS en la sección (la de un retirado conserva su última versión).
		Map<Long, Matricula> porId = activas.stream().collect(Collectors.toMap(Matricula::getId, Function.identity()));
		List<LibretaEmitida> porCorregir = vigentes.values().stream().filter(l -> l.getSeccionId().equals(seccionId))
				.filter(l -> porId.containsKey(l.getMatriculaId()))
				.filter(l -> ultimaCorreccion.getOrDefault(l.getMatriculaId(), 0L) > l.marcas().calificacion())
				.sorted(Comparator.comparing(LibretaEmitida::getId)).toList();
		Map<Long, Long> ultimas = foto.ultimasNotas(porCorregir.stream().map(LibretaEmitida::getMatriculaId).toList());
		for (LibretaEmitida anterior : porCorregir) {
			LibretaEmitida.Marcas marcas = new LibretaEmitida.Marcas(Math.max(anterior.marcas().calificacion(),
					ultimas.getOrDefault(anterior.getMatriculaId(), 0L)), anterior.marcas().marca(),
					anterior.marcas().exoneracion());
			LibretaEmitida.Cabecera cabecera = foto.cabecera(datos, porId.get(anterior.getMatriculaId()).getAlumno());
			ContenidoLibreta antes = foto.armar(datos, anterior.donde(), anterior.marcas(), anterior.cabecera());
			ContenidoLibreta despues = foto.armar(datos, anterior.donde(), marcas, cabecera);
			String cambios = cambios(antes, despues);
			LibretaEmitida l = libretas.saveAndFlush(LibretaEmitida.corregida(anterior, marcas, cabecera,
					FotoLibreta.huella(despues), cambios, ahora));
			vigentes.put(l.getMatriculaId(), l);
			corregidas++;
			auditoria.registrar(AccionAuditoria.LIBRETA_CORREGIDA_EMITIDA, "libreta_emitida", l.getId().toString(),
					"versión " + anterior.getNumeroVersion(), "versión " + l.getNumeroVersion(), "Libreta del "
							+ periodo.getNombre() + " de " + seccion.etiqueta() + " (alumno " + l.getAlumnoId()
							+ "), corregida: " + l.getCambios());
		}
		// 3. Las reemisiones aprobadas por Promotoría (A3).
		int reemitidas = 0;
		for (SolicitudCambio s : reemisionesSinLibreta()) {
			Optional<LibretaEmitida> marcada = libretas.findById(s.getEntidadId())
					.filter(l -> l.getPeriodoId().equals(periodoId) && l.getSeccionId().equals(seccionId));
			if (marcada.isEmpty() || !porId.containsKey(marcada.get().getMatriculaId())) {
				continue;
			}
			LibretaEmitida anterior = vigentes.get(marcada.get().getMatriculaId());
			ContenidoLibreta contenido = foto.armar(datos, anterior.donde(), anterior.marcas(), anterior.cabecera());
			LibretaEmitida l = libretas.saveAndFlush(LibretaEmitida.reemitida(anterior, anterior.marcas(),
					anterior.cabecera(), FotoLibreta.huella(contenido), s.getId(), ahora));
			vigentes.put(l.getMatriculaId(), l);
			reemitidas++;
			auditoria.registrar(AccionAuditoria.LIBRETA_REEMITIDA, "libreta_emitida", l.getId().toString(),
					anterior.getHuellaContenido(), l.getHuellaContenido(), "Libreta del " + periodo.getNombre() + " de "
							+ seccion.etiqueta() + " (alumno " + l.getAlumnoId() + ") reemitida como versión "
							+ l.getNumeroVersion() + " con la reemisión aprobada por " + s.getResueltoPor() + " (solicitud "
							+ s.getId() + ").");
		}
		return new Resultado(nuevas.size(), corregidas, reemitidas);
	}

	/**
	 * Lo que cambió de una versión a la siguiente en el periodo de la libreta, sin conclusiones: «Matemática · Resuelve
	 * problemas de cantidad: B → A».
	 */
	static String cambios(ContenidoLibreta antes, ContenidoLibreta despues) {
		List<String> lineas = new ArrayList<>();
		for (int a = 0; a < despues.areas().size() && a < antes.areas().size(); a++) {
			ContenidoLibreta.Area area = despues.areas().get(a);
			for (int c = 0; c < area.competencias().size(); c++) {
				ContenidoLibreta.Competencia nueva = area.competencias().get(c);
				ContenidoLibreta.Nota de = antes.areas().get(a).competencias().get(c).delPeriodo();
				ContenidoLibreta.Nota a2 = nueva.delPeriodo();
				if (!java.util.Objects.equals(de.nivel(), a2.nivel()) || de.exonerada() != a2.exonerada()) {
					lineas.add(area.nombre() + " · " + nueva.nombre() + ": " + nivel(de) + " → " + nivel(a2));
				}
				else if (!java.util.Objects.equals(de.conclusionSha256(), a2.conclusionSha256())) {
					lineas.add(area.nombre() + " · " + nueva.nombre() + ": se actualizó la conclusión");
				}
			}
		}
		if (lineas.isEmpty()) {
			return "Se registró una corrección aprobada";
		}
		StringBuilder texto = new StringBuilder();
		for (int i = 0; i < lineas.size(); i++) {
			String siguiente = (i == 0 ? "" : "; ") + lineas.get(i);
			String resto = "; y " + (lineas.size() - i) + " cambio(s) más";
			if (texto.length() + siguiente.length() + resto.length() > LibretaEmitida.MAX_CAMBIOS) {
				texto.append(resto);
				break;
			}
			texto.append(siguiente);
		}
		return texto.toString();
	}

	private static String nivel(ContenidoLibreta.Nota n) {
		return n.exonerada() ? "exonerado" : n.nivel() == null ? "sin nota" : n.nivel();
	}

	private Map<Long, LibretaEmitida> vigentesPorMatricula(long periodoId) {
		return libretas.findByPeriodoIdOrderByIdAsc(periodoId).stream().collect(Collectors.toMap(
				LibretaEmitida::getMatriculaId, Function.identity(),
				(a, b) -> a.getNumeroVersion() > b.getNumeroVersion() ? a : b, java.util.HashMap::new));
	}

	private Map<Long, Long> ultimaCorreccionPorMatricula(long periodoId) {
		return calificaciones.findByPeriodoIdAndOrigenInOrderByIdAsc(periodoId, CAMBIAN_LA_LIBRETA).stream()
				.collect(Collectors.toMap(Calificacion::getMatriculaId, Calificacion::getId, Math::max));
	}

	private List<SolicitudCambio> reemisionesSinLibreta() {
		return solicitudes.findByTipoOrderByIdDesc(TipoSolicitud.REEMISION_LIBRETA).stream()
				.filter(s -> s.getEstado() == EstadoSolicitud.APROBADA && !libretas.existsBySolicitudId(s.getId()))
				.toList();
	}
}
