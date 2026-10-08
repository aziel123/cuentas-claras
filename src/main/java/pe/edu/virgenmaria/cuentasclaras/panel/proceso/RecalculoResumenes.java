package pe.edu.virgenmaria.cuentasclaras.panel.proceso;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.panel.service.ResumenesDiarios;

/**
 * Cada mañana (06:15, Lima), en cada colegio y como {@code sistema.panel}: recalcula las fotos del resumen de los últimos
 * {@code recalculo-dias} (35) días contra los libros y deja en la bitácora lo que cambió (P4; resaltado si nada lo
 * explica). La alerta CRÍTICA la calcula el panel al consultar, así que no espera a esta tarea.
 */
@Component
public class RecalculoResumenes {

	private final ResumenesDiarios resumenes;

	private final RecorridoColegios colegios;

	public RecalculoResumenes(ResumenesDiarios resumenes, RecorridoColegios colegios) {
		this.resumenes = resumenes;
		this.colegios = colegios;
	}

	@Scheduled(cron = "${cuentasclaras.panel.recalculo:0 15 6 * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		colegios.enCadaColegio(ActorSistema.PANEL, colegio -> resumenes.registrarCambios());
	}

	/** El recálculo de un colegio (las pruebas lo llaman directamente). @return hallazgos nuevos en la bitácora */
	public int enColegio(Long colegio) {
		return EjecucionComoSistema.como(ActorSistema.PANEL, colegio, () -> resumenes.registrarCambios());
	}
}
