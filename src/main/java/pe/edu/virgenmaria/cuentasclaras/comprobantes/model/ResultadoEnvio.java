package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

import java.util.Objects;

/**
 * Respuesta del OSE (o del emisor simulado) a un envío o a una consulta. {@code estado} PENDIENTE significa «el OSE no
 * lo tiene» (solo en una consulta). {@code codigoRespuesta}: el código de SUNAT (0 aceptado, 2000-3999 rechazo, 4000+
 * observación); {@code observaciones}: el texto de las observaciones, si las hay.
 */
public record ResultadoEnvio(EstadoEnvio estado, String respuesta, String codigoHash, String enlacePdf,
		String codigoRespuesta, String observaciones) {

	public ResultadoEnvio {
		Objects.requireNonNull(estado, "estado");
	}

	public ResultadoEnvio(EstadoEnvio estado, String respuesta, String codigoHash, String enlacePdf) {
		this(estado, respuesta, codigoHash, enlacePdf, null, null);
	}
}
