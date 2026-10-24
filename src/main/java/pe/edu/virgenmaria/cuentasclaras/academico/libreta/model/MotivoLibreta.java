package pe.edu.virgenmaria.cuentasclaras.academico.libreta.model;

/**
 * Sprint 10, tanda 3 (sección 3.7 y 3.13, A3): por qué se emitió una versión de la libreta. La 1 es siempre el CIERRE (lo
 * informado al cerrar el periodo); las siguientes, una CORRECCION (una nota corregida o tardía aprobada) o una REEMISION
 * (Promotoría aprobó volver a emitirla tras una huella que no coincide).
 */
public enum MotivoLibreta {

	CIERRE("Al cierre del periodo"),
	CORRECCION("Por una corrección aprobada"),
	REEMISION("Reemitida tras una revisión de integridad");

	/** El texto fijo de {@code cambios} en una reemisión (lo imprime la libreta y lo muestra el portal). */
	public static final String TEXTO_REEMISION = "Reemitida tras una revisión de integridad";

	private final String etiqueta;

	MotivoLibreta(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
