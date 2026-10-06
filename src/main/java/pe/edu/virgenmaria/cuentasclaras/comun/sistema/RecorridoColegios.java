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

	public void enCadaColegio(ActorSistema actor, Consumer<Long> tarea) {
		for (Long colegio : activos()) {
			try {
				EjecucionComoSistema.como(actor, colegio, () -> tarea.accept(colegio));
			}
			catch (RuntimeException e) {
				LOG.error("La tarea de {} falló en el colegio {}: {}", actor.usuario(), colegio, e.getClass().getSimpleName());
			}
		}
	}
}
