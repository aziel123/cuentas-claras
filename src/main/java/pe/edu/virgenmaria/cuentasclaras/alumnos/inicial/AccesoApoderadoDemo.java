package pe.edu.virgenmaria.cuentasclaras.alumnos.inicial;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DocumentoIdentidad;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.PoliticaClaves;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Sprint 8, tanda 3 (sección 8.2): la cuenta del portal de las familias de la demo ({@code prefijo.apoderado}), enlazada a
 * la primera familia ficticia de {@link FamiliasDemo}, con la misma clave aleatoria que el personal. Así el visitante ve
 * el estado de cuenta, el aviso de cada pago y el pago en línea SIMULADO. Por la ruta de identidad, como
 * {@link DatosDemoApoderadoDev}. Solo en el perfil {@code demo}.
 */
@Component
@Profile("demo")
public class AccesoApoderadoDemo {

	private final UsuarioRepository usuarios;

	private final ApoderadoRepository apoderados;

	private final PasswordEncoder codificador;

	private final AuditoriaService auditoria;

	private final EjecucionIdentidad identidad;

	private final Clock reloj;

	public AccesoApoderadoDemo(UsuarioRepository usuarios, ApoderadoRepository apoderados, PasswordEncoder codificador,
			AuditoriaService auditoria, EjecucionIdentidad identidad, Clock reloj) {
		this.usuarios = usuarios;
		this.apoderados = apoderados;
		this.codificador = codificador;
		this.auditoria = auditoria;
		this.identidad = identidad;
		this.reloj = reloj;
	}

	/** Crea la cuenta del apoderado con ese DNI (de una familia de la demo). @return su nombre de usuario */
	public String crear(long colegioId, String nombreUsuario, String clave, String dniApoderado) {
		PoliticaClaves.validar(clave, nombreUsuario);
		return ContextoColegio.en(colegioId, () -> identidad.como(() -> {
			Apoderado apoderado = apoderados.findByDocumento(new DocumentoIdentidad(TipoDocumento.DNI, dniApoderado))
					.orElseThrow(() -> new IllegalStateException("No está la familia de la demo del apoderado"));
			String hash = codificador.encode(clave);
			Usuario usuario = Usuario.deApoderado(nombreUsuario, apoderado.nombreCompleto(), null, hash, apoderado.getId());
			usuario.cambiarClave(hash, LocalDateTime.now(reloj), false);
			usuarios.save(usuario);
			auditoria.registrar(Actor.sistema(colegioId), AccionAuditoria.ACCESO_APODERADO_CREADO, "usuario",
					usuario.getId().toString(), null, "apoderado " + apoderado.getId(),
					"Acceso en línea de la demo (sistema.demo; datos ficticios).");
			return nombreUsuario;
		}));
	}
}
