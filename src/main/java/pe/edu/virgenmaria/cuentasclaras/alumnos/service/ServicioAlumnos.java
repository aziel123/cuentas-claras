package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ActualizarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.AlumnoResumen;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoOpcion;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoVista;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.BusquedaAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CabeceraAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CambiarResponsableRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.EdicionAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.FichaAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.HermanoVista;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.MatriculaVista;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.OpcionesRegistro;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RegistrarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RegistroResultado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RetirarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatoInvalidoException;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosApoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.ReglasDatosPersonales;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.SeccionOpcion;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Alumnos: búsqueda, ficha, registro (con su apoderado y familia), corrección, cambio de responsable de pago y
 * retiro.
 * <ul>
 *   <li>Lectura: Promotoría, Dirección y Administración. Escritura: Dirección y Administración.</li>
 *   <li>Las reglas de los datos personales son las de {@link ReglasDatosPersonales}; la escritura y la auditoría,
 *       las de {@link RegistroAlumnos}.</li>
 * </ul>
 * Las consultas las filtra {@code @TenantId}: un alumno de otro colegio "no existe" (404).
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioAlumnos {

	public static final int TAMANO_PAGINA = 25;

	private static final int MAX_PALABRAS = 3;

	private final AlumnoRepository alumnos;

	private final ApoderadoRepository apoderados;

	private final FamiliaRepository familias;

	private final MatriculaRepository matriculas;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final ServicioEstructura estructura;

	private final RegistroAlumnos registro;

	private final AuditoriaService auditoria;

	private final VistasAlumnos vistas;

	private final Clock reloj;

	ServicioAlumnos(AlumnoRepository alumnos, ApoderadoRepository apoderados, FamiliaRepository familias,
			MatriculaRepository matriculas, AnioEscolarRepository anios, SeccionRepository secciones,
			ServicioEstructura estructura, RegistroAlumnos registro, AuditoriaService auditoria, VistasAlumnos vistas,
			Clock reloj) {
		this.alumnos = alumnos;
		this.apoderados = apoderados;
		this.familias = familias;
		this.matriculas = matriculas;
		this.anios = anios;
		this.secciones = secciones;
		this.estructura = estructura;
		this.registro = registro;
		this.auditoria = auditoria;
		this.vistas = vistas;
		this.reloj = reloj;
	}

	/**
	 * Busca por nombre (hasta tres palabras, en cualquier orden y sin importar tildes) o por el inicio del número de
	 * documento, con filtros de año, sección y estado. De 25 en 25, por apellidos.
	 */
	@Transactional(readOnly = true)
	public Page<AlumnoResumen> buscar(BusquedaAlumnos busqueda, int pagina) {
		String[] palabras = { null, null, null };
		String documento = null;
		String texto = Normalizador.limpiar(busqueda.texto());
		if (texto != null) {
			String compacto = texto.replace(" ", "");
			if (compacto.matches("[0-9A-Za-z]+") && compacto.chars().anyMatch(Character::isDigit)) {
				documento = Normalizador.escaparLike(compacto.toUpperCase(java.util.Locale.ROOT)) + "%";
			}
			else {
				String[] partes = Normalizador.paraBusqueda(texto).split(" ");
				for (int i = 0; i < Math.min(partes.length, MAX_PALABRAS); i++) {
					palabras[i] = "%" + Normalizador.escaparLike(partes[i]) + "%";
				}
			}
		}
		Page<Alumno> encontrados = alumnos.buscar(palabras[0], palabras[1], palabras[2], documento, busqueda.estado(),
				busqueda.anioId(), busqueda.seccionId(), PageRequest.of(Math.max(pagina, 0), TAMANO_PAGINA));

		AnioEscolar anioMostrado = busqueda.anioId() != null ? anios.findById(busqueda.anioId()).orElse(null)
				: anios.findByEstado(EstadoAnioEscolar.EN_CURSO).orElse(null);
		Map<Long, Matricula> delAnio = anioMostrado == null || encontrados.isEmpty() ? Map.of()
				: matriculas.delAnioParaAlumnos(anioMostrado.getId(),
								encontrados.getContent().stream().map(Alumno::getId).toList()).stream()
						.filter(Matricula::activa)
						.collect(Collectors.toMap(m -> m.getAlumno().getId(), Function.identity(), (a, b) -> a));
		Integer anio = anioMostrado == null ? null : anioMostrado.getAnio();
		return encontrados.map(a -> new AlumnoResumen(a.getId(), a.nombreCompleto(), a.getDocumento().texto(), anio,
				delAnio.containsKey(a.getId()) ? delAnio.get(a.getId()).getSeccion().etiqueta() : null, a.getEstado(),
				a.getEstado().etiqueta()));
	}

	@Transactional(readOnly = true)
	public CabeceraAlumno cabecera(Long id) {
		return cabecera(buscarAlumno(id));
	}

	@Transactional(readOnly = true)
	public FichaAlumno obtenerFicha(Long id) {
		Alumno alumno = buscarAlumno(id);
		LocalDate hoy = LocalDate.now(reloj);
		Familia familia = alumno.getFamilia();
		List<Apoderado> deLaFamilia = apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familia.getId());
		List<Alumno> hermanos = alumnos.findByFamiliaIdOrderByFechaNacimientoAsc(familia.getId());
		ApoderadoVista responsable = VistasAlumnos.apoderado(alumno.getResponsablePago(), hermanos);
		List<ApoderadoVista> otros = deLaFamilia.stream()
				.filter(a -> a.isActivo() && !a.getId().equals(alumno.getResponsablePago().getId()))
				.map(a -> VistasAlumnos.apoderado(a, hermanos))
				.toList();
		List<HermanoVista> otrosHermanos = hermanos.stream().filter(h -> !h.getId().equals(alumno.getId()))
				.map(vistas::hermano).toList();

		List<SeccionOpcion> abiertas = estructura.seccionesParaMatricular();
		List<Matricula> suyas = matriculas.findByAlumnoIdOrderByAnioEscolarAnioDesc(alumno.getId());
		Set<Long> aniosConMatricula = suyas.stream().map(m -> m.getAnioEscolar().getId()).collect(Collectors.toSet());
		List<MatriculaVista> matriculasVista = suyas.stream()
				.map(m -> new MatriculaVista(m.getId(), m.getAnioEscolar().getId(), m.getAnioEscolar().getAnio(),
						m.getSeccion().etiqueta(), m.getFechaMatricula(), m.getEstado(), m.getEstado().etiqueta(),
						m.getRetiradaEn(), abiertas.stream()
								.filter(s -> s.anioId().equals(m.getAnioEscolar().getId())
										&& !s.id().equals(m.getSeccion().getId()))
								.toList()))
				.toList();
		List<SeccionOpcion> paraMatricular = alumno.activo()
				? abiertas.stream().filter(s -> !aniosConMatricula.contains(s.anioId())).toList()
				: List.of();

		return new FichaAlumno(cabecera(alumno), alumno.getDocumento().tipo().etiqueta(),
				alumno.getDocumento().numero(), alumno.getApellidoPaterno(), alumno.getApellidoMaterno(),
				alumno.getNombres(), alumno.getFechaNacimiento(),
				Period.between(alumno.getFechaNacimiento(), hoy).getYears(), responsable, otros, otrosHermanos,
				matriculasVista, alumno.getRetiradoEn(), alumno.getRetiradoPor(), alumno.getMotivoRetiro(),
				paraMatricular);
	}

	/** Listas del formulario de alumno nuevo (solo quien puede registrar). */
	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional(readOnly = true)
	public OpcionesRegistro prepararRegistro(Long familiaId) {
		List<SeccionOpcion> abiertas = estructura.seccionesParaMatricular();
		if (familiaId == null) {
			return new OpcionesRegistro(null, null, List.of(), abiertas);
		}
		Familia familia = familias.findById(familiaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Familia no encontrada"));
		List<ApoderadoOpcion> opciones = apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familiaId).stream()
				.filter(Apoderado::isActivo)
				.map(a -> new ApoderadoOpcion(a.getId(), a.nombreCompleto() + " · " + a.getParentesco().etiqueta()))
				.toList();
		return new OpcionesRegistro(familia.getId(), familia.getNombre(), opciones, abiertas);
	}

	/**
	 * Registra al alumno con su responsable de pago (uno ya registrado, o uno nuevo con su familia) y, si se eligió
	 * sección, lo matricula. Todo o nada.
	 */
	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional
	public RegistroResultado registrar(RegistrarAlumnoRequest solicitud) {
		LocalDate hoy = LocalDate.now(reloj);
		Seccion seccion = null;
		if (solicitud.seccionId() != null) {
			seccion = secciones.findById(solicitud.seccionId())
					.orElseThrow(() -> new RecursoNoEncontradoException("Sección no encontrada"));
		}
		int anioReferencia = seccion != null ? seccion.getAnioEscolar().getAnio() : hoy.getYear();
		DatosAlumno datos = datosAlumno(solicitud.tipoDocumento(), solicitud.numeroDocumento(),
				solicitud.apellidoPaterno(), solicitud.apellidoMaterno(), solicitud.nombres(),
				solicitud.fechaNacimiento(), anioReferencia, hoy);

		Apoderado responsable;
		if (solicitud.conApoderadoExistente()) {
			if (solicitud.conApoderadoNuevo()) {
				throw new DatoInvalidoException("documentoApoderadoExistente", "Elige una sola opción: un apoderado ya "
						+ "registrado o los datos de uno nuevo.");
			}
			responsable = apoderadoExistente(solicitud.apoderadoExistenteId(), solicitud.documentoApoderadoExistente(),
					"documentoApoderadoExistente");
		}
		else {
			if (!solicitud.conApoderadoNuevo()) {
				throw new DatoInvalidoException("apoderadoNumeroDocumento", "Indica quién es el responsable de pago: "
						+ "elige un apoderado ya registrado o escribe los datos de uno nuevo.");
			}
			DatosApoderado datosApoderado = datosApoderado(solicitud.apoderadoTipoDocumento(),
					solicitud.apoderadoNumeroDocumento(), solicitud.apoderadoApellidoPaterno(),
					solicitud.apoderadoApellidoMaterno(), solicitud.apoderadoNombres(), solicitud.apoderadoParentesco(),
					solicitud.apoderadoTelefonoWhatsapp(), solicitud.apoderadoCorreo(), "apoderado");
			Familia familia = registro.crearFamilia(
					Familia.nombrePorDefecto(datos.apellidoPaterno(), datos.apellidoMaterno()));
			responsable = registro.registrarApoderado(familia, datosApoderado);
		}
		Alumno alumno = registro.registrarAlumno(datos, responsable);

		String advertencia = null;
		if (seccion != null) {
			LocalDate fecha = solicitud.fechaMatricula() != null ? solicitud.fechaMatricula()
					: seccion.getAnioEscolar().fechaMatriculaPorDefecto(hoy);
			registro.matricular(alumno, seccion, fecha);
			advertencia = ReglasDatosPersonales.advertenciaEdad(datos.fechaNacimiento(), seccion.getGrado(),
					anioReferencia).orElse(null);
		}
		return new RegistroResultado(alumno.getId(), alumno.getFamilia().getId(), advertencia);
	}

	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional(readOnly = true)
	public EdicionAlumno datosParaEditar(Long id) {
		Alumno a = buscarAlumno(id);
		return new EdicionAlumno(cabecera(a), new ActualizarAlumnoRequest(a.getDocumento().tipo(),
				a.getDocumento().numero(), a.getApellidoPaterno(), a.getApellidoMaterno(), a.getNombres(),
				a.getFechaNacimiento(), ""));
	}

	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional
	public void actualizar(Long id, ActualizarAlumnoRequest solicitud) {
		Alumno alumno = buscarAlumno(id);
		String motivo = Motivo.exigir(solicitud.motivo());
		LocalDate hoy = LocalDate.now(reloj);
		DatosAlumno datos = datosAlumno(solicitud.tipoDocumento(), solicitud.numeroDocumento(),
				solicitud.apellidoPaterno(), solicitud.apellidoMaterno(), solicitud.nombres(),
				solicitud.fechaNacimiento(), hoy.getYear(), hoy);
		if (!registro.actualizarAlumno(alumno, datos, motivo)) {
			throw new ReglaNegocioException("No cambiaste ningún dato.");
		}
	}

	/**
	 * Cambia el responsable de pago por otro apoderado activo. Si es de otra familia, el alumno pasa a esa familia.
	 * Queda RESALTADO en la bitácora: decide a quién se cobra y quién recibe los avisos de pago.
	 */
	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional
	public void cambiarResponsablePago(Long alumnoId, CambiarResponsableRequest solicitud) {
		Alumno alumno = buscarAlumno(alumnoId);
		String motivo = Motivo.exigir(solicitud.motivo());
		if (solicitud.apoderadoId() == null && (solicitud.documentoApoderado() == null
				|| solicitud.documentoApoderado().isBlank())) {
			throw new ReglaNegocioException("Elige al nuevo responsable de pago o escribe su número de documento.");
		}
		Apoderado nuevo = apoderadoExistente(solicitud.apoderadoId(), solicitud.documentoApoderado(),
				"documentoApoderado");
		Apoderado anterior = alumno.getResponsablePago();
		Familia familiaAnterior = alumno.getFamilia();
		alumno.cambiarResponsable(nuevo);
		boolean cambioDeFamilia = !familiaAnterior.getId().equals(nuevo.getFamilia().getId());
		auditoria.registrar(AccionAuditoria.RESPONSABLE_PAGO_CAMBIADO, "alumno", alumnoId.toString(),
				responsableTexto(anterior, familiaAnterior), responsableTexto(nuevo, nuevo.getFamilia()),
				"Alumno " + alumno.nombreCompleto() + "." + (cambioDeFamilia ? " Pasó a otra familia." : "")
						+ " Motivo: " + motivo);
	}

	/** Retira al alumno y sus matrículas activas. Queda resaltado en la bitácora. */
	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional
	public void retirar(Long alumnoId, RetirarAlumnoRequest solicitud) {
		Alumno alumno = buscarAlumno(alumnoId);
		String motivo = Motivo.exigir(solicitud.motivo());
		LocalDate fecha = solicitud.fecha();
		if (fecha == null) {
			throw new ReglaNegocioException("Elige la fecha de retiro.");
		}
		if (fecha.isAfter(LocalDate.now(reloj))) {
			throw new ReglaNegocioException("La fecha de retiro no puede ser futura.");
		}
		alumno.retirar(fecha, usuarioActual(), motivo);
		List<String> retiradas = new java.util.ArrayList<>();
		for (Matricula m : matriculas.findByAlumnoIdOrderByAnioEscolarAnioDesc(alumnoId)) {
			if (m.activa()) {
				m.retirar(fecha);
				retiradas.add(m.getSeccion().etiqueta() + " " + m.getAnioEscolar().getAnio());
			}
		}
		auditoria.registrar(AccionAuditoria.ALUMNO_RETIRADO, "alumno", alumnoId.toString(), "activo",
				"retirado el " + Calendario.formatear(fecha), "Alumno " + alumno.nombreCompleto() + "."
						+ (retiradas.isEmpty() ? "" : " Matrículas retiradas: " + String.join(", ", retiradas) + ".")
						+ " Motivo: " + motivo);
	}

	/** Datos del alumno validados. Los nombres de campo son los del formulario. */
	static DatosAlumno datosAlumno(TipoDocumento tipo, String numero, String apellidoPaterno, String apellidoMaterno,
			String nombres, LocalDate nacimiento, int anio, LocalDate hoy) {
		return new DatosAlumno(ReglasDatosPersonales.documento(tipo, numero, "numeroDocumento"),
				ReglasDatosPersonales.nombre(apellidoPaterno, "apellidoPaterno"),
				ReglasDatosPersonales.nombreOpcional(apellidoMaterno, "apellidoMaterno"),
				ReglasDatosPersonales.nombre(nombres, "nombres"),
				ReglasDatosPersonales.fechaNacimiento(nacimiento, anio, hoy, "fechaNacimiento"));
	}

	/**
	 * Datos del apoderado validados.
	 *
	 * @param prefijo prefijo de los campos del formulario: "" → «numeroDocumento»; "apoderado" →
	 *                «apoderadoNumeroDocumento»
	 */
	static DatosApoderado datosApoderado(TipoDocumento tipo, String numero, String apellidoPaterno,
			String apellidoMaterno, String nombres, Parentesco parentesco,
			String telefono, String correo, String prefijo) {
		if (parentesco == null) {
			throw new DatoInvalidoException(campo(prefijo, "parentesco"), "Elige el parentesco.");
		}
		DatosApoderado datos = new DatosApoderado(
				ReglasDatosPersonales.documento(tipo, numero, campo(prefijo, "numeroDocumento")),
				ReglasDatosPersonales.nombre(apellidoPaterno, campo(prefijo, "apellidoPaterno")),
				ReglasDatosPersonales.nombreOpcional(apellidoMaterno, campo(prefijo, "apellidoMaterno")),
				ReglasDatosPersonales.nombre(nombres, campo(prefijo, "nombres")), parentesco,
				ReglasDatosPersonales.telefonoWhatsapp(telefono, campo(prefijo, "telefonoWhatsapp")),
				ReglasDatosPersonales.correo(correo, campo(prefijo, "correo")));
		ReglasDatosPersonales.exigirContacto(datos.telefonoWhatsapp(), datos.correo(), campo(prefijo, "telefonoWhatsapp"));
		return datos;
	}

	private static String campo(String prefijo, String nombre) {
		return prefijo.isEmpty() ? nombre : prefijo + Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1);
	}

	/** Un apoderado activo del colegio, por id o por su número de documento (de cualquier tipo). */
	private Apoderado apoderadoExistente(Long id, String documento, String campo) {
		Apoderado apoderado;
		if (id != null) {
			apoderado = apoderados.findById(id)
					.orElseThrow(() -> new RecursoNoEncontradoException("Apoderado no encontrado"));
		}
		else {
			String numero = Normalizador.sinEspacios(documento);
			numero = numero == null ? "" : numero.toUpperCase(java.util.Locale.ROOT);
			List<Apoderado> encontrados = apoderados.findByDocumentoNumeroIn(List.of(numero));
			if (encontrados.isEmpty()) {
				throw new DatoInvalidoException(campo, "No hay un apoderado registrado con el documento «"
						+ numero + "». Revisa el número o registra sus datos como apoderado nuevo.");
			}
			if (encontrados.size() > 1) {
				throw new DatoInvalidoException(campo, "Hay más de un apoderado con ese número (con distinto tipo de "
						+ "documento). Elígelo desde la página de su familia.");
			}
			apoderado = encontrados.getFirst();
		}
		if (!apoderado.isActivo()) {
			throw new DatoInvalidoException(campo, apoderado.nombreCompleto() + " está desactivado: elige un apoderado "
					+ "activo.");
		}
		return apoderado;
	}

	private Alumno buscarAlumno(Long id) {
		return alumnos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Alumno no encontrado"));
	}

	private CabeceraAlumno cabecera(Alumno alumno) {
		return new CabeceraAlumno(alumno.getId(), alumno.nombreCompleto(), alumno.getDocumento().texto(),
				alumno.getEstado(), alumno.getEstado().etiqueta(), alumno.getFamilia().getId(),
				alumno.getFamilia().getNombre(), vistas.seccionEnCurso(alumno));
	}

	private static String responsableTexto(Apoderado apoderado, Familia familia) {
		return apoderado.nombreCompleto() + " (" + apoderado.getParentesco().etiqueta() + ", " + familia.getNombre()
				+ ")";
	}

	private static String usuarioActual() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? "sistema" : autenticacion.getName();
	}
}
