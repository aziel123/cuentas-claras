package pe.edu.virgenmaria.cuentasclaras.panel.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.panel.service.DifusionAvisos;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.PasadaPorColegios;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Alertas al celular (sprint 6, tanda 2; decisiones 70 y 71): cada 15 minutos de 07:00 a 20:45 (Lima), en cada colegio y
 * como {@code sistema.panel}, difunde las alertas CRÍTICAS con aviso y las dos ATENCIÓN que también salen. Cada alerta se
 * avisa una sola vez por persona (la clave del mensaje), aunque esta tarea corra dos veces. Lo de la noche, del domingo
 * y de los feriados sale en la primera pasada del siguiente día de mensajes.
 */
@Component
public class AvisosPromotoria {

	private static final Logger LOG = LoggerFactory.getLogger(AvisosPromotoria.class);

	private final DifusionAvisos difusion;

	private final RecorridoColegios colegios;

	private final Clock reloj;

	public AvisosPromotoria(DifusionAvisos difusion, RecorridoColegios colegios, Clock reloj) {
		this.difusion = difusion;
		this.colegios = colegios;
		this.reloj = reloj;
	}

	@Scheduled(cron = "${cuentasclaras.panel.avisos-cada:0 */15 7-20 * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		LocalDate hoy = LocalDate.now(reloj);
		PasadaPorColegios.recorrer(LOG, "Los avisos a Promotoría", colegios.activos(), colegio -> enColegio(colegio, hoy));
	}

	/** Una pasada en un colegio (la tarea programada y las pruebas). @return los avisos publicados */
	public List<Aviso> enColegio(Long colegio, LocalDate fecha) {
		return EjecucionComoSistema.como(ActorSistema.PANEL, colegio, () -> difusion.difundir(fecha));
	}
}
