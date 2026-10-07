package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ContactoCambiado;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.HuellaDelDia;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CanalMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionBd;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.ConfiguracionBdRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PropositoEnlace;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnvioEnlaceSolicitado;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Mensajes de cuenta (sprint 5), creados en la misma transacción de lo que los origina:
 * <ul>
 *   <li>{@link EnvioEnlaceSolicitado}: el mensaje de activación al contacto REGISTRADO del titular, SIN token (el enlace
 *       lo genera el proceso de envío). Si no hay a dónde enviarlo, el acceso no se da (la excepción revierte todo).</li>
 *   <li>{@link ContactoCambiado}: aviso al contacto ANTERIOR («este celular dejó de recibir los avisos»; G5).</li>
 *   <li>{@link HuellaDelDia}: la huella a cada usuario de Promotoría activo y al correo externo del contador (G13).</li>
 * </ul>
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AvisosCuenta {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final CreadorMensajes creador;

	private final UsuarioRepository usuarios;

	private final ApoderadoRepository apoderados;

	private final ConfiguracionBdRepository configuracion;

	private final AuditoriaService auditoria;

	public AvisosCuenta(CreadorMensajes creador, UsuarioRepository usuarios, ApoderadoRepository apoderados,
			ConfiguracionBdRepository configuracion, AuditoriaService auditoria) {
		this.creador = creador;
		this.usuarios = usuarios;
		this.apoderados = apoderados;
		this.configuracion = configuracion;
		this.auditoria = auditoria;
	}

	@EventListener
	public void alEnvioEnlaceSolicitado(EnvioEnlaceSolicitado evento) {
		Usuario usuario = usuarios.findById(evento.usuarioId())
				.orElseThrow(() -> new IllegalStateException("El usuario del enlace no existe"));
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.ACTIVACION_CUENTA,
				PlantillaMensaje.ACTIVACION, List.of(), "usuario", usuario.getId());
		String pedido = UUID.randomUUID().toString().replace("-", "");
		boolean creado;
		if (evento.proposito() == PropositoEnlace.PERSONAL) {
			creado = creador.paraUsuario(usuario, contenido, pedido).isPresent();
		}
		else {
			Apoderado apoderado = apoderados.findById(Objects.requireNonNull(evento.apoderadoId(), "apoderadoId"))
					.orElseThrow(() -> new IllegalStateException("El apoderado del enlace no existe"));
			creado = !creador.paraApoderado(apoderado, contenido, false, pedido).isEmpty();
		}
		if (!creado) {
			throw new ReglaNegocioException("No hay a dónde enviar el enlace: el titular no tiene celular ni correo, o su "
					+ "contacto es de alguien del personal y otra persona debe aprobarlo primero (cambio de contacto).");
		}
	}

	@EventListener
	public void alContactoCambiado(ContactoCambiado evento) {
		Apoderado apoderado = apoderados.findById(evento.apoderadoId())
				.orElseThrow(() -> new IllegalStateException("El apoderado del cambio no existe"));
		avisarAnterior(apoderado, CanalMensaje.WHATSAPP, evento.telefonoAnterior(), apoderado.getTelefonoWhatsapp(),
				evento.solicitudId());
		avisarAnterior(apoderado, CanalMensaje.CORREO, evento.correoAnterior(), apoderado.getCorreo(),
				evento.solicitudId());
	}

	private void avisarAnterior(Apoderado apoderado, CanalMensaje canal, String anterior, String actual,
			Long solicitudId) {
		if (anterior == null || anterior.isBlank() || anterior.equals(actual)) {
			return;
		}
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.CONTACTO_CAMBIADO,
				PlantillaMensaje.CONTACTO_CAMBIADO, List.of(canal == CanalMensaje.WHATSAPP ? "número" : "correo"),
				"solicitud_cambio", solicitudId);
		Optional<Mensaje> mensaje = creador.alContactoAnterior(apoderado, canal, anterior, contenido);
		mensaje.ifPresent(m -> auditoria.registrar(AccionAuditoria.CONTACTO_CAMBIADO_AVISADO, "mensaje",
				m.getId().toString(), null, canal.etiqueta() + " " + (canal == CanalMensaje.WHATSAPP
						? Enmascarar.telefono(anterior) : Enmascarar.correo(anterior)),
				"Se avisó al contacto anterior de " + apoderado.nombreCompleto() + " que dejó de recibir los avisos del "
						+ "colegio (solicitud " + solicitudId + ")."));
	}

	@EventListener
	public void alHuellaDelDia(HuellaDelDia evento) {
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.HUELLA_BITACORA,
				PlantillaMensaje.HUELLA, List.of(evento.fecha().format(FECHA), Long.toString(evento.secuencia()),
						evento.codigo(), evento.verificacionOk() ? "sí" : "NO, avise al contador"),
				"huella_bitacora", evento.huellaId());
		usuarios.activosConRol(Rol.PROMOTOR).forEach(p -> creador.paraUsuario(p, contenido, null));
		configuracion.findById(ConfiguracionBd.HUELLA_CORREO_EXTERNO).map(ConfiguracionBd::getValor)
				.filter(c -> c.contains("@")).ifPresent(c -> creador.externo(c.strip(), contenido));
	}
}
