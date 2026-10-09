package pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Adelanta el siguiente intento de un mensaje PENDIENTE cuando Administración lo pide (sprint 7, tanda 2): el estado de
 * un mensaje lo escribe SOLO {@code sistema.mensajeria} con la conexión de {@code cc_sistema}. Nunca cambia el destino ni
 * el texto (no están en su GRANT).
 */
@Component
public class AdelantosMensajes {

	private final MensajeRepository mensajes;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public AdelantosMensajes(MensajeRepository mensajes, PlatformTransactionManager transacciones, Clock reloj) {
		this.mensajes = mensajes;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
	}

	public void adelantar(long colegioId, Long mensajeId) {
		EjecucionComoSistema.como(ActorSistema.MENSAJERIA, colegioId, () -> transaccion.executeWithoutResult(estado -> {
			Mensaje mensaje = mensajes.bloquear(mensajeId).orElseThrow();
			mensaje.adelantar(LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
			mensajes.saveAndFlush(mensaje);
		}));
	}
}
