package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto;

import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.model.PasoPuestaEnMarcha;

/**
 * Sprint 8, tanda 1 (sección 3.5): un punto de la lista de verificación de la puesta en marcha. {@code conteo} es un
 * sí/no o una cifra («Alumnos: 312»), nunca nombres, documentos, contactos ni montos: lo ve también el operador.
 * {@code enlace}: la pantalla del colegio donde se completa (vacío para el operador).
 * <p>
 * Sprint 11, tanda 1b (sección 3.14): {@code bloqueadoPor} es el primer paso pendiente del que depende (o nulo), calculado
 * en {@code EstadoPuestaEnMarcha}; {@link #definicion()} es el paso del catálogo ({@link PasoPuestaEnMarcha}), que es la
 * única fuente de «Quién lo hace». (El brief lo llama {@code paso}; ese nombre ya era el título, que se conserva.)
 */
public record PuntoLista(String clave, String paso, Estado estado, String conteo, String enlace,
		PasoPuestaEnMarcha bloqueadoPor) {

	public PuntoLista(String clave, String paso, Estado estado, String conteo, String enlace) {
		this(clave, paso, estado, conteo, enlace, null);
	}

	public enum Estado {
		HECHO("Hecho"),
		PENDIENTE("Pendiente"),
		OPCIONAL("Opcional");

		private final String etiqueta;

		Estado(String etiqueta) {
			this.etiqueta = etiqueta;
		}

		public String etiqueta() {
			return etiqueta;
		}
	}

	public boolean hecho() {
		return estado == Estado.HECHO;
	}

	public boolean obligatorio() {
		return estado != Estado.OPCIONAL;
	}

	/** El paso del catálogo con la misma clave. */
	public PasoPuestaEnMarcha definicion() {
		return PasoPuestaEnMarcha.deClave(clave).orElseThrow();
	}

	/** Quién del colegio (o de la plataforma) completa el paso: lo dice el catálogo. */
	public String responsable() {
		return definicion().responsable();
	}

	public boolean bloqueado() {
		return !hecho() && bloqueadoPor != null;
	}

	/** Lo mismo, sin el enlace: para la ficha del operador. */
	public PuntoLista paraOperador() {
		return new PuntoLista(clave, paso, estado, conteo, null, bloqueadoPor);
	}
}
