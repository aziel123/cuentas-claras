package pe.edu.virgenmaria.cuentasclaras.plataforma.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoOperadores;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ConciliacionBloqueada;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 11, tanda 2 (pendiente de la 1c, A1c-4): cuando la base del colegio rechazó que quien cambió la cuenta bancaria
 * confirme la conciliación, avisa también a los operadores de la plataforma (la Promotoría y el correo de control ya
 * recibieron la alerta CRÍTICA en la misma transacción del colegio). Va después de confirmarla, en el colegio de
 * plataforma, por {@link EjecucionPlataforma}; si falla, queda en el log (el testigo del colegio ya salió).
 */
@Component
public class AvisosConciliacionOperadores {

	private static final Logger LOG = LoggerFactory.getLogger(AvisosConciliacionOperadores.class);

	private final ColegioRepository colegios;

	private final EjecucionPlataforma plataforma;

	private final ApplicationEventPublisher eventos;

	private final AuditoriaService auditoria;

	private final TransactionTemplate lectura;

	public AvisosConciliacionOperadores(ColegioRepository colegios, EjecucionPlataforma plataforma,
			ApplicationEventPublisher eventos, AuditoriaService auditoria, PlatformTransactionManager transacciones) {
		this.colegios = colegios;
		this.plataforma = plataforma;
		this.eventos = eventos;
		this.auditoria = auditoria;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alBloquear(ConciliacionBloqueada bloqueo) {
		// Después de confirmar, la transacción del colegio sigue enlazada al hilo hasta terminar: el aviso va en un hilo
		// propio (con la misma sesión), que abre su transacción en el colegio de plataforma. Se espera a que termine.
		Thread hilo = new Thread(new org.springframework.security.concurrent.DelegatingSecurityContextRunnable(
				() -> avisar(bloqueo)), "aviso-conciliacion-operadores");
		hilo.start();
		try {
			hilo.join(30_000);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void avisar(ConciliacionBloqueada bloqueo) {
		try {
			Long plataformaId = lectura.execute(t -> colegios.findFirstByTipo(TipoColegio.PLATAFORMA).map(Colegio::getId)
					.orElse(null));
			if (plataformaId == null) {
				return;
			}
			String colegio = lectura.execute(t -> colegios.findById(bloqueo.colegioId()).map(Colegio::getNombre).orElse("?"));
			List<String> parametros = new ArrayList<>(bloqueo.parametros());
			parametros.set(0, parametros.getFirst() + " (" + colegio + ")");
			ContextoColegio.en(plataformaId, () -> plataforma.ejecutar(() -> {
				eventos.publishEvent(new AvisoOperadores(plataformaId, AvisoPlataforma.Tipo.CONCILIACION_BLOQUEADA, parametros,
						"colegio", bloqueo.colegioId(), bloqueo.entidad() + ":" + bloqueo.entidadId() + ":"
						+ Integer.toHexString(bloqueo.parametros().getFirst().hashCode())));
				auditoria.registrar(AccionAuditoria.CONCILIACION_TRAS_CAMBIO_RECHAZADA, "colegio",
						bloqueo.colegioId().toString(), null, null, "Colegio " + colegio + ": " + String.join(" · ",
								bloqueo.parametros()) + ". Se avisó a los operadores.");
			}));
		}
		catch (RuntimeException e) {
			LOG.warn("No se pudo avisar a los operadores del bloqueo de conciliación del colegio {}: {}", bloqueo.colegioId(),
					e.getClass().getSimpleName());
		}
	}
}
