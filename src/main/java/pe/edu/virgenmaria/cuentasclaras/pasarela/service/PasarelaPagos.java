package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoCobro;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Puerto hacia una pasarela de pagos en línea (un adaptador por proveedor). Reglas para todo adaptador:
 * <ul>
 *   <li>{@link #consultar} con la llave secreta es la ÚNICA fuente de verdad del dinero: un aviso solo despierta la
 *       consulta (los avisos de algunos proveedores ni siquiera están firmados).</li>
 *   <li>A la pasarela no se le envían nombres de alumnos (Ley 29733): la descripción lleva solo la referencia.</li>
 *   <li>Un reembolso vuelve SIEMPRE al mismo medio de origen del cargo.</li>
 * </ul>
 */
public interface PasarelaPagos {

	ProveedorPasarela proveedor();

	/** Crea la orden en la pasarela (página de pago alojada por ella) por el monto que calculó el servidor. */
	OrdenCreada crearOrden(SolicitudOrden solicitud);

	/**
	 * Valida la firma o la autenticación del aviso y lo lee.
	 *
	 * @throws AvisoNoAutenticoException si no es auténtico (se responde 401 y no se escribe nada en la bitácora)
	 */
	AvisoPasarela verificarAviso(byte[] cuerpo, Map<String, String> cabeceras);

	/** Estado del cobro de una orden en la pasarela, con la llave secreta. */
	EstadoCobro consultar(String proveedorOrdenId);

	/** Reembolsa el cargo (total o parcial) al mismo medio de origen. */
	ReembolsoPasarela reembolsar(String cargoId, BigDecimal monto, String motivo);
}
