package pe.edu.virgenmaria.cuentasclaras.comprobantes.proceso;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.EnvioComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Cada noche (23:30, hora de Lima) vuelve a consultar al OSE los comprobantes ACEPTADO u OBSERVADO del día. Si el OSE no
 * reconoce alguno (por ejemplo, alguien apuntó el «OSE» a un servidor propio que respondía ACEPTADO), queda en la bitácora
 * y Promotoría lo ve como alerta CRÍTICA. El estado no se toca: el trigger no lo permite.
 */
@Component
public class ReconsultaNocturna {

	private final EnvioComprobantes envio;

	private final RecorridoColegios colegios;

	private final Clock reloj;

	public ReconsultaNocturna(EnvioComprobantes envio, RecorridoColegios colegios, Clock reloj) {
		this.envio = envio;
		this.colegios = colegios;
		this.reloj = reloj;
	}

	@Scheduled(cron = "${cuentasclaras.comprobantes.reconsulta-nocturna-cron:0 30 23 * * *}",
			zone = ConfiguracionTiempo.ZONA)
	public void reconsultar() {
		LocalDate hoy = LocalDate.now(reloj);
		colegios.enCadaColegio(ActorSistema.OSE, colegio -> envio.reconsultar(hoy));
	}
}
