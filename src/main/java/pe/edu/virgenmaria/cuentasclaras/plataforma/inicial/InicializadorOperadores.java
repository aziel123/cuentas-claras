package pe.edu.virgenmaria.cuentasclaras.plataforma.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.comun.config.PropiedadesPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.config.PropiedadesPlataforma.Operador;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.MotivoCierreSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PropositoEnlace;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnvioEnlaceSolicitado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.GeneradorClaveTemporal;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sprint 8, tanda 1 (sección 3.2, decisión 112): sincroniza los operadores de la plataforma con {@code CC_OPERADORES}
 * ({@code usuario:correo,...}) al arrancar, como {@code sistema.plataforma} y por la ruta de identidad:
 * <ul>
 *   <li>crea al operador que falta, en el colegio de plataforma, con el rol {@code OPERADOR_PLATAFORMA} y una clave al
 *       azar ya vencida, y le envía a su correo un enlace de activación de un solo uso (sin clave en el entorno);</li>
 *   <li>desactiva al que ya no está en la lista y cierra sus sesiones;</li>
 *   <li>reactiva al que vuelve a la lista (con un enlace nuevo).</li>
 * </ul>
 * Agregar o quitar un operador exige un despliegue: queda en el historial de la infraestructura y en la bitácora de la
 * plataforma ({@code OPERADOR_CREADO} y {@code OPERADOR_DESACTIVADO}). Reemplaza al inicializador del primer Promotor
 * ({@code CC_PROMOTOR_*}): todo colegio, también en una instalación dedicada, se da de alta desde {@code /plataforma}.
 */
@Component
@Profile({ "prod", "piloto", "demo" })
@Order(1)
public class InicializadorOperadores implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(InicializadorOperadores.class);

	private final UsuarioRepository usuarios;

	private final ColegioService colegios;

	private final PasswordEncoder codificador;

	private final GeneradorClaveTemporal generador;

	private final AuditoriaService auditoria;

	private final EjecucionIdentidad identidad;

	private final SesionesFirmadas sesiones;

	private final ApplicationEventPublisher eventos;

	private final PropiedadesPlataforma propiedades;

	private final TransactionTemplate lectura;

	private final Clock reloj;

	public InicializadorOperadores(UsuarioRepository usuarios, ColegioService colegios, PasswordEncoder codificador,
			GeneradorClaveTemporal generador, AuditoriaService auditoria, EjecucionIdentidad identidad,
			SesionesFirmadas sesiones, ApplicationEventPublisher eventos, PropiedadesPlataforma propiedades,
			PlatformTransactionManager transacciones, Clock reloj) {
		this.usuarios = usuarios;
		this.colegios = colegios;
		this.codificador = codificador;
		this.generador = generador;
		this.auditoria = auditoria;
		this.identidad = identidad;
		this.sesiones = sesiones;
		this.eventos = eventos;
		this.propiedades = propiedades;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
		this.reloj = reloj;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		sincronizar();
	}

	/** @return los nombres de los operadores creados, reactivados y desactivados, para el log y las pruebas */
	public Resultado sincronizar() {
		List<Operador> lista = propiedades.listaOperadores();
		long plataformaId = colegios.plataformaId();
		List<String> creados = new ArrayList<>();
		List<String> reactivados = new ArrayList<>();
		List<String> desactivados = new ArrayList<>();
		EjecucionComoSistema.como(ActorSistema.PLATAFORMA, plataformaId, () -> {
			List<Usuario> existentes = lectura.execute(t -> usuarios.personalActivo());
			List<Usuario> todos = lectura.execute(t -> usuarios.findAllByOrderByNombreCompletoAsc());
			for (Operador operador : lista) {
				Usuario actual = todos.stream().filter(u -> u.getNombreUsuario().equals(operador.nombreUsuario()))
						.findFirst().orElse(null);
				if (actual == null) {
					crear(operador);
					creados.add(operador.nombreUsuario());
				}
				else if (!actual.isActivo()) {
					reactivar(actual, operador);
					reactivados.add(operador.nombreUsuario());
				}
			}
			for (Usuario existente : existentes) {
				if (lista.stream().noneMatch(o -> o.nombreUsuario().equals(existente.getNombreUsuario()))) {
					desactivar(existente);
					desactivados.add(existente.getNombreUsuario());
				}
			}
		});
		if (lista.isEmpty()) {
			LOG.warn("CC_OPERADORES está vacío: nadie puede dar de alta colegios desde /plataforma.");
		}
		LOG.info("Operadores de la plataforma sincronizados: {} en la lista, {} creados, {} reactivados, {} desactivados.",
				lista.size(), creados.size(), reactivados.size(), desactivados.size());
		return new Resultado(creados, reactivados, desactivados);
	}

	public record Resultado(List<String> creados, List<String> reactivados, List<String> desactivados) {
	}

	private void crear(Operador operador) {
		identidad.ejecutar(() -> {
			LocalDateTime ahora = LocalDateTime.now(reloj);
			Usuario nuevo = Usuario.nuevo(operador.nombreUsuario(), "Operador " + operador.nombreUsuario(), operador.correo(),
					codificador.encode(generador.generar()), Set.of(Rol.OPERADOR_PLATAFORMA));
			nuevo.vencerClaveTemporalEn(ahora);
			usuarios.saveAndFlush(nuevo);
			auditoria.registrar(AccionAuditoria.OPERADOR_CREADO, "usuario", nuevo.getId().toString(), null,
					"roles=OPERADOR_PLATAFORMA; activo; enlace de activación al correo",
					"El despliegue (CC_OPERADORES) agregó al operador " + nuevo.getNombreUsuario() + ". Su enlace de un solo "
							+ "uso va a su correo; nadie más lo ve.");
			eventos.publishEvent(new EnvioEnlaceSolicitado(nuevo.getId(), PropositoEnlace.PERSONAL, null));
		});
	}

	private void reactivar(Usuario operador, Operador datos) {
		identidad.ejecutar(() -> {
			Usuario cuenta = usuarios.findById(operador.getId()).orElseThrow();
			cuenta.reactivar();
			cuenta.restablecerClave(codificador.encode(generador.generar()), LocalDateTime.now(reloj),
					LocalDateTime.now(reloj), ActorSistema.PLATAFORMA.usuario());
			usuarios.saveAndFlush(cuenta);
			auditoria.registrar(AccionAuditoria.OPERADOR_CREADO, "usuario", cuenta.getId().toString(), "inactivo", "activo",
					"El despliegue (CC_OPERADORES) volvió a incluir al operador " + cuenta.getNombreUsuario()
							+ ". Su enlace nuevo va a su correo.");
			eventos.publishEvent(new EnvioEnlaceSolicitado(cuenta.getId(), PropositoEnlace.PERSONAL, null));
		});
	}

	private void desactivar(Usuario operador) {
		identidad.ejecutar(() -> {
			Usuario cuenta = usuarios.findById(operador.getId()).orElseThrow();
			cuenta.desactivar(ActorSistema.PLATAFORMA.usuario(), LocalDateTime.now(reloj));
			usuarios.saveAndFlush(cuenta);
			auditoria.registrar(AccionAuditoria.OPERADOR_DESACTIVADO, "usuario", cuenta.getId().toString(), "activo",
					"inactivo", "El despliegue (CC_OPERADORES) quitó al operador " + cuenta.getNombreUsuario()
							+ ": ya no puede entrar ni dar de alta colegios. Sus sesiones se cerraron.");
			sesiones.cerrarDe(cuenta.getId(), MotivoCierreSesion.CUENTA_CAMBIADA);
		});
	}
}
