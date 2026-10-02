package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.ImportacionAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ImportacionAlumnosRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.RegistroAlumnos;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ValidadorArchivoXlsx;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.PrincipalConColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Importación de alumnos y apoderados desde Excel, en dos pasos.
 * <ol>
 *   <li>{@link #previsualizar}: valida el archivo, lee y planifica. NO guarda nada ni audita: la revisión vive en la
 *       sesión de quien subió el archivo.</li>
 *   <li>{@link #confirmar}: bloquea el año, vuelve a planificar contra la base y compara la huella del plan. Si algo
 *       cambió, pide revisar de nuevo. Aplica todo con {@link RegistroAlumnos} (las mismas reglas y auditoría del
 *       registro manual) en UNA transacción: todo o nada. Registra la importación (SHA-256 y conteos) y la audita.</li>
 * </ol>
 * Los logs nunca llevan DNI, nombres, teléfonos ni valores de celdas: solo la huella, el tamaño y las filas.
 */
@Service
@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
public class ServicioImportacionAlumnos {

	private static final Logger LOG = LoggerFactory.getLogger(ServicioImportacionAlumnos.class);

	private static final int HISTORIAL = 50;

	private final ValidadorArchivoXlsx validador;

	private final LectorImportacionAlumnos lector;

	private final PlanificadorImportacion planificador;

	private final PlantillaImportacionAlumnos plantilla;

	private final RegistroAlumnos registro;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final AlumnoRepository alumnos;

	private final ApoderadoRepository apoderados;

	private final ImportacionAlumnosRepository importaciones;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ServicioImportacionAlumnos(ValidadorArchivoXlsx validador, LectorImportacionAlumnos lector,
			PlanificadorImportacion planificador, PlantillaImportacionAlumnos plantilla, RegistroAlumnos registro,
			AnioEscolarRepository anios, SeccionRepository secciones, AlumnoRepository alumnos,
			ApoderadoRepository apoderados, ImportacionAlumnosRepository importaciones, AuditoriaService auditoria,
			Clock reloj) {
		this.validador = validador;
		this.lector = lector;
		this.planificador = planificador;
		this.plantilla = plantilla;
		this.registro = registro;
		this.anios = anios;
		this.secciones = secciones;
		this.alumnos = alumnos;
		this.apoderados = apoderados;
		this.importaciones = importaciones;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** Plantilla vacía (sin datos personales). */
	public byte[] plantilla() {
		return plantilla.generar();
	}

	/** Valida, lee y planifica. Solo lectura: no guarda nada ni audita. */
	@Transactional(readOnly = true)
	public VistaPreviaImportacion previsualizar(Long anioId, String nombreArchivo, byte[] contenido) {
		return previsualizar(anioId, nombreArchivo, contenido, contenido.length);
	}

	/**
	 * Como {@link #previsualizar(Long, String, byte[])} cuando del archivo solo se leyó el comienzo porque ya pasaba
	 * el máximo: {@code tamanoReal} es su tamaño completo, para el mensaje.
	 */
	@Transactional(readOnly = true)
	public VistaPreviaImportacion previsualizar(Long anioId, String nombreArchivo, byte[] contenido, long tamanoReal) {
		if (anioId == null) {
			throw new ReglaNegocioException("Elige el año escolar al que pertenecen los alumnos.");
		}
		AnioEscolar anio = anios.findById(anioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado"));
		if (anio.cerrado()) {
			throw new ReglaNegocioException("El año " + anio.getAnio() + " está cerrado: ya no recibe alumnos.");
		}
		String nombre = nombreSeguro(nombreArchivo);
		validador.exigirTamano(tamanoReal);
		validador.validar(nombre, contenido);
		String sha256 = PlanificadorImportacion.sha256(contenido);
		LecturaImportacion lectura = lector.leer(contenido, anio.getAnio(), LocalDate.now(reloj));
		PlanImportacion plan = planificador.planificar(anio, lectura.filas());
		LocalDateTime importadoAntes = importaciones.findFirstByArchivoSha256OrderByCreadoEnDesc(sha256)
				.map(ImportacionAlumnos::getCreadoEn).orElse(null);
		PrincipalConColegio actor = actor();
		LOG.info("Vista previa de importación: sha256={}, bytes={}, filas={}, con errores={}", sha256, contenido.length,
				plan.resumen().filas(), plan.resumen().filasConErrores());
		return new VistaPreviaImportacion(UUID.randomUUID(), actor.colegioId(), actor.usuarioId(), anio.getId(),
				anio.getAnio(), nombre, sha256, contenido.length, plan, lectura.avisos(), importadoAntes);
	}

	/**
	 * Confirma la revisión. Todo o nada.
	 *
	 * @param token el token del formulario de confirmación: debe ser el de la revisión de esta sesión
	 */
	@Transactional
	public ResultadoImportacion confirmar(VistaPreviaImportacion previa, UUID token) {
		if (previa == null) {
			throw new ReglaNegocioException("No hay una revisión pendiente. Sube el archivo otra vez.");
		}
		PrincipalConColegio actor = actor();
		if (!previa.colegioId().equals(actor.colegioId()) || !previa.usuarioId().equals(actor.usuarioId())
				|| token == null || !token.equals(previa.token())) {
			throw new ReglaNegocioException("Esta revisión no es válida: es de otra sesión o ya se usó. Sube el "
					+ "archivo otra vez.");
		}
		if (previa.resumen().conErrores()) {
			throw new ReglaNegocioException("Corrige los errores en el archivo y súbelo otra vez: no se importa nada "
					+ "mientras haya errores.");
		}
		if (previa.resumen().sinCambios()) {
			throw new ReglaNegocioException("No hay nada que importar: todo ya está registrado igual.");
		}
		AnioEscolar anio = anios.bloquear(previa.anioId())
				.orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado"));
		PlanImportacion plan = planificador.planificar(anio, previa.filas());
		if (!plan.huella().equals(previa.plan().huella())) {
			throw new ReglaNegocioException("Los datos cambiaron desde la revisión (alguien registró o corrigió "
					+ "alumnos o secciones). Sube el archivo otra vez para revisarlo antes de importar.");
		}
		aplicar(plan, anio, previa.archivoNombre());
		ImportacionAlumnos importacion = importaciones.save(ImportacionAlumnos.registrar(anio.getId(),
				previa.archivoNombre(), previa.sha256(), previa.bytes(), plan.resumen().conteos()));
		auditoria.registrar(AccionAuditoria.IMPORTACION_CONFIRMADA, "importacion_alumnos",
				importacion.getId().toString(), null, plan.resumen().comoTexto(), "Archivo «" + previa.archivoNombre()
						+ "», " + previa.bytes() + " bytes, SHA-256 " + previa.sha256() + ", año " + anio.getAnio() + ".");
		LOG.info("Importación confirmada: id={}, sha256={}, filas={}", importacion.getId(), previa.sha256(),
				plan.resumen().filas());
		return new ResultadoImportacion(importacion.getId(), anio.getAnio(), previa.archivoNombre(), plan.resumen());
	}

	/** Las últimas importaciones del colegio, de la más reciente a la más antigua. */
	@Transactional(readOnly = true)
	public List<ImportacionResumen> historial() {
		List<ImportacionAlumnos> ultimas = importaciones.findAllByOrderByCreadoEnDesc(PageRequest.of(0, HISTORIAL));
		Map<Long, Integer> numeroDeAnio = anios.findAllById(ultimas.stream().map(ImportacionAlumnos::getAnioEscolarId)
				.distinct().toList()).stream().collect(Collectors.toMap(AnioEscolar::getId, AnioEscolar::getAnio));
		return ultimas.stream()
				.map(i -> new ImportacionResumen(i.getId(), i.getCreadoEn(), i.getCreadoPor(),
						numeroDeAnio.get(i.getAnioEscolarId()), i.getArchivoNombre(), i.getArchivoSha256().substring(0, 12),
						i.conteos()))
				.toList();
	}

	private void aplicar(PlanImportacion plan, AnioEscolar anio, String archivo) {
		LocalDate fechaMatricula = anio.fechaMatriculaPorDefecto(LocalDate.now(reloj));
		Map<Long, Seccion> seccionesPorId = secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(anio.getId()).stream()
				.collect(Collectors.toMap(Seccion::getId, Function.identity()));
		Map<String, Apoderado> apoderadosNuevos = new HashMap<>();
		for (FilaPlan p : plan.filas()) {
			if (p.clasificacion() == Clasificacion.SIN_CAMBIOS) {
				continue;
			}
			FilaImportacion f = p.fila();
			String motivo = "Importación desde Excel «" + archivo + "», fila " + f.fila();
			Apoderado apoderado = p.apoderadoId() != null
					? apoderados.findById(p.apoderadoId()).orElseThrow()
					: apoderadosNuevos.computeIfAbsent(PlanificadorImportacion.clave(f.apoderado().documento()), k -> {
						Familia familia = registro.crearFamilia(
								Familia.nombrePorDefecto(f.alumno().apellidoPaterno(), f.alumno().apellidoMaterno()));
						return registro.registrarApoderado(familia, f.apoderado());
					});
			if (p.actualizarApoderado()) {
				registro.actualizarApoderado(apoderado, f.apoderado(), motivo);
			}
			Alumno alumno;
			if (p.alumnoId() == null) {
				alumno = registro.registrarAlumno(f.alumno(), apoderado);
			}
			else {
				alumno = alumnos.findById(p.alumnoId()).orElseThrow();
				if (p.actualizarAlumno()) {
					registro.actualizarAlumno(alumno, f.alumno(), motivo);
				}
				if (p.cambiarResponsable()) {
					registro.cambiarResponsable(alumno, apoderado, motivo);
				}
			}
			if (p.matricular()) {
				registro.matricular(alumno, seccionesPorId.get(p.seccionId()), fechaMatricula);
			}
		}
	}

	/** Solo el nombre (sin carpetas), limpio y de hasta 150 caracteres. */
	static String nombreSeguro(String nombre) {
		String base = nombre == null ? "" : nombre.replace('\\', '/');
		base = base.substring(base.lastIndexOf('/') + 1);
		String limpio = Normalizador.limpiar(base);
		if (limpio == null) {
			return "archivo.xlsx";
		}
		return limpio.length() <= 150 ? limpio : limpio.substring(limpio.length() - 150);
	}

	private static PrincipalConColegio actor() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof PrincipalConColegio principal
				&& principal.usuarioId() != null && principal.colegioId() != null) {
			return principal;
		}
		throw new AccessDeniedException("Se requiere un usuario en sesión");
	}
}
