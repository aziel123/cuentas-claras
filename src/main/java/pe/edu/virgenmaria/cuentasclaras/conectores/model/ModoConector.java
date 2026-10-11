package pe.edu.virgenmaria.cuentasclaras.conectores.model;

/**
 * Sprint 8, tanda 2: SIMULADO (solo con el proveedor SIMULADO: nada sale del sistema), PRUEBAS (la cuenta de pruebas del
 * proveedor: no tiene valor legal ni mueve dinero) o REAL. Una cuenta bancaria y una recaudación son siempre REAL (CHECK
 * {@code ck_conector_colegio_modo}).
 */
public enum ModoConector {

	SIMULADO("Simulado"),
	PRUEBAS("Pruebas del proveedor"),
	REAL("Real");

	private final String etiqueta;

	ModoConector(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
