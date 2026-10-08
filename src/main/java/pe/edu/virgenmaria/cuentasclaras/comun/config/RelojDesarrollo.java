package pe.edu.virgenmaria.cuentasclaras.comun.config;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Reloj de la aplicación en desarrollo (perfil {@code dev}): la hora de Lima más un desplazamiento que solo mueven los
 * datos de demostración al arrancar (para dejar «un día anterior» con cajas cerradas) y que vuelven a 0 al terminar.
 * Nunca se activa en {@code prod}: ahí el reloj es el del sistema ({@link ConfiguracionTiempo}).
 */
@Component
@Primary
@Profile("dev")
public class RelojDesarrollo extends Clock implements RelojMovible {

	private final Clock sistema = Clock.system(ConfiguracionTiempo.ZONA_LIMA);

	private volatile Duration desplazamiento = Duration.ZERO;

	@Override
	public void mover(Duration nuevo) {
		desplazamiento = desplazamiento.plus(nuevo);
	}

	@Override
	public ZoneId getZone() {
		return sistema.getZone();
	}

	@Override
	public Clock withZone(ZoneId zona) {
		return Clock.offset(sistema.withZone(zona), desplazamiento);
	}

	@Override
	public Instant instant() {
		return sistema.instant().plus(desplazamiento);
	}
}
