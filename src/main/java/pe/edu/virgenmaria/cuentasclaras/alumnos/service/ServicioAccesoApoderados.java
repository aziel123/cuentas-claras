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
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSeguridad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.UsuarioCreado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.GeneradorClaveTemporal;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioDetallesUsuario;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Cuenta en línea del apoderado (sprint 4, decisión 27): la crea Promotoría o Administración desde su ficha. El usuario
 * es su número de documento, queda enlazado a SU registro de apoderado (y por él, a su familia: solo ve y paga lo suyo)
 * y la clave temporal vence en 48 horas y se entrega en persona hasta el sprint 5. Quitar el acceso desactiva la cuenta.
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

	public ServicioAccesoApoderados(ApoderadoRepository apoderados, UsuarioRepository usuarios,
			ServicioDetallesUsuario detalles, PasswordEncoder codificador, GeneradorClaveTemporal generador,
			PropiedadesSeguridad propiedades, AuditoriaService auditoria, PlatformTransactionManager transacciones,
			Clock reloj, pe.edu.virgenmaria.cuentasclaras.seguridad.service.SesionesUsuario sesiones) {
		this.sesiones = sesiones;
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

	/**
	 * Crea la cuenta (usuario = número de documento) con una clave temporal que se muestra UNA vez. No es
	 * {@code @Transactional}: el nombre se busca antes en toda la plataforma.
	 */
	public UsuarioCreado darAcceso(Long apoderadoId) {
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
		String clave = generador.generar();
		Usuario usuario;
		try {
			usuario = transaccion.execute(t -> {
				// Releído en ESTA transacción (el de arriba ya no tiene sesión para su familia).
				Apoderado vigente = apoderados.findById(apoderadoId).filter(Apoderado::isActivo)
						.orElseThrow(() -> new RecursoNoEncontradoException("Apoderado no encontrado"));
				LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
				Usuario nuevo = Usuario.deApoderado(nombreUsuario, vigente.nombreCompleto(), vigente.getCorreo(),
						codificador.encode(clave), vigente.getId());
				nuevo.vencerClaveTemporalEn(ahora.plus(propiedades.vigenciaClaveTemporal()));
				usuarios.save(nuevo);
				auditoria.registrar(AccionAuditoria.ACCESO_APODERADO_CREADO, "usuario", nuevo.getId().toString(), null,
						"roles=APODERADO; activo; clave temporal", "Cuenta en línea del apoderado " + vigente.nombreCompleto()
								+ " (" + vigente.getDocumento().enmascarado() + ") de " + vigente.getFamilia().getNombre()
								+ ": solo ve y paga lo de su familia. La clave temporal se entrega en persona.");
				return nuevo;
			});
		}
		catch (DataIntegrityViolationException e) {
			throw new ReglaNegocioException("Este apoderado ya tiene su cuenta en línea.");
		}
		return new UsuarioCreado(usuario.getId(), usuario.getNombreUsuario(), usuario.getNombreCompleto(), clave);
	}

	/** Desactiva la cuenta en línea del apoderado (con motivo, resaltado en la bitácora). */
	public void quitarAcceso(Long apoderadoId, String motivo) {
		String texto = Motivo.exigir(motivo);
		Long usuarioId = transaccion.execute(t -> {
			Usuario usuario = usuarios.findByApoderadoId(apoderadoId)
					.orElseThrow(() -> new ReglaNegocioException("Este apoderado no tiene cuenta en línea."));
			LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
			usuario.desactivar(SecurityContextHolder.getContext().getAuthentication().getName(), ahora);
			auditoria.registrar(AccionAuditoria.ACCESO_APODERADO_QUITADO, "usuario", usuario.getId().toString(), "activo",
					"desactivado", "Se quitó el acceso en línea de " + usuario.getNombreCompleto() + ". Motivo: " + texto);
			return usuario.getId();
		});
		sesiones.expirar(usuarioId);
	}
}
