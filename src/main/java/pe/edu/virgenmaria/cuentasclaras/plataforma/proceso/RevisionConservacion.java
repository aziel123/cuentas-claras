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
import pe.edu.virgenmaria.cuentasclaras.colegio.model.ActaSupresion;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ActaSupresionRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoOperadores;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;

import java.time.LocalDate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 11, tanda 2 (3.11 y AP2-12): cada día, como {@code sistema.plataforma}:
 * <ul>
 * <li>un colegio CERRADO o en BAJA cuya conservación vence en 30 días o hoy: aviso al colegio (correo institucional, de
 * control y Promotoría) y a los operadores (para proponer el acta);</li>
 * <li>un acta EJECUTADA cuyo último respaldo con datos del colegio vence en 60 días: aviso a los operadores; el día que
 * vence, también al correo de control del colegio (escala). Nunca suprime nada: la supresión la ejecuta el DBA.</li>
 * </ul>
 */
@Component
public class RevisionConservacion {

	static final int DIAS_AVISO_CONSERVACION = 30;

	static final int DIAS_AVISO_RESPALDOS = 60;

	private static final Logger LOG = LoggerFactory.getLogger(RevisionConservacion.class);

	private final ColegioRepository colegios;

	private final ActaSupresionRepository actas;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public RevisionConservacion(ColegioRepository colegios, ActaSupresionRepository actas, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, PlatformTransactionManager transacciones, Clock reloj) {
		this.colegios = colegios;
		this.actas = actas;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	@Scheduled(cron = "${cuentasclaras.ciclo.conservacion-cron:0 20 5 * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		for (Colegio c : transaccion.execute(t -> colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO))) {
			try {
				revisar(c.getId());
			}
			catch (RuntimeException e) {
				LOG.error("No se pudo revisar la conservación del colegio {}: {}", c.getId(), e.getClass().getSimpleName());
			}
		}
	}

	/** Los avisos que dio hoy para el colegio (vacío si ninguno). La usan también las pruebas. */
	public List<String> revisar(long colegioId) {
		LocalDate hoy = LocalDate.now(reloj);
		List<String> avisos = new ArrayList<>();
		Colegio colegio = transaccion.execute(t -> colegios.findById(colegioId).orElseThrow());
		LocalDate hasta = colegio.getConservarHasta();
		if ((colegio.getEstado() == EstadoColegio.CERRADO || colegio.getEstado() == EstadoColegio.BAJA) && hasta != null
				&& (hoy.equals(hasta) || hoy.equals(hasta.minusDays(DIAS_AVISO_CONSERVACION)))) {
			String que = hoy.equals(hasta) ? "la conservación de los datos vence hoy"
					: "la conservación de los datos vence en " + DIAS_AVISO_CONSERVACION + " días";
			avisos.add(que);
			EjecucionComoSistema.como(ActorSistema.PLATAFORMA, colegioId, () -> transaccion.executeWithoutResult(t -> {
				auditoria.registrar(AccionAuditoria.CONSERVACION_POR_VENCER, "colegio", Long.toString(colegioId), null,
						hasta.toString(), "Revisión diaria: " + que + " (" + Calendario.formatear(hasta) + "). Después, "
								+ "nadie entra y los operadores proponen el acta de supresión.");
				eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.CONSERVACION, List.of(que,
						"fecha de supresión: " + Calendario.formatear(hasta)), "colegio", colegioId, "VENCE:" + hoy, null,
						true, true, true));
			}));
			aOperadores(que + " (" + colegio.getNombre() + ")", colegioId, hoy, AccionAuditoria.CONSERVACION_POR_VENCER);
		}
		Optional<ActaSupresion> acta = EjecucionComoSistema.como(ActorSistema.PLATAFORMA, colegioId,
				() -> transaccion.execute(t -> actas.findFirstByIdNotNull()));
		acta.filter(a -> a.getEstado() == ActaSupresion.Estado.EJECUTADA).ifPresent(a -> {
			LocalDate vence = a.getRespaldosVencenEl();
			if (hoy.equals(vence.minusDays(DIAS_AVISO_RESPALDOS)) || hoy.equals(vence)) {
				String que = hoy.equals(vence) ? "vence hoy el último respaldo con datos del colegio suprimido: confirma "
						+ "que se borró" : "el último respaldo con datos del colegio suprimido vence en "
								+ DIAS_AVISO_RESPALDOS + " días";
				avisos.add(que);
				if (hoy.equals(vence)) {
					try {
						EjecucionComoSistema.como(ActorSistema.PLATAFORMA, colegioId, () -> transaccion.executeWithoutResult(
								t -> eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.CONSERVACION,
										List.of("vence hoy el último respaldo con sus datos", "acta de supresión " + a.getId()),
										"acta_supresion", a.getId(), "RESPALDOS:" + hoy, null, false, true, false))));
					}
					catch (RuntimeException e) {
						// El colegio suprimido puede no tener ya a quién avisar: los operadores sí reciben la alerta.
						LOG.warn("Sin destinatario en el colegio {} para el aviso de respaldos", colegioId);
					}
				}
				aOperadores(que + " (" + colegio.getNombre() + ", acta " + a.getId() + ")", colegioId, hoy,
						AccionAuditoria.RESPALDOS_CON_DATOS_POR_VENCER);
			}
		});
		return avisos;
	}

	private void aOperadores(String que, long colegioId, LocalDate hoy, AccionAuditoria accion) {
		Long plataforma = transaccion.execute(t -> colegios.findFirstByTipo(TipoColegio.PLATAFORMA).map(Colegio::getId)
				.orElse(null));
		if (plataforma == null) {
			return;
		}
		EjecucionComoSistema.como(ActorSistema.PLATAFORMA, plataforma, () -> transaccion.executeWithoutResult(t -> {
			eventos.publishEvent(new AvisoOperadores(plataforma, AvisoPlataforma.Tipo.CONSERVACION, List.of(que,
					"revisa el fin del colegio " + colegioId), "colegio", colegioId, accion.name() + ":" + hoy));
			auditoria.registrar(accion, "colegio", Long.toString(colegioId), null, null, "Revisión diaria: " + que + ".");
		}));
	}
}
