package pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso;

import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.config.PropiedadesMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CanalMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.DestinatarioTipo;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.EstadoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ProveedorMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.CreadorMensajes;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ProveedorCorreo;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ProveedorWhatsApp;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PropositoEnlace;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnlacesActivacion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Envía el outbox de mensajes (sprint 5), cada {@code envio-cada} (30 s), en cada colegio y como
 * {@code sistema.mensajeria}: los PENDIENTE cuyo intento ya toca, de a {@value #LOTE}, cada uno en su PROPIA transacción.
 * <ul>
 *   <li>Activación: genera el enlace AQUÍ ({@link EnlacesActivacion#generarParaMensaje}, en la misma transacción que el
 *       envío). Si el proveedor no lo acepta, TODO se revierte (no queda ningún enlace) y el fallo se registra en otra
 *       transacción: el siguiente intento genera otro token.</li>
 *   <li>Error de red o 5xx: un intento más, con espera creciente (1, 2, 4… hasta 60 min). A los {@code intentos-maximos},
 *       FALLIDO.</li>
 *   <li>Error definitivo o intentos agotados: FALLIDO, resaltado en la bitácora y, si era un WhatsApp, su respaldo por
 *       correo al mismo destinatario.</li>
 *   <li>Sin el proveedor de ese canal (conector apagado), el mensaje queda PENDIENTE y Promotoría ve la alerta.</li>
 * </ul>
 * Es la ÚNICA clase que genera enlaces de activación (regla ArchUnit).
 */
@Component
public class DespachoMensajes {

	private static final Logger LOG = LoggerFactory.getLogger(DespachoMensajes.class);

	static final int LOTE = 20;

	static final DateTimeFormatter VENCE = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm");

	private final MensajeRepository mensajes;

	private final ObjectProvider<ProveedorWhatsApp> whatsapp;

	private final ObjectProvider<ProveedorCorreo> correo;

	private final EnlacesActivacion enlaces;

	private final UsuarioRepository usuarios;

	private final ApoderadoRepository apoderados;

	private final CreadorMensajes creador;

	private final AuditoriaService auditoria;

	private final PropiedadesMensajeria propiedades;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	private final CalendarioHabil calendario;

	public DespachoMensajes(MensajeRepository mensajes, ObjectProvider<ProveedorWhatsApp> whatsapp,
			ObjectProvider<ProveedorCorreo> correo, EnlacesActivacion enlaces, UsuarioRepository usuarios,
			ApoderadoRepository apoderados, CreadorMensajes creador, AuditoriaService auditoria,
			PropiedadesMensajeria propiedades, RecorridoColegios colegios, PlatformTransactionManager transacciones,
			Clock reloj, CalendarioHabil calendario) {
		this.calendario = calendario;
		this.mensajes = mensajes;
		this.whatsapp = whatsapp;
		this.correo = correo;
		this.enlaces = enlaces;
		this.usuarios = usuarios;
		this.apoderados = apoderados;
		this.creador = creador;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	@Scheduled(fixedDelayString = "${cuentasclaras.mensajeria.envio-cada:30s}", initialDelayString = "20s")
	public void despachar() {
		for (Long colegio : colegios.activos()) {
			try {
				despacharColegio(colegio);
			}
			catch (RuntimeException e) {
				LOG.error("El envío de mensajes falló en el colegio {}: {}", colegio, e.getClass().getSimpleName());
			}
		}
	}

	/** Una pasada del outbox en un colegio (la usan la tarea programada y las pruebas). @return cuántos salieron */
	public int despacharColegio(Long colegio) {
		return EjecucionComoSistema.como(ActorSistema.MENSAJERIA, colegio, () -> {
			LocalDateTime ahora = ahora();
			List<Long> ids = transaccion.execute(t -> mensajes.porEnviar(ahora, PageRequest.of(0, LOTE)));
			int enviados = 0;
			int simulados = 0;
			for (Long id : ids == null ? List.<Long>of() : ids) {
				ProveedorMensajeria quien = procesar(id);
				if (quien != null) {
					enviados++;
					simulados += quien == ProveedorMensajeria.SIMULADO ? 1 : 0;
				}
			}
			if (simulados > 0) {
				int total = simulados;
				transaccion.executeWithoutResult(t -> auditoria.registrar(AccionAuditoria.MENSAJERIA_SIMULADA_USADA,
						"mensaje", null, null, total + " simulados", "Se marcaron " + total + " mensajes como enviados con la "
								+ "mensajería SIMULADA: nadie los recibió (solo dev, test o piloto)."));
			}
			return enviados;
		});
	}

	/** Lo que se le entrega al proveedor (con el enlace recién generado, si es una activación). */
	private record Envio(List<String> parametros, String sufijoBoton, String cuerpoCorreo) {
	}

	/** El resultado de un intento: quién lo aceptó, o el error para registrar en otra transacción. */
	private record Intento(ProveedorMensajeria aceptadoPor, ResultadoEnvio fallo) {
	}

	/**
	 * Envía un mensaje en su propia transacción (como {@code sistema.mensajeria}, ya fijado por quien llama).
	 *
	 * @return el proveedor que lo aceptó, o {@code null} si no salió
	 */
	ProveedorMensajeria procesar(Long id) {
		Intento intento;
		try {
			intento = transaccion.execute(estado -> {
				Mensaje mensaje = mensajes.bloquear(id).orElse(null);
				if (mensaje == null || mensaje.getEstado() != EstadoMensaje.PENDIENTE) {
					return null;
				}
				boolean conProveedor = mensaje.getCanal() == CanalMensaje.WHATSAPP ? whatsapp.getIfAvailable() != null
						: correo.getIfAvailable() != null;
				if (!conProveedor) {
					return null; // conector apagado: queda PENDIENTE (alerta «pendientes»)
				}
				if (esRecordatorio(mensaje.getTipo()) && !enVentana(ahora())) {
					// Tanda 3 (INDECOPI): los recordatorios salen de lunes a sábado de 08:00 a 20:00, nunca en feriado.
					mensaje.posponerHasta(siguienteVentana(ahora()));
					mensajes.saveAndFlush(mensaje);
					return null;
				}
				Envio envio = preparar(mensaje);
				ResultadoEnvio resultado = enviar(mensaje, envio);
				if (resultado.aceptado()) {
					mensaje.marcarEnviado(resultado.proveedor(), resultado.idProveedor(), ahora());
					mensajes.saveAndFlush(mensaje);
					if (mensaje.getTipo() == TipoMensaje.ACTIVACION_CUENTA) {
						auditoria.registrar(AccionAuditoria.ENLACE_ACTIVACION_ENVIADO, "usuario",
								String.valueOf(mensaje.getEntidadId()), null, mensaje.getCanal().etiqueta(),
								"El enlace de un solo uso salió directo a su titular por " + mensaje.getCanal().etiqueta()
										+ " (mensaje " + mensaje.getId() + "). Nadie más lo ve.");
					}
					return new Intento(resultado.proveedor(), null);
				}
				// Nada de este intento queda (tampoco el enlace generado): el fallo se registra aparte.
				estado.setRollbackOnly();
				return new Intento(null, resultado);
			});
		}
		catch (RuntimeException e) {
			LOG.error("No se pudo enviar el mensaje {}: {}", id, e.getClass().getSimpleName());
			intento = new Intento(null, ResultadoEnvio.reintentable("Falló el envío (" + e.getClass().getSimpleName() + ")."));
		}
		if (intento == null) {
			return null;
		}
		if (intento.fallo() != null) {
			ResultadoEnvio fallo = intento.fallo();
			try {
				transaccion.executeWithoutResult(t -> registrarFallo(id, fallo));
			}
			catch (RuntimeException e) {
				LOG.error("No se pudo registrar el fallo del mensaje {}: {}", id, e.getClass().getSimpleName());
			}
			return null;
		}
		return intento.aceptadoPor();
	}

	private Envio preparar(Mensaje mensaje) {
		if (mensaje.getTipo() != TipoMensaje.ACTIVACION_CUENTA) {
			List<String> parametros = mensaje.parametrosLista();
			String cuerpo = mensaje.getPlantilla().componer(parametros)
					+ (mensaje.getDestinatarioTipo() == DestinatarioTipo.APODERADO
							? "\n\nVer en el portal: " + propiedades.urlPublica() + "/familia" : "");
			return new Envio(parametros, null, cuerpo);
		}
		PropositoEnlace proposito = mensaje.getDestinatarioTipo() == DestinatarioTipo.USUARIO ? PropositoEnlace.PERSONAL
				: PropositoEnlace.APODERADO;
		LocalDateTime vence = ahora().plus(propiedades.vigenciaEnlace());
		String ruta = enlaces.generarParaMensaje(ContextoColegio.actual(), mensaje.getId(), mensaje.getEntidadId(),
				proposito, propiedades.vigenciaEnlace());
		List<String> parametros = List.of(vence.format(VENCE));
		String cuerpo = PlantillaMensaje.ACTIVACION.componer(parametros) + "\n\n" + propiedades.urlPublica() + ruta;
		return new Envio(parametros, ruta, cuerpo);
	}

	private ResultadoEnvio enviar(Mensaje mensaje, Envio envio) {
		try {
			ResultadoEnvio resultado = mensaje.getCanal() == CanalMensaje.WHATSAPP
					? whatsapp.getObject().enviar(mensaje.getPlantilla(), mensaje.getDestino(), envio.parametros(),
							envio.sufijoBoton())
					: correo.getObject().enviar(mensaje.getDestino(), mensaje.getPlantilla().asunto(), envio.cuerpoCorreo());
			return resultado == null ? ResultadoEnvio.reintentable("El proveedor no respondió.") : resultado;
		}
		catch (RuntimeException e) {
			LOG.warn("El proveedor de {} falló: {}", mensaje.getCanal(), e.getClass().getSimpleName());
			return ResultadoEnvio.reintentable("El proveedor falló (" + e.getClass().getSimpleName() + ").");
		}
	}

	private void registrarFallo(Long id, ResultadoEnvio fallo) {
		Mensaje mensaje = mensajes.bloquear(id).orElse(null);
		if (mensaje == null || mensaje.getEstado() != EstadoMensaje.PENDIENTE) {
			return;
		}
		LocalDateTime ahora = ahora();
		int intento = mensaje.getIntentos() + 1;
		if (fallo.tipo() == ResultadoEnvio.Tipo.ERROR_REINTENTABLE && intento < propiedades.intentosMaximos()) {
			mensaje.reintentarEn(fallo.error(), ahora.plus(propiedades.esperaTrasIntentos(intento)));
			mensajes.saveAndFlush(mensaje);
			return;
		}
		mensaje.fallar(fallo.error(), true);
		mensajes.saveAndFlush(mensaje);
		alFallar(mensaje);
	}

	/**
	 * Un mensaje quedó FALLIDO (por el despacho o por un aviso del proveedor): se resalta en la bitácora y, si era un
	 * WhatsApp, sale su respaldo por correo al mismo destinatario. Lo usa también el aviso de WhatsApp.
	 */
	void alFallar(Mensaje mensaje) {
		auditoria.registrar(AccionAuditoria.MENSAJE_FALLIDO, "mensaje", mensaje.getId().toString(), null,
				EstadoMensaje.FALLIDO.name(), mensaje.getTipo().etiqueta() + " por " + mensaje.getCanal().etiqueta()
						+ " no se pudo enviar tras " + mensaje.getIntentos() + " intento(s): " + mensaje.getUltimoError());
		if (mensaje.getCanal() != CanalMensaje.WHATSAPP || mensaje.getTipo() == TipoMensaje.CONTACTO_CAMBIADO
				|| mensaje.getDestinatarioTipo() == DestinatarioTipo.EXTERNO) {
			return;
		}
		Apoderado apoderado = mensaje.getApoderadoId() == null ? null
				: apoderados.findById(mensaje.getApoderadoId()).orElse(null);
		Usuario usuario = mensaje.getUsuarioId() == null ? null : usuarios.findById(mensaje.getUsuarioId()).orElse(null);
		creador.respaldo(mensaje, apoderado, usuario);
	}

	static final java.time.LocalTime VENTANA_DESDE = java.time.LocalTime.of(8, 0);

	static final java.time.LocalTime VENTANA_HASTA = java.time.LocalTime.of(20, 0);

	static boolean esRecordatorio(TipoMensaje tipo) {
		return tipo == TipoMensaje.RECORDATORIO_VENCIMIENTO || tipo == TipoMensaje.CUOTA_VENCIDA;
	}

	/** Lunes a sábado (sin feriados nacionales ni del colegio), de 08:00 a 20:00. */
	boolean enVentana(LocalDateTime momento) {
		java.time.LocalTime hora = momento.toLocalTime();
		return calendario.admiteMensajes(momento.toLocalDate()) && !hora.isBefore(VENTANA_DESDE)
				&& hora.isBefore(VENTANA_HASTA);
	}

	/** El inicio de la próxima ventana de recordatorios. */
	LocalDateTime siguienteVentana(LocalDateTime momento) {
		java.time.LocalDate dia = momento.toLocalTime().isBefore(VENTANA_DESDE) ? momento.toLocalDate()
				: momento.toLocalDate().plusDays(1);
		return calendario.diaDeMensajesEnODespues(dia).atTime(VENTANA_DESDE);
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}
}
