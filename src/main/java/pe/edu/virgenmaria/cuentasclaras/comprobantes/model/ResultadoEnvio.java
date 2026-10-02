package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

import java.util.Objects;

/** Respuesta del OSE (o del emisor simulado) a un envío. */
public record ResultadoEnvio(EstadoEnvio estado, String respuesta, String codigoHash, String enlacePdf) {

	public ResultadoEnvio {
		Objects.requireNonNull(estado, "estado");
	}
}
