package pe.edu.virgenmaria.cuentasclaras.comunicacion.dto;

import java.time.LocalDateTime;

/**
 * Un mensaje como lo ve el personal (bandeja) o la familia (historial). El destino va ENMASCARADO («+51 *** *** 321»);
 * el texto de una activación nunca se muestra («Enlace de acceso enviado»). {@code simulado}: «SIMULADO · no se envió».
 */
public record MensajeVista(Long id, LocalDateTime creadoEn, String tipo, String canal, String destino, String estado,
		String variante, int intentos, String texto, String ultimoError, boolean simulado, boolean puedeReintentar) {
}
