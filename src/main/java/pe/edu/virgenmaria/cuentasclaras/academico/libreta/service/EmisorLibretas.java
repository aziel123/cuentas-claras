package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.IntegridadNotas;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 3 (secciones 3.7, 3.8 y 3.13; sección 3 del brief): la emisión de libretas, que hace SOLO
 * {@code sistema.libretas} (cc_sistema: es la única conexión con INSERT en {@code libreta_emitida}).
 * <ul>
 *   <li><b>Versión 1</b>, por cada matrícula que estaba ACTIVA o RESERVADA (C1) en la sección al CIERRE del periodo
 *       ({@link MatriculasAlCierre}: también quien se retiró o se trasladó después, en la sección donde estaba; nunca una
 *       matrícula creada después del cierre), con la foto de lo registrado hasta el cierre. La que debe y la que tiene la
 *       matrícula impaga la reciben igual (INDECOPI): este servicio no puede leer cobranza (ArchUnit).</li>
 *   <li><b>Versión siguiente</b> por cada corrección o nota tardía aprobada con id mayor que la marca de agua de la vigente,
 *       de ese periodo o de uno ANTERIOR del año (correcciones del sprint 10, hallazgo 4: la libreta del II bimestre muestra
 *       la columna del I y, en el último periodo, la anual), en la sección de la versión anterior (P-1), para las matrículas
 *       que siguen activas o reservadas.</li>
 *   <li><b>Reemisión</b> por una {@code REEMISION_LIBRETA} aprobada por Promotoría (A3), con los MISMOS niveles emitidos
 *       (hallazgo 2): si la base hoy dice otra cosa, no se reemite y queda para una corrección por solicitud.</li>
 * </ul>
 * Toda libreta guarda sus niveles canónicos ({@link FotoLibreta#niveles}). Si una sección tiene notas que no coinciden con
 * la huella de su guardado ({@link IntegridadNotas}, hallazgo 1), sus libretas NO se emiten hasta revisarlo. Bitácora
 * (decisión 191): UN evento {@code LIBRETAS_EMITIDAS} por periodo y sección, no uno por libreta.
 */
@Service
@PreAuthorize("hasRole('SISTEMA_LIBRETAS')")
@Transactional(propagation = Propagation.MANDATORY)
public class EmisorLibretas {

	private static final Logger LOG = LoggerFactory.getLogger(EmisorLibretas.class);

	static final Set<OrigenCalificacion> CAMBIAN_LA_LIBRETA = EnumSet.of(OrigenCalificacion.CORRECCION_APROBADA,
			OrigenCalificacion.REGISTRO_TARDIO);

	static final Set<EstadoMatricula> RECIBEN_LIBRETA = MatriculasAlCierre.RECIBEN_LIBRETA;

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

	private final MatriculasAlCierre alCierre;

	private final LibretaEmitidaRepository libretas;

	private final CalificacionRepository calificaciones;

	private final SolicitudCambioRepository solicitudes;

	private final FotoLibreta foto;

	private final IntegridadNotas integridad;

	private final AuditoriaService auditoria;

	public EmisorLibretas(PeriodoEvaluacionRepository periodos, AnioEscolarRepository anios, SeccionRepository secciones,
			MatriculaRepository matriculas, MatriculasAlCierre alCierre, LibretaEmitidaRepository libretas,
			CalificacionRepository calificaciones, SolicitudCambioRepository solicitudes, FotoLibreta foto,
			IntegridadNotas integridad, AuditoriaService auditoria) {
		this.periodos = periodos;
		this.anios = anios;
		this.secciones = secciones;
		this.matriculas = matriculas;
		this.alCierre = alCierre;
		this.libretas = libretas;
		this.calificaciones = calificaciones;
		this.solicitudes = solicitudes;
		this.foto = foto;
		this.integridad = integridad;
		this.auditoria = auditoria;
	}

	/**
	 * Las secciones de los periodos CERRADOS (de años que no están cerrados) que tienen algo por emitir: una matrícula que
	 * estaba en la sección al cierre sin libreta, una corrección o nota tardía posterior a su libreta vigente o una reemisión
	 * aprobada.
	 */
	@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
	public List<Pendiente> pendientes() {
		Map<Long, AnioEscolar> abiertos = anios.findAll().stream().filter(a -> a.getEstado() != EstadoAnioEscolar.CERRADO)
				.collect(Collectors.toMap(AnioEscolar::getId, Function.identity()));
		Set<Pendiente> pendientes = new LinkedHashSet<>();
		Map<Long, Set<Long>> activasPorAnio = new HashMap<>();
		for (PeriodoEvaluacion p : periodos.findByEstadoOrderByAnioEscolarIdAscNumeroAsc(EstadoPeriodo.CERRADO)) {
			if (!abiertos.containsKey(p.getAnioEscolarId()) || p.getCerradoEn() == null) {
				continue;
			}
			Map<Long, LibretaEmitida> vigentes = vigentesPorMatricula(p.getId());
			for (MatriculasAlCierre.AlCierre a : alCierre.recibenLibreta(p)) {
				if (!vigentes.containsKey(a.matricula().getId())) {
					pendientes.add(new Pendiente(p.getId(), a.seccionId()));
				}
			}
			Map<Long, Long> corregidas = ultimaCorreccionPorMatricula(p);
			Set<Long> activas = activasPorAnio.computeIfAbsent(p.getAnioEscolarId(), this::activasHoy);
			// QA del sprint 10: solo las matrículas que siguen activas o reservadas (la de un retirado no se reemite).
			vigentes.values().stream().filter(l -> activas.contains(l.getMatriculaId()))
					.filter(l -> corregidas.getOrDefault(l.getMatriculaId(), 0L) > l.marcas().calificacion())
					.forEach(l -> pendientes.add(new Pendiente(p.getId(), l.getSeccionId())));
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
		List<Long> hastaEste = hastaEste(periodo);
		// Hallazgo 1: con notas que no coinciden con la huella de su guardado, ninguna libreta de la sección sale.
		if (integridad.seccionAlterada(seccionId, hastaEste)) {
			LOG.warn("Libretas de la sección {} del periodo {} detenidas: hay notas que no coinciden con su guardado.",
					seccionId, periodoId);
			return new Resultado(0, 0, 0);
		}
		Map<Long, Matricula> delAnio = matriculas.delAnioEnOrden(periodo.getAnioEscolarId()).stream()
				.collect(Collectors.toMap(Matricula::getId, Function.identity()));
		List<Matricula> recibenAlCierre = alCierre.deLaSeccion(periodo, seccionId);
		Map<Long, LibretaEmitida> vigentes = vigentesPorMatricula(periodoId);
		List<LibretaEmitida> deLaSeccion = vigentes.values().stream()
				.filter(l -> l.getSeccionId().equals(seccion.getId())).toList();
		Set<Long> todas = new LinkedHashSet<>();
		recibenAlCierre.forEach(m -> todas.add(m.getId()));
		deLaSeccion.forEach(l -> todas.add(l.getMatriculaId()));
		FotoLibreta.DatosSeccion datos = foto.cargar(periodoId, seccionId, todas);
		// 1. La versión 1 de las que faltan, con lo registrado hasta el cierre.
		List<Matricula> faltan = recibenAlCierre.stream().filter(m -> !vigentes.containsKey(m.getId())).toList();
		Map<Long, LibretaEmitida.Marcas> marcasAlCierre = foto.marcasAlCierre(datos,
				faltan.stream().map(Matricula::getId).toList());
		List<LibretaEmitida> nuevas = new ArrayList<>();
		for (Matricula m : faltan) {
			LibretaEmitida.Donde donde = new LibretaEmitida.Donde(periodo.getAnioEscolarId(), periodoId, seccionId, m.getId(),
					m.getAlumno().getId());
			LibretaEmitida.Cabecera cabecera = foto.cabecera(datos, m.getAlumno());
			LibretaEmitida.Marcas marcas = marcasAlCierre.get(m.getId());
			ContenidoLibreta contenido = foto.armar(datos, donde, marcas, cabecera);
			LibretaEmitida l = libretas.saveAndFlush(LibretaEmitida.alCierre(donde, marcas, cabecera,
					FotoLibreta.huella(contenido), ahora).conNiveles(FotoLibreta.niveles(contenido)));
			vigentes.put(m.getId(), l);
			nuevas.add(l);
		}
		if (!nuevas.isEmpty()) {
			String lista = nuevas.stream().sorted(Comparator.comparing(LibretaEmitida::getId))
					.map(l -> l.getId() + ":" + l.getHuellaContenido()).collect(Collectors.joining("\n"));
			auditoria.registrar(AccionAuditoria.LIBRETAS_EMITIDAS, "seccion", String.valueOf(seccionId), null,
					nuevas.size() + " libreta(s) · " + FotoLibreta.sha256(lista), "Libretas del " + periodo.getNombre()
							+ " de " + seccion.etiqueta() + ": " + nuevas.size() + " emitida(s) al cierre (matrículas activas o "
							+ "reservadas en la sección al cierre; la deuda no cuenta).");
		}
		// 2. La versión siguiente de las que tienen una corrección o nota tardía aprobada (de este periodo o de uno anterior
		// del año) después de su marca de agua. Solo las matrículas que siguen activas o reservadas (en cualquier sección:
		// la libreta sigue en la sección del cierre, P-1); la de un retirado conserva su última versión.
		int corregidas = 0;
		Map<Long, Long> ultimaCorreccion = ultimaCorreccionPorMatricula(periodo);
		List<LibretaEmitida> porCorregir = vigentes.values().stream().filter(l -> l.getSeccionId().equals(seccionId))
				.filter(l -> activa(delAnio.get(l.getMatriculaId())))
				.filter(l -> ultimaCorreccion.getOrDefault(l.getMatriculaId(), 0L) > l.marcas().calificacion())
				.sorted(Comparator.comparing(LibretaEmitida::getId)).toList();
		Map<Long, Long> ultimas = foto.ultimasNotas(porCorregir.stream().map(LibretaEmitida::getMatriculaId).toList());
		for (LibretaEmitida anterior : porCorregir) {
			LibretaEmitida.Marcas marcas = new LibretaEmitida.Marcas(Math.max(anterior.marcas().calificacion(),
					ultimas.getOrDefault(anterior.getMatriculaId(), 0L)), anterior.marcas().marca(),
					anterior.marcas().exoneracion());
			LibretaEmitida.Cabecera cabecera = foto.cabecera(datos, delAnio.get(anterior.getMatriculaId()).getAlumno());
			ContenidoLibreta antes = foto.armar(datos, anterior.donde(), anterior.marcas(), anterior.cabecera());
			ContenidoLibreta despues = foto.armar(datos, anterior.donde(), marcas, cabecera);
			String cambios = cambios(antes, despues);
			LibretaEmitida l = libretas.saveAndFlush(LibretaEmitida.corregida(anterior, marcas, cabecera,
					FotoLibreta.huella(despues), cambios, ahora).conNiveles(FotoLibreta.niveles(despues)));
			vigentes.put(l.getMatriculaId(), l);
			corregidas++;
			auditoria.registrar(AccionAuditoria.LIBRETA_CORREGIDA_EMITIDA, "libreta_emitida", l.getId().toString(),
					"versión " + anterior.getNumeroVersion(), "versión " + l.getNumeroVersion(), "Libreta del "
							+ periodo.getNombre() + " de " + seccion.etiqueta() + " (alumno " + l.getAlumnoId()
							+ "), corregida: " + l.getCambios());
		}
		// 3. Las reemisiones aprobadas por Promotoría (A3), con los mismos niveles emitidos (hallazgo 2).
		int reemitidas = 0;
		for (SolicitudCambio s : reemisionesSinLibreta()) {
			Optional<LibretaEmitida> marcada = libretas.findById(s.getEntidadId())
					.filter(l -> l.getPeriodoId().equals(periodoId) && l.getSeccionId().equals(seccionId));
			if (marcada.isEmpty() || !activa(delAnio.get(marcada.get().getMatriculaId()))) {
				continue;
			}
			LibretaEmitida anterior = vigentes.get(marcada.get().getMatriculaId());
			ContenidoLibreta contenido = foto.armar(datos, anterior.donde(), anterior.marcas(), anterior.cabecera());
			String niveles = FotoLibreta.niveles(contenido);
			if (anterior.getNiveles() != null && !anterior.getNiveles().equals(niveles)) {
				LOG.warn("La reemisión {} no sale: los niveles de hoy no son los emitidos en la libreta {} (corregir por "
						+ "solicitud).", s.getId(), anterior.getId());
				continue;
			}
			LibretaEmitida l = libretas.saveAndFlush(LibretaEmitida.reemitida(anterior, anterior.marcas(),
					anterior.cabecera(), FotoLibreta.huella(contenido), s.getId(), ahora).conNiveles(niveles));
			vigentes.put(l.getMatriculaId(), l);
			reemitidas++;
			auditoria.registrar(AccionAuditoria.LIBRETA_REEMITIDA, "libreta_emitida", l.getId().toString(),
					anterior.getHuellaContenido(), l.getHuellaContenido(), "Libreta del " + periodo.getNombre() + " de "
							+ seccion.etiqueta() + " (alumno " + l.getAlumnoId() + ") reemitida como versión "
							+ l.getNumeroVersion() + " con los niveles emitidos y la reemisión aprobada por "
							+ s.getResueltoPor() + " (solicitud " + s.getId() + ").");
		}
		return new Resultado(nuevas.size(), corregidas, reemitidas);
	}

	/**
	 * Lo que cambió de una versión a la siguiente, sin conclusiones: en el periodo de la libreta «Matemática · Resuelve
	 * problemas de cantidad: B → A»; en un periodo anterior o en la anual, con su nombre entre paréntesis (hallazgo 4).
	 */
	static String cambios(ContenidoLibreta antes, ContenidoLibreta despues) {
		Map<Integer, String> nombres = despues.periodos().stream()
				.collect(Collectors.toMap(ContenidoLibreta.Periodo::numero, ContenidoLibreta.Periodo::nombre, (a, b) -> a));
		List<String> lineas = new ArrayList<>();
		for (int a = 0; a < despues.areas().size() && a < antes.areas().size(); a++) {
			ContenidoLibreta.Area area = despues.areas().get(a);
			for (int c = 0; c < area.competencias().size() && c < antes.areas().get(a).competencias().size(); c++) {
				ContenidoLibreta.Competencia nueva = area.competencias().get(c);
				ContenidoLibreta.Competencia vieja = antes.areas().get(a).competencias().get(c);
				for (int n = 0; n < nueva.notas().size() && n < vieja.notas().size(); n++) {
					ContenidoLibreta.Nota de = vieja.notas().get(n);
					ContenidoLibreta.Nota a2 = nueva.notas().get(n);
					boolean delPeriodo = a2.periodo() == despues.periodoNumero();
					String donde = delPeriodo ? "" : " (" + nombres.getOrDefault(a2.periodo(), "periodo " + a2.periodo())
							+ ")";
					if (!Objects.equals(de.nivel(), a2.nivel()) || de.exonerada() != a2.exonerada()) {
						lineas.add(area.nombre() + " · " + nueva.nombre() + donde + ": " + nivel(de) + " → " + nivel(a2));
					}
					else if (delPeriodo && !Objects.equals(de.conclusionSha256(), a2.conclusionSha256())) {
						lineas.add(area.nombre() + " · " + nueva.nombre() + ": se actualizó la conclusión");
					}
				}
				if (despues.ultimoPeriodo() && !Objects.equals(vieja.anual(), nueva.anual())) {
					lineas.add(area.nombre() + " · " + nueva.nombre() + " (anual): " + texto(vieja.anual()) + " → "
							+ texto(nueva.anual()));
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
		return n.exonerada() ? "exonerado" : texto(n.nivel());
	}

	private static String texto(String nivel) {
		return nivel == null ? "sin nota" : nivel;
	}

	private static boolean activa(Matricula m) {
		return m != null && RECIBEN_LIBRETA.contains(m.getEstado());
	}

	private Set<Long> activasHoy(Long anioId) {
		return matriculas.delAnioEnOrden(anioId).stream().filter(EmisorLibretas::activa).map(Matricula::getId)
				.collect(Collectors.toSet());
	}

	/** Los periodos del año hasta este (las columnas que muestra su libreta). */
	private List<Long> hastaEste(PeriodoEvaluacion periodo) {
		return periodos.findByAnioEscolarIdOrderByNumeroAsc(periodo.getAnioEscolarId()).stream()
				.filter(p -> p.getNumero() <= periodo.getNumero()).map(PeriodoEvaluacion::getId).toList();
	}

	private Map<Long, LibretaEmitida> vigentesPorMatricula(long periodoId) {
		return libretas.findByPeriodoIdOrderByIdAsc(periodoId).stream().collect(Collectors.toMap(
				LibretaEmitida::getMatriculaId, Function.identity(),
				(a, b) -> a.getNumeroVersion() > b.getNumeroVersion() ? a : b, HashMap::new));
	}

	/** Hallazgo 4: la última corrección o nota tardía de cada matrícula en este periodo o en uno anterior del año. */
	private Map<Long, Long> ultimaCorreccionPorMatricula(PeriodoEvaluacion periodo) {
		return calificaciones.findByPeriodoIdInAndOrigenInOrderByIdAsc(hastaEste(periodo), CAMBIAN_LA_LIBRETA).stream()
				.collect(Collectors.toMap(Calificacion::getMatriculaId, Calificacion::getId, Math::max));
	}

	private List<SolicitudCambio> reemisionesSinLibreta() {
		return solicitudes.findByTipoOrderByIdDesc(TipoSolicitud.REEMISION_LIBRETA).stream()
				.filter(s -> s.getEstado() == EstadoSolicitud.APROBADA && !libretas.existsBySolicitudId(s.getId()))
				.toList();
	}
}
