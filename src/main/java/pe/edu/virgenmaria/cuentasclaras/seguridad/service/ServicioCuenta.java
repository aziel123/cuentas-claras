package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSeguridad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarClaveRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Cuenta del propio usuario: cambio de clave.
 */
@Service
public class ServicioCuenta {

	private final UsuarioRepository usuarios;

	private final PasswordEncoder codificador;

	private final AuditoriaService auditoria;

	private final PropiedadesSeguridad propiedades;

	private final Clock reloj;

	public ServicioCuenta(UsuarioRepository usuarios, PasswordEncoder codificador, AuditoriaService auditoria,
			PropiedadesSeguridad propiedades, Clock reloj) {
		this.usuarios = usuarios;
		this.codificador = codificador;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	/**
	 * Cambia la clave del usuario en sesión. Exige la clave actual, una nueva distinta que cumpla la
	 * política y la confirmación. Audita {@code CLAVE_CAMBIADA} sin ningún valor de la clave.
	 * <p>
	 * Una clave actual incorrecta cuenta como intento fallido, igual que en el login (así la pantalla no
	 * sirve para adivinar la clave de una sesión abierta). La excepción NO revierte la transacción: el
	 * contador y la auditoría se guardan. Quien llama debe cerrar la sesión tras el cambio o el bloqueo.
	 */
	@Transactional(noRollbackFor = ClaveActualIncorrectaException.class)
	public void cambiarClave(Long usuarioId, CambiarClaveRequest solicitud) {
		Usuario usuario = usuarios.bloquearPorId(usuarioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
		LocalDateTime ahora = LocalDateTime.now(reloj);
		if (usuario.estaBloqueado(ahora)) {
			throw new ClaveActualIncorrectaException(true);
		}
		if (!codificador.matches(solicitud.claveActual(), usuario.getClaveHash())) {
			registrarFallo(usuario, ahora);
		}
		if (!solicitud.claveNueva().equals(solicitud.confirmacion())) {
			throw new ReglaNegocioException("La nueva clave y su confirmación no coinciden.");
		}
		PoliticaClaves.validar(solicitud.claveNueva(), usuario.getNombreUsuario());
		if (codificador.matches(solicitud.claveNueva(), usuario.getClaveHash())) {
			throw new ReglaNegocioException("Tu nueva clave debe ser distinta de la actual.");
		}
		boolean eraTemporal = usuario.isDebeCambiarClave();
		usuario.cambiarClave(codificador.encode(solicitud.claveNueva()), ahora, false);
		usuario.desbloquear(); // reinicia el contador de intentos fallidos
		auditoria.registrar(AccionAuditoria.CLAVE_CAMBIADA, "usuario", usuario.getId().toString(), null, null,
				eraTemporal ? "Reemplazó su clave temporal." : "Cambió su clave.");
	}

	private void registrarFallo(Usuario usuario, LocalDateTime ahora) {
		int intento = usuario.getIntentosFallidos() + 1;
		boolean bloqueada = usuario.registrarIngresoFallido(propiedades.intentosMaximos(),
				propiedades.duracionBloqueo(), ahora);
		String id = usuario.getId().toString();
		auditoria.registrar(AccionAuditoria.INGRESO_FALLIDO, "usuario", id, null, null,
				"Clave actual incorrecta al cambiar la clave. Intento " + intento + " de "
						+ propiedades.intentosMaximos() + ".");
		if (bloqueada) {
			auditoria.registrar(AccionAuditoria.CUENTA_BLOQUEADA, "usuario", id, null,
					"bloqueada hasta " + usuario.getBloqueadoHasta(),
					"Se bloqueó por " + propiedades.intentosMaximos() + " intentos fallidos seguidos.");
		}
		throw new ClaveActualIncorrectaException(bloqueada);
	}
}
