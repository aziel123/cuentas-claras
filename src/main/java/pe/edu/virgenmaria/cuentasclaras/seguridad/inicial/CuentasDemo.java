package pe.edu.virgenmaria.cuentasclaras.seguridad.inicial;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.PoliticaClaves;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Sprint 8, tanda 3 (sección 8.2): las cuentas del personal del colegio ficticio de un visitante de la demo, una por rol,
 * todas con la misma clave aleatoria (la ve una sola vez) y sin clave temporal. Se crean por la ruta de identidad, como en
 * {@link DatosDemoDev}. Solo en el perfil {@code demo} (H2 en memoria, datos ficticios). La del apoderado la crea
 * {@code alumnos.inicial.AccesoApoderadoDemo}, enlazada a una familia de ejemplo.
 */
@Component
@Profile("demo")
public class CuentasDemo {

	/** El sufijo del nombre de usuario, el nombre de la persona (ficticio) y su rol. */
	public record CuentaDemo(String sufijo, String nombreCompleto, Rol rol) {
	}

	/** El personal de la demo. La cajera es «Lucía Ramos», la del guion de los anuncios. */
	public static final List<CuentaDemo> PERSONAL = List.of(
			new CuentaDemo("promotoria", "María Elena Torres", Rol.PROMOTOR),
			new CuentaDemo("direccion", "Jorge Salazar", Rol.DIRECTOR),
			new CuentaDemo("administracion", "Rosa Medina", Rol.ADMINISTRACION),
			new CuentaDemo("caja", "Lucía Ramos", Rol.CAJA),
			new CuentaDemo("docente", "Carlos Quispe", Rol.DOCENTE));

	private final UsuarioRepository usuarios;

	private final PasswordEncoder codificador;

	private final AuditoriaService auditoria;

	private final EjecucionIdentidad identidad;

	private final Clock reloj;

	public CuentasDemo(UsuarioRepository usuarios, PasswordEncoder codificador, AuditoriaService auditoria,
			EjecucionIdentidad identidad, Clock reloj) {
		this.usuarios = usuarios;
		this.codificador = codificador;
		this.auditoria = auditoria;
		this.identidad = identidad;
		this.reloj = reloj;
	}

	/** Crea las cinco cuentas del personal ({@code prefijo.promotoria}, ...). @return sus nombres de usuario */
	public List<String> crear(long colegioId, String prefijo, String clave) {
		String hash = codificador.encode(clave);
		return PERSONAL.stream().map(c -> crear(colegioId, prefijo + "." + c.sufijo(), c.nombreCompleto(), c.rol(), clave,
				hash)).toList();
	}

	private String crear(long colegioId, String nombreUsuario, String nombreCompleto, Rol rol, String clave, String hash) {
		PoliticaClaves.validar(clave, nombreUsuario);
		ContextoColegio.en(colegioId, () -> identidad.ejecutar(() -> {
			Usuario usuario = Usuario.nuevo(nombreUsuario, nombreCompleto, null, hash, Set.of(rol));
			// Un celular de la demo (rango 900, ficticio): ahí «llega» su enlace o la huella diaria (mensajería simulada).
			usuario.asignarTelefonoWhatsapp("+51900" + String.format("%06d", Math.floorMod(nombreUsuario.hashCode(), 1_000_000)));
			usuario.cambiarClave(hash, LocalDateTime.now(reloj), false);
			usuarios.save(usuario);
			auditoria.registrar(Actor.sistema(colegioId), AccionAuditoria.USUARIO_CREADO, "usuario",
					usuario.getId().toString(), null, rol.name(), "Cuenta de la demo (sistema.demo; datos ficticios).");
		}));
		return nombreUsuario;
	}
}
