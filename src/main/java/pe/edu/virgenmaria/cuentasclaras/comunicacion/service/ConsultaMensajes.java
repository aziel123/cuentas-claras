package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.SesionApoderado;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.config.PropiedadesMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.dto.AvisosDelDia;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.dto.BandejaMensajes;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.dto.EntregaResumen;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.dto.MensajeVista;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.dto.UltimoAviso;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CanalMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.DestinatarioTipo;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.EstadoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ProveedorMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Consulta de mensajes (sprint 5): la bandeja de envíos del personal y el historial de la familia en el portal. Nadie
 * edita el destino ni el texto; Administración solo adelanta el reintento de un PENDIENTE.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ConsultaMensajes {

	private final MensajeRepository mensajes;

	private final SesionApoderado sesion;

	private final PropiedadesMensajeria propiedades;

	private final Clock reloj;

	private final UsuarioRepository usuarios;

	public ConsultaMensajes(MensajeRepository mensajes, SesionApoderado sesion, PropiedadesMensajeria propiedades,
			Clock reloj, UsuarioRepository usuarios) {
		this.mensajes = mensajes;
		this.sesion = sesion;
		this.propiedades = propiedades;
		this.reloj = reloj;
		this.usuarios = usuarios;
	}

	private static final Set<EstadoMensaje> SALIERON = EnumSet.of(EstadoMensaje.ENVIADO, EstadoMensaje.ENTREGADO,
			EstadoMensaje.LEIDO);

	private static final List<TipoMensaje> FINANCIEROS = Arrays.stream(TipoMensaje.values()).filter(TipoMensaje::financiero)
			.toList();

	/**
	 * Sprint 6, tanda 2: cómo salió el resumen diario de una foto a Promotoría (para el panel y para la alerta «el
	 * resumen de hoy no salió»). Cuenta a cada persona de Promotoría activa a la que le salió por algún canal.
	 */
	@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
	@Transactional(readOnly = true)
	public EntregaResumen entregaDelResumen(Long resumenId) {
		List<Mensaje> delResumen = mensajes.findByTipoAndEntidadAndEntidadIdOrderByIdAsc(TipoMensaje.RESUMEN_DIARIO,
				"resumen_diario", resumenId).stream().filter(m -> m.getDestinatarioTipo() == DestinatarioTipo.USUARIO)
				.toList();
		List<Usuario> promotores = usuarios.activosConRol(Rol.PROMOTOR);
		int salieron = (int) promotores.stream().filter(p -> delResumen.stream()
				.anyMatch(m -> p.getId().equals(m.getUsuarioId()) && SALIERON.contains(m.getEstado()))).count();
		LocalDateTime enviadoEn = delResumen.stream().map(Mensaje::getEnviadoEn).filter(java.util.Objects::nonNull)
				.min(LocalDateTime::compareTo).orElse(null);
		String estado;
		if (delResumen.isEmpty()) {
			estado = "Aún no sale";
		}
		else if (!promotores.isEmpty() && salieron >= promotores.size()) {
			estado = delResumen.stream().anyMatch(m -> m.getEstado() == EstadoMensaje.ENTREGADO
					|| m.getEstado() == EstadoMensaje.LEIDO) ? "Entregado" : "Enviado";
		}
		else if (delResumen.stream().anyMatch(m -> m.getEstado() == EstadoMensaje.PENDIENTE)) {
			estado = "Pendiente de envío";
		}
		else {
			estado = "No salió a todos";
		}
		return new EntregaResumen(promotores.size(), salieron, enviadoEn, estado);
	}

	/**
	 * Sprint 6, tanda 2: avisos financieros a las familias (pago, anulación, descuento) creados en el día y, de ellos, los
	 * que ya salieron al corte del resumen.
	 */
	@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
	@Transactional(readOnly = true)
	public AvisosDelDia avisosFinancieros(LocalDate fecha) {
		LocalDateTime desde = fecha.atStartOfDay();
		LocalDateTime hasta = fecha.plusDays(1).atStartOfDay();
		return new AvisosDelDia(mensajes.countByTipoInAndCreadoEnGreaterThanEqualAndCreadoEnLessThan(FINANCIEROS, desde,
				hasta), mensajes.countByTipoInAndCreadoEnGreaterThanEqualAndCreadoEnLessThanAndEstadoIn(FINANCIEROS, desde,
						hasta, SALIERON));
	}

	/** Avisos de cobranza que cuentan como «último aviso entregado» en la lista de familias morosas (sprint 6). */
	private static final List<TipoMensaje> AVISOS_DE_COBRANZA = List.of(TipoMensaje.RECORDATORIO_VENCIMIENTO,
			TipoMensaje.CUOTA_VENCIDA);

	/**
	 * Sprint 6: el último recordatorio o aviso de cuota vencida ENTREGADO a cada familia (sin destino ni texto). Las
	 * familias sin ninguno no aparecen en el mapa.
	 */
	@Transactional(readOnly = true)
	public Map<Long, UltimoAviso> ultimosAvisosDeCobranza(Collection<Long> familias) {
		Map<Long, UltimoAviso> ultimos = new HashMap<>();
		if (familias == null || familias.isEmpty()) {
			return ultimos;
		}
		for (Object[] fila : mensajes.ultimosEntregados(familias, AVISOS_DE_COBRANZA)) {
			UltimoAviso aviso = new UltimoAviso(((TipoMensaje) fila[1]).etiqueta(), (LocalDateTime) fila[2]);
			ultimos.merge((Long) fila[0], aviso, (a, b) -> a.entregadoEn().isAfter(b.entregadoEn()) ? a : b);
		}
		return ultimos;
	}

	/** Bandeja del personal: los fallidos, los pendientes de más de {@code alerta-pendiente-minutos} y los de hoy. */
	@Transactional(readOnly = true)
	public BandejaMensajes bandeja() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		boolean reintenta = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
				.anyMatch(a -> "ROLE_ADMINISTRACION".equals(a.getAuthority()));
		LocalDateTime limite = ahora.minusMinutes(propiedades.alertaPendienteMinutos());
		return new BandejaMensajes(
				mensajes.findTop100ByEstadoOrderByIdDesc(EstadoMensaje.FALLIDO).stream().map(m -> vista(m, false)).toList(),
				mensajes.findTop100ByEstadoOrderByIdDesc(EstadoMensaje.PENDIENTE).stream()
						.filter(m -> m.getCreadoEn().isBefore(limite)).map(m -> vista(m, reintenta)).toList(),
				mensajes.findTop200ByCreadoEnGreaterThanEqualOrderByIdDesc(LocalDate.now(reloj).atStartOfDay()).stream()
						.map(m -> vista(m, false)).toList(),
				reintenta);
	}

	/** Administración adelanta el siguiente intento de un PENDIENTE (nunca edita el destino ni el texto). */
	@PreAuthorize("hasRole('ADMINISTRACION')")
	@Transactional
	public void reintentar(Long id) {
		Mensaje mensaje = mensajes.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Mensaje no encontrado"));
		if (mensaje.getEstado() != EstadoMensaje.PENDIENTE) {
			throw new ReglaNegocioException("Solo se adelanta un mensaje pendiente: este ya está "
					+ mensaje.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT) + ".");
		}
		mensaje.adelantar(LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
		mensajes.saveAndFlush(mensaje);
	}

	/** Historial de la familia del apoderado en sesión (la familia sale de su cuenta, nunca de la URL). */
	@PreAuthorize("hasRole('APODERADO')")
	@Transactional(readOnly = true)
	public List<MensajeVista> historialDeMiFamilia() {
		Long familia = sesion.familiaId();
		LocalDateTime desde = LocalDateTime.now(reloj).minusMonths(propiedades.historialFamiliaMeses());
		return mensajes.findByFamiliaIdAndCreadoEnGreaterThanEqualOrderByIdDesc(familia, desde).stream()
				.map(m -> vista(m, false)).toList();
	}

	static MensajeVista vista(Mensaje m, boolean puedeReintentar) {
		boolean simulado = m.getProveedor() == ProveedorMensajeria.SIMULADO;
		String texto = m.getTipo() == TipoMensaje.ACTIVACION_CUENTA ? "Enlace de acceso enviado"
				: m.getPlantilla().parametros() == m.parametrosLista().size()
						? m.getPlantilla().componer(m.parametrosLista()) : m.getTipo().etiqueta();
		String destino = m.getCanal() == CanalMensaje.WHATSAPP ? Enmascarar.telefono(m.getDestino())
				: Enmascarar.correo(m.getDestino());
		String estado = simulado ? "SIMULADO · no se envió" : m.getEstado() == EstadoMensaje.FALLIDO
				&& m.getCanal() == CanalMensaje.WHATSAPP ? "No se pudo enviar: le escribimos al correo"
						: m.getEstado().etiqueta();
		String variante = switch (m.getEstado()) {
			case FALLIDO -> "peligro";
			case PENDIENTE -> "alerta";
			case ENVIADO -> simulado ? "alerta" : "info";
			case ENTREGADO, LEIDO -> "exito";
		};
		return new MensajeVista(m.getId(), m.getCreadoEn(), m.getTipo().etiqueta(), m.getCanal().etiqueta(), destino,
				estado, variante, m.getIntentos(), texto, m.getUltimoError(), simulado,
				puedeReintentar && m.getEstado() == EstadoMensaje.PENDIENTE);
	}
}
