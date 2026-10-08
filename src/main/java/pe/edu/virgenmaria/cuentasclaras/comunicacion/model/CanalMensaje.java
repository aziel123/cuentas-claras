package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

/** Por dónde sale un mensaje: WhatsApp primero; el correo es el respaldo (o el único canal si no hay celular). */
public enum CanalMensaje {

	WHATSAPP("WhatsApp"),
	CORREO("correo");

	private final String etiqueta;

	CanalMensaje(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
