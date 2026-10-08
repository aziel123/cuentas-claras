package pe.edu.virgenmaria.cuentasclaras.recaudacion.model;

/**
 * CON_BASE_DE_DEUDAS: el banco recibe del colegio la lista de cuotas (base de deudas) y cada pago trae el código de la
 * cuota exacta (referencia_deuda). SIN_BASE: solo el código del alumno; el pago se imputa a sus cuotas de la más antigua
 * a la más nueva (la misma regla de caja).
 */
public enum ModalidadRecaudacion {
	CON_BASE_DE_DEUDAS, SIN_BASE
}
