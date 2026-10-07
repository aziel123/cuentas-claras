package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CanalMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.DestinatarioTipo;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Crea los mensajes del outbox (sprint 5), siempre dentro de la transacción de quien los pide (sin mensaje no hay pago).
 * <ul>
 *   <li>El destino es SIEMPRE el contacto REGISTRADO del destinatario: nadie lo elige (en MySQL lo exige
 *       trg_mensaje_nace).</li>
 *   <li>G6: a un apoderado no se le escribe a un celular o correo que también es de alguien del personal, salvo que ese
 *       contacto lo haya aprobado otra persona ({@code contacto_solicitud_id}). Ese canal se omite (y queda en el log).</li>
 *   <li>La clave es idempotente ({@code PAGO_REGISTRADO:pago:15:APODERADO:7:WHATSAPP}): una clave repetida no crea otro.</li>
 *   <li>Guarda con {@code saveAndFlush}: el INSERT llega a la base (y a su trigger) antes que lo que sigue.</li>
 * </ul>
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class CreadorMensajes {

	private static final Logger LOG = LoggerFactory.getLogger(CreadorMensajes.class);

	private final MensajeRepository mensajes;

	private final UsuarioRepository usuarios;

	private final ObjectProvider<ProveedorWhatsApp> whatsapp;

	public CreadorMensajes(MensajeRepository mensajes, UsuarioRepository usuarios,
			ObjectProvider<ProveedorWhatsApp> whatsapp) {
		this.mensajes = mensajes;
		this.usuarios = usuarios;
		this.whatsapp = whatsapp;
	}

	/** WhatsApp si hay celular; si el conector de WhatsApp está apagado y hay correo, solo correo. */
	private boolean usarWhatsapp(String celular, String correo) {
		return celular != null && (correo == null || whatsapp.getIfAvailable() != null);
	}

	/** Lo que se le envía a alguien: tipo, plantilla, parámetros y la entidad que lo originó. */
	public record Contenido(TipoMensaje tipo, PlantillaMensaje plantilla, List<String> parametros, String entidad,
			Long entidadId) {

		public Contenido {
			Objects.requireNonNull(tipo, "tipo");
			Objects.requireNonNull(plantilla, "plantilla");
			parametros = parametros == null ? List.of() : List.copyOf(parametros);
		}
	}

	/**
	 * Mensajes a un apoderado: por WhatsApp si tiene celular y, si {@code ambosCanales} o no tiene celular, por correo.
	 *
	 * @param sufijoClave distingue dos pedidos de la misma entidad (por ejemplo, dos activaciones); {@code null} si no hace
	 *                    falta
	 * @return los mensajes creados o que ya existían con esa clave (vacío si no hubo a dónde escribir)
	 */
	public List<Mensaje> paraApoderado(Apoderado apoderado, Contenido contenido, boolean ambosCanales,
			String sufijoClave) {
		List<Mensaje> creados = new ArrayList<>();
		String celular = apoderado.getTelefonoWhatsapp();
		String correo = apoderado.getCorreo();
		boolean porWhatsapp = usarWhatsapp(celular, correo);
		if (porWhatsapp) {
			crearParaApoderado(apoderado, CanalMensaje.WHATSAPP, celular, contenido, sufijoClave, null)
					.ifPresent(creados::add);
		}
		if (correo != null && (ambosCanales || !porWhatsapp)) {
			crearParaApoderado(apoderado, CanalMensaje.CORREO, correo, contenido, sufijoClave, null)
					.ifPresent(creados::add);
		}
		return creados;
	}

	/** Mensaje a un contacto ANTERIOR del apoderado (aviso de cambio de contacto: tipo CONTACTO_CAMBIADO). */
	public Optional<Mensaje> alContactoAnterior(Apoderado apoderado, CanalMensaje canal, String contactoAnterior,
			Contenido contenido) {
		return crearParaApoderado(apoderado, canal, contactoAnterior, contenido, null, null);
	}

	/** Mensaje a una persona del personal: WhatsApp si tiene celular; si no, correo. */
	public Optional<Mensaje> paraUsuario(Usuario usuario, Contenido contenido, String sufijoClave) {
		CanalMensaje canal = usarWhatsapp(usuario.getTelefonoWhatsapp(), usuario.getCorreo()) ? CanalMensaje.WHATSAPP
				: CanalMensaje.CORREO;
		String destino = canal == CanalMensaje.WHATSAPP ? usuario.getTelefonoWhatsapp() : usuario.getCorreo();
		if (destino == null) {
			return Optional.empty();
		}
		return guardar(clave(contenido, "USUARIO", usuario.getId(), canal, sufijoClave), contenido,
				new Mensaje.Destinatario(DestinatarioTipo.USUARIO, null, null, usuario.getId(), canal, destino), null);
	}

	/** Correo EXTERNO (solo la huella al contador; el correo lo dejó el DBA en configuracion_bd). */
	public Optional<Mensaje> externo(String correo, Contenido contenido) {
		return guardar(clave(contenido, "EXTERNO", 0L, CanalMensaje.CORREO, null), contenido,
				new Mensaje.Destinatario(DestinatarioTipo.EXTERNO, null, null, null, CanalMensaje.CORREO, correo), null);
	}

	/**
	 * Respaldo por correo de un WhatsApp FALLIDO, al MISMO destinatario (una sola vez). Vacío si no tiene correo o si ese
	 * aviso ya salía también por correo.
	 */
	public Optional<Mensaje> respaldo(Mensaje original, Apoderado apoderado, Usuario usuario) {
		if (original.getCanal() != CanalMensaje.WHATSAPP) {
			return Optional.empty();
		}
		String correo = apoderado != null ? apoderado.getCorreo() : usuario != null ? usuario.getCorreo() : null;
		if (correo == null) {
			return Optional.empty();
		}
		Contenido contenido = new Contenido(original.getTipo(), original.getPlantilla(), original.parametrosLista(),
				original.getEntidad(), original.getEntidadId());
		String clave = original.getClave().replaceFirst(":WHATSAPP", ":CORREO");
		if (mensajes.findByClave(clave).isPresent()) {
			return Optional.empty();
		}
		Mensaje.Destinatario para = apoderado != null
				? new Mensaje.Destinatario(DestinatarioTipo.APODERADO, apoderado.getId(), apoderado.getFamilia().getId(),
						null, CanalMensaje.CORREO, correo)
				: new Mensaje.Destinatario(DestinatarioTipo.USUARIO, null, null, usuario.getId(), CanalMensaje.CORREO, correo);
		if (apoderado != null && !puedeEscribirAlApoderado(apoderado, correo)) {
			return Optional.empty();
		}
		return guardar(clave, contenido, para, original.getId());
	}

	/** G6: el contacto es del personal y nadie más lo aprobó para este apoderado. */
	public boolean puedeEscribirAlApoderado(Apoderado apoderado, String destino) {
		return apoderado.getContactoSolicitudId() != null || !usuarios.esContactoDelPersonal(destino);
	}

	private Optional<Mensaje> crearParaApoderado(Apoderado apoderado, CanalMensaje canal, String destino,
			Contenido contenido, String sufijoClave, Long respaldoDe) {
		if (!puedeEscribirAlApoderado(apoderado, destino)) {
			LOG.warn("No se creó el mensaje {} al apoderado {} por {}: ese contacto es del personal y nadie lo aprobó.",
					contenido.tipo(), apoderado.getId(), canal);
			return Optional.empty();
		}
		return guardar(clave(contenido, "APODERADO", apoderado.getId(), canal, sufijoClave), contenido,
				new Mensaje.Destinatario(DestinatarioTipo.APODERADO, apoderado.getId(), apoderado.getFamilia().getId(), null,
						canal, destino), respaldoDe);
	}

	private Optional<Mensaje> guardar(String clave, Contenido contenido, Mensaje.Destinatario para, Long respaldoDe) {
		Optional<Mensaje> existente = mensajes.findByClave(clave);
		if (existente.isPresent()) {
			return existente;
		}
		return Optional.of(mensajes.saveAndFlush(Mensaje.nuevo(clave, contenido.tipo(), contenido.plantilla(), para,
				contenido.parametros(), contenido.entidad(), contenido.entidadId(), respaldoDe, null)));
	}

	static String clave(Contenido contenido, String destinatario, Long id, CanalMensaje canal, String sufijo) {
		return contenido.tipo().name() + ":" + contenido.entidad() + ":" + contenido.entidadId() + ":" + destinatario + ":"
				+ id + ":" + canal.name() + (sufijo == null ? "" : ":" + sufijo);
	}
}
