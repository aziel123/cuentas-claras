package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;

/**
 * Puerto hacia el OSE o PSE que valida el comprobante ante SUNAT. En el piloto lo implementa {@link EmisorSimulado};
 * el adaptador real (Nubefact u otro OSE) llega en el sprint 4 o 6.
 */
public interface EmisorElectronico {

	ProveedorComprobantes proveedor();

	/** Envía el comprobante. Idempotente por serie y número: reenviarlo no crea otro. */
	ResultadoEnvio enviar(DocumentoElectronico documento);

	ResultadoEnvio consultar(TipoComprobante tipo, String serie, int numero);
}
