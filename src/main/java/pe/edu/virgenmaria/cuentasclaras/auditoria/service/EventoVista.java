package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import java.time.LocalDateTime;

/**
 * Evento de la bitácora listo para mostrar a la promotora: en lenguaje claro y en hora de Lima.
 *
 * @param roles            roles legibles ("Caja", "Dirección · Administración")
 * @param accion           descripción clara de la acción
 * @param requiereAtencion intento fallido o rechazado: se resalta
 * @param cambio           "anterior → nuevo", si hubo cambio de valor
 */
public record EventoVista(long secuencia, LocalDateTime ocurridoEn, String nombreUsuario, String roles, String accion,
		boolean requiereAtencion, String cambio, String detalle, String ip) {
}
