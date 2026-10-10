package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;

import java.util.List;
import java.util.function.Consumer;

/**
 * Recorre los colegios activos para las tareas programadas: cada colegio con {@link ContextoColegio#en} y como el
 * actor de sistema (nunca con {@code comoSistema}, que vería todo). La falla de un colegio se registra en el log y no
 * corta a los demás. Lo usan solo los procesos ({@code ..proceso..}).
 */
@Component
public class RecorridoColegios {

	private static final Logger LOG = LoggerFactory.getLogger(RecorridoColegios.class);

	private final ColegioRepository colegios;

	public RecorridoColegios(ColegioRepository colegios) {
		this.colegios = colegios;
	}

	public List<Long> activos() {
		return colegios.findByActivoTrueOrderByIdAsc().stream().map(Colegio::getId).toList();
	}

	/**
	 * Correcciones del sprint 7 (QA-S7-2): si falló en algún colegio (después de intentar en todos), la pasada queda
	 * marcada ({@link PasadaPorColegios}) y la tarea no deja latido.
	 */
	public void enCadaColegio(ActorSistema actor, Consumer<Long> tarea) {
		PasadaPorColegios.recorrer(LOG, "La tarea de " + actor.usuario(), activos(),
				colegio -> EjecucionComoSistema.como(actor, colegio, () -> tarea.accept(colegio)));
	}
}
