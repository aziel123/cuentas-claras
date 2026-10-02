package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.LineaDocumento;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Emisor del piloto: no envía nada a SUNAT. Responde ACEPTADO con el SHA-256 de la forma canónica del documento (el
 * mismo documento da siempre el mismo hash: es idempotente). El comprobante impreso lleva la marca «COMPROBANTE
 * SIMULADO · SIN VALOR TRIBUTARIO».
 */
@Component
@ConditionalOnProperty(name = "cuentasclaras.comprobantes.proveedor", havingValue = "SIMULADO", matchIfMissing = true)
public class EmisorSimulado implements EmisorElectronico {

	public static final String RESPUESTA = "Simulado: sin validez ante SUNAT";

	@Override
	public ProveedorComprobantes proveedor() {
		return ProveedorComprobantes.SIMULADO;
	}

	@Override
	public ResultadoEnvio enviar(DocumentoElectronico documento) {
		return new ResultadoEnvio(EstadoEnvio.ACEPTADO, RESPUESTA, hash(canonica(documento)), null);
	}

	@Override
	public ResultadoEnvio consultar(TipoComprobante tipo, String serie, int numero) {
		return new ResultadoEnvio(EstadoEnvio.ACEPTADO, RESPUESTA, null, null);
	}

	static String canonica(DocumentoElectronico d) {
		StringBuilder texto = new StringBuilder().append(d.tipo().codigoSunat()).append('|').append(d.serie()).append('|')
				.append(d.numero()).append('|').append(d.fecha()).append('|').append(d.receptor().tipo().codigoSunat())
				.append('|').append(d.receptor().numero()).append('|').append(d.moneda()).append('|')
				.append(d.total().toPlainString()).append('|').append(d.afectacion().codigoSunat());
		for (LineaDocumento linea : d.lineas()) {
			texto.append('|').append(linea.descripcion()).append('=').append(linea.monto().toPlainString());
		}
		return texto.toString();
	}

	private static String hash(String texto) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(texto.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}
}
