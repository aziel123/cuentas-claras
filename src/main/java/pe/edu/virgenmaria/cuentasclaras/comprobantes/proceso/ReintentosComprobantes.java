package pe.edu.virgenmaria.cuentasclaras.comprobantes.proceso;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.EnvioComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;

/**
 * Outbox del OSE: cada minuto, en cada colegio y como {@code sistema.ose}, reintenta los PENDIENTE cuyo reintento ya
 * toca y consulta los ENVIADO (hasta 20 por colegio, el más antiguo primero), cada uno en su propia transacción.
 */
@Component
public class ReintentosComprobantes {

	private final EnvioComprobantes envio;

	private final RecorridoColegios colegios;

	public ReintentosComprobantes(EnvioComprobantes envio, RecorridoColegios colegios) {
		this.envio = envio;
		this.colegios = colegios;
	}

	@Scheduled(fixedDelayString = "${cuentasclaras.comprobantes.outbox-cada:60s}", initialDelayString = "30s")
	public void reintentar() {
		colegios.enCadaColegio(ActorSistema.OSE, colegio -> procesarColegio());
	}

	/** Una pasada en el colegio actual (lo fija {@link RecorridoColegios}). @return cuántos procesó */
	public int procesarColegio() {
		int procesados = 0;
		for (Long id : envio.porEnviar()) {
			envio.procesar(id);
			procesados++;
		}
		return procesados;
	}
}
