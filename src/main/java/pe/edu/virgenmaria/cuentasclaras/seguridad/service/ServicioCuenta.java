package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
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

	private final Clock reloj;

	public ServicioCuenta(UsuarioRepository usuarios, PasswordEncoder codificador, AuditoriaService auditoria,
			Clock reloj) {
		this.usuarios = usuarios;
		this.codificador = codificador;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/**
	 * Cambia la clave del usuario en sesión. Exige la clave actual, una nueva distinta que cumpla la
	 * política y la confirmación. Audita {@code CLAVE_CAMBIADA} sin ningún valor de la clave.
	 * Quien llama debe cerrar la sesión: el usuario vuelve a ingresar con la clave nueva.
	 */
	@Transactional
	public void cambiarClave(Long usuarioId, CambiarClaveRequest solicitud) {
		Usuario usuario = usuarios.findById(usuarioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
		if (!codificador.matches(solicitud.claveActual(), usuario.getClaveHash())) {
			throw new ReglaNegocioException("Tu clave actual no es correcta.");
		}
		if (!solicitud.claveNueva().equals(solicitud.confirmacion())) {
			throw new ReglaNegocioException("La nueva clave y su confirmación no coinciden.");
		}
		if (codificador.matches(solicitud.claveNueva(), usuario.getClaveHash())) {
			throw new ReglaNegocioException("Tu nueva clave debe ser distinta de la actual.");
		}
		PoliticaClaves.validar(solicitud.claveNueva(), usuario.getNombreUsuario());
		boolean eraTemporal = usuario.isDebeCambiarClave();
		usuario.cambiarClave(codificador.encode(solicitud.claveNueva()), LocalDateTime.now(reloj), false);
		auditoria.registrar(AccionAuditoria.CLAVE_CAMBIADA, "usuario", usuario.getId().toString(), null, null,
				eraTemporal ? "Reemplazó su clave temporal." : "Cambió su clave.");
	}
}
