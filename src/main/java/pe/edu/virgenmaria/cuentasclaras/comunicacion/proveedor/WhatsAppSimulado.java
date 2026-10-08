package pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CanalMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ProveedorWhatsApp;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ResultadoEnvio;

import java.util.List;

/**
 * WhatsApp SIMULADO: nadie recibe nada (queda en {@link BuzonSimulado}). Solo existe en dev, test y piloto (capa 2 de
 * la sección 8.3) y solo si {@code cuentasclaras.mensajeria.whatsapp.proveedor: SIMULADO}. En MySQL, un mensaje
 * simulado solo pasa a ENVIADO si el DBA habilitó la base (trg_mensaje_envio).
 */
@Component
@Profile({ "dev", "test", "piloto" })
@ConditionalOnProperty(name = "cuentasclaras.mensajeria.whatsapp.proveedor", havingValue = "SIMULADO")
public class WhatsAppSimulado implements ProveedorWhatsApp {

	private final BuzonSimulado buzon;

	public WhatsAppSimulado(BuzonSimulado buzon) {
		this.buzon = buzon;
	}

	@Override
	public ResultadoEnvio enviar(PlantillaMensaje plantilla, String destino, List<String> parametros, String sufijoBoton) {
		return buzon.recibir(CanalMensaje.WHATSAPP, destino, plantilla.nombreMeta(), plantilla.componer(parametros),
				sufijoBoton);
	}
}
