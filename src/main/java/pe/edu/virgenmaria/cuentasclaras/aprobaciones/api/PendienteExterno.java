package pe.edu.virgenmaria.cuentasclaras.aprobaciones.api;

import java.time.LocalDateTime;

/**
 * Sprint 11, tanda 1b (F-D5, decisión 209): algo que se aprueba en otra pantalla (un plan de pensiones enviado, un lote de
 * saldo inicial, un lote de recaudación o un extracto por confirmar, un feriado propuesto, el correo de control propuesto
 * por el operador o un respaldo con filas faltantes). La bandeja lo lista con «Revisar», que solo enlaza: aprobar y
 * rechazar siguen en la pantalla del módulo, con sus controles. Sin datos de familias ni montos de alumnos.
 *
 * @param tipo          clave estable del tipo («PLAN_PENSION», «SALDO_INICIAL», …)
 * @param titulo        corto («Plan de pensiones de Primaria 2026»)
 * @param detalle       quién y cuándo («Enviado por adm.lopez el 24/10»)
 * @param solicitadoPor quien lo pidió o lo subió
 * @param desde         desde cuándo espera (para ordenar)
 * @param enlace        la pantalla donde se resuelve
 * @param puedeResolver quien está en sesión puede resolverlo (no lo pidió ni participó)
 */
public record PendienteExterno(String tipo, String titulo, String detalle, String solicitadoPor, LocalDateTime desde,
		String enlace, boolean puedeResolver) {
}
