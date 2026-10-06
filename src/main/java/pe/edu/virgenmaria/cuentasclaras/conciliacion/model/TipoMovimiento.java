package pe.edu.virgenmaria.cuentasclaras.conciliacion.model;

/** Un abono (entra dinero) o un cargo (sale) del extracto. */
public enum TipoMovimiento {

	ABONO("Abono"),
	CARGO("Cargo");

	private final String etiqueta;

	TipoMovimiento(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
