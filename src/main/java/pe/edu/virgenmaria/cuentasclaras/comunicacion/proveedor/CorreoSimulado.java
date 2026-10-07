package pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CanalMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ProveedorCorreo;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ResultadoEnvio;

/**
 * Correo SIMULADO: nadie recibe nada (queda en {@link BuzonSimulado}). Solo dev, test y piloto, y solo con
 * {@code cuentasclaras.mensajeria.correo.proveedor: SIMULADO}.
 */
@Component
@Profile({ "dev", "test", "piloto" })
@ConditionalOnProperty(name = "cuentasclaras.mensajeria.correo.proveedor", havingValue = "SIMULADO")
public class CorreoSimulado implements ProveedorCorreo {

	private final BuzonSimulado buzon;

	public CorreoSimulado(BuzonSimulado buzon) {
		this.buzon = buzon;
	}

	@Override
	public ResultadoEnvio enviar(String destino, String asunto, String cuerpo) {
		String enlace = cuerpo == null || !cuerpo.contains("/activar/") ? null
				: cuerpo.substring(cuerpo.indexOf("/activar/")).split("\\s")[0];
		return buzon.recibir(CanalMensaje.CORREO, destino, asunto, cuerpo, enlace);
	}
}
