package pe.edu.virgenmaria.cuentasclaras.panel.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.virgenmaria.cuentasclaras.caja.service.CierreConDiferencia;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.panel.service.DifusionAvisos;

/**
 * Correcciones del sprint 6 (S6-M3): un cierre de caja con faltante o sobrante sale al celular de Promotoría APENAS se
 * confirma, como {@code sistema.panel} y con la MISMA clave que usa la pasada de cada 15 minutos
 * ({@code ALERTA:CIERRE_CON_DIFERENCIA:C:<id>}): se avisa una sola vez aunque después lo vea también la pasada. Antes, si
 * Dirección lo aprobaba entre dos pasadas (de noche, por ejemplo), la alerta dejaba de existir y el faltante no salía
 * nunca. Corre después del commit (sin cierre confirmado no hay aviso); si falla, solo queda en el log: la pasada y el
 * resumen diario (que informa los cierres con diferencia desde el anterior) siguen siendo el respaldo.
 */
@Component
public class AvisoCierreConDiferencia {

	private static final Logger LOG = LoggerFactory.getLogger(AvisoCierreConDiferencia.class);

	private final DifusionAvisos difusion;

	public AvisoCierreConDiferencia(DifusionAvisos difusion) {
		this.difusion = difusion;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alCerrar(CierreConDiferencia evento) {
		try {
			Aviso aviso = new Aviso(TipoAviso.CIERRE_CON_DIFERENCIA, "C:" + evento.cierreId(),
					Dinero.formatear(evento.diferencia()));
			EjecucionComoSistema.como(ActorSistema.PANEL, evento.colegioId(), () -> difusion.avisarYa(aviso));
		}
		catch (RuntimeException e) {
			LOG.error("No se pudo avisar al celular el cierre con diferencia {}: {}", evento.cierreId(),
					e.getClass().getSimpleName());
		}
	}
}
