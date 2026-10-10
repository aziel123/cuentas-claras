package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.ScheduledTaskObservationContext;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Sprint 7 (monitoreo): una tarea programada deja su latido solo si terminó sin error. */
class ObservadorLatidosTest {

	public void tarea() {
		// método observado
	}

	@Test
	void soloLateLaTareaQueTerminoBien() throws Exception {
		RelojAjustable reloj = new RelojAjustable(Instant.parse("2026-10-08T15:00:00Z"), ConfiguracionTiempo.ZONA_LIMA);
		Latidos latidos = new Latidos(reloj);
		ObservadorLatidos observador = new ObservadorLatidos(latidos);
		ScheduledTaskObservationContext incompleta = new ScheduledTaskObservationContext(this,
				ObservadorLatidosTest.class.getMethod("tarea"));

		assertThat(observador.supportsContext(incompleta)).isTrue();
		observador.onStop(incompleta);
		assertThat(latidos.ultimos()).isEmpty();

		ScheduledTaskObservationContext conError = new ScheduledTaskObservationContext(this,
				ObservadorLatidosTest.class.getMethod("tarea"));
		conError.setError(new IllegalStateException("x"));
		conError.setComplete(true);
		observador.onStop(conError);
		assertThat(latidos.ultimos()).isEmpty();

		ScheduledTaskObservationContext bien = new ScheduledTaskObservationContext(this,
				ObservadorLatidosTest.class.getMethod("tarea"));
		bien.setComplete(true);
		observador.onStop(bien);
		assertThat(latidos.ultimos()).containsEntry("ObservadorLatidosTest.tarea", reloj.instant());
	}

	@Test
	void laClaveEsLaMismaDesdeElMetodoYDesdeElTextoDeLaTarea() throws Exception {
		assertThat(ObservadorLatidos.nombre(ObservadorLatidosTest.class.getMethod("tarea")))
				.isEqualTo("ObservadorLatidosTest.tarea");
		assertThat(ObservadorLatidos.nombre("pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes.despachar"))
				.isEqualTo("DespachoMensajes.despachar");
		assertThat(ObservadorLatidos.nombre("pe.edu.Externa$Interna.metodo")).isEqualTo("Interna.metodo");
	}
}
