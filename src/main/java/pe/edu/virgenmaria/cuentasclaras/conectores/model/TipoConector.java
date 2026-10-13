package pe.edu.virgenmaria.cuentasclaras.conectores.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Sprint 8, tanda 2 (sección 3.6): a qué conecta un conector del colegio. Los cuatro deciden adónde entra el dinero o
 * cómo se le da valor legal: cambiarlos lo aprueba OTRA persona con su firma y se avisa a toda la Promotoría.
 * Se guarda por nombre ({@code VARCHAR(20)} con CHECK): nunca renombres un valor ya usado.
 */
public enum TipoConector {

	OSE("Comprobantes (OSE)", "el emisor de las boletas y facturas", true,
			EnumSet.of(ProveedorConector.SIMULADO, ProveedorConector.NUBEFACT)),
	PASARELA("Pago en línea", "la pasarela de pago en línea", true,
			EnumSet.of(ProveedorConector.SIMULADO, ProveedorConector.CULQI, ProveedorConector.NIUBIZ, ProveedorConector.IZIPAY)),
	CUENTA_BANCARIA("Cuenta bancaria", "la cuenta bancaria donde se deposita el efectivo y de donde sale el extracto",
			false, EnumSet.of(ProveedorConector.BCP, ProveedorConector.BBVA, ProveedorConector.INTERBANK,
					ProveedorConector.SCOTIABANK, ProveedorConector.OTRO)),
	RECAUDACION("Recaudación bancaria", "la recaudación por código de alumno en el banco", false,
			EnumSet.of(ProveedorConector.GENERICO, ProveedorConector.BCP, ProveedorConector.BBVA, ProveedorConector.INTERBANK,
					ProveedorConector.SCOTIABANK));

	private final String etiqueta;

	private final String descripcion;

	private final boolean conSecreto;

	private final Set<ProveedorConector> proveedores;

	TipoConector(String etiqueta, String descripcion, boolean conSecreto, Set<ProveedorConector> proveedores) {
		this.etiqueta = etiqueta;
		this.descripcion = descripcion;
		this.conSecreto = conSecreto;
		this.proveedores = proveedores;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Para los avisos: «cambió la cuenta bancaria donde se deposita el efectivo…». */
	public String descripcion() {
		return descripcion;
	}

	/** Solo el OSE (token) y la pasarela (llave secreta y secreto de avisos) guardan un secreto. */
	public boolean conSecreto() {
		return conSecreto;
	}

	public Set<ProveedorConector> proveedores() {
		return proveedores;
	}

	/**
	 * Mueve dinero (la pasarela, la cuenta bancaria y la recaudación): una Promotoría debe participar, proponiendo o
	 * aprobando. El OSE no mueve dinero (da valor legal a la boleta): basta otra persona de Promotoría o Dirección.
	 */
	public boolean mueveDinero() {
		return this != OSE;
	}
}
