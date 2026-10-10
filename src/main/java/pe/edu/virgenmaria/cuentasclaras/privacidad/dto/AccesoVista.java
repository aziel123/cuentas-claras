package pe.edu.virgenmaria.cuentasclaras.privacidad.dto;

import java.time.LocalDateTime;

/** Una fila de «Quién vio datos personales» (/auditoria/accesos): persona, pantalla, familia o alumno, cuántas filas. */
public record AccesoVista(LocalDateTime fecha, String persona, String nombreCompleto, String pantalla, String familia,
		String alumno, int cantidad, String ip) {
}
