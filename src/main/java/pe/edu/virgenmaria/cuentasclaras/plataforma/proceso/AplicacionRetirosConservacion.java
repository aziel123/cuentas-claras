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
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Correcciones del sprint 11 (A2): cada 15 minutos, como {@code sistema.plataforma}, aplica los retiros de la autorización
 * de conservación que pidió el titular hace 24 horas o más y que ningún operador frenó (la base exige el plazo). Avisa al
 * colegio y al correo de control.
 */
@Component
public class AplicacionRetirosConservacion {

	private static final Logger LOG = LoggerFactory.getLogger(AplicacionRetirosConservacion.class);

	private final ColegioRepository colegios;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public AplicacionRetirosConservacion(ColegioRepository colegios, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, PlatformTransactionManager transacciones, Clock reloj) {
		this.colegios = colegios;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	@Scheduled(cron = "${cuentasclaras.ciclo.aplicacion-cron:0 */15 * * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		List<Long> vencidos = transaccion.execute(t -> colegios
				.findByConservacionRetiroAplicaDesdeLessThanEqualOrderByIdAsc(LocalDateTime.now(reloj)).stream()
				.map(Colegio::getId).toList());
		for (Long colegio : vencidos) {
			try {
				enColegio(colegio);
			}
			catch (RuntimeException e) {
				LOG.error("No se pudo aplicar el retiro de la conservación del colegio {}: {}", colegio,
						e.getClass().getSimpleName());
			}
		}
	}

	/** @return si lo aplicó */
	public boolean enColegio(long colegioId) {
		return Boolean.TRUE.equals(EjecucionComoSistema.como(ActorSistema.PLATAFORMA, colegioId,
				() -> transaccion.execute(t -> {
					Colegio c = colegios.findById(colegioId).orElseThrow();
					LocalDateTime ahora = LocalDateTime.now(reloj);
					if (c.getConservacionRetiroPor() == null || c.getConservacionRetiroAplicaDesde().isAfter(ahora)) {
						return false;
					}
					String titular = c.getConservacionRetiroPor();
					c.aplicarRetiroConservacion(ahora);
					colegios.saveAndFlush(c);
					auditoria.registrar(AccionAuditoria.CONSERVACION_RETIRADA, "colegio", Long.toString(colegioId),
							"AUTORIZADA", null, "Pasaron las 24 horas del retiro que pidió el titular " + titular
									+ " y ningún operador lo frenó: sin autorización, los datos se suprimen 30 días después "
									+ "del cierre.");
					eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.CONSERVACION,
							List.of("se aplicó el retiro de la autorización de conservar los datos",
									"se suprimen 30 días después del cierre"), "colegio", colegioId, "RETIRADA:" + ahora,
							null, true, true, true));
					return true;
				})));
	}
}
