package pe.edu.virgenmaria.cuentasclaras.seguridad.inicial;

import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.BaseH2Local;
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
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PrimeraPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.PoliticaClaves;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Usuarios de demostración para desarrollo local: uno por rol en el Colegio Virgen María y un
 * promotor en un "Colegio de Prueba B" para ver el aislamiento entre colegios.
 * <p>
 * Solo con el perfil {@code dev}, solo si la base es H2 local ({@link BaseH2Local}: en memoria o en archivo) y solo si
 * todavía no hay usuarios. La clave de todos es {@code cuentasclaras.demo.clave} (variable
 * {@code CC_DEMO_CLAVE}); no se escribe en el log.
 */
@Component
@Profile("dev")
@Order(1)
public class DatosDemoDev implements ApplicationRunner {

	public static final long COLEGIO_PRINCIPAL = 1L;

	public static final String NOMBRE_COLEGIO_B = "Colegio de Prueba B";

	/**
	 * RUC de ejemplo (formato y dígito verificador válidos, prefijo 2000000: no son de ninguna empresa) para que la lista
	 * de colegios de la plataforma no los muestre vacíos.
	 */
	public static final String RUC_COLEGIO_PRINCIPAL = "20000000010";

	public static final String RUC_COLEGIO_B = "20000000028";

	/** DNI ficticio del titular del contrato en la ficha (los de la demo no son de nadie). */
	static final String DNI_TITULAR_DEMO = "00000000";

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

	/** Sprint 8, tanda 1: el operador de la plataforma (vive en el colegio PLATAFORMA; da de alta colegios). */
	static final UsuarioDemo OPERADOR = new UsuarioDemo("operador", "Operador de la plataforma", Rol.OPERADOR_PLATAFORMA);

	/** Cajera del colegio B (sprint 3): para ver que no encuentra familias ni pagos del colegio principal. */
	static final UsuarioDemo CAJA_COLEGIO_B = new UsuarioDemo("caja.b", "Cajera del Colegio B", Rol.CAJA);

	private final UsuarioRepository usuarios;

	private final ColegioRepository colegios;

	private final PasswordEncoder codificador;

	private final AuditoriaService auditoria;

	private final PrimeraPromotoriaRepository primeras;

	/** Sprint 7, tanda 2: las cuentas se crean por la ruta de identidad (cc_sistema). */
	private final EjecucionIdentidad identidad;

	private final Clock reloj;

	private final String urlBaseDatos;

	private final String claveDemo;

	public DatosDemoDev(UsuarioRepository usuarios, ColegioRepository colegios, PasswordEncoder codificador,
			AuditoriaService auditoria, PrimeraPromotoriaRepository primeras, EjecucionIdentidad identidad, Clock reloj,
			@Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.clave:}") String claveDemo) {
		this.usuarios = usuarios;
		this.colegios = colegios;
		this.codificador = codificador;
		this.auditoria = auditoria;
		this.primeras = primeras;
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
		if (!BaseH2Local.es(urlBaseDatos)) {
			LOG.warn("No se crean usuarios de demostración: la base no es H2 local (en memoria o en archivo).");
			return false;
		}
		if (ContextoColegio.comoSistema(usuarios::count) > 0) {
			LOG.info("Ya hay usuarios: no se crean los de demostración.");
			return false;
		}
		// El colegio B con su ficha (RUC de ejemplo y titular «promotor.b»), ya en producción.
		Colegio nuevoB = Colegio.enPreparacion(NOMBRE_COLEGIO_B, NOMBRE_COLEGIO_B + " S.A.C. (ficticio)", RUC_COLEGIO_B,
				"Av. Ejemplo 456, Lima (dirección ficticia)", "contacto@colegio-b.example", EnumSet.allOf(Nivel.class),
				PROMOTOR_COLEGIO_B.nombreCompleto(), DNI_TITULAR_DEMO, null);
		nuevoB.pasarAProduccion(PROMOTOR_COLEGIO_B.nombreUsuario(), LocalDateTime.now(reloj));
		long colegioB = colegios.save(nuevoB).getId();
		Long promotor = null;
		for (UsuarioDemo u : USUARIOS_COLEGIO_PRINCIPAL) {
			Long id = crear(COLEGIO_PRINCIPAL, u);
			promotor = u.rol() == Rol.PROMOTOR && promotor == null ? id : promotor;
		}
		Long promotorB = crear(colegioB, PROMOTOR_COLEGIO_B);
		crear(colegioB, CAJA_COLEGIO_B);
		Long[] operador = new Long[2];
		colegios.findFirstByTipo(pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio.PLATAFORMA)
				.ifPresent(p -> {
					operador[0] = crear(p.getId(), OPERADOR);
					operador[1] = p.getId();
				});
		// El titular de cada colegio en la lista de la plataforma: su primera Promotoría, dada por el operador.
		if (operador[0] != null) {
			titular(COLEGIO_PRINCIPAL, promotor, operador);
			titular(colegioB, promotorB, operador);
		}
		// Sprint 8, tanda 1: V28 deja en preparación al colegio de una base sin cuentas; en la demostración ya opera (H2 en
		// memoria, sin triggers: en una base real lo pasa a producción su Promotoría con su firma). Antes, su ficha con el
		// RUC de ejemplo y su titular («promotor»): en producción el RUC ya no cambia.
		colegios.findById(COLEGIO_PRINCIPAL).filter(c -> !c.enProduccion()).ifPresent(c -> {
			if (c.getRuc() == null) {
				c.cambiarRuc(RUC_COLEGIO_PRINCIPAL);
			}
			if (c.getTitularNombre() == null) {
				c.cambiarTitular(USUARIOS_COLEGIO_PRINCIPAL.getFirst().nombreCompleto(), DNI_TITULAR_DEMO,
						c.getContratoCodigo());
			}
			c.pasarAProduccion("promotor", LocalDateTime.now(reloj));
			colegios.save(c);
		});
		LOG.info("Usuarios de demostración creados: {} y {} (en {}). Clave: la de cuentasclaras.demo.clave (CC_DEMO_CLAVE).",
				USUARIOS_COLEGIO_PRINCIPAL.stream().map(UsuarioDemo::nombreUsuario).toList(),
				List.of(PROMOTOR_COLEGIO_B.nombreUsuario(), CAJA_COLEGIO_B.nombreUsuario()), NOMBRE_COLEGIO_B);
		return true;
	}

	/** La primera Promotoría del colegio (su titular), registrada por el operador de la plataforma. */
	private void titular(long colegioId, Long usuarioId, Long[] operador) {
		ContextoColegio.en(colegioId, () -> identidad.ejecutar(() -> {
			if (!primeras.existsByIdNotNull()) {
				primeras.saveAndFlush(PrimeraPromotoria.de(usuarioId, operador[0], operador[1]));
			}
		}));
	}

	/** @return el id de la cuenta creada */
	private Long crear(long colegioId, UsuarioDemo demo) {
		PoliticaClaves.validar(claveDemo, demo.nombreUsuario());
		return ContextoColegio.en(colegioId, () -> identidad.como(() -> {
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
			return usuario.getId();
		}));
	}
}
