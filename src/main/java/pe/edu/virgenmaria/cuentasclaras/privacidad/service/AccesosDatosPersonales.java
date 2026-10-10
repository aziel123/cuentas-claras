package pe.edu.virgenmaria.cuentasclaras.privacidad.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoReciente;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.ConsultaAccesos;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;
import pe.edu.virgenmaria.cuentasclaras.privacidad.dto.AccesoVista;
import pe.edu.virgenmaria.cuentasclaras.privacidad.dto.FiltroAccesos;
import pe.edu.virgenmaria.cuentasclaras.privacidad.dto.PersonaOpcion;
import pe.edu.virgenmaria.cuentasclaras.privacidad.model.AccesoDatoPersonal;
import pe.edu.virgenmaria.cuentasclaras.privacidad.repository.AccesoDatoPersonalRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.SesionUsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionAbierta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.TokenDeSesion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registro de quién ve datos personales (sprint 7, tanda 3; Ley 29733, sección 8.2; decisión 96).
 * <ul>
 *   <li>{@link #registrar}: lo llama el interceptor de las pantallas marcadas, ANTES de mostrarlas, en su propia
 *       transacción. Solo personal (una familia que ve sus propios datos no se registra). Guarda la sesión de la base de
 *       quien vio (la misma que firma sus aprobaciones) y la IP.</li>
 *   <li>Lo consulta SOLO Promotoría: en la ficha de la familia (90 días) y en {@code /auditoria/accesos}.</li>
 * </ul>
 * La tabla es de solo inserción ({@code cc_app} no la edita ni la borra) y no entra en la cadena HMAC de la bitácora.
 */
@Service
public class AccesosDatosPersonales implements ConsultaAccesos {

	/** Rango máximo de una consulta de /auditoria/accesos. */
	static final int DIAS_MAXIMOS_CONSULTA = 92;

	private final AccesoDatoPersonalRepository accesos;

	private final UsuarioRepository usuarios;

	private final FamiliaRepository familias;

	private final AlumnoRepository alumnos;

	private final TokenDeSesion tokenDeSesion;

	private final SolicitudCambioRepository solicitudes;

	private final ApoderadoRepository apoderados;

	private final SesionUsuarioRepository sesiones;

	private final Clock reloj;

	public AccesosDatosPersonales(AccesoDatoPersonalRepository accesos, UsuarioRepository usuarios,
			FamiliaRepository familias, AlumnoRepository alumnos, TokenDeSesion tokenDeSesion,
			SolicitudCambioRepository solicitudes, ApoderadoRepository apoderados, SesionUsuarioRepository sesiones,
			Clock reloj) {
		this.sesiones = sesiones;
		this.solicitudes = solicitudes;
		this.apoderados = apoderados;
		this.accesos = accesos;
		this.usuarios = usuarios;
		this.familias = familias;
		this.alumnos = alumnos;
		this.tokenDeSesion = tokenDeSesion;
		this.reloj = reloj;
	}

	/**
	 * Registra lo que mostró una pantalla: una fila por familia (o una sola con el alumno, o con la cantidad de filas). Sin
	 * nada anotado, una fila con cantidad 0 (la pantalla se abrió, aunque no mostró filas).
	 *
	 * @return las filas registradas (0 si quien vio es una familia)
	 */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION','CAJA','DOCENTE')")
	@Transactional
	public int registrar(TipoAcceso tipo, AccesoMostrado mostrado, String ip) {
		UsuarioAutenticado persona = personaEnSesion();
		if (persona.roles().contains(Rol.APODERADO)) {
			return 0;
		}
		// Quien vio es una cuenta de la base (siempre, en producción: se autenticó contra ella). Sin cuenta no hay a quién
		// atribuirlo (solo pasa con las personas inventadas de algunas pruebas) y la FK lo rechazaría.
		if (usuarios.findById(persona.usuarioId()).isEmpty()) {
			return 0;
		}
		// La sesión de la base de quien vio (la misma que firma sus aprobaciones), si sigue en la base y es de este colegio.
		Long sesionId = tokenDeSesion.actual().filter(s -> s.usuarioId().equals(persona.usuarioId()))
				.map(SesionAbierta::sesionId).filter(id -> sesiones.findById(id).isPresent()).orElse(null);
		List<AccesoDatoPersonal> filas = new ArrayList<>();
		if (mostrado != null && mostrado.solicitudId() != null) {
			// Una solicitud de la bandeja: solo el cambio de contacto de un apoderado muestra datos de una familia.
			Optional<Long> familia = solicitudes.findById(mostrado.solicitudId())
					.filter(s -> s.getTipo() == TipoSolicitud.CAMBIO_CONTACTO_APODERADO && "apoderado".equals(s.getEntidad()))
					.flatMap(s -> apoderados.findById(s.getEntidadId())).map(a -> a.getFamilia().getId());
			if (familia.isEmpty()) {
				return 0;
			}
			filas.add(AccesoDatoPersonal.de(persona.usuarioId(), sesionId, tipo, familia.get(), null, 1, ip));
		}
		else if (mostrado == null && (tipo == TipoAcceso.FICHA_FAMILIA || tipo == TipoAcceso.FICHA_ALUMNO
				|| tipo == TipoAcceso.APROBACION_CONTACTO || tipo == TipoAcceso.COBRO)) {
			// Falla cerrado: una ficha que no dijo de quién es no se muestra.
			throw new IllegalStateException("La pantalla " + tipo + " no indicó qué ficha mostró");
		}
		else if (mostrado == null) {
			filas.add(AccesoDatoPersonal.de(persona.usuarioId(), sesionId, tipo, null, null, 0, ip));
		}
		else if (mostrado.alumnoId() != null) {
			Long familia = mostrado.familias().stream().findFirst().orElse(null);
			filas.add(AccesoDatoPersonal.de(persona.usuarioId(), sesionId, tipo, familia, mostrado.alumnoId(), 1, ip));
		}
		else if (!mostrado.familias().isEmpty()) {
			mostrado.familias().forEach(f -> filas.add(AccesoDatoPersonal.de(persona.usuarioId(), sesionId, tipo, f, null,
					1, ip)));
		}
		else if (tipo == TipoAcceso.LLAMADA_CONTROL) {
			// La muestra de la semana está vacía: la pantalla no mostró datos de ninguna familia.
			return 0;
		}
		else {
			filas.add(AccesoDatoPersonal.de(persona.usuarioId(), sesionId, tipo, null, null, mostrado.cantidad(), ip));
		}
		filas.forEach(accesos::saveAndFlush);
		return filas.size();
	}

	/** Bloque «Quién consultó estos datos (90 días)» de la ficha de la familia. Solo Promotoría. */
	@Override
	@PreAuthorize("hasRole('PROMOTOR')")
	@Transactional(readOnly = true)
	public List<AccesoReciente> deFamilia(Long familiaId) {
		LocalDateTime desde = LocalDate.now(reloj).minusDays(DIAS_FICHA).atStartOfDay();
		Map<Long, Optional<Usuario>> personas = new HashMap<>();
		return accesos.findTop200ByFamiliaIdAndCreadoEnGreaterThanEqualOrderByIdDesc(familiaId, desde).stream()
				.map(a -> {
					Optional<Usuario> u = personas.computeIfAbsent(a.getUsuarioId(), usuarios::findById);
					return new AccesoReciente(u.map(Usuario::getNombreUsuario).orElse("—"),
							u.map(Usuario::getNombreCompleto).orElse("—"), a.getTipo().etiqueta(), a.getCreadoEn());
				})
				.toList();
	}

	/** /auditoria/accesos: por persona y rango de fechas (como máximo 92 días, 500 filas). Solo Promotoría. */
	@PreAuthorize("hasRole('PROMOTOR')")
	@Transactional(readOnly = true)
	public List<AccesoVista> consultar(FiltroAccesos filtro) {
		LocalDate hoy = LocalDate.now(reloj);
		LocalDate hasta = filtro.hasta() == null ? hoy : filtro.hasta();
		LocalDate desde = filtro.desde() == null ? hasta.minusDays(6) : filtro.desde();
		if (desde.isAfter(hasta)) {
			throw new ReglaNegocioException("La fecha «desde» debe ser anterior o igual a «hasta».");
		}
		if (ChronoUnit.DAYS.between(desde, hasta) > DIAS_MAXIMOS_CONSULTA) {
			throw new ReglaNegocioException("Consulta como máximo " + DIAS_MAXIMOS_CONSULTA + " días a la vez.");
		}
		LocalDateTime inicio = desde.atStartOfDay();
		LocalDateTime fin = hasta.plusDays(1).atStartOfDay();
		List<AccesoDatoPersonal> filas = filtro.usuarioId() == null
				? accesos.findTop500ByCreadoEnGreaterThanEqualAndCreadoEnLessThanOrderByIdDesc(inicio, fin)
				: accesos.findTop500ByUsuarioIdAndCreadoEnGreaterThanEqualAndCreadoEnLessThanOrderByIdDesc(
						filtro.usuarioId(), inicio, fin);
		Map<Long, Optional<Usuario>> personas = new HashMap<>();
		Map<Long, String> nombresFamilia = new HashMap<>();
		Map<Long, String> nombresAlumno = new HashMap<>();
		return filas.stream().map(a -> {
			Optional<Usuario> u = personas.computeIfAbsent(a.getUsuarioId(), usuarios::findById);
			String familia = a.getFamiliaId() == null ? null : nombresFamilia.computeIfAbsent(a.getFamiliaId(),
					id -> familias.findById(id).map(f -> f.getNombre()).orElse("—"));
			String alumno = a.getAlumnoId() == null ? null : nombresAlumno.computeIfAbsent(a.getAlumnoId(),
					id -> alumnos.findById(id).map(x -> x.getNombres() + " " + x.getApellidoPaterno()).orElse("—"));
			return new AccesoVista(a.getCreadoEn(), u.map(Usuario::getNombreUsuario).orElse("—"),
					u.map(Usuario::getNombreCompleto).orElse("—"), a.getTipo().etiqueta(), familia, alumno,
					a.getCantidad(), a.getIp());
		}).toList();
	}

	/** Las personas del personal (para el filtro). Solo Promotoría. */
	@PreAuthorize("hasRole('PROMOTOR')")
	@Transactional(readOnly = true)
	public List<PersonaOpcion> personal() {
		return usuarios.findAllByOrderByNombreCompletoAsc().stream().filter(u -> u.getApoderadoId() == null)
				.sorted(Comparator.comparing(Usuario::getNombreCompleto, String.CASE_INSENSITIVE_ORDER))
				.map(u -> new PersonaOpcion(u.getId(), u.getNombreCompleto() + " (" + u.getNombreUsuario() + ")"))
				.toList();
	}

	private static UsuarioAutenticado personaEnSesion() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new IllegalStateException("No hay una persona en sesión para registrar el acceso a datos personales");
	}
}
