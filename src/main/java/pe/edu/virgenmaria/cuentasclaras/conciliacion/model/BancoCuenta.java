package pe.edu.virgenmaria.cuentasclaras.conciliacion.model;

/** Banco de una cuenta del colegio que se concilia (CHECK {@code ck_cuenta_bancaria_banco}). */
public enum BancoCuenta {

	BCP("BCP"),
	INTERBANK("Interbank"),
	BBVA("BBVA"),
	SCOTIABANK("Scotiabank"),
	OTRO("Otro banco");

	private final String etiqueta;

	BancoCuenta(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
