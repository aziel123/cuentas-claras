package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSeguridad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.AccesoEnviado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PropositoEnlace;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnvioEnlaceSolicitado;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.MotivoCierreSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnlacesActivacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.GeneradorClaveTemporal;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioDetallesUsuario;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Cuenta en línea del apoderado (sprint 4, decisión 27): la crea Promotoría o Administración desde su ficha. El usuario
 * es su número de documento y queda enlazado a SU registro de apoderado (y por él, a su familia: solo ve y paga lo
 * suyo). Quitar el acceso desactiva la cuenta.
 * <p>
 * Correcciones del sprint 4 (S4-M2) y sprint 5: quien la crea NO ve ninguna clave ni el enlace. El enlace de un solo uso
 * (48 horas; en la base solo su SHA-256) lo genera el proceso de envío y llega DIRECTO al celular o al correo registrado
 * del apoderado; con él, el apoderado elige su propia clave. La activación queda auditada con su IP (y con aviso a Promotoría si es la misma IP de
 * quien la creó). Si el enlace se perdió o lo usó otra persona, Promotoría restablece el acceso con un enlace nuevo.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','ADMINISTRACION')")
public class ServicioAccesoApoderados {

	private final ApoderadoRepository apoderados;

	private final UsuarioRepository usuarios;

	private final ServicioDetallesUsuario detalles;

	private final PasswordEncoder codificador;

	private final GeneradorClaveTemporal generador;

	private final PropiedadesSeguridad propiedades;

	private final AuditoriaService auditoria;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	private final pe.edu.virgenmaria.cuentasclaras.seguridad.service.SesionesUsuario sesiones;

	private final EnlacesActivacion enlaces;

	private final org.springframework.context.ApplicationEventPublisher eventos;

	/** Sprint 7, tanda 2: la cuenta se crea, se restablece o se desactiva por la ruta de identidad (cc_sistema). */
	private final EjecucionIdentidad identidad;

	private final SesionesFirmadas sesionesFirmadas;

	public ServicioAccesoApoderados(ApoderadoRepository apoderados, UsuarioRepository usuarios,
			ServicioDetallesUsuario detalles, PasswordEncoder codificador, GeneradorClaveTemporal generador,
			PropiedadesSeguridad propiedades, AuditoriaService auditoria, PlatformTransactionManager transacciones,
			Clock reloj, pe.edu.virgenmaria.cuentasclaras.seguridad.service.SesionesUsuario sesiones,
			EnlacesActivacion enlaces, org.springframework.context.ApplicationEventPublisher eventos,
			EjecucionIdentidad identidad, SesionesFirmadas sesionesFirmadas) {
		this.identidad = identidad;
		this.sesionesFirmadas = sesionesFirmadas;
		this.eventos = eventos;
		this.sesiones = sesiones;
		this.enlaces = enlaces;
		this.apoderados = apoderados;
		this.usuarios = usuarios;
		this.detalles = detalles;
		this.codificador = codificador;
		this.generador = generador;
		this.propiedades = propiedades;
		this.auditoria = auditoria;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
	}

	/** La cuenta del apoderado, si tiene (solo lectura; también la ve Dirección en la ficha). */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
	public Optional<String> cuentaDe(Long apoderadoId) {
		return Optional.ofNullable(transaccion.execute(t -> usuarios.findByApoderadoId(apoderadoId)
				.map(u -> u.getNombreUsuario() + (u.isActivo() ? "" : " (desactivada)")).orElse(null)));
	}

	/** S4-M2: de estos apoderados, los que tienen cuenta en línea activa (para el botón «Restablecer acceso»). */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
	public java.util.Set<Long> conCuentaActiva(java.util.Collection<Long> apoderadoIds) {
		return transaccion.execute(t -> apoderadoIds.stream().filter(id -> usuarios.findByApoderadoId(id)
				.filter(Usuario::isActivo).isPresent()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
	}

	/**
	 * Crea la cuenta (usuario = número de documento) SIN clave conocida por nadie (la temporal es al azar, nunca se
	 * muestra y ya está vencida) y devuelve el enlace de activación de un solo uso. No es {@code @Transactional}: el
	 * nombre se busca antes en toda la plataforma.
	 */
	public AccesoEnviado darAcceso(Long apoderadoId) {
		Apoderado apoderado = transaccion.execute(t -> apoderados.findById(apoderadoId).filter(Apoderado::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("Apoderado no encontrado")));
		String nombreUsuario = apoderado.getDocumento().numero().toLowerCase(Locale.ROOT);
		if (!Usuario.esNombreUsuarioValido(nombreUsuario)) {
			throw new ReglaNegocioException("El documento del apoderado no sirve como usuario: corrígelo primero.");
		}
		if (transaccion.execute(t -> usuarios.findByApoderadoId(apoderadoId)).isPresent()) {
			throw new ReglaNegocioException("Este apoderado ya tiene su cuenta en línea.");
		}
		if (detalles.colegioDe(nombreUsuario).isPresent()) {
			throw new ReglaNegocioException("Ya existe un usuario «" + nombreUsuario + "» en la plataforma: revisa si el "
					+ "apoderado ya tiene cuenta o si su documento está repetido.");
		}
		try {
			return identidad.como(() -> {
				// Releído en ESTA transacción (el de arriba ya no tiene sesión para su familia).
				Apoderado vigente = apoderados.findById(apoderadoId).filter(Apoderado::isActivo)
						.orElseThrow(() -> new RecursoNoEncontradoException("Apoderado no encontrado"));
				LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
				// Una clave al azar que nadie conoce y que ya venció: la cuenta solo se activa con el enlace.
				Usuario nuevo = Usuario.deApoderado(nombreUsuario, vigente.nombreCompleto(), vigente.getCorreo(),
						codificador.encode(generador.generar()), vigente.getId());
				nuevo.vencerClaveTemporalEn(ahora);
				usuarios.saveAndFlush(nuevo);
				LocalDateTime vence = ahora.plus(propiedades.vigenciaClaveTemporal());
				auditoria.registrar(AccionAuditoria.ACCESO_APODERADO_CREADO, "usuario", nuevo.getId().toString(), null,
						"roles=APODERADO; activo; enlace de un solo uso al titular", "Cuenta en línea del apoderado "
								+ vigente.nombreCompleto() + " (" + vigente.getDocumento().enmascarado() + ") de "
								+ vigente.getFamilia().getNombre() + ": solo ve y paga lo de su familia. Nadie ve su clave ni "
								+ "su enlace: el enlace de un solo uso va a " + destino(vigente) + " y vence el "
								+ Calendario.formatear(vence.toLocalDate()) + ".");
				eventos.publishEvent(new EnvioEnlaceSolicitado(nuevo.getId(), PropositoEnlace.APODERADO, vigente.getId()));
				return enviado(nuevo, vigente, vence);
			});
		}
		catch (DataIntegrityViolationException e) {
			throw new ReglaNegocioException("Este apoderado ya tiene su cuenta en línea.");
		}
	}

	/**
	 * S4-M2: Promotoría restablece el acceso del apoderado (no pudo entrar, perdió el enlace o alguien más lo usó): anula
	 * los enlaces anteriores, deja una clave al azar ya vencida (nadie la conoce), cierra sus sesiones y genera un enlace
	 * nuevo de un solo uso.
	 */
	@PreAuthorize("hasRole('PROMOTOR')")
	public AccesoEnviado restablecerAcceso(Long apoderadoId) {
		AccesoEnviado creado = identidad.como(() -> {
			Usuario usuario = usuarios.findByApoderadoId(apoderadoId)
					.orElseThrow(() -> new ReglaNegocioException("Este apoderado no tiene cuenta en línea."));
			if (!usuario.isActivo()) {
				throw new ReglaNegocioException("La cuenta en línea del apoderado está desactivada.");
			}
			Apoderado apoderado = apoderados.findById(apoderadoId).filter(Apoderado::isActivo)
					.orElseThrow(() -> new ReglaNegocioException("El apoderado está desactivado."));
			LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
			enlaces.anularVigentes(usuario.getId());
			usuario.restablecerClave(codificador.encode(generador.generar()), ahora, ahora,
					SecurityContextHolder.getContext().getAuthentication().getName());
			usuarios.saveAndFlush(usuario);
			LocalDateTime vence = ahora.plus(propiedades.vigenciaClaveTemporal());
			auditoria.registrar(AccionAuditoria.ACCESO_APODERADO_RESTABLECIDO, "usuario", usuario.getId().toString(), null,
					"enlace nuevo de un solo uso al titular", "Se restableció el acceso en línea de "
							+ usuario.getNombreCompleto() + ": los enlaces anteriores ya no sirven y sus sesiones se cerraron. "
							+ "El enlace nuevo va a " + destino(apoderado) + " y vence el "
							+ Calendario.formatear(vence.toLocalDate()) + ".");
			eventos.publishEvent(new EnvioEnlaceSolicitado(usuario.getId(), PropositoEnlace.APODERADO, apoderadoId));
			sesionesFirmadas.cerrarDe(usuario.getId(), MotivoCierreSesion.CUENTA_CAMBIADA);
			return enviado(usuario, apoderado, vence);
		});
		sesiones.expirar(creado.usuarioId());
		return creado;
	}

	/** El contacto registrado al que va el enlace (WhatsApp primero; si no tiene, correo), enmascarado. */
	private static String destino(Apoderado apoderado) {
		return apoderado.getTelefonoWhatsapp() != null ? "WhatsApp " + Enmascarar.telefono(apoderado.getTelefonoWhatsapp())
				: "correo " + Enmascarar.correo(apoderado.getCorreo());
	}

	private static AccesoEnviado enviado(Usuario usuario, Apoderado apoderado, LocalDateTime vence) {
		boolean whatsapp = apoderado.getTelefonoWhatsapp() != null;
		return new AccesoEnviado(usuario.getId(), usuario.getNombreUsuario(), usuario.getNombreCompleto(),
				whatsapp ? "WhatsApp" : "correo", whatsapp ? Enmascarar.telefono(apoderado.getTelefonoWhatsapp())
						: Enmascarar.correo(apoderado.getCorreo()), vence);
	}

	/** Desactiva la cuenta en línea del apoderado (con motivo, resaltado en la bitácora). */
	public void quitarAcceso(Long apoderadoId, String motivo) {
		String texto = Motivo.exigir(motivo);
		Long usuarioId = identidad.como(() -> {
			Usuario usuario = usuarios.findByApoderadoId(apoderadoId)
					.orElseThrow(() -> new ReglaNegocioException("Este apoderado no tiene cuenta en línea."));
			LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
			usuario.desactivar(SecurityContextHolder.getContext().getAuthentication().getName(), ahora);
			usuarios.saveAndFlush(usuario);
			sesionesFirmadas.cerrarDe(usuario.getId(), MotivoCierreSesion.CUENTA_CAMBIADA);
			auditoria.registrar(AccionAuditoria.ACCESO_APODERADO_QUITADO, "usuario", usuario.getId().toString(), "activo",
					"desactivado", "Se quitó el acceso en línea de " + usuario.getNombreCompleto() + ". Motivo: " + texto);
			return usuario.getId();
		});
		sesiones.expirar(usuarioId);
	}
}
