package pe.edu.virgenmaria.cuentasclaras.alumnos.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DocumentoIdentidad;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.PoliticaClaves;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Sprint 4: el usuario de demostración {@code apoderado}, enlazado a Rosa Huamán Ccori (DNI 45678912, familia de
 * demostración del colegio principal), para probar el pago en línea SIMULADO de punta a punta. Solo con el perfil
 * {@code dev}, con H2 en memoria, si ya existen las familias de demostración y el usuario todavía no existe.
 */
@Component
@Profile("dev")
@Order(5)
public class DatosDemoApoderadoDev implements ApplicationRunner {

	public static final String USUARIO = "apoderado";

	static final String DNI = "45678912";

	private static final Logger LOG = LoggerFactory.getLogger(DatosDemoApoderadoDev.class);

	private final UsuarioRepository usuarios;

	private final ApoderadoRepository apoderados;

	private final PasswordEncoder codificador;

	private final AuditoriaService auditoria;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	private final String urlBaseDatos;

	private final String claveDemo;

	public DatosDemoApoderadoDev(UsuarioRepository usuarios, ApoderadoRepository apoderados, PasswordEncoder codificador,
			AuditoriaService auditoria, PlatformTransactionManager transacciones, Clock reloj,
			@Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.clave:}") String claveDemo) {
		this.usuarios = usuarios;
		this.apoderados = apoderados;
		this.codificador = codificador;
		this.auditoria = auditoria;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
		this.urlBaseDatos = urlBaseDatos;
		this.claveDemo = claveDemo;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		crearSiCorresponde();
	}

	/** @return {@code true} si creó el usuario */
	boolean crearSiCorresponde() {
		if (urlBaseDatos == null || !urlBaseDatos.startsWith("jdbc:h2:mem:")) {
			return false;
		}
		long colegio = DatosDemoDev.COLEGIO_PRINCIPAL;
		return ContextoColegio.en(colegio, () -> transaccion.execute(estado -> {
			if (usuarios.findByNombreUsuario(USUARIO).isPresent()) {
				return false;
			}
			Optional<Apoderado> rosa = apoderados.findByDocumento(new DocumentoIdentidad(TipoDocumento.DNI, DNI));
			if (rosa.isEmpty()) {
				LOG.info("No se crea el usuario «{}»: no están las familias de demostración.", USUARIO);
				return false;
			}
			PoliticaClaves.validar(claveDemo, USUARIO);
			String hash = codificador.encode(claveDemo);
			Apoderado apoderado = rosa.get();
			Usuario usuario = Usuario.deApoderado(USUARIO, apoderado.nombreCompleto(), null, hash, apoderado.getId());
			// En demostración la clave no es temporal: se puede ingresar directo.
			usuario.cambiarClave(hash, LocalDateTime.now(reloj), false);
			usuarios.save(usuario);
			auditoria.registrar(Actor.sistema(colegio), AccionAuditoria.ACCESO_APODERADO_CREADO, "usuario",
					usuario.getId().toString(), null, "apoderado " + apoderado.getId(),
					"Acceso en línea de demostración (solo desarrollo).");
			LOG.info("Usuario de demostración «{}» creado para {} (pago en línea simulado).", USUARIO,
					apoderado.nombreCompleto());
			return true;
		}));
	}
}
