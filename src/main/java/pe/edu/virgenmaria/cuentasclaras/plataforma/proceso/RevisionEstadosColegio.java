package pe.edu.virgenmaria.cuentasclaras.plataforma.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.CambioEstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.CambioEstadoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoOperadores;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AplicadorCambioEstado;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 11, tanda 1 (F7): cada día, como {@code sistema.plataforma}, revisa que todo colegio fuera de PREPARACION y
 * PRODUCCION esté ahí por SU cambio APLICADO (del mismo destino) y que toda SUSPENSION aprobada o aplicada tenga 30 días o
 * más entre su aprobación y su fecha efectiva. Si no (el DBA cambió el estado a mano o adelantó una fecha), deja el evento
 * CRÍTICO {@code ESTADO_COLEGIO_SIN_CAMBIO} en la bitácora del colegio y avisa al correo de control, a la Promotoría y a los
 * operadores.
 */
@Component
public class RevisionEstadosColegio {

	private static final Logger LOG = LoggerFactory.getLogger(RevisionEstadosColegio.class);

	private final ColegioRepository colegios;

	private final CambioEstadoColegioRepository cambios;

	private final AplicadorCambioEstado aplicador;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public RevisionEstadosColegio(ColegioRepository colegios, CambioEstadoColegioRepository cambios,
			AplicadorCambioEstado aplicador, AuditoriaService auditoria, ApplicationEventPublisher eventos,
			PlatformTransactionManager transacciones, Clock reloj) {
		this.colegios = colegios;
		this.cambios = cambios;
		this.aplicador = aplicador;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	@Scheduled(cron = "${cuentasclaras.ciclo.revision-cron:0 10 5 * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		for (Colegio c : transaccion.execute(t -> colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO))) {
			try {
				revisar(c.getId());
			}
			catch (RuntimeException e) {
				LOG.error("No se pudo revisar el estado del colegio {}: {}", c.getId(), e.getClass().getSimpleName());
			}
		}
	}

	/** Los hallazgos de un colegio (vacío si está en orden); si hay, alerta. La usan también las pruebas. */
	public List<String> revisar(long colegioId) {
		List<String> hallazgos = EjecucionComoSistema.como(ActorSistema.PLATAFORMA, colegioId,
				() -> transaccion.execute(t -> hallazgos(colegioId)));
		if (!hallazgos.isEmpty()) {
			alertar(colegioId, hallazgos);
		}
		return hallazgos;
	}

	private List<String> hallazgos(long colegioId) {
		List<String> hallazgos = new ArrayList<>();
		Colegio colegio = colegios.findById(colegioId).orElseThrow();
		if (!colegio.getEstado().opera()) {
			Optional<CambioEstadoColegio> cambio = Optional.ofNullable(colegio.getCambioEstadoId())
					.flatMap(cambios::findById);
			boolean enOrden = cambio.filter(x -> x.getEstado() == EstadoCambioEstado.APLICADO
					&& x.getEstadoDestino() == colegio.getEstado()).isPresent();
			if (!enOrden) {
				hallazgos.add("el colegio está " + colegio.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT)
						+ " sin su cambio aprobado por dos operadores y aplicado");
			}
		}
		for (CambioEstadoColegio x : cambios.findAllByOrderByIdDesc()) {
			if (x.getTipo() == TipoCambioEstado.SUSPENSION && x.getAprobadoEn() != null && x.getEfectivaDesde() != null
					&& Duration.between(x.getAprobadoEn(), x.getEfectivaDesde()).toDays()
							< CambioEstadoColegio.DIAS_AVISO_SUSPENSION) {
				hallazgos.add("la suspensión " + x.getId() + " tiene menos de " + CambioEstadoColegio.DIAS_AVISO_SUSPENSION
						+ " días de aviso");
			}
		}
		return hallazgos;
	}

	private void alertar(long colegioId, List<String> hallazgos) {
		String que = String.join("; ", hallazgos);
		LocalDate hoy = LocalDate.now(reloj);
		EjecucionComoSistema.como(ActorSistema.PLATAFORMA, colegioId, () -> transaccion.executeWithoutResult(t -> {
			auditoria.registrar(AccionAuditoria.ESTADO_COLEGIO_SIN_CAMBIO, "colegio", Long.toString(colegioId), null, que,
					"Revisión diaria (CRÍTICA): " + que + ". Si nadie lo hizo a propósito con un acta, es un incidente.");
			cambios.findAllByOrderByIdDesc().stream().findFirst().ifPresent(ultimo -> aplicador.avisarAlColegio(colegioId,
					AvisoPlataforma.Tipo.CAMBIO_ESTADO_EN_ESPERA, List.of("la revisión diaria del estado del colegio", que),
					ultimo, "F7:" + hoy));
		}));
		Long plataforma = transaccion.execute(t -> colegios.findFirstByTipo(TipoColegio.PLATAFORMA).map(Colegio::getId)
				.orElse(null));
		if (plataforma != null) {
			EjecucionComoSistema.como(ActorSistema.PLATAFORMA, plataforma, () -> transaccion.executeWithoutResult(t -> {
				eventos.publishEvent(new AvisoOperadores(plataforma, AvisoPlataforma.Tipo.CAMBIO_ESTADO_EN_ESPERA,
						List.of("la revisión diaria del estado del colegio " + colegioId, que), "colegio", colegioId,
						"F7:" + hoy));
				auditoria.registrar(AccionAuditoria.ESTADO_COLEGIO_SIN_CAMBIO, "colegio", Long.toString(colegioId), null,
						que, "Revisión diaria (CRÍTICA) del colegio " + colegioId + ": " + que + ".");
			}));
		}
	}
}
