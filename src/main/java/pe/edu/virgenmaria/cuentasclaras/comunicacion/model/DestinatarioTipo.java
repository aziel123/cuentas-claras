package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

/**
 * A quién va un mensaje: un apoderado (con su familia, para el historial del portal), un usuario del personal o EXTERNO
 * (solo el correo del contador que el DBA dejó en {@code configuracion_bd}, solo para la huella).
 */
public enum DestinatarioTipo {

	APODERADO,
	USUARIO,
	EXTERNO
}
