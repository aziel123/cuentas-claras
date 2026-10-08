package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

public enum TipoCuota {

	MATRICULA("Matrícula"),
	PENSION("Pensión"),
	SALDO_INICIAL("Saldo inicial");

	private final String etiqueta;

	TipoCuota(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
