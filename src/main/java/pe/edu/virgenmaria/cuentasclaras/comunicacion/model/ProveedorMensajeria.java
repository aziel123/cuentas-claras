package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

/**
 * Quién aceptó el mensaje. SIMULADO solo existe en dev, test y piloto, y en MySQL solo con la fila
 * {@code configuracion_bd('mensajeria_simulada')} que escribe el DBA (nunca en producción).
 */
public enum ProveedorMensajeria {

	SIMULADO,
	WHATSAPP_CLOUD,
	SMTP
}
