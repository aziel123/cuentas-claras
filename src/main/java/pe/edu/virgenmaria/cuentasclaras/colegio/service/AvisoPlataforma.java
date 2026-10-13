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
		CORREO_CONTROL_CAMBIADO
	}

	public AvisoPlataforma {
		Objects.requireNonNull(colegioId, "colegioId");
		Objects.requireNonNull(tipo, "tipo");
		Objects.requireNonNull(entidad, "entidad");
		Objects.requireNonNull(entidadId, "entidadId");
		parametros = parametros == null ? List.of() : List.copyOf(parametros);
	}
}
