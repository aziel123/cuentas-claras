package pe.edu.virgenmaria.cuentasclaras.conectores.model;

/**
 * Sprint 8, tanda 2 (sección 3.6): el proveedor de un conector. Qué proveedor vale para cada tipo lo dicen
 * {@link TipoConector#proveedores()} y el CHECK {@code ck_conector_colegio_proveedor} de V29. Los adaptadores reales de
 * Culqi, Niubiz e Izipay y los formatos de cada banco se construyen cuando el primer colegio tenga su cuenta (decisiones
 * 22 y 24): hasta entonces se pueden registrar, pero su prueba de conexión no pasa y no entran en vigencia.
 */
public enum ProveedorConector {

	SIMULADO("Simulado (sin valor real)"),
	NUBEFACT("Nubefact"),
	CULQI("Culqi"),
	NIUBIZ("Niubiz"),
	IZIPAY("Izipay"),
	BCP("BCP"),
	BBVA("BBVA"),
	INTERBANK("Interbank"),
	SCOTIABANK("Scotiabank"),
	OTRO("Otro banco"),
	GENERICO("Formato genérico");

	private final String etiqueta;

	ProveedorConector(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
