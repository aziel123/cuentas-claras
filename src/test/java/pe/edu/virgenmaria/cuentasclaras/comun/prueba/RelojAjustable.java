package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Reloj que las pruebas pueden adelantar, por ejemplo para ver que una cuenta se desbloquea sola.
 */
public class RelojAjustable extends Clock implements pe.edu.virgenmaria.cuentasclaras.comun.config.RelojMovible {

	private final ZoneId zona;

	private volatile Instant ahora;

	public RelojAjustable(Instant inicio, ZoneId zona) {
		this.ahora = inicio;
		this.zona = zona;
	}

	public void avanzar(Duration duracion) {
		ahora = ahora.plus(duracion);
	}

	@Override
	public void mover(Duration desplazamiento) {
		avanzar(desplazamiento);
	}

	public void fijar(Instant instante) {
		ahora = instante;
	}

	@Override
	public ZoneId getZone() {
		return zona;
	}

	@Override
	public Clock withZone(ZoneId otraZona) {
		return new RelojAjustable(ahora, otraZona);
	}

	@Override
	public Instant instant() {
		return ahora;
	}
}
