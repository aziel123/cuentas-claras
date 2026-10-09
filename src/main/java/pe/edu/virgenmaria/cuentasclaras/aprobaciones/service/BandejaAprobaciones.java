package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.BandejaVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ControlParticipantes;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bandeja de aprobaciones (Promotoría y Dirección): aprueba o rechaza las solicitudes. Quien la pidió (o quien creó o
 * restableció la clave de esa cuenta en los últimos 30 días) no la resuelve: el intento queda auditado. Al aprobar,
 * la solicitud queda APROBADA (con flush) y después el manejador del tipo aplica el cambio en la misma transacción: si
 * falla, nada queda aprobado (M1: los triggers de MySQL exigen la solicitud aprobada para reabrir una caja o anular una
 * cuota). Si el manejador pide llamadas (A2), se exige «Hablé con el apoderado» y los números llamados.
 * <p>
 * Sprint 7, tanda 2:
 * <ul>
 *   <li><b>Firma de sesión</b> (sección 3.4): antes de resolver, quien aprueba o rechaza firma la clave
 *       {@code solicitud_cambio:id:estado} con el secreto de su sesión ({@link FirmaSesion}); en MySQL,
 *       trg_solicitud_cambio_resuelta lo exige. Con la clave de {@code cc_app} no se aprueba a nombre de otra persona.</li>
 *   <li><b>Tipos de identidad</b> ({@link #TIPOS_DE_IDENTIDAD}: contacto y roles del personal): la resolución, su firma
 *       y el cambio de la cuenta corren completos en la ruta de identidad ({@code cc_sistema}), en una sola transacción.
 *       Los demás, en la de la aplicación.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class BandejaAprobaciones {

	/** Sprint 7, tanda 2: se resuelven por la ruta de identidad (escriben usuario o usuario_rol). */
	public static final Set<TipoSolicitud> TIPOS_DE_IDENTIDAD = EnumSet.of(TipoSolicitud.CAMBIO_CONTACTO_PERSONAL,
			TipoSolicitud.CAMBIO_ROLES);

	private final SolicitudCambioRepository solicitudes;

	/** Los manejadores exigen una transacción abierta (también {@code tipo()}): se eligen dentro de ella. */
	private final List<ManejadorSolicitud> manejadores;

	private final ControlParticipantes participantes;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	private final FirmaSesion firma;

	private final EjecucionIdentidad identidad;

	private final TransactionTemplate transaccion;

	public BandejaAprobaciones(SolicitudCambioRepository solicitudes, List<ManejadorSolicitud> manejadores,
			ControlParticipantes participantes, AuditoriaService auditoria, Clock reloj, FirmaSesion firma,
			EjecucionIdentidad identidad, PlatformTransactionManager transacciones) {
		this.firma = firma;
		this.identidad = identidad;
		this.transaccion = new TransactionTemplate(transacciones);
		this.solicitudes = solicitudes;
		this.manejadores = List.copyOf(manejadores);
		this.participantes = participantes;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/**
	 * Pendientes por prioridad del manejador: primero los cierres de caja con diferencia, luego las anulaciones de pago
	 * (mueven dinero ya cobrado) y después el resto, en orden de llegada.
	 */
	@Transactional(readOnly = true)
	public BandejaVista bandeja() {
		String usuario = usuario();
		return new BandejaVista(
				solicitudes.findByEstadoOrderByIdAsc(EstadoSolicitud.PENDIENTE).stream()
						.sorted(Comparator.comparingInt((SolicitudCambio s) -> manejadorDe(s).prioridad(s)))
						.map(s -> vista(s, usuario, true)).toList(),
				solicitudes.findTop30ByEstadoNotOrderByResueltoEnDescIdDesc(EstadoSolicitud.PENDIENTE).stream()
						.map(s -> vista(s, usuario, false)).toList(),
				solicitudes.findByTipoOrderByIdDesc(TipoSolicitud.FECHA_MATRICULA).stream()
						.map(s -> vista(s, usuario, false)).toList());
	}

	/**
	 * Sprint 6, tanda 2 (decisión 72): una solicitud para la vista del celular, con su detalle y si quien está en sesión
	 * puede resolverla (la misma regla que la bandeja: quien la pidió o participó, no). La de otro colegio no existe (404).
	 */
	@Transactional(readOnly = true)
	public SolicitudVista detalle(Long id) {
		SolicitudCambio solicitud = solicitudes.findById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
		return vista(solicitud, usuario(), solicitud.estaPendiente());
	}

	/** Sprint 6, tanda 2: cuántas solicitudes esperan aprobación (el resumen diario las informa; lo lee sistema.panel). */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','SISTEMA_PANEL')")
	@Transactional(readOnly = true)
	public long contarPendientes() {
		return solicitudes.countByEstado(EstadoSolicitud.PENDIENTE);
	}

	/** Aprueba una solicitud que no pide llamadas. */
	public void aprobar(Long id, String comentario) {
		aprobar(id, comentario, false, List.of());
	}

	/**
	 * Aprueba: {@code hablo} y {@code telefonos} son la confirmación de las llamadas que pide el manejador (A2): un
	 * número por cada familia, que debe ser un celular registrado de un apoderado de esa familia.
	 */
	public void aprobar(Long id, String comentario, boolean hablo, List<String> telefonos) {
		resolverEnSuRuta(id, () -> aprobarEnTransaccion(id, comentario, hablo, telefonos));
	}

	private void aprobarEnTransaccion(Long id, String comentario, boolean hablo, List<String> telefonos) {
		SolicitudCambio solicitud = pendiente(id);
		String usuario = usuario();
		exigirOtraPersona(solicitud, usuario, "aprobar");
		ManejadorSolicitud manejador = manejadorDe(solicitud);
		String llamadas = confirmarLlamadas(manejador, solicitud, hablo, telefonos);
		firma.firmar(ClaveFirma.solicitud(id, EstadoSolicitud.APROBADA));
		solicitud.aprobar(usuario, comentario, ahora());
		solicitudes.saveAndFlush(solicitud);
		manejador.aplicar(solicitud, usuario, comentario);
		auditoria.registrar(AccionAuditoria.SOLICITUD_APROBADA, "solicitud_cambio", id.toString(),
				EstadoSolicitud.PENDIENTE.name(), EstadoSolicitud.APROBADA.name(), solicitud.getTipo().etiqueta() + ": "
						+ solicitud.getResumen() + ". Pedida por " + solicitud.getSolicitadoPor() + "."
						+ (llamadas == null ? "" : " " + llamadas));
	}

	private static String confirmarLlamadas(ManejadorSolicitud manejador, SolicitudCambio solicitud, boolean hablo,
			List<String> telefonos) {
		List<String> llamadas = manejador.llamadas(solicitud);
		if (llamadas.isEmpty()) {
			return null;
		}
		List<String> numeros = telefonos == null ? List.of()
				: telefonos.stream().filter(t -> t != null && !t.isBlank()).toList();
		if (!hablo || numeros.size() != llamadas.size()) {
			throw new ReglaNegocioException(llamadas.size() == 1
					? "Antes de aprobar, llama al apoderado: marca «Hablé con el apoderado» y escribe el número al que llamaste."
					: "Antes de aprobar, llama a ambas familias: marca «Hablé con ambas familias» y escribe los dos números.");
		}
		return manejador.confirmarLlamadas(solicitud, numeros);
	}

	public void rechazar(Long id, String motivo) {
		resolverEnSuRuta(id, () -> rechazarEnTransaccion(id, motivo));
	}

	private void rechazarEnTransaccion(Long id, String motivo) {
		SolicitudCambio solicitud = pendiente(id);
		String usuario = usuario();
		exigirOtraPersona(solicitud, usuario, "rechazar");
		firma.firmar(ClaveFirma.solicitud(id, EstadoSolicitud.RECHAZADA));
		solicitud.rechazar(usuario, motivo, ahora());
		manejadorDe(solicitud).alRechazar(solicitud);
		solicitudes.saveAndFlush(solicitud);
		auditoria.registrar(AccionAuditoria.SOLICITUD_RECHAZADA, "solicitud_cambio", id.toString(),
				EstadoSolicitud.PENDIENTE.name(), EstadoSolicitud.RECHAZADA.name(), solicitud.getTipo().etiqueta() + ": "
						+ solicitud.getResumen() + ". Motivo del rechazo: " + solicitud.getComentario());
	}

	/**
	 * Resuelve en una sola transacción: la de identidad ({@code cc_sistema}) para {@link #TIPOS_DE_IDENTIDAD}; para los
	 * demás, la de la aplicación (si quien llama ya tiene una, se une). Un intento de resolver lo propio
	 * ({@link AutoaprobacionSolicitudException}) NO revierte: su bitácora se confirma y después se relanza.
	 */
	private void resolverEnSuRuta(Long id, Runnable resolucion) {
		TipoSolicitud tipo = transaccion.execute(t -> solicitudes.findById(id).map(SolicitudCambio::getTipo).orElse(null));
		if (tipo != null && TIPOS_DE_IDENTIDAD.contains(tipo)) {
			identidad.como(() -> {
				resolucion.run();
				return null;
			}, List.of(AutoaprobacionSolicitudException.class));
			return;
		}
		AutoaprobacionSolicitudException[] diferida = new AutoaprobacionSolicitudException[1];
		transaccion.executeWithoutResult(t -> {
			try {
				resolucion.run();
			}
			catch (AutoaprobacionSolicitudException e) {
				diferida[0] = e;
			}
		});
		if (diferida[0] != null) {
			throw diferida[0];
		}
	}

	/** El manejador del tipo (y, si el tipo lo comparten varios módulos, el de la entidad de la solicitud). */
	private ManejadorSolicitud manejadorDe(SolicitudCambio solicitud) {
		List<ManejadorSolicitud> delTipo = manejadores.stream().filter(m -> m.tipo() == solicitud.getTipo())
				.filter(m -> m.entidad() == null || m.entidad().equals(solicitud.getEntidad())).toList();
		if (delTipo.size() != 1) {
			throw new IllegalStateException(delTipo.size() + " manejadores para " + solicitud.getTipo() + " de "
					+ solicitud.getEntidad());
		}
		return delTipo.getFirst();
	}

	private SolicitudCambio pendiente(Long id) {
		SolicitudCambio solicitud = solicitudes.bloquear(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
		if (!solicitud.estaPendiente()) {
			throw new ReglaNegocioException("La solicitud ya fue " + solicitud.getEstado().etiqueta().toLowerCase()
					+ " por " + solicitud.getResueltoPor() + ".");
		}
		return solicitud;
	}

	/** Quien la pidió y los involucrados que dice su manejador (la cajera del pago), más quienes prepararon esas cuentas. */
	private Set<String> participantesDe(SolicitudCambio solicitud) {
		Set<String> autores = new LinkedHashSet<>();
		autores.add(solicitud.getSolicitadoPor());
		autores.addAll(manejadorDe(solicitud).involucrados(solicitud));
		return participantes.ampliar(autores);
	}

	private void exigirOtraPersona(SolicitudCambio solicitud, String usuario, String accion) {
		Set<String> involucrados = participantesDe(solicitud);
		if (involucrados.contains(usuario)) {
			auditoria.registrar(AccionAuditoria.AUTOAPROBACION_RECHAZADA, "solicitud_cambio",
					solicitud.getId().toString(), null, solicitud.getTipo().etiqueta() + ": " + solicitud.getResumen(),
					"Intentó " + accion + " una solicitud que pidió o en la que participó (o una cuenta que preparó). Se rechazó.");
			throw new AutoaprobacionSolicitudException("No puedes " + accion + " una solicitud que tú pediste o en la que "
					+ "participaste (por ejemplo, el pago que cobraste), ni la de una cuenta que creaste o a la que le "
					+ "restableciste la clave: debe hacerlo otra persona.");
		}
	}

	private SolicitudVista vista(SolicitudCambio s, String usuario, boolean conDetalle) {
		boolean puede = s.estaPendiente() && !participantesDe(s).contains(usuario);
		ManejadorSolicitud manejador = conDetalle ? manejadorDe(s) : null;
		return new SolicitudVista(s.getId(), s.getTipo().name(), s.getTipo().etiqueta(), s.getResumen(), s.getMotivo(),
				s.getEstado().name(), s.getEstado().etiqueta(), s.getEstado().variante(), s.getSolicitadoPor(),
				s.getCreadoEn(), s.getResueltoPor(), s.getResueltoEn(), s.getComentario(), puede,
				manejador == null ? List.of() : manejador.detalle(s), manejador == null ? null : manejador.advertencia(s),
				manejador != null && manejador.exigeComentario(s), manejador == null ? "Rechazar" : manejador.accionRechazo(),
				manejador == null ? List.of() : manejador.llamadas(s));
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}

	private static String usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? "sistema" : autenticacion.getName();
	}
}
