package pe.edu.virgenmaria.cuentasclaras.operacion.salud;

import java.time.Instant;

/**
 * Un proceso programado: su clave ({@code DespachoMensajes.despachar}), su nombre en lenguaje claro, cada cuánto corre,
 * su último éxito (latido) y si está atrasado. Los críticos (despacho de mensajes, huellas y resumen) son alerta CRÍTICA.
 */
public record EstadoProceso(String clave, String nombre, String programacion, Instant ultimoExito, boolean atrasado,
		boolean critico) {
}
