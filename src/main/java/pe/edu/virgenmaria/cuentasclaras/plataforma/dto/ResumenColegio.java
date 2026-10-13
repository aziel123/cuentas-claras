package pe.edu.virgenmaria.cuentasclaras.plataforma.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;

import java.time.LocalDateTime;

/**
 * Sprint 8, tanda 1 (decisión 113): lo que el operador ve de cada colegio en la lista: nombre, RUC, estado, si ya tiene
 * su primera Promotoría, cuántas cuentas y última actividad. Nunca nombres, documentos, contactos ni montos de familias.
 */
public record ResumenColegio(Long id, String nombre, String ruc, EstadoColegio estado, boolean conTitular,
		boolean titularActivado, long cuentas, LocalDateTime ultimaActividad) {
}
