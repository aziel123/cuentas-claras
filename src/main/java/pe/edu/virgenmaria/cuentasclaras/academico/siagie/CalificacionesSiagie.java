package pe.edu.virgenmaria.cuentasclaras.academico.siagie;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.LibretaEmitidaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.ContenidoLibreta;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.RegeneracionLibreta;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.Calificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.OrigenCalificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.CalificacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.service.SesionDeQuienEscribe;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.model.ExportacionCalificaciones;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.repository.ExportacionCalificacionesRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
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
import pe.edu.virgenmaria.cuentasclaras.comun.excel.Celda;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.EscritorXlsxSeguro;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.HojaReporte;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 3 (secciones 3.10 y 3.13; A6, M12, decisiones 181 y 189): la exportación PROVISIONAL de calificaciones
 * para el SIAGIE, un {@code .xlsx} por sección y periodo CERRADO que la secretaria copia en la plantilla que generó el
 * SIAGIE.
 * <ul>
 *   <li><b>Misma foto que la libreta (A6):</b> sale de la libreta VIGENTE de cada matrícula, con su huella; se rechaza si
 *       a alguna le falta la libreta o tiene una corrección o nota tardía aprobada sin su libreta nueva («Espere a que se
 *       emitan las libretas corregidas»), o si una no coincide con su huella. Al SIAGIE se carga lo que recibió la
 *       familia.</li>
 *   <li>Una hoja por área, una de transversales y la hoja «Control» (por competencia, cuántos AD, A, B, C, sin nota y EXO)
 *       para que Dirección la compare con el SIAGIE después de la carga.</li>
 *   <li>La sacan Dirección o la persona de Administración delegada (M12), con su sesión
 *       (trg_exportacion_calificaciones_nace). Cada descarga queda en {@code exportacion_calificaciones} (sin datos de
 *       alumnos), en la bitácora y en «Quién vio datos personales» ({@code CALIFICACIONES_SIAGIE}, con la cantidad); más de
 *       {@code max-secciones-dia} en un día por persona se resalta para Promotoría.</li>
 *   <li>«PROVISIONAL», el código, el usuario y la fecha en la primera fila y en el nombre del archivo; todo como texto
 *       ({@link EscritorXlsxSeguro}).</li>
 * </ul>
 */
@Service
@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
public class CalificacionesSiagie {

	static final Set<OrigenCalificacion> CAMBIAN_LA_LIBRETA = EnumSet.of(OrigenCalificacion.CORRECCION_APROBADA,
			OrigenCalificacion.REGISTRO_TARDIO);

	private static final DateTimeFormatter MOMENTO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private static final SecureRandom AZAR = new SecureRandom();

	/** El archivo (no se guarda) y lo que se registró. */
	public record Archivo(String nombreArchivo, byte[] contenido, String codigo, int filas) {
	}

	/** La pantalla: qué secciones y periodos se pueden exportar y la última exportación de cada sección. */
	public record Vista(boolean puede, boolean direccion, List<Opcion> secciones, List<Opcion> periodos, List<Ultima> ultimas) {
	}

	public record Opcion(Long id, String etiqueta) {
	}

	/** La última exportación de una sección y un periodo, y si quedó desactualizada por una libreta posterior. */
	public record Ultima(String seccion, String periodo, String codigo, String cuando, String por, boolean desactualizada) {
	}

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final PeriodoEvaluacionRepository periodos;

	private final MatriculaRepository matriculas;

	private final LibretaEmitidaRepository libretas;

	private final CalificacionRepository calificaciones;

	private final RegeneracionLibreta regeneracion;

	private final ExportacionCalificacionesRepository exportaciones;

	private final DelegadaSiagie delegada;

	private final EscritorXlsxSeguro escritor;

	private final AuditoriaService auditoria;

	private final SesionDeQuienEscribe sesiones;

	private final Clock reloj;

	private final String perfil;

	private final int maxSeccionesDia;

	private final boolean demo;

	public CalificacionesSiagie(AnioEscolarRepository anios, SeccionRepository secciones,
			PeriodoEvaluacionRepository periodos, MatriculaRepository matriculas, LibretaEmitidaRepository libretas,
			CalificacionRepository calificaciones, RegeneracionLibreta regeneracion,
			ExportacionCalificacionesRepository exportaciones, DelegadaSiagie delegada, EscritorXlsxSeguro escritor,
			AuditoriaService auditoria, SesionDeQuienEscribe sesiones, Clock reloj,
			@Value("${cuentasclaras.siagie.perfil-calificaciones:SIAGIE_CALIFICACIONES_PROVISIONAL}") String perfil,
			@Value("${cuentasclaras.siagie.max-secciones-dia:10}") int maxSeccionesDia, Environment entorno) {
		this.anios = anios;
		this.secciones = secciones;
		this.periodos = periodos;
		this.matriculas = matriculas;
		this.libretas = libretas;
		this.calificaciones = calificaciones;
		this.regeneracion = regeneracion;
		this.exportaciones = exportaciones;
		this.delegada = delegada;
		this.escritor = escritor;
		this.auditoria = auditoria;
		this.sesiones = sesiones;
		this.reloj = reloj;
		this.perfil = perfil;
		this.maxSeccionesDia = maxSeccionesDia;
		this.demo = Arrays.asList(entorno.getActiveProfiles()).contains("demo");
	}

	@Transactional(readOnly = true)
	public Vista vista() {
		UsuarioAutenticado persona = DelegadaSiagie.persona();
		boolean puede = delegada.puedeExportar(persona);
		boolean direccion = persona.roles().contains(pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.DIRECTOR);
		AnioEscolar anio = anios.findByEstado(EstadoAnioEscolar.EN_CURSO).orElse(null);
		if (!puede || anio == null) {
			return new Vista(puede, direccion, List.of(), List.of(), List.of());
		}
		List<Seccion> delAnio = secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(anio.getId());
		List<PeriodoEvaluacion> cerrados = periodos.findByAnioEscolarIdOrderByNumeroAsc(anio.getId()).stream()
				.filter(p -> p.getEstado() == EstadoPeriodo.CERRADO).toList();
		List<Ultima> ultimas = new ArrayList<>();
		for (PeriodoEvaluacion p : cerrados) {
			for (Seccion s : delAnio) {
				exportaciones.findByPeriodoIdAndSeccionIdOrderByIdDesc(p.getId(), s.getId()).stream().findFirst()
						.ifPresent(e -> ultimas.add(new Ultima(s.etiqueta(), p.getNombre(), e.getCodigo(),
								e.getCreadoEn().format(MOMENTO), e.getCreadoPor(),
								!e.getHuellaLibretas().equals(huellaLibretas(libretas.vigentesDeSeccion(p.getId(),
										s.getId()))))));
			}
		}
		return new Vista(true, direccion, delAnio.stream().map(s -> new Opcion(s.getId(), s.etiqueta())).toList(),
				cerrados.stream().map(p -> new Opcion(p.getId(), p.getNombre())).toList(), ultimas);
	}

	/** La exportación de una sección y un periodo cerrado (A6): de las libretas vigentes, con su hoja «Control». */
	@Transactional
	public Archivo exportar(long seccionId, long periodoId) {
		UsuarioAutenticado persona = DelegadaSiagie.persona();
		if (!delegada.puedeExportar(persona)) {
			throw new org.springframework.security.access.AccessDeniedException(
					"Exporta Dirección o la persona de Administración con su delegación vigente");
		}
		Seccion seccion = secciones.findById(seccionId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Sección no encontrada"));
		PeriodoEvaluacion periodo = periodos.findById(periodoId)
				.filter(p -> p.getAnioEscolarId().equals(seccion.getAnioEscolar().getId()))
				.orElseThrow(() -> new RecursoNoEncontradoException("Periodo no encontrado"));
		if (periodo.getEstado() != EstadoPeriodo.CERRADO) {
			throw new ReglaNegocioException("El " + periodo.getNombre() + " todavía no está cerrado: se exporta al cerrarlo.");
		}
		List<Matricula> lista = matriculas.deLaSeccionEnOrden(seccion.getId(), List.of(EstadoMatricula.ACTIVA,
				EstadoMatricula.RESERVADA));
		Map<Long, LibretaEmitida> vigentes = libretas.vigentesDeSeccion(periodo.getId(), seccion.getId()).stream()
				.collect(Collectors.toMap(LibretaEmitida::getMatriculaId, Function.identity()));
		long sinLibreta = lista.stream().filter(m -> !vigentes.containsKey(m.getId())).count();
		if (sinLibreta > 0) {
			throw new ReglaNegocioException(sinLibreta + " alumno(s) todavía no tienen su libreta del "
					+ periodo.getNombre() + ": espere unos minutos a que se emitan.");
		}
		Map<Long, Long> ultimaCorreccion = calificaciones.findByPeriodoIdAndOrigenInOrderByIdAsc(periodo.getId(),
				CAMBIAN_LA_LIBRETA).stream().collect(Collectors.toMap(Calificacion::getMatriculaId, Calificacion::getId,
						Math::max));
		boolean pendientes = vigentes.values().stream()
				.anyMatch(l -> ultimaCorreccion.getOrDefault(l.getMatriculaId(), 0L) > l.marcas().calificacion());
		if (pendientes) {
			throw new ReglaNegocioException("Hay correcciones o notas tardías aprobadas que todavía no llegan a su libreta. "
					+ "Espere a que se emitan las libretas corregidas: al SIAGIE se carga lo mismo que recibieron las "
					+ "familias.");
		}
		List<LibretaEmitida> usadas = lista.stream().map(m -> vigentes.get(m.getId())).toList();
		List<RegeneracionLibreta.Regenerada> regeneradas = regeneracion.regenerar(usadas);
		if (regeneradas.stream().anyMatch(r -> !r.coincide())) {
			throw new ReglaNegocioException("Una libreta de la sección no coincide con su huella y está en revisión: "
					+ "Promotoría ya recibió la alerta. Se exporta cuando se reemita.");
		}
		List<RegeneracionLibreta.Regenerada> enOrden = regeneradas.stream()
				.sorted(Comparator.comparing(r -> r.libreta().cabecera().alumnoNombre(), espanol())).toList();
		PerfilCalificaciones columnas = PerfilCalificaciones.leer(perfil);
		String codigo = nuevoCodigo();
		LocalDateTime ahora = LocalDateTime.now(reloj);
		String marca = (demo ? "DEMO · DATOS FICTICIOS · " : "") + (columnas.esProvisional() ? "PROVISIONAL" : columnas.nombre())
				+ " · copie estas columnas en la plantilla del SIAGIE · " + seccion.etiqueta() + " · " + periodo.getNombre()
				+ " · código " + codigo + " · " + persona.getUsername() + " · " + ahora.format(MOMENTO);
		List<HojaReporte> hojas = new ArrayList<>();
		ContenidoLibreta molde = enOrden.get(0).contenido();
		List<ContenidoLibreta.Area> deAreas = molde.areas().stream().filter(a -> !a.transversal()).toList();
		List<ContenidoLibreta.Area> transversales = molde.areas().stream().filter(ContenidoLibreta.Area::transversal)
				.toList();
		for (int a = 0; a < molde.areas().size(); a++) {
			ContenidoLibreta.Area area = molde.areas().get(a);
			if (area.transversal()) {
				continue;
			}
			hojas.add(hoja(nombreHoja(area.nombre()), marca, columnas, enOrden, List.of(a)));
		}
		if (!transversales.isEmpty()) {
			List<Integer> indices = new ArrayList<>();
			for (int a = 0; a < molde.areas().size(); a++) {
				if (molde.areas().get(a).transversal()) {
					indices.add(a);
				}
			}
			hojas.add(hoja("Transversales", marca, columnas, enOrden, indices));
		}
		hojas.add(control(marca, enOrden));
		byte[] archivo = escritor.escribir(hojas);
		String sha = sha256(archivo);
		String huella = huellaLibretas(usadas);
		exportaciones.saveAndFlush(ExportacionCalificaciones.de(periodo.getId(), seccion.getId(), columnas.nombre(),
				enOrden.size(), huella, codigo, sha, sesiones.de(persona), ahora));
		long hoy = exportaciones.countByCreadoPorAndCreadoEnGreaterThanEqual(persona.getUsername(),
				ahora.toLocalDate().atStartOfDay());
		auditoria.registrar(AccionAuditoria.CALIFICACIONES_SIAGIE_EXPORTADAS, "exportacion_calificaciones", codigo, huella,
				sha, persona.nombreCompleto() + " descargó las calificaciones del " + periodo.getNombre() + " de "
						+ seccion.etiqueta() + " para el SIAGIE (perfil " + columnas.nombre() + ", " + enOrden.size()
						+ " alumno(s), de sus libretas vigentes; " + deAreas.size() + " área(s))."
						+ (hoy > maxSeccionesDia ? " Revisar: " + hoy + " secciones hoy por la misma persona." : ""));
		String nombre = (demo ? "DEMO-" : "") + (columnas.esProvisional() ? "PROVISIONAL-" : "") + "calificaciones-"
				+ seccion.getGrado().name().toLowerCase(Locale.ROOT) + "-" + seccion.getNombre().replaceAll("[^A-Za-z0-9]", "")
				+ "-p" + periodo.getNumero() + "-" + codigo + ".xlsx";
		return new Archivo(nombre, archivo, codigo, enOrden.size());
	}

	/** Una hoja: por alumno, sus columnas del perfil y, por competencia de esas áreas, el nivel y la conclusión (o «EXO»). */
	private static HojaReporte hoja(String nombre, String marca, PerfilCalificaciones columnas,
			List<RegeneracionLibreta.Regenerada> enOrden, List<Integer> areas) {
		ContenidoLibreta molde = enOrden.get(0).contenido();
		List<Celda> encabezado = new ArrayList<>();
		columnas.columnas().forEach(c -> encabezado.add(Celda.texto(c.encabezado())));
		for (int a : areas) {
			for (ContenidoLibreta.Competencia c : molde.areas().get(a).competencias()) {
				encabezado.add(Celda.texto(c.codigo() + " · " + c.nombre() + " (nivel)"));
				if (columnas.conclusion()) {
					encabezado.add(Celda.texto(c.codigo() + " (conclusión)"));
				}
			}
		}
		List<List<Celda>> filas = new ArrayList<>();
		filas.add(encabezado);
		int orden = 0;
		for (RegeneracionLibreta.Regenerada r : enOrden) {
			orden++;
			LibretaEmitida.Cabecera k = r.libreta().cabecera();
			List<Celda> fila = new ArrayList<>();
			for (PerfilCalificaciones.Columna c : columnas.columnas()) {
				fila.add(Celda.texto(c.traducir(switch (c.campo()) {
					case ORDEN -> String.valueOf(orden);
					case CODIGO -> k.alumnoCodigo();
					case TIPO_CODIGO -> k.tipoCodigo().name();
					case APELLIDOS_Y_NOMBRES -> k.alumnoNombre();
					case VACIO -> "";
				})));
			}
			for (int a : areas) {
				for (ContenidoLibreta.Competencia c : r.contenido().areas().get(a).competencias()) {
					ContenidoLibreta.Nota n = c.delPeriodo();
					fila.add(Celda.texto(n.exonerada() ? "EXO" : n.nivel() == null ? "" : n.nivel()));
					if (columnas.conclusion()) {
						fila.add(Celda.texto(n.exonerada() || n.conclusion() == null ? "" : n.conclusion()));
					}
				}
			}
			filas.add(fila);
		}
		return new HojaReporte(nombre, primeraFila(marca, encabezado.size()), filas);
	}

	/** A6: la hoja «Control», para comparar con el SIAGIE después de la carga. */
	private static HojaReporte control(String marca, List<RegeneracionLibreta.Regenerada> enOrden) {
		ContenidoLibreta molde = enOrden.get(0).contenido();
		List<List<Celda>> filas = new ArrayList<>();
		filas.add(List.of(Celda.texto("Área"), Celda.texto("Competencia"), Celda.texto("AD"), Celda.texto("A"),
				Celda.texto("B"), Celda.texto("C"), Celda.texto("Sin nota"), Celda.texto("EXO")));
		for (int a = 0; a < molde.areas().size(); a++) {
			ContenidoLibreta.Area area = molde.areas().get(a);
			for (int c = 0; c < area.competencias().size(); c++) {
				Map<String, Integer> conteo = new LinkedHashMap<>();
				for (String k : List.of("AD", "A", "B", "C", "-", "EXO")) {
					conteo.put(k, 0);
				}
				for (RegeneracionLibreta.Regenerada r : enOrden) {
					ContenidoLibreta.Nota n = r.contenido().areas().get(a).competencias().get(c).delPeriodo();
					conteo.merge(n.exonerada() ? "EXO" : n.nivel() == null ? "-" : n.nivel(), 1, Integer::sum);
				}
				List<Celda> fila = new ArrayList<>();
				fila.add(Celda.texto(area.nombre()));
				fila.add(Celda.texto(area.competencias().get(c).codigo() + " · " + area.competencias().get(c).nombre()));
				conteo.values().forEach(v -> fila.add(Celda.entero(v)));
				filas.add(fila);
			}
		}
		return new HojaReporte("Control", primeraFila(marca, 8), filas);
	}

	private static List<String> primeraFila(String marca, int columnas) {
		List<String> primera = new ArrayList<>();
		primera.add(marca);
		while (primera.size() < columnas) {
			primera.add("");
		}
		return primera;
	}

	/** A6: SHA-256 de los «id:huella» de las libretas usadas, en orden de id (la base solo la guarda). */
	public static String huellaLibretas(List<LibretaEmitida> usadas) {
		String cadena = usadas.stream().sorted(Comparator.comparing(LibretaEmitida::getId))
				.map(l -> l.getId() + ":" + l.getHuellaContenido()).collect(Collectors.joining("\n"));
		return sha256(cadena.getBytes(StandardCharsets.UTF_8));
	}

	private static String nombreHoja(String area) {
		String limpio = area.replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").strip();
		return limpio.length() > 31 ? limpio.substring(0, 31).strip() : limpio;
	}

	private static Comparator<String> espanol() {
		java.text.Collator collator = java.text.Collator.getInstance(Locale.forLanguageTag("es-PE"));
		collator.setStrength(java.text.Collator.SECONDARY);
		return collator::compare;
	}

	private String nuevoCodigo() {
		for (int intento = 0; intento < 5; intento++) {
			byte[] azar = new byte[4];
			AZAR.nextBytes(azar);
			String codigo = "CAL-" + HexFormat.of().withUpperCase().formatHex(azar);
			if (!exportaciones.existsByCodigo(codigo)) {
				return codigo;
			}
		}
		throw new IllegalStateException("No se pudo generar el código de la exportación");
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
