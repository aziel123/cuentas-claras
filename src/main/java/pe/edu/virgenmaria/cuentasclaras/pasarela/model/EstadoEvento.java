package pe.edu.virgenmaria.cuentasclaras.pasarela.model;

/** Bandeja de avisos de la pasarela: RECIBIDO → PROCESADO, IGNORADO (la orden ya era final) o ERROR (tras 5 intentos). */
public enum EstadoEvento {
	RECIBIDO, PROCESADO, IGNORADO, ERROR
}
