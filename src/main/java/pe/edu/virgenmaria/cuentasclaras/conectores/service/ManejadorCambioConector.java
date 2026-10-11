package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.EstadoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sprint 8, tanda 2 (sección 3.6, E12): aprueba o rechaza la solicitud {@code CAMBIO_CONECTOR} desde la bandeja (que ya
 * comprobó que quien resuelve es otra persona de Promotoría o Dirección, firmó y dejó la solicitud APROBADA o RECHAZADA).
 * Al aprobar, en la misma transacción:
 * <ol>
 *   <li>vuelve a validar: el conector sigue PROPUESTO, con su prueba de conexión correcta; si mueve dinero (pasarela,
 *       cuenta bancaria, recaudación), una Promotoría participa (propone o aprueba); y lo que exige el módulo que lo usa
 *       ({@link AdaptadorConector#validarAntesDeVigencia}, por ejemplo las series del OSE);</li>
 *   <li>el vigente pasa a REEMPLAZADO (con flush) y el propuesto a VIGENTE (trg_conector_colegio_estado lo exige);</li>
 *   <li>bitácora resaltada {@code CONECTOR_VIGENTE} (con la pista, nunca el secreto) y {@link ConectorEnVigencia}: la
 *       mensajería avisa a TODA la Promotoría y al correo externo de control en esta misma transacción (si no se puede
 *       avisar, el cambio no se guarda). «Para revisar» muestra una alerta CRÍTICA durante unos días
 *       ({@link AlertasConectores}).</li>
 * </ol>
 * Al rechazar, firma el rechazo del conector ({@code conector_colegio:{id}:RECHAZADO}) y lo deja RECHAZADO.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorCambioConector implements ManejadorSolicitud {

	private final ConectorColegioRepository conectores;

	private final ConectoresColegio vigentes;

	private final List<AdaptadorConector> adaptadores;

	private final UsuarioRepository usuarios;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public ManejadorCambioConector(ConectorColegioRepository conectores, ConectoresColegio vigentes,
			List<AdaptadorConector> adaptadores, UsuarioRepository usuarios, FirmaSesion firma, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, Clock reloj) {
		this.conectores = conectores;
		this.vigentes = vigentes;
		this.adaptadores = List.copyOf(adaptadores);
		this.usuarios = usuarios;
		this.firma = firma;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.CAMBIO_CONECTOR;
	}

	@Override
	public String entidad() {
		return PropuestaConector.ENTIDAD;
	}

	@Override
	public int prioridad(SolicitudCambio solicitud) {
		return 1;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		ConectorColegio conector = propuestoDe(solicitud);
		if (!Boolean.TRUE.equals(conector.getPruebaOk())) {
			throw new ReglaNegocioException("Antes de aprobarlo, quien lo propuso debe probar la conexión con éxito "
					+ "(«Probar conexión» en Conectores).");
		}
		TipoConector tipo = conector.getTipo();
		if (tipo.mueveDinero() && !esPromotoria(conector.getPropuestoPor()) && !esPromotoria(aprobador)) {
			throw new ReglaNegocioException("Cambiar " + tipo.descripcion() + " cambia adónde entra el dinero del colegio: "
					+ "debe aprobarlo una persona de Promotoría (lo propuso alguien que no es de Promotoría).");
		}
		ConectorVigente candidato = vigentes.descifradoParaPrueba(conector);
		adaptadores.stream().filter(a -> a.tipo() == tipo && a.admite(conector.getProveedor()))
				.forEach(a -> a.validarAntesDeVigencia(candidato));
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		Optional<ConectorColegio> anterior = conectores.bloquearVigente(tipo);
		anterior.ifPresent(a -> {
			a.reemplazar();
			// Primero el anterior: el UNIQUE (colegio, tipo, vigente) admite un solo vigente.
			conectores.saveAndFlush(a);
			vigentes.olvidar(a.getId());
		});
		conector.entrarEnVigencia(aprobador, ahora);
		conectores.saveAndFlush(conector);
		vigentes.olvidar(conector.getId());
		ConfiguracionConector configuracion = ConfiguracionConector.deJson(tipo, conector.getConfiguracion());
		String antes = anterior.map(PropuestaConector::descripcion).orElse("sin conector");
		auditoria.registrar(AccionAuditoria.CONECTOR_VIGENTE, PropuestaConector.ENTIDAD, conector.getId().toString(),
				antes, PropuestaConector.descripcion(conector), "Cambió " + tipo.descripcion() + " a "
						+ conector.getProveedor().etiqueta() + " (" + conector.getModo().etiqueta().toLowerCase() + "): "
						+ configuracion.resumen() + (conector.getSecretoPista() == null ? ""
								: " (secreto que termina en " + conector.getSecretoPista() + ")")
						+ ". Propuso " + conector.getPropuestoPor() + " y aprobó " + aprobador + " (solicitud "
						+ solicitud.getId() + "). Se avisó a toda la Promotoría y al correo externo de control.");
		eventos.publishEvent(new ConectorEnVigencia(conector.getColegioId(), conector.getId(), tipo,
				conector.getProveedor().etiqueta(), detalleAviso(tipo, configuracion, conector), conector.getPropuestoPor(),
				aprobador, ahora));
	}

	@Override
	public void alRechazar(SolicitudCambio solicitud) {
		ConectorColegio conector = propuestoDe(solicitud);
		String quien = usuario();
		firma.firmar(ClaveFirma.conectorRechazado(conector.getId()));
		conector.rechazar(quien, LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
		conectores.saveAndFlush(conector);
		auditoria.registrar(AccionAuditoria.CONECTOR_RECHAZADO, PropuestaConector.ENTIDAD, conector.getId().toString(),
				EstadoConector.PROPUESTO.name(), EstadoConector.RECHAZADO.name(), "Rechazó el cambio de "
						+ conector.getTipo().descripcion() + " que propuso " + conector.getPropuestoPor() + " (solicitud "
						+ solicitud.getId() + "). Sigue el conector anterior.");
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		Optional<ConectorColegio> conector = conectores.findById(solicitud.getEntidadId());
		if (conector.isEmpty()) {
			return List.of();
		}
		ConectorColegio c = conector.get();
		ConfiguracionConector configuracion = ConfiguracionConector.deJson(c.getTipo(), c.getConfiguracion());
		List<String> lineas = new ArrayList<>();
		lineas.add("Qué cambia: " + c.getTipo().descripcion());
		lineas.add("Nuevo: " + c.getProveedor().etiqueta() + " (" + c.getModo().etiqueta().toLowerCase() + "). "
				+ configuracion.resumen() + (c.getSecretoPista() == null ? "" : ". Secreto que termina en "
						+ c.getSecretoPista()));
		conectores.findFirstByTipoAndEstado(c.getTipo(), EstadoConector.VIGENTE).ifPresentOrElse(
				v -> lineas.add("Reemplaza a: " + v.getProveedor().etiqueta() + ". "
						+ ConfiguracionConector.deJson(v.getTipo(), v.getConfiguracion()).resumen()),
				() -> lineas.add("Hoy el colegio no tiene este conector."));
		lineas.add("Prueba de conexión: " + (Boolean.TRUE.equals(c.getPruebaOk()) ? "correcta" : c.getPruebaOk() == null
				? "todavía no se probó" : "falló") + (c.getPruebaDetalle() == null ? "" : " (" + c.getPruebaDetalle() + ")"));
		if (c.getTipo().mueveDinero()) {
			lineas.add("Una Promotoría debe participar: " + (esPromotoria(c.getPropuestoPor())
					? "lo propuso Promotoría." : "como no lo propuso Promotoría, debe aprobarlo Promotoría."));
		}
		return lineas;
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return conectores.findById(solicitud.getEntidadId()).map(c -> c.getTipo().mueveDinero()
				? "Esto cambia adónde entra el dinero del colegio. Antes de aprobar, confirma el número con el banco o el "
						+ "proveedor (no con quien lo propuso). Se avisará a toda la Promotoría y al correo externo de control."
				: "Esto cambia quién emite las boletas del colegio. Confirma la cuenta del OSE con tu contador. Se avisará "
						+ "a toda la Promotoría y al correo externo de control.").orElse(null);
	}

	/** Lo que dice el aviso a la Promotoría: el número de la cuenta enmascarado y nunca un secreto. */
	private static String detalleAviso(TipoConector tipo, ConfiguracionConector configuracion, ConectorColegio conector) {
		return switch (configuracion) {
			case ConfiguracionConector.CuentaBancaria cuenta -> "cuenta que termina en "
					+ ConfiguracionConector.CuentaBancaria.terminacion(cuenta.numero());
			case ConfiguracionConector.Ose ose -> "series " + ose.serieBoleta() + " y " + ose.serieFactura();
			case ConfiguracionConector.Pasarela pasarela -> conector.getSecretoPista() == null ? "simulada"
					: "llave que termina en " + conector.getSecretoPista();
			case ConfiguracionConector.Recaudacion recaudacion -> "SIN_BASE".equals(recaudacion.modalidad())
					? "sin base de deudas" : "con base de deudas";
		};
	}

	private ConectorColegio propuestoDe(SolicitudCambio solicitud) {
		ConectorColegio conector = conectores.bloquear(Objects.requireNonNull(solicitud.getEntidadId(), "entidadId"))
				.orElseThrow(() -> new ReglaNegocioException("El conector de la solicitud ya no existe."));
		if (conector.getEstado() != EstadoConector.PROPUESTO || !solicitud.getId().equals(conector.getSolicitudId())) {
			throw new ReglaNegocioException("Este cambio de conector ya no está por aprobar ("
					+ conector.getEstado().etiqueta().toLowerCase() + ").");
		}
		return conector;
	}

	private boolean esPromotoria(String nombreUsuario) {
		return usuarios.findByNombreUsuario(nombreUsuario).filter(Usuario::isActivo)
				.map(u -> u.getRoles().contains(Rol.PROMOTOR)).orElse(false);
	}

	private static String usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? "sistema" : autenticacion.getName();
	}
}
