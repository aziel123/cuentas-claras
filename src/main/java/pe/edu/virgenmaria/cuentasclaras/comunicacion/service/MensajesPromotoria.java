package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AvisosPromotoriaListos;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.ResumenDiarioListo;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.ConfiguracionColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ControlParticipantes;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mensajes a Promotoría del sprint 6 (tanda 2), creados en la MISMA transacción de {@code sistema.panel}:
 * <ul>
 *   <li>{@link ResumenDiarioListo}: el resumen a cada persona de Promotoría activa (WhatsApp; si falla, el despacho saca su
 *       respaldo por correo) y, si el DBA lo configuró, al correo externo del contador. Si nadie de Promotoría tiene a
 *       dónde recibirlo, la excepción revierte también la foto: sin mensaje no hay foto.</li>
 *   <li>{@link AvisosPromotoriaListos}: cada alerta difundible UNA sola vez a cada persona de Promotoría activa (y de
 *       Dirección, la anulación de pago por aprobar), nunca a quien la pidió ni a la cajera del pago (ni a quienes
 *       prepararon sus cuentas: {@link ControlParticipantes}). Texto FIJO del tipo y un monto, una hora o la fecha: nunca
 *       la explicación de la cajera ni un nombre (hallazgo 2). Tope diario por persona SOLO para las ATENCIÓN (S6-B2,
 *       QA-S6-7: las CRÍTICAS no cuentan ni se retienen); lo retenido en una pasada sale en un solo «hoy hay N alertas
 *       más» de ESA pasada. Domingos y feriados no sale nada (sale el siguiente día de mensajes a las 07:00), salvo un
 *       aviso inmediato (S6-M3: el cierre con diferencia recién confirmado).</li>
 * </ul>
 * Correcciones del sprint 6: el correo externo del resumen es el del colegio de la foto ({@code configuracion_colegio},
 * QA-S6-6), y el resumen y las alertas van solo a cuentas del PERSONAL ({@code apoderado_id} vacío, S6-A1).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class MensajesPromotoria {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final CreadorMensajes creador;

	private final UsuarioRepository usuarios;

	private final MensajeRepository mensajes;

	private final ConfiguracionColegioRepository configuracion;

	private final ControlParticipantes participantes;

	private final CalendarioHabil calendario;

	private final AuditoriaService auditoria;

	private final int topeDiario;

	private final Clock reloj;

	/** Prefijos de clave de las alertas que cuentan para el tope (las ATENCIÓN que salen al celular). */
	private static final List<String> CLAVES_CON_TOPE = Arrays.stream(TipoAviso.values()).filter(t -> !t.esCritico())
			.map(t -> "ALERTA:" + t.name() + ":").toList();

	private static final DateTimeFormatter PASADA = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

	public MensajesPromotoria(CreadorMensajes creador, UsuarioRepository usuarios, MensajeRepository mensajes,
			ConfiguracionColegioRepository configuracion, ControlParticipantes participantes, CalendarioHabil calendario,
			AuditoriaService auditoria, @Value("${cuentasclaras.panel.avisos-tope-diario:10}") int topeDiario,
			Clock reloj) {
		this.creador = creador;
		this.usuarios = usuarios;
		this.mensajes = mensajes;
		this.configuracion = configuracion;
		this.participantes = participantes;
		this.calendario = calendario;
		this.auditoria = auditoria;
		this.topeDiario = topeDiario;
		this.reloj = reloj;
	}

	/** S6-A1: solo cuentas del personal (una cuenta enlazada a un apoderado no recibe lo de Promotoría). */
	private List<Usuario> personalActivoConRol(Rol rol) {
		return usuarios.activosConRol(rol).stream().filter(u -> u.getApoderadoId() == null).toList();
	}

	@EventListener
	public void alResumenDiario(ResumenDiarioListo evento) {
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.RESUMEN_DIARIO,
				PlantillaMensaje.RESUMEN_DIARIO, evento.parametros(), "resumen_diario", evento.resumenId());
		int creados = 0;
		for (Usuario promotor : personalActivoConRol(Rol.PROMOTOR)) {
			creados += creador.paraUsuario(promotor, contenido, null).isPresent() ? 1 : 0;
		}
		if (creados == 0) {
			throw new IllegalStateException("Nadie de Promotoría tiene celular ni correo: el resumen no tiene a dónde salir");
		}
		// QA-S6-6: el correo del contador de ESTE colegio (configuracion_colegio), nunca uno de toda la instalación.
		configuracion.findByColegioIdAndClave(evento.colegioId(), ConfiguracionColegio.RESUMEN_CORREO_EXTERNO)
				.map(ConfiguracionColegio::getValor).filter(c -> c.contains("@"))
				.ifPresent(c -> creador.externo(c.strip(), contenido));
	}

	@EventListener
	public void alAvisos(AvisosPromotoriaListos evento) {
		LocalDate fecha = evento.fecha();
		if ((!evento.inmediato() && !calendario.admiteMensajes(fecha)) || evento.avisos().isEmpty()) {
			return;
		}
		List<Usuario> promotores = personalActivoConRol(Rol.PROMOTOR);
		List<Usuario> directores = personalActivoConRol(Rol.DIRECTOR);
		Map<Long, Integer> hoy = new HashMap<>();
		Map<Long, Integer> retenidos = new LinkedHashMap<>();
		Map<Long, Usuario> porId = new HashMap<>();
		int creados = 0;
		List<String> avisados = new ArrayList<>();
		for (Aviso aviso : evento.avisos()) {
			String claveBase = "ALERTA:" + aviso.tipo().name() + ":" + aviso.referencia();
			Set<String> excluidos = aviso.excluidos().isEmpty() ? Set.of() : participantes.ampliar(aviso.excluidos());
			int aEste = 0;
			for (Usuario destinatario : destinatarios(aviso.tipo(), promotores, directores)) {
				if (excluidos.contains(destinatario.getNombreUsuario())
						|| creador.yaExisteParaUsuario(claveBase, destinatario.getId())) {
					continue;
				}
				porId.put(destinatario.getId(), destinatario);
				boolean conTope = !aviso.tipo().esCritico();
				int enviadosHoy = hoy.computeIfAbsent(destinatario.getId(), id -> atencionesDeHoy(id, fecha));
				if (conTope && enviadosHoy >= topeDiario) {
					retenidos.merge(destinatario.getId(), 1, Integer::sum);
					continue;
				}
				CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.ALERTA_PROMOTORIA,
						PlantillaMensaje.ALERTA_PROMOTORIA, List.of(aviso.tipo().texto(),
								aviso.dato() != null ? aviso.dato() : fecha.format(FECHA)), "aviso", null);
				if (creador.paraUsuarioConClave(destinatario, contenido, claveBase).isPresent()) {
					hoy.put(destinatario.getId(), enviadosHoy + (conTope ? 1 : 0));
					creados++;
					aEste++;
				}
			}
			if (aEste > 0) {
				avisados.add(aviso.tipo().texto() + " (" + aEste + ")");
			}
		}
		String pasada = LocalDateTime.now(reloj).format(PASADA);
		for (Map.Entry<Long, Integer> retenido : retenidos.entrySet()) {
			// S6-B2: un «y N alertas más» por persona y PASADA (antes, uno por día: después del primero, lo retenido no se
			// avisaba). No cuenta para el tope (es otra plantilla).
			CreadorMensajes.Contenido mas = new CreadorMensajes.Contenido(TipoMensaje.ALERTA_PROMOTORIA,
					PlantillaMensaje.ALERTA_MAS, List.of(String.valueOf(retenido.getValue())), "aviso", null);
			String claveMas = "ALERTA_MAS:" + pasada;
			if (!creador.yaExisteParaUsuario(claveMas, retenido.getKey())
					&& creador.paraUsuarioConClave(porId.get(retenido.getKey()), mas, claveMas).isPresent()) {
				creados++;
			}
		}
		if (creados > 0) {
			auditoria.registrar(AccionAuditoria.AVISOS_PROMOTORIA_ENVIADOS, "mensaje", null, null, creados + " mensaje(s)",
					"Alertas avisadas al celular: " + (avisados.isEmpty() ? "—" : String.join("; ", avisados))
							+ (retenidos.isEmpty() ? "" : ". Retenidas por el tope diario de " + topeDiario + ": "
									+ retenidos.values().stream().mapToInt(Integer::intValue).sum()) + ".");
		}
	}

	/** S6-B2: las ATENCIÓN avisadas hoy a esa persona (las CRÍTICAS y los respaldos no cuentan para el tope). */
	private int atencionesDeHoy(Long usuarioId, LocalDate fecha) {
		long total = 0;
		for (String prefijo : CLAVES_CON_TOPE) {
			total += mensajes
					.countByTipoAndPlantillaAndUsuarioIdAndRespaldoDeIdIsNullAndCreadoEnGreaterThanEqualAndClaveStartingWith(
							TipoMensaje.ALERTA_PROMOTORIA, PlantillaMensaje.ALERTA_PROMOTORIA, usuarioId, fecha.atStartOfDay(),
							prefijo);
		}
		return (int) total;
	}

	/** Promotoría; la anulación de pago por aprobar, también Dirección (decisión 71: también aprueba). */
	private static List<Usuario> destinatarios(TipoAviso tipo, List<Usuario> promotores, List<Usuario> directores) {
		if (tipo != TipoAviso.ANULACION_PAGO_PENDIENTE) {
			return promotores;
		}
		Map<Long, Usuario> todos = new LinkedHashMap<>();
		promotores.forEach(p -> todos.put(p.getId(), p));
		directores.forEach(d -> todos.putIfAbsent(d.getId(), d));
		return new ArrayList<>(todos.values());
	}
}
