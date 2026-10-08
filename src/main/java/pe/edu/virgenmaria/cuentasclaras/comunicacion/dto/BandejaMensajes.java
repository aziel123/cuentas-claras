package pe.edu.virgenmaria.cuentasclaras.comunicacion.dto;

import java.util.List;

/** Bandeja de envíos del personal (pantalla 7): fallidos, pendientes de más de 15 minutos y los de hoy. */
public record BandejaMensajes(List<MensajeVista> fallidos, List<MensajeVista> pendientes, List<MensajeVista> hoy,
		boolean puedeReintentar) {
}
