package pe.edu.virgenmaria.cuentasclaras.colegio.service;

/**
 * Sprint 11, tanda 1b (sección 3.14, punto 3): lo que no se puede hacer mientras el colegio está en preparación, con el
 * texto que lo explica (segunda persona, sin jerga).
 */
public enum AccionBloqueada {

	COBRAR("todavía no se registran pagos. Cuando la Promotoría lo pase a producción, podrás cobrar."),
	PAGO_EN_LINEA("todavía no se reciben pagos en línea. Empieza cuando la Promotoría lo pase a producción."),
	RECAUDACION("todavía no se aplican los pagos del banco. Empieza cuando la Promotoría lo pase a producción."),
	COMUNICADO("todavía no salen mensajes a las familias. Podrás enviarlo cuando la Promotoría lo pase a producción."),
	RECORDATORIO("todavía no salen recordatorios de cobro. Empiezan cuando la Promotoría lo pase a producción."),
	RENOVACION("todavía no se abre la renovación de matrícula. Podrás abrirla cuando la Promotoría lo pase a producción.");

	private final String motivo;

	AccionBloqueada(String motivo) {
		this.motivo = motivo;
	}

	/** «Tu colegio está en preparación: todavía no se registran pagos. …» */
	public String mensaje() {
		return "Tu colegio está en preparación: " + motivo;
	}
}
