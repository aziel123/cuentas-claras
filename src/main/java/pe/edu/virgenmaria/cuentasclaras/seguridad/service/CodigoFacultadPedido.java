package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 11, tanda 1 (AP-2): el titular pidió el código de un uso de su facultad. Lo escucha la mensajería en la MISMA
 * transacción y crea el mensaje {@code CODIGO_FACULTAD} al contacto REGISTRADO del titular, sin el código (lo deriva el
 * envío, {@code DerivadorSecreto.codigoFacultad}). La mensajería deja aquí el mensaje creado ({@link #registrar}): la
 * aplicación lo guarda en la sesión del titular y el uso de la facultad apunta a él ({@code uso_facultad_titular.mensaje_id}).
 *
 * @param titularId la cuenta del titular
 * @param claveBase la base de la clave idempotente del mensaje (con un aleatorio: cada pedido es un mensaje nuevo)
 * @param venceA    la hora (HH:mm) hasta la que vale el código, para el texto del mensaje
 */
public record CodigoFacultadPedido(Long titularId, String claveBase, String venceA, Resultado resultado) {

	/** Lo que deja la mensajería: el mensaje creado. */
	public static final class Resultado {

		private Long mensajeId;

		private String clave;

		private LocalDateTime creadoEn;

		private String destino;

		public Long mensajeId() {
			return mensajeId;
		}

		public String clave() {
			return clave;
		}

		public LocalDateTime creadoEn() {
			return creadoEn;
		}

		/** A dónde salió (enmascarado). */
		public String destino() {
			return destino;
		}
	}

	public CodigoFacultadPedido {
		Objects.requireNonNull(titularId, "titularId");
		Objects.requireNonNull(claveBase, "claveBase");
		Objects.requireNonNull(resultado, "resultado");
	}

	public static CodigoFacultadPedido de(Long titularId, String claveBase, String venceA) {
		return new CodigoFacultadPedido(titularId, claveBase, venceA, new Resultado());
	}

	/** Lo llama la mensajería al crear el mensaje. */
	public void registrar(Long mensajeId, String clave, LocalDateTime creadoEn, String destino) {
		resultado.mensajeId = Objects.requireNonNull(mensajeId, "mensajeId");
		resultado.clave = Objects.requireNonNull(clave, "clave");
		resultado.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
		resultado.destino = destino;
	}
}
