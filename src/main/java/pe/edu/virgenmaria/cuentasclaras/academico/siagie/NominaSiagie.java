package pe.edu.virgenmaria.cuentasclaras.academico.siagie;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.model.CodigoModular;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ExportacionNomina;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.CodigoModularRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.ExportacionNominaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.service.SesionDeQuienEscribe;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
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
import pe.edu.virgenmaria.cuentasclaras.comun.excel.Celda;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.EscritorXlsxSeguro;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.HojaReporte;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 9, tanda 3 (sección 3.11, decisiones 164 a 166, E34, B10, B14): los datos para registrar la matrícula en el SIAGIE.
 * Cuotaria no produce la nómina OFICIAL (la genera el SIAGIE): produce un {@code .xlsx} por sección en el perfil de
 * columnas del jar, que la secretaria copia en la plantilla que descargó del SIAGIE.
 * <ul>
 *   <li>Solo Dirección (responsable del SIAGIE según las UGEL), con su sesión (trg_exportacion_nomina_nace, B14).</li>
 *   <li>Cada descarga queda en {@code exportacion_nomina} (sin datos de alumnos), en la bitácora
 *       ({@code NOMINA_SIAGIE_EXPORTADA}, con el código y el SHA-256) y en «Quién vio datos personales».</li>
 *   <li>El archivo imprime el código, el usuario y la fecha junto a «PROVISIONAL» (B14); todo como texto (el DNI conserva
 *       sus ceros, nada empieza con «=»: {@link EscritorXlsxSeguro}).</li>
 *   <li>Más de {@value #MAX_SECCIONES_DIA} secciones de una misma persona en un día: el evento va resaltado (B10, lo ve
 *       Promotoría en «Para revisar»).</li>
 * </ul>
 */
@Service
@PreAuthorize("hasRole('DIRECTOR')")
public class NominaSiagie {

	static final int MAX_SECCIONES_DIA = 10;

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final DateTimeFormatter MOMENTO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private static final SecureRandom AZAR = new SecureRandom();

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final MatriculaRepository matriculas;

	private final CodigoModularRepository codigos;

	private final ExportacionNominaRepository exportaciones;

	private final EscritorXlsxSeguro escritor;

	private final AuditoriaService auditoria;

	private final SesionDeQuienEscribe sesiones;

	private final Clock reloj;

	private final String perfil;

	private final boolean demo;

	public NominaSiagie(AnioEscolarRepository anios, SeccionRepository secciones, MatriculaRepository matriculas,
			CodigoModularRepository codigos, ExportacionNominaRepository exportaciones, EscritorXlsxSeguro escritor,
			AuditoriaService auditoria, SesionDeQuienEscribe sesiones, Clock reloj,
			@Value("${cuentasclaras.siagie.perfil-nomina:SIAGIE_MATRICULA_PROVISIONAL}") String perfil,
			Environment entorno) {
		this.anios = anios;
		this.secciones = secciones;
		this.matriculas = matriculas;
		this.codigos = codigos;
		this.exportaciones = exportaciones;
		this.escritor = escritor;
		this.auditoria = auditoria;
		this.sesiones = sesiones;
		this.reloj = reloj;
		this.perfil = perfil;
		this.demo = Arrays.asList(entorno.getActiveProfiles()).contains("demo");
	}

	/** «Datos que faltan para el SIAGIE» del año en curso, por sección. */
	@Transactional(readOnly = true)
	public DatosFaltantes faltantes() {
		// Sin año en curso (un colegio recién creado) la pantalla abre vacía, con su mensaje, en vez de un error.
		var enCurso = anios.findByEstado(EstadoAnioEscolar.EN_CURSO);
		if (enCurso.isEmpty()) {
			return new DatosFaltantes(List.of(), List.of());
		}
		AnioEscolar anio = enCurso.get();
		Map<Long, List<Matricula>> porSeccion = matriculas.findByAnioEscolarIdAndEstado(anio.getId(), EstadoMatricula.ACTIVA)
				.stream().collect(Collectors.groupingBy(m -> m.getSeccion().getId()));
		List<DatosFaltantes.Seccion> resultado = new ArrayList<>();
		for (Seccion s : secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(anio.getId())) {
			List<Matricula> lista = porSeccion.getOrDefault(s.getId(), List.of());
			List<String> sinSexo = lista.stream().map(Matricula::getAlumno).filter(a -> a.getSexo() == null)
					.map(NominaSiagie::nombre).toList();
			List<String> sinDocumento = lista.stream().map(Matricula::getAlumno).filter(NominaSiagie::sinIdentificador)
					.map(NominaSiagie::nombre).toList();
			resultado.add(new DatosFaltantes.Seccion(s.getId(), s.etiqueta(), lista.size(), sinSexo, sinDocumento));
		}
		Map<Nivel, CodigoModular> registrados = codigos.findAllByOrderByNivelAsc().stream()
				.collect(Collectors.toMap(CodigoModular::getNivel, Function.identity()));
		List<String> niveles = porSeccion.keySet().stream().map(id -> secciones.findById(id).map(Seccion::nivel).orElse(null))
				.filter(n -> n != null && !registrados.containsKey(n)).distinct().sorted().map(Nivel::name).toList();
		return new DatosFaltantes(resultado, niveles);
	}

	@Transactional(readOnly = true)
	public List<CodigoModularVista> codigosModulares() {
		Map<Nivel, CodigoModular> registrados = codigos.findAllByOrderByNivelAsc().stream()
				.collect(Collectors.toMap(CodigoModular::getNivel, Function.identity()));
		return Arrays.stream(Nivel.values()).map(n -> Optional.ofNullable(registrados.get(n))
				.map(c -> new CodigoModularVista(n.name(), c.getCodigo(), c.getAnexo(), c.getActualizadoPor()))
				.orElse(new CodigoModularVista(n.name(), null, null, null))).toList();
	}

	/** Registra o corrige el código modular de un nivel (resaltado en la bitácora, con el antes y el después). */
	@Transactional
	public void registrarCodigoModular(CodigoModularRequest pedido) {
		UsuarioAutenticado persona = persona();
		Optional<CodigoModular> actual = codigos.findByNivel(pedido.nivel());
		String antes = actual.map(c -> c.getCodigo() + "-" + c.getAnexo()).orElse(null);
		CodigoModular guardado;
		if (actual.isPresent()) {
			actual.get().cambiar(pedido.codigo(), pedido.anexo(), persona.getUsername());
			guardado = codigos.saveAndFlush(actual.get());
		}
		else {
			guardado = codigos.saveAndFlush(CodigoModular.nuevo(pedido.nivel(), pedido.codigo(), pedido.anexo(),
					persona.getUsername()));
		}
		String despues = guardado.getCodigo() + "-" + guardado.getAnexo();
		if (!despues.equals(antes)) {
			auditoria.registrar(AccionAuditoria.CODIGO_MODULAR_CAMBIADO, "codigo_modular", guardado.getId().toString(),
					antes, despues, persona.nombreCompleto() + " registró el código modular de " + pedido.nivel().name()
							+ ".");
		}
	}

	/** La nómina de una sección: el archivo (no se guarda) y su registro (exportación, bitácora y accesos). */
	@Transactional
	public ArchivoNomina exportar(long seccionId) {
		UsuarioAutenticado persona = persona();
		AnioEscolar anio = anioEnCurso();
		Seccion seccion = secciones.findById(seccionId).filter(s -> s.getAnioEscolar().getId().equals(anio.getId()))
				.orElseThrow(() -> new RecursoNoEncontradoException("Sección no encontrada"));
		PerfilNomina columnas = PerfilNomina.leer(perfil);
		List<Matricula> lista = matriculas.deLaSeccionEnOrden(seccion.getId(), List.of(EstadoMatricula.ACTIVA)).stream()
				.sorted(Comparator.comparing((Matricula m) -> m.getAlumno().getApellidoPaterno())
						.thenComparing(m -> m.getAlumno().getNombres()))
				.toList();
		Map<Long, Matricula> anteriores = anteriores(anio, lista);
		Optional<CodigoModular> codigo = codigos.findByNivel(seccion.nivel());
		String codigoExportacion = nuevoCodigo();
		LocalDateTime ahora = LocalDateTime.now(reloj);
		String marca = (demo ? "DEMO · DATOS FICTICIOS · " : "") + (columnas.esProvisional() ? "PROVISIONAL" : columnas.nombre())
				+ " · copie estas columnas en la plantilla del SIAGIE · código " + codigoExportacion + " · "
				+ persona.getUsername() + " · " + ahora.format(MOMENTO);
		List<String> primeraFila = new ArrayList<>();
		primeraFila.add(marca);
		while (primeraFila.size() < columnas.columnas().size()) {
			primeraFila.add("");
		}
		List<List<Celda>> filas = new ArrayList<>();
		filas.add(columnas.columnas().stream().map(c -> (Celda) Celda.texto(c.encabezado())).toList());
		int orden = 0;
		int faltantes = 0;
		for (Matricula m : lista) {
			orden++;
			Alumno a = m.getAlumno();
			if (a.getSexo() == null || sinIdentificador(a)) {
				faltantes++;
			}
			List<Celda> fila = new ArrayList<>();
			for (PerfilNomina.Columna c : columnas.columnas()) {
				fila.add(Celda.texto(c.traducir(valor(c.campo(), orden, m, codigo, anteriores.get(a.getId())))));
			}
			filas.add(fila);
		}
		String hoja = columnas.hoja().replaceAll("[^\\p{L}\\p{N} ]", " ").strip();
		byte[] archivo = escritor.escribir(List.of(new HojaReporte(hoja.length() > 31 ? hoja.substring(0, 31) : hoja,
				primeraFila, filas)));
		String sha = sha256(archivo);
		exportaciones.saveAndFlush(ExportacionNomina.de(anio.getId(), seccion.getId(), columnas.nombre(), lista.size(),
				faltantes, codigoExportacion, sha, sesiones.de(persona)));
		long hoy = exportaciones.countByCreadoPorAndCreadoEnGreaterThanEqual(persona.getUsername(),
				ahora.toLocalDate().atStartOfDay());
		auditoria.registrar(AccionAuditoria.NOMINA_SIAGIE_EXPORTADA, "exportacion_nomina", codigoExportacion, null, sha,
				persona.nombreCompleto() + " descargó la nómina de " + seccion.etiqueta() + " (perfil " + columnas.nombre()
						+ ", " + lista.size() + " alumno(s), " + faltantes + " con datos que faltan)."
						+ (hoy > MAX_SECCIONES_DIA ? " Revisar: " + hoy + " secciones hoy por la misma persona." : ""));
		String nombre = (demo ? "DEMO-" : "") + (columnas.esProvisional() ? "PROVISIONAL-" : "") + "nomina-"
				+ seccion.getGrado().name().toLowerCase(Locale.ROOT) + "-" + seccion.getNombre().replaceAll("[^A-Za-z0-9]", "")
				+ "-" + codigoExportacion + ".xlsx";
		return new ArchivoNomina(nombre, archivo, codigoExportacion, lista.size());
	}

	private String valor(CampoNomina campo, int orden, Matricula m, Optional<CodigoModular> codigo, Matricula anterior) {
		Alumno a = m.getAlumno();
		return switch (campo) {
			case ORDEN -> String.valueOf(orden);
			case TIPO_DOCUMENTO -> a.getDocumento().tipo().name();
			case NUMERO_DOCUMENTO -> a.getDocumento().numero();
			case CODIGO_ESTUDIANTE -> a.getCodigoEstudiante();
			case APELLIDO_PATERNO -> a.getApellidoPaterno();
			case APELLIDO_MATERNO -> a.getApellidoMaterno();
			case NOMBRES -> a.getNombres();
			case SEXO -> a.getSexo();
			case FECHA_NACIMIENTO -> a.getFechaNacimiento() == null ? null : a.getFechaNacimiento().format(FECHA);
			case NIVEL -> m.getSeccion().nivel().name();
			case GRADO -> String.valueOf(m.getSeccion().getGrado().numero());
			case SECCION -> m.getSeccion().getNombre();
			case CODIGO_MODULAR -> codigo.map(CodigoModular::getCodigo).orElse(null);
			case ANEXO -> codigo.map(CodigoModular::getAnexo).orElse(null);
			case SITUACION -> situacion(m, anterior).name();
			case FECHA_MATRICULA -> m.getFechaMatricula() == null ? null : m.getFechaMatricula().format(FECHA);
			case VACIO -> "";
		};
	}

	/** La situación CALCULADA (sección 3.11), con la matrícula del año anterior en el colegio. */
	static SituacionMatricula situacion(Matricula actual, Matricula anterior) {
		if (anterior == null) {
			return SituacionMatricula.INGRESANTE;
		}
		if (anterior.getSeccion().getGrado() == actual.getSeccion().getGrado()) {
			return SituacionMatricula.REPITENTE;
		}
		return anterior.getSeccion().getGrado().siguiente().filter(g -> g == actual.getSeccion().getGrado()).isPresent()
				? SituacionMatricula.PROMOVIDO : SituacionMatricula.REENTRANTE;
	}

	private Map<Long, Matricula> anteriores(AnioEscolar anio, List<Matricula> lista) {
		Optional<AnioEscolar> previo = anios.findByAnio(anio.getAnio() - 1);
		if (previo.isEmpty() || lista.isEmpty()) {
			return Map.of();
		}
		return matriculas.delAnioParaAlumnos(previo.get().getId(), lista.stream().map(m -> m.getAlumno().getId()).toList())
				.stream().collect(Collectors.toMap(m -> m.getAlumno().getId(), Function.identity(), (x, y) -> x));
	}

	private static boolean sinIdentificador(Alumno a) {
		return a.getDocumento().tipo() != TipoDocumento.DNI && a.getCodigoEstudiante() == null;
	}

	private static String nombre(Alumno a) {
		return a.getApellidoPaterno() + ", " + a.getNombres();
	}

	private AnioEscolar anioEnCurso() {
		return anios.findByEstado(EstadoAnioEscolar.EN_CURSO)
				.orElseThrow(() -> new ReglaNegocioException("No hay un año escolar en curso."));
	}

	private String nuevoCodigo() {
		for (int intento = 0; intento < 5; intento++) {
			byte[] azar = new byte[4];
			AZAR.nextBytes(azar);
			String codigo = "NOM-" + HexFormat.of().withUpperCase().formatHex(azar);
			if (!exportaciones.existsByCodigo(codigo)) {
				return codigo;
			}
		}
		throw new IllegalStateException("No se pudo generar el código de la nómina");
	}

	private static UsuarioAutenticado persona() {
		if (SecurityContextHolder.getContext().getAuthentication() != null
				&& SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof UsuarioAutenticado u) {
			return u;
		}
		throw new org.springframework.security.access.AccessDeniedException("Se requiere una persona en sesión");
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
