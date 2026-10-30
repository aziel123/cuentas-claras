package pe.edu.virgenmaria.cuentasclaras.exportacion.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoOperadores;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.Exportador;

import java.util.List;

/**
 * Sprint 11, tanda 2 (AP2-13): una exportación FALLIDA (una libreta que no coincide con su huella, o el archivo que no se
 * pudo generar) es una alerta CRÍTICA también para los operadores: el aviso y el evento van en el colegio de plataforma,
 * en su propia transacción, después de dejar la exportación FALLIDA en el colegio.
 */
@Component
public class AlertaExportacionFallida {

	private static final Logger LOG = LoggerFactory.getLogger(AlertaExportacionFallida.class);

	private final ColegioRepository colegios;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final TransactionTemplate transaccion;

	public AlertaExportacionFallida(ColegioRepository colegios, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, PlatformTransactionManager transacciones) {
		this.colegios = colegios;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@EventListener
	public void alFallar(Exportador.ExportacionFallida falla) {
		try {
			Long plataforma = transaccion.execute(t -> colegios.findFirstByTipo(TipoColegio.PLATAFORMA)
					.map(Colegio::getId).orElse(null));
			if (plataforma == null) {
				return;
			}
			EjecucionComoSistema.como(ActorSistema.EXPORTACION, plataforma, () -> transaccion.executeWithoutResult(t -> {
				eventos.publishEvent(new AvisoOperadores(plataforma, AvisoPlataforma.Tipo.EXPORTACION_FALLIDA,
						List.of(falla.motivo() + " (colegio " + falla.colegioId() + ")"), "exportacion_colegio",
						falla.exportacionId(), "FALLIDA"));
				auditoria.registrar(AccionAuditoria.EXPORTACION_FALLIDA, "exportacion_colegio",
						falla.exportacionId().toString(), null, "FALLIDA", "Alerta CRÍTICA: la exportación "
								+ falla.exportacionId() + " del colegio " + falla.colegioId() + " falló: " + falla.motivo()
								+ ".");
			}));
		}
		catch (RuntimeException e) {
			LOG.error("ALERTA: no se pudo avisar a los operadores de la exportación fallida {}", falla.exportacionId(), e);
		}
	}
}
