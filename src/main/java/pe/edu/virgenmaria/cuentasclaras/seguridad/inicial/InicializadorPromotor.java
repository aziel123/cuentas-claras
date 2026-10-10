package pe.edu.virgenmaria.cuentasclaras.seguridad.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.PoliticaClaves;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * En producción crea el primer PROMOTOR desde variables de entorno, SOLO si no hay ningún usuario.
 * Recibe una clave que debe cambiar en su primer ingreso. Si ya hay usuarios no toca nada.
 * Si no hay usuarios y faltan las variables, la aplicación no arranca (mensaje claro en el log).
 */
@Component
@Profile({ "prod", "piloto" })
public class InicializadorPromotor implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(InicializadorPromotor.class);

	private final UsuarioRepository usuarios;

	private final ColegioRepository colegios;

	private final PasswordEncoder codificador;

	private final AuditoriaService auditoria;

	/** Sprint 7, tanda 2: las cuentas se crean por la ruta de identidad (cc_sistema). */
	private final EjecucionIdentidad identidad;

	private final Long colegioId;

	private final String nombreUsuario;

	private final String nombreCompleto;

	private final String clave;

	public InicializadorPromotor(UsuarioRepository usuarios, ColegioRepository colegios, PasswordEncoder codificador,
			AuditoriaService auditoria, EjecucionIdentidad identidad,
			@Value("${cuentasclaras.inicial.colegio-id:1}") Long colegioId,
			@Value("${cuentasclaras.inicial.promotor-usuario:}") String nombreUsuario,
			@Value("${cuentasclaras.inicial.promotor-nombre:}") String nombreCompleto,
			@Value("${cuentasclaras.inicial.promotor-clave:}") String clave) {
		this.usuarios = usuarios;
		this.colegios = colegios;
		this.codificador = codificador;
		this.auditoria = auditoria;
		this.identidad = identidad;
		this.colegioId = colegioId;
		this.nombreUsuario = nombreUsuario;
		this.nombreCompleto = nombreCompleto;
		this.clave = clave;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		crearSiNoHayUsuarios();
	}

	/** @return {@code true} si creó al promotor */
	boolean crearSiNoHayUsuarios() {
		if (ContextoColegio.comoSistema(usuarios::count) > 0) {
			LOG.info("Ya hay usuarios: no se crea el promotor inicial.");
			return false;
		}
		validarVariables();
		ContextoColegio.en(colegioId, () -> identidad.ejecutar(() -> {
			Usuario promotor = usuarios.save(Usuario.nuevo(nombreUsuario, nombreCompleto, null,
					codificador.encode(clave), Set.of(Rol.PROMOTOR)));
			auditoria.registrar(Actor.sistema(colegioId), AccionAuditoria.USUARIO_CREADO, "usuario",
					promotor.getId().toString(), null, Rol.PROMOTOR.name(),
					"Promotor inicial creado al arrancar. Debe cambiar su clave al ingresar.");
		}));
		LOG.warn("Se creó el promotor inicial '{}'. Debe cambiar su clave al ingresar. "
				+ "Retira CC_PROMOTOR_CLAVE del entorno.", Usuario.normalizarNombreUsuario(nombreUsuario));
		return true;
	}

	private void validarVariables() {
		List<String> faltantes = new ArrayList<>();
		if (colegioId == null) {
			faltantes.add("CC_COLEGIO_ID");
		}
		if (nombreUsuario == null || nombreUsuario.isBlank()) {
			faltantes.add("CC_PROMOTOR_USUARIO");
		}
		if (nombreCompleto == null || nombreCompleto.isBlank()) {
			faltantes.add("CC_PROMOTOR_NOMBRE");
		}
		if (clave == null || clave.isBlank()) {
			faltantes.add("CC_PROMOTOR_CLAVE");
		}
		if (!faltantes.isEmpty()) {
			throw new IllegalStateException("No hay usuarios y faltan variables de entorno para crear el promotor inicial: "
					+ String.join(", ", faltantes));
		}
		if (!colegios.existsById(colegioId)) {
			throw new IllegalStateException("No existe el colegio indicado en CC_COLEGIO_ID (" + colegioId + ").");
		}
		try {
			PoliticaClaves.validar(clave, nombreUsuario);
		}
		catch (ReglaNegocioException e) {
			throw new IllegalStateException("La clave inicial del promotor (CC_PROMOTOR_CLAVE) no es válida: "
					+ e.getMessage());
		}
	}
}
