package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.AnioEscolarDetalle;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.AnioEscolarVista;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.GradoConSecciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.NivelConGrados;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.SeccionOpcion;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.SeccionVista;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Estructura académica: años escolares y secciones (los niveles y grados son el catálogo nacional, {@link Grado}).
 * <ul>
 *   <li>Lectura: Promotoría, Dirección y Administración. Secciones: Dirección y Administración.</li>
 *   <li>Sprint 11, tanda 1 (sección 3.7, AP-9): el año escolar lo crean y lo cambian Promotoría o Dirección con la firma
 *       de su sesión ({@code anio_escolar:{anio}:CREADO} y {@code anio_escolar:{id}:{version}}); Administración ya no.
 *       En MySQL lo exigen trg_anio_escolar_nace y trg_anio_escolar_cambio (nunca el operador ni un proceso).</li>
 *   <li>Solo un año EN_CURSO por colegio (lo comprueba el servicio y lo garantiza la base).</li>
 *   <li>Nada se borra: una sección se desactiva con motivo. Todo cambio queda en la bitácora.</li>
 * </ul>
 * Las consultas las filtra {@code @TenantId}: un año o una sección de otro colegio "no existe" (404).
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioEstructura {

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final ConteoMatriculas conteo;

	private final AuditoriaService auditoria;

	private final FirmaSesion firma;

	private final ColegioRepository colegios;

	public ServicioEstructura(AnioEscolarRepository anios, SeccionRepository secciones, ConteoMatriculas conteo,
			AuditoriaService auditoria, FirmaSesion firma, ColegioRepository colegios) {
		this.anios = anios;
		this.secciones = secciones;
		this.conteo = conteo;
		this.auditoria = auditoria;
		this.firma = firma;
		this.colegios = colegios;
	}

	@Transactional(readOnly = true)
	public List<AnioEscolarVista> listarAnios() {
		return anios.findAllByOrderByAnioDesc().stream().map(this::vista).toList();
	}

	@Transactional(readOnly = true)
	public Optional<AnioEscolarVista> anioEnCurso() {
		return anios.findByEstado(EstadoAnioEscolar.EN_CURSO).map(this::vista);
	}

	@Transactional(readOnly = true)
	public AnioEscolarDetalle obtenerAnio(Long id) {
		AnioEscolar anio = buscarAnio(id);
		Map<Long, Long> matriculados = conteo.porSeccion(anio.getId());
		List<Seccion> delAnio = secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(anio.getId());
		Map<Grado, List<SeccionVista>> porGrado = new EnumMap<>(Grado.class);
		delAnio.stream()
				.sorted(Comparator.comparing(Seccion::getGrado).thenComparing(Seccion::getNombre))
				.forEach(s -> porGrado.computeIfAbsent(s.getGrado(), g -> new ArrayList<>())
						.add(new SeccionVista(s.getId(), s.getGrado(), s.etiqueta(), s.getNombre(), s.isActiva(),
								matriculados.getOrDefault(s.getId(), 0L))));
		List<NivelConGrados> niveles = new ArrayList<>();
		for (Nivel nivel : Nivel.values()) {
			List<GradoConSecciones> grados = porGrado.entrySet().stream()
					.filter(e -> e.getKey().nivel() == nivel)
					.map(e -> new GradoConSecciones(e.getKey(), e.getKey().etiqueta(), e.getValue()))
					.toList();
			if (!grados.isEmpty()) {
				niveles.add(new NivelConGrados(nivel, nivel.etiqueta(), grados));
			}
		}
		return new AnioEscolarDetalle(vista(anio, delAnio.size(), matriculados), niveles);
	}

	/** Secciones activas de los años que no están cerrados, para elegir dónde matricular. */
	@Transactional(readOnly = true)
	public List<SeccionOpcion> seccionesParaMatricular() {
		List<Long> abiertos = anios.findAllByOrderByAnioDesc().stream().filter(a -> !a.cerrado())
				.map(AnioEscolar::getId).toList();
		if (abiertos.isEmpty()) {
			return List.of();
		}
		return secciones.activasDe(abiertos).stream()
				.sorted(Comparator.comparing((Seccion s) -> -s.getAnioEscolar().getAnio())
						.thenComparing(Seccion::getGrado).thenComparing(Seccion::getNombre))
				.map(ServicioEstructura::opcion)
				.toList();
	}

	/** Todas las secciones de todos los años (también las desactivadas), para filtrar listas. */
	@Transactional(readOnly = true)
	public List<SeccionOpcion> listarSecciones() {
		return secciones.todasConAnio().stream()
				.sorted(Comparator.comparing((Seccion s) -> -s.getAnioEscolar().getAnio())
						.thenComparing(Seccion::getGrado).thenComparing(Seccion::getNombre))
				.map(ServicioEstructura::opcion)
				.toList();
	}

	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	@Transactional
	public Long crearAnio(CrearAnioEscolarRequest solicitud) {
		if (solicitud.anio() == null) {
			throw new ReglaNegocioException("Escribe el año.");
		}
		exigirColegioQueOpera();
		int numero = solicitud.anio();
		if (anios.existsByAnio(numero)) {
			throw anioRepetido(numero);
		}
		if (solicitud.esEnCurso()) {
			anios.findByEstado(EstadoAnioEscolar.EN_CURSO).ifPresent(a -> {
				throw yaHayUnoEnCurso(a.getAnio());
			});
		}
		AnioEscolar anio = AnioEscolar.nuevo(numero, solicitud.esEnCurso(), solicitud.inicioClases(),
				solicitud.finClases());
		// Sprint 11, tanda 1: con la firma de la sesión de quien lo crea (en MySQL, trg_anio_escolar_nace la exige).
		firma.firmar(ClaveFirma.anioEscolarCreado(numero));
		try {
			anios.saveAndFlush(anio);
		}
		catch (DataIntegrityViolationException e) {
			// Otra persona creó el mismo año (o puso otro en curso) al mismo tiempo: lo detiene la base.
			throw new ReglaNegocioException("No se pudo crear el año " + numero + ": ya existe o ya hay otro año en "
					+ "curso. Recarga la página y revisa la lista.");
		}
		auditoria.registrar(AccionAuditoria.ANIO_ESCOLAR_CREADO, "anio_escolar", anio.getId().toString(), null,
				"año " + numero + "; " + anio.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT) + "; clases del "
						+ anio.rangoClases(), "Con la firma de su sesión.");
		return anio.getId();
	}

	/**
	 * Sprint 11, tanda 1 (AP-9): cambia las fechas de clases de un año (no cerrado), con la firma de quien lo cambia sobre
	 * la versión que tenía ({@code anio_escolar:{id}:{version}}). Las cuotas ya generadas no cambian: salen del plan de
	 * pensiones, que aprueba otra persona.
	 */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	@Transactional
	public void cambiarFechas(Long id, java.time.LocalDate inicio, java.time.LocalDate fin, String por) {
		exigirColegioQueOpera();
		AnioEscolar anio = anios.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado"));
		String antes = anio.rangoClases();
		long version = anio.getVersion();
		anio.cambiarFechas(inicio, fin, por);
		firma.firmar(ClaveFirma.anioEscolarCambiado(anio.getId(), version));
		anios.saveAndFlush(anio);
		auditoria.registrar(AccionAuditoria.ANIO_ESCOLAR_CAMBIADO, "anio_escolar", anio.getId().toString(),
				"clases del " + antes, "clases del " + anio.rangoClases(), "Año " + anio.getAnio()
						+ ", con la firma de su sesión.");
	}

	/** Sprint 11, tanda 1: un colegio suspendido o dado de baja no crea ni cambia años (lo exige también la base). */
	private void exigirColegioQueOpera() {
		Long colegioId = pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio.actual();
		boolean opera = colegioId != null && colegios.findById(colegioId).map(c -> c.opera()).orElse(true);
		if (!opera) {
			throw new ReglaNegocioException("El colegio no está operando: no se crean ni cambian años escolares.");
		}
	}

	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional
	public Long crearSeccion(Long anioId, CrearSeccionRequest solicitud) {
		AnioEscolar anio = buscarAnio(anioId);
		if (anio.cerrado()) {
			throw new ReglaNegocioException("El año " + anio.getAnio() + " está cerrado: ya no se le agregan secciones.");
		}
		Seccion seccion = Seccion.nueva(anio, solicitud.grado(), solicitud.nombre());
		// «a» y «A», o «Señor» y «Senor», son la misma sección (MySQL tampoco distingue mayúsculas ni tildes).
		String clave = Normalizador.paraBusqueda(seccion.getNombre());
		boolean repetida = secciones.findByAnioEscolarIdAndGrado(anio.getId(), seccion.getGrado()).stream()
				.anyMatch(s -> Normalizador.paraBusqueda(s.getNombre()).equals(clave));
		if (repetida) {
			throw seccionRepetida(seccion, anio);
		}
		try {
			secciones.saveAndFlush(seccion);
		}
		catch (DataIntegrityViolationException e) {
			throw seccionRepetida(seccion, anio);
		}
		auditoria.registrar(AccionAuditoria.SECCION_CREADA, "seccion", seccion.getId().toString(), null,
				seccion.etiqueta() + " " + anio.getAnio(), null);
		return seccion.getId();
	}

	/**
	 * Desactiva la sección (no se borra): deja de ofrecerse para matricular. Sus matrículas se conservan.
	 *
	 * @return id del año de la sección
	 */
	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional
	public Long desactivarSeccion(Long seccionId, String motivo) {
		Seccion seccion = secciones.findById(seccionId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Sección no encontrada"));
		String texto = Motivo.exigir(motivo);
		seccion.desactivar();
		AnioEscolar anio = seccion.getAnioEscolar();
		auditoria.registrar(AccionAuditoria.SECCION_DESACTIVADA, "seccion", seccionId.toString(), "activa", "inactiva",
				"Sección " + seccion.etiqueta() + " " + anio.getAnio() + ". Motivo: " + texto);
		return anio.getId();
	}

	private static SeccionOpcion opcion(Seccion s) {
		return new SeccionOpcion(s.getId(), s.getAnioEscolar().getId(), s.getAnioEscolar().getAnio(), s.getGrado(),
				s.etiqueta() + (s.isActiva() ? "" : " (desactivada)"));
	}

	private AnioEscolar buscarAnio(Long id) {
		return anios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado"));
	}

	private AnioEscolarVista vista(AnioEscolar anio) {
		Map<Long, Long> matriculados = conteo.porSeccion(anio.getId());
		return vista(anio, secciones.countByAnioEscolarId(anio.getId()), matriculados);
	}

	private static AnioEscolarVista vista(AnioEscolar anio, long totalSecciones, Map<Long, Long> matriculados) {
		long total = matriculados.values().stream().mapToLong(Long::longValue).sum();
		return new AnioEscolarVista(anio.getId(), anio.getAnio(), anio.getEstado(), anio.getEstado().etiqueta(),
				anio.getInicioClases(), anio.getFinClases(), totalSecciones, total);
	}

	private static ReglaNegocioException anioRepetido(int anio) {
		return new ReglaNegocioException("El año " + anio + " ya existe. Ábrelo en la lista para agregarle secciones.");
	}

	private static ReglaNegocioException yaHayUnoEnCurso(int anio) {
		return new ReglaNegocioException("Ya hay un año en curso (" + anio + "). Crea este año como planificado: "
				+ "solo puede haber un año en curso a la vez.");
	}

	private static ReglaNegocioException seccionRepetida(Seccion seccion, AnioEscolar anio) {
		return new ReglaNegocioException("Ya existe la sección " + seccion.etiqueta() + " en " + anio.getAnio() + ".");
	}
}
