package pe.edu.virgenmaria.cuentasclaras.caja.dto;

/** Cajas de ventanilla de un día: abiertas, cerradas y cierres con diferencia. */
public record EstadoCajas(long abiertas, long cerradas, long conDiferencia) {
}
