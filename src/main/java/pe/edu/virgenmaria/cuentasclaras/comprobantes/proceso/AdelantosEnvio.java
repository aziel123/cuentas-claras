package pe.edu.virgenmaria.cuentasclaras.comprobantes.proceso;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.ComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Adelanta el próximo envío o consulta de un comprobante sin resolver cuando Administración lo pide (sprint 7, tanda 2):
 * el estado del envío lo escribe SOLO {@code sistema.ose} con la conexión de {@code cc_sistema} (con {@code cc_app}, 1142).
 * Quien lo pide no toca la fila: lo hace el actor de sistema, en una transacción propia.
 */
@Component
public class AdelantosEnvio {

	private final ComprobanteRepository comprobantes;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public AdelantosEnvio(ComprobanteRepository comprobantes, PlatformTransactionManager transacciones, Clock reloj) {
		this.comprobantes = comprobantes;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
	}

	/** @return el número del comprobante */
	public String adelantar(long colegioId, Long comprobanteId) {
		return EjecucionComoSistema.como(ActorSistema.OSE, colegioId, () -> transaccion.execute(estado -> {
			Comprobante c = comprobantes.findById(comprobanteId).orElseThrow();
			c.adelantarReintento(LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
			comprobantes.save(c);
			return c.numeroCompleto();
		}));
	}
}
