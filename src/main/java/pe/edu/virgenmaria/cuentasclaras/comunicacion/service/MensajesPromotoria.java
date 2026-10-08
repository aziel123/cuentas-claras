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
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionBd;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.ConfiguracionBdRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ControlParticipantes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 *       la explicación de la cajera ni un nombre (hallazgo 2). Tope diario por persona: a partir del siguiente, un solo
 *       «hoy hay N alertas más». Domingos y feriados no sale nada: sale el siguiente día de mensajes a las 07:00.</li>
 * </ul>
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class MensajesPromotoria {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final CreadorMensajes creador;

	private final UsuarioRepository usuarios;

	private final MensajeRepository mensajes;

	private final ConfiguracionBdRepository configuracion;

	private final ControlParticipantes participantes;

	private final CalendarioHabil calendario;

	private final AuditoriaService auditoria;

	private final int topeDiario;

	public MensajesPromotoria(CreadorMensajes creador, UsuarioRepository usuarios, MensajeRepository mensajes,
			ConfiguracionBdRepository configuracion, ControlParticipantes participantes, CalendarioHabil calendario,
			AuditoriaService auditoria, @Value("${cuentasclaras.panel.avisos-tope-diario:10}") int topeDiario) {
		this.creador = creador;
		this.usuarios = usuarios;
		this.mensajes = mensajes;
		this.configuracion = configuracion;
		this.participantes = participantes;
		this.calendario = calendario;
		this.auditoria = auditoria;
		this.topeDiario = topeDiario;
	}

	@EventListener
	public void alResumenDiario(ResumenDiarioListo evento) {
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.RESUMEN_DIARIO,
				PlantillaMensaje.RESUMEN_DIARIO, evento.parametros(), "resumen_diario", evento.resumenId());
		int creados = 0;
		for (Usuario promotor : usuarios.activosConRol(Rol.PROMOTOR)) {
			creados += creador.paraUsuario(promotor, contenido, null).isPresent() ? 1 : 0;
		}
		if (creados == 0) {
			throw new IllegalStateException("Nadie de Promotoría tiene celular ni correo: el resumen no tiene a dónde salir");
		}
		configuracion.findById(ConfiguracionBd.RESUMEN_CORREO_EXTERNO).map(ConfiguracionBd::getValor)
				.filter(c -> c.contains("@")).ifPresent(c -> creador.externo(c.strip(), contenido));
	}

	@EventListener
	public void alAvisos(AvisosPromotoriaListos evento) {
		LocalDate fecha = evento.fecha();
		if (!calendario.admiteMensajes(fecha) || evento.avisos().isEmpty()) {
			return;
		}
		List<Usuario> promotores = usuarios.activosConRol(Rol.PROMOTOR);
		List<Usuario> directores = usuarios.activosConRol(Rol.DIRECTOR);
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
				int enviadosHoy = hoy.computeIfAbsent(destinatario.getId(), id -> (int) mensajes
						.countByTipoAndPlantillaAndUsuarioIdAndRespaldoDeIdIsNullAndCreadoEnGreaterThanEqual(
								TipoMensaje.ALERTA_PROMOTORIA, PlantillaMensaje.ALERTA_PROMOTORIA, id, fecha.atStartOfDay()));
				if (enviadosHoy >= topeDiario) {
					retenidos.merge(destinatario.getId(), 1, Integer::sum);
					continue;
				}
				CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.ALERTA_PROMOTORIA,
						PlantillaMensaje.ALERTA_PROMOTORIA, List.of(aviso.tipo().texto(),
								aviso.dato() != null ? aviso.dato() : fecha.format(FECHA)), "aviso", null);
				if (creador.paraUsuarioConClave(destinatario, contenido, claveBase).isPresent()) {
					hoy.put(destinatario.getId(), enviadosHoy + 1);
					creados++;
					aEste++;
				}
			}
			if (aEste > 0) {
				avisados.add(aviso.tipo().texto() + " (" + aEste + ")");
			}
		}
		for (Map.Entry<Long, Integer> retenido : retenidos.entrySet()) {
			// Un solo «y N alertas más» por persona y día (P19: que el canal no se vuelva ruido).
			CreadorMensajes.Contenido mas = new CreadorMensajes.Contenido(TipoMensaje.ALERTA_PROMOTORIA,
					PlantillaMensaje.ALERTA_MAS, List.of(String.valueOf(retenido.getValue())), "aviso", null);
			String claveMas = "ALERTA_MAS:" + fecha;
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
