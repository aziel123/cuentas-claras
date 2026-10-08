package pe.edu.virgenmaria.cuentasclaras.pasarela.model;

/** Línea de una liquidación de la pasarela: un cargo (positivo), su reembolso o contracargo (negativos) o un ajuste. */
public enum TipoLineaLiquidacion {
	CARGO, REEMBOLSO, CONTRACARGO, AJUSTE
}
