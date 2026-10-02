package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

/** Estado del envío al OSE. PENDIENTE: aún no se envió o el envío falló (se reintenta). */
public enum EstadoEnvio {
	PENDIENTE, ACEPTADO, OBSERVADO, RECHAZADO
}
