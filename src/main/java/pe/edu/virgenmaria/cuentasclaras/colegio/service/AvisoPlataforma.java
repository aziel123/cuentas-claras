package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import java.util.List;
import java.util.Objects;

/**
 * Correcciones del sprint 8 (S8-A1, S8-A2 y S8-M5): lo que el operador de la plataforma (o la Promotoría, al aprobar el
 * correo de control) cambia en un colegio se avisa en la MISMA transacción del cambio, como los avisos de pago. La
 * mensajería lo escucha ({@code comunicacion.service.AvisosPlataforma}) y crea un mensaje a cada destinatario pedido, al
 * contacto REGISTRADO en ese momento (por eso el aviso al contacto anterior se publica ANTES de cambiarlo). Si no hay a
 * quién avisar, lanza la excepción y el cambio no se guarda.
 *
 * @param colegioId    el colegio (debe ser el del contexto: el mensaje es de ese colegio)
 * @param tipo         qué pasó (elige la plantilla)
 * @param parametros   los parámetros propios de la plantilla (sin el nombre del colegio, que antepone la mensajería)
 * @param entidad      la entidad que lo origina (para la clave idempotente)
 * @param entidadId    su id
 * @param sufijo       distingue dos avisos de la misma entidad (por ejemplo, la versión de la ficha)
 * @param titularId    la cuenta del titular a la que se avisa por cada canal registrado, o {@code null}
 * @param institucional si se avisa al correo institucional del colegio
 * @param control      si se avisa al correo externo de control del colegio
 * @param promotoria   si se avisa a toda la Promotoría activa del colegio
 */
public record AvisoPlataforma(Long colegioId, Tipo tipo, List<String> parametros, String entidad, Long entidadId,
		String sufijo, Long titularId, boolean institucional, boolean control, boolean promotoria) {

	/** Los avisos de la plataforma (cada uno con su plantilla fija). */
	public enum Tipo {
		/** El operador reenvió el enlace de activación al titular (al mismo contacto). */
		ENLACE_TITULAR_REENVIADO,
		/** Dos operadores cambiaron el contacto del titular antes de su activación. */
		CONTACTO_TITULAR_CORREGIDO,
		/** El operador corrigió la ficha del colegio en preparación. */
		FICHA_CORREGIDA,
		/** El operador propuso cambiar el correo externo de control (falta la aprobación de la Promotoría). */
		CORREO_CONTROL_PROPUESTO,
		/** El correo externo de control cambió (el operador en preparación; la Promotoría lo aprobó en producción). */
		CORREO_CONTROL_CAMBIADO,
		/** Sprint 11, tanda 1: un operador propuso un cambio de estado (o el bloqueo de la facultad del titular). */
		CAMBIO_ESTADO_PROPUESTO,
		/** Sprint 11, tanda 1: otro operador lo aprobó (con la fecha en que rige). */
		CAMBIO_ESTADO_APROBADO,
		/** Sprint 11, tanda 1: se rechazó o se anuló el cambio propuesto. */
		CAMBIO_ESTADO_RESUELTO,
		/** Sprint 11, tanda 1: sistema.plataforma aplicó el cambio. */
		CAMBIO_ESTADO_APLICADO,
		/** Sprint 11, tanda 1 (AP-4, AP-13 y H3): la suspensión espera cierres, comprobantes, órdenes o libretas. */
		CAMBIO_ESTADO_EN_ESPERA,
		/** Sprint 11, tanda 1: recordatorio a 7 días y a 1 día de la suspensión. */
		SUSPENSION_PROXIMA,
		/** Sprint 11, tanda 1 (AP-10): el titular objetó una baja o una suspensión. */
		OBJECION_TITULAR,
		/** Sprint 11, tanda 1 (AP-10): un operador respondió la objeción. */
		OBJECION_RESPONDIDA,
		/** Sprint 11, tanda 1 (sección 3.12): el titular usó su facultad. */
		FACULTAD_TITULAR_USADA;

		/**
		 * Lo que distingue la clave idempotente de cada tipo. Los del sprint 8 conservan sus cuatro letras (las claves ya
		 * guardadas no cambian); los nuevos llevan su nombre completo (los de cambio de estado empiezan igual).
		 */
		public String codigo() {
			return ordinal() <= CORREO_CONTROL_CAMBIADO.ordinal() ? name().substring(0, 4) : name();
		}
	}

	public AvisoPlataforma {
		Objects.requireNonNull(colegioId, "colegioId");
		Objects.requireNonNull(tipo, "tipo");
		Objects.requireNonNull(entidad, "entidad");
		Objects.requireNonNull(entidadId, "entidadId");
		parametros = parametros == null ? List.of() : List.copyOf(parametros);
	}

}
