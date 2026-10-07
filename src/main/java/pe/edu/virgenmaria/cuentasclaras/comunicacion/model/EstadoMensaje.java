package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estado de un mensaje. PENDIENTE → ENVIADO → ENTREGADO → LEIDO, o FALLIDO. FALLIDO y LEIDO son finales; ENVIADO → LEIDO
 * directo vale (los avisos de Meta llegan en desorden). Nunca vuelve atrás: en MySQL lo exige trg_mensaje_envio.
 */
public enum EstadoMensaje {

	PENDIENTE("Pendiente"),
	ENVIADO("Enviado"),
	ENTREGADO("Entregado"),
	LEIDO("Leído"),
	FALLIDO("No se pudo enviar");

	private final String etiqueta;

	EstadoMensaje(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Transiciones permitidas (las mismas que trg_mensaje_envio). */
	public boolean puedePasarA(EstadoMensaje nuevo) {
		Set<EstadoMensaje> siguientes = switch (this) {
			case PENDIENTE -> EnumSet.of(ENVIADO, FALLIDO);
			case ENVIADO -> EnumSet.of(ENTREGADO, LEIDO, FALLIDO);
			case ENTREGADO -> EnumSet.of(LEIDO, FALLIDO);
			case LEIDO, FALLIDO -> EnumSet.noneOf(EstadoMensaje.class);
		};
		return siguientes.contains(nuevo);
	}

	/** El proveedor lo aceptó (ENVIADO, ENTREGADO o LEIDO). */
	public boolean salio() {
		return this == ENVIADO || this == ENTREGADO || this == LEIDO;
	}
}
