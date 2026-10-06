package pe.edu.virgenmaria.cuentasclaras.pasarela.model;

import java.util.Objects;

/** La respuesta de la pasarela a una consulta. Solo {@code PAGADO} trae el cobro confirmado. */
public record EstadoCobro(Estado estado, CobroConfirmado cobro) {

	public enum Estado {
		PAGADO, PENDIENTE, RECHAZADO, EXPIRADO,
		/** El apoderado desconoció ante su banco un cargo ya pagado. */
		CONTRACARGO
	}

	public EstadoCobro {
		Objects.requireNonNull(estado, "estado");
		if ((estado == Estado.PAGADO) != (cobro != null)) {
			throw new IllegalArgumentException("Solo un cobro PAGADO trae su confirmación");
		}
	}

	public static EstadoCobro pagado(CobroConfirmado cobro) {
		return new EstadoCobro(Estado.PAGADO, cobro);
	}

	public static EstadoCobro sin(Estado estado) {
		return new EstadoCobro(estado, null);
	}
}
