package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto;

/**
 * Sprint 8, tanda 1 (sección 3.5): un punto de la lista de verificación de la puesta en marcha. {@code conteo} es un
 * sí/no o una cifra («Alumnos: 312»), nunca nombres, documentos, contactos ni montos: lo ve también el operador.
 * {@code enlace}: la pantalla del colegio donde se completa (vacío para el operador).
 */
public record PuntoLista(String clave, String paso, Estado estado, String conteo, String enlace) {

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

	/** Lo mismo, sin el enlace: para la ficha del operador. */
	public PuntoLista paraOperador() {
		return new PuntoLista(clave, paso, estado, conteo, null);
	}
}
