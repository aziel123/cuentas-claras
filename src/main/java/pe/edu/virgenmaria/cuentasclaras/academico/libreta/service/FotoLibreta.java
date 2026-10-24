package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.AreaCurricular;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.Competencia;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.AreaCurricularRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.CompetenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.ConfiguracionEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.EscalaLogroRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.TipoCodigo;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.CodigoModular;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MarcaAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.Calificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.ExoneracionArea;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.CalificacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.ExoneracionAreaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.CodigoModularRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.MarcaAsistenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 3 (secciones 3.7 y 3.13; decisión 170, A3, M9): la FOTO de una libreta. Arma el contenido con las marcas
 * de agua (hasta qué nota, qué marca de asistencia y qué exoneración entran) y la cabecera congelada, y calcula su huella.
 * Como las notas, las marcas y las exoneraciones son de solo inserción, la misma foto da siempre el mismo contenido: al
 * descargar se recalcula y, si la huella no coincide, alguien tocó la base (E40).
 * <ul>
 *   <li>La nota de cada periodo y competencia es la última de su cadena con id hasta la marca de agua.</li>
 *   <li>Una exoneración rige desde su periodo (A3): los periodos anteriores conservan sus notas.</li>
 *   <li>La asistencia: por periodo, las marcas vigentes (hasta la marca de agua) de FALTA_JUSTIFICADA, FALTA y TARDE.</li>
 * </ul>
 * No lee nada de cobranza (ArchUnit): no hay forma de que la libreta dependa de la deuda (INDECOPI, sección 3.8). Sin
 * {@code @PreAuthorize}: la usan los servicios protegidos de la libreta (sistema.libretas, la familia, Dirección y el
 * tutor), siempre dentro de su transacción.
 */
@Component
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class FotoLibreta {

	/** La cadena canónica de la huella cambia de versión si cambia su forma (nunca en silencio). */
	static final String VERSION_HUELLA = "LIBRETA-1";

	private final PeriodoEvaluacionRepository periodos;

	private final ConfiguracionEvaluacionRepository configuraciones;

	private final SeccionRepository secciones;

	private final AreaCurricularRepository areas;

	private final CompetenciaRepository competencias;

	private final EscalaLogroRepository escala;

	private final CalificacionRepository calificaciones;

	private final MarcaAsistenciaRepository marcas;

	private final ExoneracionAreaRepository exoneraciones;

	private final CodigoModularRepository codigos;

	private final AsignacionDocenteRepository asignaciones;

	private final UsuarioRepository usuarios;

	private final ColegioService colegios;

	public FotoLibreta(PeriodoEvaluacionRepository periodos, ConfiguracionEvaluacionRepository configuraciones,
			SeccionRepository secciones, AreaCurricularRepository areas, CompetenciaRepository competencias,
			EscalaLogroRepository escala, CalificacionRepository calificaciones, MarcaAsistenciaRepository marcas,
			ExoneracionAreaRepository exoneraciones, CodigoModularRepository codigos,
			AsignacionDocenteRepository asignaciones, UsuarioRepository usuarios, ColegioService colegios) {
		this.periodos = periodos;
		this.configuraciones = configuraciones;
		this.secciones = secciones;
		this.areas = areas;
		this.competencias = competencias;
		this.escala = escala;
		this.calificaciones = calificaciones;
		this.marcas = marcas;
		this.exoneraciones = exoneraciones;
		this.codigos = codigos;
		this.asignaciones = asignaciones;
		this.usuarios = usuarios;
		this.colegios = colegios;
	}

	/**
	 * Lo de una sección y un periodo que comparten sus libretas, cargado una sola vez (emitir 300 libretas o imprimir 45 no
	 * hace 45 consultas por alumno): el catálogo del grado, los periodos hasta este y la historia de esas matrículas.
	 */
	public final class DatosSeccion {

		private final PeriodoEvaluacion periodo;

		private final List<PeriodoEvaluacion> hastaEste;

		private final Seccion seccion;

		private final ConfiguracionEvaluacion configuracion;

		private final List<AreaCurricular> areasDelGrado;

		private final Map<Long, List<Competencia>> competenciasPorArea;

		private final List<ContenidoLibreta.Escala> leyenda;

		private final Map<Long, List<Calificacion>> notasPorMatricula;

		private final Map<Long, List<MarcaAsistencia>> marcasPorMatricula;

		private final Map<Long, List<ExoneracionArea>> exoneracionesPorMatricula;

		private final String colegio;

		private DatosSeccion(PeriodoEvaluacion periodo, List<PeriodoEvaluacion> hastaEste, Seccion seccion,
				ConfiguracionEvaluacion configuracion, List<AreaCurricular> areasDelGrado,
				Map<Long, List<Competencia>> competenciasPorArea, List<ContenidoLibreta.Escala> leyenda,
				Map<Long, List<Calificacion>> notasPorMatricula, Map<Long, List<MarcaAsistencia>> marcasPorMatricula,
				Map<Long, List<ExoneracionArea>> exoneracionesPorMatricula, String colegio) {
			this.periodo = periodo;
			this.hastaEste = hastaEste;
			this.seccion = seccion;
			this.configuracion = configuracion;
			this.areasDelGrado = areasDelGrado;
			this.competenciasPorArea = competenciasPorArea;
			this.leyenda = leyenda;
			this.notasPorMatricula = notasPorMatricula;
			this.marcasPorMatricula = marcasPorMatricula;
			this.exoneracionesPorMatricula = exoneracionesPorMatricula;
			this.colegio = colegio;
		}

		public PeriodoEvaluacion periodo() {
			return periodo;
		}

		public Seccion seccion() {
			return seccion;
		}

		public ConfiguracionEvaluacion configuracion() {
			return configuracion;
		}
	}

	/** Carga lo de la sección para esas matrículas (las de la sección o de sus libretas). */
	public DatosSeccion cargar(Long periodoId, Long seccionId, Collection<Long> matriculas) {
		PeriodoEvaluacion periodo = periodos.findById(periodoId)
				.orElseThrow(() -> new IllegalStateException("El periodo " + periodoId + " no existe"));
		Seccion seccion = secciones.findById(seccionId)
				.orElseThrow(() -> new IllegalStateException("La sección " + seccionId + " no existe"));
		ConfiguracionEvaluacion configuracion = configuraciones.findByAnioEscolarId(periodo.getAnioEscolarId())
				.orElseThrow(() -> new IllegalStateException("El año del periodo no tiene evaluación configurada"));
		List<PeriodoEvaluacion> hastaEste = periodos.findByAnioEscolarIdOrderByNumeroAsc(periodo.getAnioEscolarId())
				.stream().filter(p -> p.getNumero() <= periodo.getNumero()).toList();
		List<AreaCurricular> delNivel = areas.findByVersionIdAndNivelOrderByOrdenAsc(configuracion.getCurriculoVersionId(),
				seccion.nivel());
		Map<Long, List<Competencia>> porArea = delNivel.isEmpty() ? Map.of()
				: competencias.findByAreaIdInOrderByOrdenAsc(delNivel.stream().map(AreaCurricular::getId).toList()).stream()
						.filter(c -> c.aplicaA(seccion.getGrado()))
						.sorted(Comparator.comparingInt(Competencia::getOrden))
						.collect(Collectors.groupingBy(Competencia::getAreaId));
		List<AreaCurricular> conCompetencias = delNivel.stream().filter(a -> porArea.containsKey(a.getId()))
				.sorted(Comparator.comparing(AreaCurricular::isTransversal).thenComparingInt(AreaCurricular::getOrden))
				.toList();
		List<ContenidoLibreta.Escala> leyenda = escala.findByVersionIdOrderByNivelAscOrdenAsc(
				configuracion.getCurriculoVersionId()).stream().filter(e -> e.getNivel() == seccion.nivel())
				.map(e -> new ContenidoLibreta.Escala(e.getValor(), e.getNombre(), e.getDescripcion())).toList();
		List<Long> ids = List.copyOf(matriculas);
		Map<Long, List<Calificacion>> notas = ids.isEmpty() ? Map.of()
				: calificaciones.findByMatriculaIdInAndPeriodoIdInOrderByIdAsc(ids,
						hastaEste.stream().map(PeriodoEvaluacion::getId).toList()).stream()
						.collect(Collectors.groupingBy(Calificacion::getMatriculaId));
		Map<Long, List<MarcaAsistencia>> asistencia = ids.isEmpty() || hastaEste.isEmpty() ? Map.of()
				: marcas.findByMatriculaIdInAndFechaBetweenOrderByIdAsc(ids, hastaEste.get(0).getInicio(), periodo.getFin())
						.stream().collect(Collectors.groupingBy(MarcaAsistencia::getMatriculaId));
		Map<Long, List<ExoneracionArea>> exoneradas = ids.isEmpty() ? Map.of()
				: exoneraciones.findByMatriculaIdIn(ids).stream()
						.collect(Collectors.groupingBy(ExoneracionArea::getMatriculaId));
		return new DatosSeccion(periodo, hastaEste, seccion, configuracion, conCompetencias, porArea, leyenda, notas,
				asistencia, exoneradas, colegios.nombreDe(ContextoColegio.actual()));
	}

	/** El contenido de UNA libreta emitida (la descarga y la verificación). */
	public ContenidoLibreta armar(LibretaEmitida libreta) {
		DatosSeccion datos = cargar(libreta.getPeriodoId(), libreta.getSeccionId(), List.of(libreta.getMatriculaId()));
		return armar(datos, libreta.donde(), libreta.marcas(), libreta.cabecera());
	}

	/** El contenido de una matrícula con esas marcas de agua y esa cabecera. */
	public ContenidoLibreta armar(DatosSeccion datos, LibretaEmitida.Donde donde, LibretaEmitida.Marcas marcasDeAgua,
			LibretaEmitida.Cabecera cabecera) {
		List<PeriodoEvaluacion> hastaEste = datos.hastaEste;
		Map<Long, Integer> numeroDe = hastaEste.stream().collect(Collectors.toMap(PeriodoEvaluacion::getId,
				PeriodoEvaluacion::getNumero));
		// La nota vigente a la marca de agua: la última de cada cadena (periodo, competencia) con id hasta la marca.
		Map<String, Calificacion> vigentes = new HashMap<>();
		for (Calificacion c : datos.notasPorMatricula.getOrDefault(donde.matriculaId(), List.of())) {
			if (c.getId() <= marcasDeAgua.calificacion()) {
				vigentes.merge(c.getPeriodoId() + ":" + c.getCompetenciaId(), c,
						(a, b) -> a.getId() > b.getId() ? a : b);
			}
		}
		// A3: una exoneración rige desde el número de su periodo; solo las que existían hasta la marca de agua.
		Map<Long, Integer> exoneradaDesde = new HashMap<>();
		for (ExoneracionArea e : datos.exoneracionesPorMatricula.getOrDefault(donde.matriculaId(), List.of())) {
			if (e.getId() <= marcasDeAgua.exoneracion()) {
				int desde = periodos.findById(e.getDesdePeriodoId()).map(PeriodoEvaluacion::getNumero)
						.orElse(Integer.MAX_VALUE);
				exoneradaDesde.merge(e.getAreaId(), desde, Math::min);
			}
		}
		List<ContenidoLibreta.Area> lista = new ArrayList<>();
		for (AreaCurricular area : datos.areasDelGrado) {
			List<ContenidoLibreta.Competencia> filas = new ArrayList<>();
			for (Competencia competencia : datos.competenciasPorArea.getOrDefault(area.getId(), List.of())) {
				List<ContenidoLibreta.Nota> notas = new ArrayList<>();
				for (PeriodoEvaluacion p : hastaEste) {
					boolean exonerada = exoneradaDesde.getOrDefault(area.getId(), Integer.MAX_VALUE) <= p.getNumero();
					Calificacion c = vigentes.get(p.getId() + ":" + competencia.getId());
					String nivel = exonerada || c == null || c.getNivelLogro() == null ? null : c.getNivelLogro().name();
					boolean esteperiodo = p.getId().equals(donde.periodoId());
					String conclusion = exonerada || c == null || !esteperiodo ? null : c.getConclusion();
					String sha = exonerada || c == null ? null : c.getConclusionSha256();
					notas.add(new ContenidoLibreta.Nota(p.getNumero(), nivel, exonerada, conclusion, sha));
				}
				String anual = null;
				if (ultimo(datos)) {
					anual = notas.stream().filter(n -> n.nivel() != null).reduce((a, b) -> b)
							.map(ContenidoLibreta.Nota::nivel).orElse(null);
				}
				filas.add(new ContenidoLibreta.Competencia(competencia.getCodigo(), competencia.getNombre(), notas, anual));
			}
			lista.add(new ContenidoLibreta.Area(area.getCodigo(), area.getNombre(), area.isTransversal(), filas));
		}
		List<ContenidoLibreta.Asistencia> asistencia = new ArrayList<>();
		List<MarcaAsistencia> historia = datos.marcasPorMatricula.getOrDefault(donde.matriculaId(), List.of()).stream()
				.filter(m -> m.getId() <= marcasDeAgua.marca()).toList();
		for (PeriodoEvaluacion p : hastaEste) {
			// La vigente a la marca de agua de cada registro (día): la de id más alto hasta la marca.
			Map<Long, MarcaAsistencia> porRegistro = historia.stream()
					.filter(m -> !m.getFecha().isBefore(p.getInicio()) && !m.getFecha().isAfter(p.getFin()))
					.collect(Collectors.toMap(MarcaAsistencia::getRegistroId, Function.identity(),
							(a, b) -> a.getId() > b.getId() ? a : b));
			int justificadas = 0;
			int injustificadas = 0;
			int tardanzas = 0;
			for (MarcaAsistencia m : porRegistro.values()) {
				justificadas += m.getEstado() == EstadoAsistencia.FALTA_JUSTIFICADA ? 1 : 0;
				injustificadas += m.getEstado() == EstadoAsistencia.FALTA ? 1 : 0;
				tardanzas += m.getEstado() == EstadoAsistencia.TARDE ? 1 : 0;
			}
			asistencia.add(new ContenidoLibreta.Asistencia(p.getNumero(), p.getNombre(), justificadas, injustificadas,
					tardanzas));
		}
		Seccion s = datos.seccion;
		return new ContenidoLibreta(datos.colegio, s.nivel().etiqueta(), s.getGrado().etiqueta(), s.getNombre(),
				s.getAnioEscolar().getAnio(), donde.alumnoId(), donde.periodoId(), datos.periodo.getNumero(),
				datos.periodo.getNombre(), ultimo(datos), cabecera,
				hastaEste.stream().map(p -> new ContenidoLibreta.Periodo(p.getNumero(), p.getNombre())).toList(), lista,
				asistencia, datos.leyenda);
	}

	private static boolean ultimo(DatosSeccion datos) {
		return datos.periodo.getNumero() >= datos.configuracion.getNumeroPeriodos();
	}

	/**
	 * Las marcas de agua de la versión 1 (sección 3 del brief): lo registrado hasta el {@code cerrado_en} del periodo, no
	 * «lo que haya hoy». Así una corrección aprobada después del cierre no se esconde dentro de la versión 1.
	 */
	public Map<Long, LibretaEmitida.Marcas> marcasAlCierre(DatosSeccion datos, Collection<Long> matriculas) {
		LocalDateTime cierre = datos.periodo.getCerradoEn();
		if (cierre == null) {
			throw new IllegalStateException("El periodo no está cerrado");
		}
		List<Long> ids = List.copyOf(matriculas);
		Map<Long, Long> notas = ids.isEmpty() ? Map.of() : calificaciones.ultimasHasta(ids, cierre).stream()
				.collect(Collectors.toMap(f -> (Long) f[0], f -> (Long) f[1]));
		Map<Long, Long> asistencia = ids.isEmpty() ? Map.of() : marcas.ultimasHasta(ids, cierre).stream()
				.collect(Collectors.toMap(f -> (Long) f[0], f -> (Long) f[1]));
		Map<Long, LibretaEmitida.Marcas> resultado = new HashMap<>();
		for (Long m : ids) {
			long exoneracion = datos.exoneracionesPorMatricula.getOrDefault(m, List.of()).stream()
					.filter(e -> !e.getCreadoEn().isAfter(cierre)).mapToLong(ExoneracionArea::getId).max().orElse(0);
			resultado.put(m, new LibretaEmitida.Marcas(notas.getOrDefault(m, 0L), asistencia.getOrDefault(m, 0L),
					exoneracion));
		}
		return resultado;
	}

	/** La marca de agua de las notas de una versión corregida: todo lo registrado de esa matrícula. */
	public Map<Long, Long> ultimasNotas(Collection<Long> matriculas) {
		return matriculas.isEmpty() ? Map.of() : calificaciones.ultimas(List.copyOf(matriculas)).stream()
				.collect(Collectors.toMap(f -> (Long) f[0], f -> (Long) f[1]));
	}

	/**
	 * La cabecera que se congela en la foto (A3, M9): el código de estudiante y, solo si no hay, el documento con su tipo;
	 * el código modular del nivel, la DRE y la UGEL del año, el tutor vigente de la sección y quien cerró el periodo.
	 */
	public LibretaEmitida.Cabecera cabecera(DatosSeccion datos, Alumno alumno) {
		String codigo = alumno.getCodigoEstudiante();
		TipoCodigo tipo = TipoCodigo.CODIGO_ESTUDIANTE;
		if (codigo == null || codigo.isBlank()) {
			codigo = alumno.getDocumento().numero();
			tipo = TipoCodigo.de(alumno.getDocumento().tipo());
		}
		String modular = codigos.findByNivel(datos.seccion.nivel()).map(CodigoModular::getCodigo).orElse(null);
		String tutor = asignaciones.findFirstBySeccionIdAndTipoAndEstado(datos.seccion.getId(), TipoAsignacion.TUTOR,
				EstadoAsignacion.VIGENTE).map(AsignacionDocente::getUsuarioId).flatMap(usuarios::findById)
				.map(Usuario::getNombreCompleto).orElse(null);
		String director = Optional.ofNullable(datos.periodo.getCerradoPor()).flatMap(usuarios::findByNombreUsuario)
				.map(Usuario::getNombreCompleto).orElse(datos.periodo.getCerradoPor() == null ? "Dirección"
						: datos.periodo.getCerradoPor());
		String nombre = alumno.getApellidoPaterno() + (alumno.getApellidoMaterno() == null ? ""
				: " " + alumno.getApellidoMaterno()) + ", " + alumno.getNombres();
		return new LibretaEmitida.Cabecera(recortar(nombre, 190), codigo, tipo, modular, recortar(datos.configuracion.getDre(),
				80), recortar(datos.configuracion.getUgel(), 80), recortar(tutor, 190), recortar(director, 190));
	}

	/**
	 * La huella del contenido: SHA-256 de una cadena canónica (una línea por dato; los textos sin barras verticales ni
	 * saltos de línea, que son los separadores). Entra la cabecera congelada (A3), cada nota de cada periodo con la huella
	 * de su conclusión, las exoneraciones y la asistencia. No entran el logo, el nombre del colegio ni la leyenda de la
	 * escala (no son del alumno).
	 */
	public static String huella(ContenidoLibreta c) {
		StringBuilder cadena = new StringBuilder(VERSION_HUELLA).append('\n');
		LibretaEmitida.Cabecera k = c.cabecera();
		cadena.append("alumno|").append(c.alumnoId()).append('|').append(limpio(k.alumnoNombre())).append('|')
				.append(k.tipoCodigo().name()).append('|').append(limpio(k.alumnoCodigo())).append('\n');
		cadena.append("periodo|").append(c.periodoId()).append('|').append(c.periodoNumero()).append('\n');
		cadena.append("cabecera|").append(limpio(k.codigoModular())).append('|').append(limpio(k.dre())).append('|')
				.append(limpio(k.ugel())).append('|').append(limpio(k.tutorNombre())).append('|')
				.append(limpio(k.directorNombre())).append('\n');
		for (ContenidoLibreta.Area area : c.areas()) {
			for (ContenidoLibreta.Competencia competencia : area.competencias()) {
				for (ContenidoLibreta.Nota n : competencia.notas()) {
					cadena.append("c|").append(n.periodo()).append('|').append(competencia.codigo()).append('|')
							.append(n.exonerada() ? "EXO" : n.nivel() == null ? "-" : n.nivel()).append('|')
							.append(n.conclusionSha256() == null ? "-" : n.conclusionSha256()).append('\n');
				}
			}
		}
		for (ContenidoLibreta.Asistencia a : c.asistencia()) {
			cadena.append("asis|").append(a.periodo()).append('|').append(a.justificadas()).append('|')
					.append(a.injustificadas()).append('|').append(a.tardanzas()).append('\n');
		}
		return sha256(cadena.toString());
	}

	/**
	 * Correcciones del sprint 10 (hallazgo 2): los NIVELES de la libreta en forma canónica, sin conclusiones ni asistencia:
	 * una línea {@code periodo|competencia|nivel} por celda (EXO si está exonerada, - sin nota) y
	 * {@code anual|competencia|nivel} en el último periodo. Se guarda al emitir: la reemisión vuelve a emitir estos mismos y
	 * Promotoría ve en qué difiere la base hoy.
	 */
	public static String niveles(ContenidoLibreta c) {
		StringBuilder cadena = new StringBuilder();
		for (ContenidoLibreta.Area area : c.areas()) {
			for (ContenidoLibreta.Competencia competencia : area.competencias()) {
				for (ContenidoLibreta.Nota n : competencia.notas()) {
					cadena.append(n.periodo()).append('|').append(competencia.codigo()).append('|')
							.append(n.exonerada() ? "EXO" : n.nivel() == null ? "-" : n.nivel()).append('\n');
				}
				if (c.ultimoPeriodo()) {
					cadena.append("anual|").append(competencia.codigo()).append('|')
							.append(competencia.anual() == null ? "-" : competencia.anual()).append('\n');
				}
			}
		}
		return cadena.toString();
	}

	/**
	 * Las líneas que difieren entre los niveles emitidos y los de hoy, para la bandeja: «2|C05: emitido B, hoy A». Vacía si
	 * coinciden.
	 */
	public static List<String> diferencias(String emitidos, String hoy) {
		Map<String, String> antes = porClave(emitidos);
		Map<String, String> ahora = porClave(hoy);
		java.util.Set<String> claves = new java.util.LinkedHashSet<>(antes.keySet());
		claves.addAll(ahora.keySet());
		List<String> lista = new ArrayList<>();
		for (String clave : claves) {
			String a = antes.getOrDefault(clave, "(no estaba)");
			String b = ahora.getOrDefault(clave, "(no está)");
			if (!a.equals(b)) {
				lista.add(clave.replace('|', ' ') + ": emitido " + a + ", hoy " + b);
			}
		}
		return lista;
	}

	private static Map<String, String> porClave(String niveles) {
		Map<String, String> mapa = new java.util.LinkedHashMap<>();
		if (niveles == null) {
			return mapa;
		}
		for (String linea : niveles.split("\n")) {
			int corte = linea.lastIndexOf('|');
			if (corte > 0) {
				mapa.put(linea.substring(0, corte), linea.substring(corte + 1));
			}
		}
		return mapa;
	}

	static String sha256(String texto) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(texto.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String limpio(String texto) {
		return texto == null ? "-" : texto.replace('|', ' ').replace('\n', ' ').replace('\r', ' ');
	}

	private static String recortar(String texto, int maximo) {
		if (texto == null) {
			return null;
		}
		String t = texto.strip();
		return t.length() <= maximo ? t : t.substring(0, maximo);
	}
}
