package pe.edu.virgenmaria.cuentasclaras.caja.model;

/**
 * Resultado de un conteo a ciegas: COINCIDE (primer conteo igual al esperado: cierra), RECONTAR (no coincide: se pide
 * un reconteo SIN mostrar montos) o FINAL (el reconteo, que siempre cierra).
 */
public enum ResultadoConteo {
	COINCIDE, RECONTAR, FINAL
}
