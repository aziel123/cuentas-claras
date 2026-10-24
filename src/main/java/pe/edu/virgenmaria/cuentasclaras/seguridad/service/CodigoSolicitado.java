package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 11, tanda 1c (decisión 216, A1c-1, A1c-5 y A1c-7): alguien pidió el código de una acción. Lo escucha la
 * mensajería en la MISMA transacción (ruta de identidad: lo exige trg_mensaje_nace) y crea el mensaje
 * {@code CODIGO_PROMOTORIA} o {@code CODIGO_CONTACTO} al WhatsApp REGISTRADO de la cuenta, con la SHA-256 del código y la
 * huella de la acción (nunca el código: lo deriva el envío). Sin respaldo por correo (A1c-7: la aplicación no lo ofrece).
 *
 * @param usuarioId la cuenta que recibe el código (la Promotoría que actúa, o la cuenta directiva cuyo contacto cambia)
 * @param contacto  {@code true} para el código al contacto anterior (A1c-1); {@code false} para el de Promotoría
 * @param claveBase la base de la clave idempotente del mensaje (con un aleatorio: cada pedido es un mensaje nuevo)
 * @param accion    lo que autoriza, en palabras («Dar Promotoría a j.perez»): va en el WhatsApp
 * @param huella    la SHA-256 de la acción (la misma que calcula la base)
 * @param venceA    la hora (HH:mm) hasta la que vale el código, para el texto del mensaje
 */
public record CodigoSolicitado(Long usuarioId, boolean contacto, String claveBase, String accion, String huella,
		String venceA, Resultado resultado) {

	/** Lo que deja la mensajería: el mensaje creado. */
	public static final class Resultado {

		private Long mensajeId;

		private LocalDateTime creadoEn;

		private String destino;

		public Long mensajeId() {
			return mensajeId;
		}

		public LocalDateTime creadoEn() {
			return creadoEn;
		}

		/** A dónde salió (enmascarado). */
		public String destino() {
			return destino;
		}
	}

	public CodigoSolicitado {
		Objects.requireNonNull(usuarioId, "usuarioId");
		Objects.requireNonNull(claveBase, "claveBase");
		Objects.requireNonNull(accion, "accion");
		Objects.requireNonNull(huella, "huella");
		Objects.requireNonNull(resultado, "resultado");
	}

	public static CodigoSolicitado de(Long usuarioId, boolean contacto, String claveBase, String accion, String huella,
			String venceA) {
		return new CodigoSolicitado(usuarioId, contacto, claveBase, accion, huella, venceA, new Resultado());
	}

	/** Lo llama la mensajería al crear el mensaje. */
	public void registrar(Long mensajeId, LocalDateTime creadoEn, String destino) {
		resultado.mensajeId = Objects.requireNonNull(mensajeId, "mensajeId");
		resultado.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
		resultado.destino = destino;
	}
}
