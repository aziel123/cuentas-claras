package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import java.time.LocalDateTime;

/**
 * Evento para la tarjeta "Para revisar" del inicio de Promotoría.
 *
 * @param quien     quién lo hizo (nombre de usuario)
 * @param accion    qué hizo, en lenguaje claro
 * @param usuarioId a quién (id del usuario afectado), para mostrar su nombre
 * @param cambio    valor nuevo legible (roles dados), si hay
 */
public record EventoRevision(LocalDateTime ocurridoEn, String quien, String accion, Long usuarioId, String cambio) {
}
