package pe.edu.virgenmaria.cuentasclaras.seguridad.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.PoliticaClaves;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Usuarios de demostración para desarrollo local: uno por rol en el Colegio Virgen María y un
 * promotor en un "Colegio de Prueba B" para ver el aislamiento entre colegios.
 * <p>
 * Solo con el perfil {@code dev}, solo si la base es H2 en memoria ({@code jdbc:h2:mem:}) y solo si
 * todavía no hay usuarios. La clave de todos es {@code cuentasclaras.demo.clave} (variable
 * {@code CC_DEMO_CLAVE}); no se escribe en el log.
 */
@Component
@Profile("dev")
@Order(1)
public class DatosDemoDev implements ApplicationRunner {

	static final String PREFIJO_H2_MEMORIA = "jdbc:h2:mem:";

	public static final long COLEGIO_PRINCIPAL = 1L;

	public static final String NOMBRE_COLEGIO_B = "Colegio de Prueba B";

	private static final Logger LOG = LoggerFactory.getLogger(DatosDemoDev.class);

	/** nombre de usuario, nombre completo, rol. */
	record UsuarioDemo(String nombreUsuario, String nombreCompleto, Rol rol) {
	}

	static final List<UsuarioDemo> USUARIOS_COLEGIO_PRINCIPAL = List.of(
			new UsuarioDemo("promotor", "María Elena Torres", Rol.PROMOTOR),
			new UsuarioDemo("director", "Jorge Salazar", Rol.DIRECTOR),
			new UsuarioDemo("administracion", "Rosa Medina", Rol.ADMINISTRACION),
			new UsuarioDemo("caja", "Lucía Ramos", Rol.CAJA),
			// Segunda cajera (sprint 3): para demostrar que dos cajas a la vez no cobran dos veces la misma cuota.
			new UsuarioDemo("caja2", "Pedro Huanca", Rol.CAJA),
			new UsuarioDemo("docente", "Carlos Quispe", Rol.DOCENTE));
	// El usuario «apoderado» (sprint 4) se enlaza a un apoderado registrado: lo crea DatosDemoApoderadoDev, después
	// de las familias de demostración.

	static final UsuarioDemo PROMOTOR_COLEGIO_B = new UsuarioDemo("promotor.b", "Promotor del Colegio B", Rol.PROMOTOR);

	/** Cajera del colegio B (sprint 3): para ver que no encuentra familias ni pagos del colegio principal. */
	static final UsuarioDemo CAJA_COLEGIO_B = new UsuarioDemo("caja.b", "Cajera del Colegio B", Rol.CAJA);

	private final UsuarioRepository usuarios;

	private final ColegioRepository colegios;

	private final PasswordEncoder codificador;

	private final AuditoriaService auditoria;

	/** Sprint 7, tanda 2: las cuentas se crean por la ruta de identidad (cc_sistema). */
	private final EjecucionIdentidad identidad;

	private final Clock reloj;

	private final String urlBaseDatos;

	private final String claveDemo;

	public DatosDemoDev(UsuarioRepository usuarios, ColegioRepository colegios, PasswordEncoder codificador,
			AuditoriaService auditoria, EjecucionIdentidad identidad, Clock reloj,
			@Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.clave:}") String claveDemo) {
		this.usuarios = usuarios;
		this.colegios = colegios;
		this.codificador = codificador;
		this.auditoria = auditoria;
		this.identidad = identidad;
		this.reloj = reloj;
		this.urlBaseDatos = urlBaseDatos;
		this.claveDemo = claveDemo;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		crearSiCorresponde();
	}

	/** @return {@code true} si creó los usuarios */
	boolean crearSiCorresponde() {
		if (urlBaseDatos == null || !urlBaseDatos.startsWith(PREFIJO_H2_MEMORIA)) {
			LOG.warn("No se crean usuarios de demostración: la base no es H2 en memoria.");
			return false;
		}
		if (ContextoColegio.comoSistema(usuarios::count) > 0) {
			LOG.info("Ya hay usuarios: no se crean los de demostración.");
			return false;
		}
		long colegioB = colegios.save(new Colegio(NOMBRE_COLEGIO_B)).getId();
		USUARIOS_COLEGIO_PRINCIPAL.forEach(u -> crear(COLEGIO_PRINCIPAL, u));
		crear(colegioB, PROMOTOR_COLEGIO_B);
		crear(colegioB, CAJA_COLEGIO_B);
		LOG.info("Usuarios de demostración creados: {} y {} (en {}). Clave: la de cuentasclaras.demo.clave (CC_DEMO_CLAVE).",
				USUARIOS_COLEGIO_PRINCIPAL.stream().map(UsuarioDemo::nombreUsuario).toList(),
				List.of(PROMOTOR_COLEGIO_B.nombreUsuario(), CAJA_COLEGIO_B.nombreUsuario()), NOMBRE_COLEGIO_B);
		return true;
	}

	private void crear(long colegioId, UsuarioDemo demo) {
		PoliticaClaves.validar(claveDemo, demo.nombreUsuario());
		ContextoColegio.en(colegioId, () -> identidad.ejecutar(() -> {
			String hash = codificador.encode(claveDemo);
			Usuario usuario = Usuario.nuevo(demo.nombreUsuario(), demo.nombreCompleto(), null, hash, Set.of(demo.rol()));
			// Sprint 5: celular de demostración (rango 966, que no usan los apoderados de ejemplo): ahí le llegaría su
			// enlace y, a Promotoría, la huella diaria (mensajería SIMULADA en dev).
			usuario.asignarTelefonoWhatsapp("+51966" + String.format("%06d",
					Math.floorMod(demo.nombreUsuario().hashCode(), 1_000_000)));
			// En demostración la clave no es temporal: se puede ingresar directo.
			usuario.cambiarClave(hash, LocalDateTime.now(reloj), false);
			usuarios.save(usuario);
			auditoria.registrar(Actor.sistema(colegioId), AccionAuditoria.USUARIO_CREADO, "usuario",
					usuario.getId().toString(), null, demo.rol().name(), "Usuario de demostración (solo desarrollo).");
		}));
	}
}
