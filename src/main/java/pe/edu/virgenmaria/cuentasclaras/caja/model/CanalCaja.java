package pe.edu.virgenmaria.cuentasclaras.caja.model;

/**
 * Por dónde entra el dinero de una caja (sprint 4). VENTANILLA: la caja de una cajera, como siempre. PASARELA y
 * RECAUDACION: cajas de canal, una por día, cuyo «cajero» es el actor de sistema; nunca reciben efectivo, no tienen
 * fondo fijo y nunca se cuentan ni se cierran (CHECK {@code ck_caja_diaria_canal}).
 */
public enum CanalCaja {

	VENTANILLA("Ventanilla"),
	PASARELA("Pagos en línea"),
	RECAUDACION("Recaudación bancaria");

	private final String etiqueta;

	CanalCaja(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
