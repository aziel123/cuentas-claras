package pe.edu.virgenmaria.cuentasclaras.plataforma.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.EmisorLibretas;
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
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.PasadaPorColegios;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AplicadorCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.PendientesCicloDeVida;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CierreSesionesColegio;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 11, tanda 1 (secciones 3.3, 3.4 y 3.13): cada 15 minutos, como {@code sistema.plataforma} ({@code cc_sistema}),
 * aplica en cada colegio los cambios APROBADOS cuya fecha llegó (la baja a los 7 días y la suspensión a los 30). La
 * suspensión ESPERA mientras el colegio tenga cierres de caja por revisar u observados, comprobantes sin aceptar (AP-4),
 * pagos en línea en curso (H3), libretas sin emitir de un periodo cerrado (AP-13) o la objeción del titular sin responder
 * (AP-10); cada día que espera avisa una vez al correo de control, a la Promotoría y a los operadores. Recuerda la
 * suspensión a los 7 días y a 1 día. Al aplicar, cierra las sesiones del personal (las de las familias siguen).
 */
@Component
public class AplicacionCambiosEstado {

	private static final Logger LOG = LoggerFactory.getLogger(AplicacionCambiosEstado.class);

	private final ColegioRepository colegios;

	private final CambioEstadoColegioRepository cambios;

	private final AplicadorCambioEstado aplicador;

	private final PendientesCicloDeVida pendientes;

	private final EmisorLibretas libretas;

	private final CierreSesionesColegio cierreSesiones;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	/** El último día en que se avisó la espera de cada suspensión (una alerta por día; la clave del mensaje también). */
	private final java.util.Map<Long, LocalDate> esperaAvisada = new java.util.concurrent.ConcurrentHashMap<>();

	public AplicacionCambiosEstado(ColegioRepository colegios, CambioEstadoColegioRepository cambios,
			AplicadorCambioEstado aplicador, PendientesCicloDeVida pendientes, EmisorLibretas libretas,
			CierreSesionesColegio cierreSesiones, AuditoriaService auditoria, ApplicationEventPublisher eventos,
			PlatformTransactionManager transacciones, Clock reloj) {
		this.colegios = colegios;
		this.cambios = cambios;
		this.aplicador = aplicador;
		this.pendientes = pendientes;
		this.libretas = libretas;
		this.cierreSesiones = cierreSesiones;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	@Scheduled(cron = "${cuentasclaras.ciclo.aplicacion-cron:0 */15 * * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		List<Long> todos = transaccion.execute(t -> colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO).stream()
				.map(Colegio::getId).toList());
		PasadaPorColegios.recorrer(LOG, "La aplicación de los cambios de estado", todos, this::enColegio);
	}

	/** Lo que espera la suspensión en el colegio (para la bandeja y las pruebas). */
	public List<String> esperan(long colegioId, CambioEstadoColegio cambio) {
		List<String> motivos = new ArrayList<>(EjecucionComoSistema.como(ActorSistema.PLATAFORMA, colegioId,
				() -> transaccion.execute(t -> pendientes.esperanLaSuspension())));
		int sinEmitir = EjecucionComoSistema.como(ActorSistema.LIBRETAS, colegioId,
				() -> transaccion.execute(t -> libretas.pendientes().size()));
		if (sinEmitir > 0) {
			motivos.add(sinEmitir + (sinEmitir == 1 ? " sección con libretas sin emitir" : " secciones con libretas sin emitir"));
		}
		if (cambio.objecionAbierta()) {
			motivos.add("la objeción del titular sin responder");
		}
		return motivos;
	}

	/** La pasada de un colegio (la usan también las pruebas). Devuelve cuántos cambios aplicó. */
	public int enColegio(long colegioId) {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		List<CambioEstadoColegio> aprobados = EjecucionComoSistema.como(ActorSistema.PLATAFORMA, colegioId,
				() -> transaccion.execute(t -> cambios.findByTipoAndEstadoOrderByIdAsc(TipoCambioEstado.SUSPENSION,
						EstadoCambioEstado.APROBADO)));
		for (CambioEstadoColegio s : aprobados) {
			recordar(colegioId, s, ahora.toLocalDate());
		}
		List<CambioEstadoColegio> vencidos = EjecucionComoSistema.como(ActorSistema.PLATAFORMA, colegioId,
				() -> transaccion.execute(t -> cambios.findByEstadoAndEfectivaDesdeLessThanEqualOrderByIdAsc(
						EstadoCambioEstado.APROBADO, ahora)));
		int aplicados = 0;
		for (CambioEstadoColegio cambio : vencidos) {
			if (cambio.getTipo() == TipoCambioEstado.SUSPENSION) {
				List<String> esperan = esperan(colegioId, cambio);
				if (!esperan.isEmpty()) {
					avisarEspera(colegioId, cambio, esperan, ahora.toLocalDate());
					continue;
				}
			}
			List<Long> cerradas = EjecucionComoSistema.como(ActorSistema.PLATAFORMA, colegioId,
					() -> transaccion.execute(t -> {
						CambioEstadoColegio vigente = cambios.bloquear(cambio.getId()).orElseThrow();
						if (vigente.getEstado() != EstadoCambioEstado.APROBADO) {
							return List.<Long>of();
						}
						return aplicador.aplicar(vigente, LocalDateTime.now(reloj));
					}));
			cierreSesiones.expirarHttp(cerradas);
			aplicados++;
		}
		return aplicados;
	}

	/** Recordatorio a 7 días y a 1 día de la suspensión (una vez cada uno: la clave del aviso lo garantiza). */
	private void recordar(long colegioId, CambioEstadoColegio s, LocalDate hoy) {
		long dias = ChronoUnit.DAYS.between(hoy, s.getEfectivaDesde().toLocalDate());
		if (dias != 7 && dias != 1) {
			return;
		}
		EjecucionComoSistema.como(ActorSistema.PLATAFORMA, colegioId, () -> transaccion.executeWithoutResult(t -> {
			aplicador.avisarAlColegio(colegioId, AvisoPlataforma.Tipo.SUSPENSION_PROXIMA, List.of(
					"la pausa del acceso del personal", Calendario.formatear(s.getEfectivaDesde().toLocalDate())), s, "R" + dias);
			auditoria.registrar(AccionAuditoria.SUSPENSION_PROXIMA, "cambio_estado_colegio", s.getId().toString(), null,
					dias + " día(s)", "Recordatorio: la suspensión del acceso del personal rige desde el "
							+ Calendario.formatear(s.getEfectivaDesde().toLocalDate()) + ".");
		}));
	}

	/** AP-4 y AP-13: una alerta por día mientras la suspensión espera, al colegio y a los operadores. */
	private void avisarEspera(long colegioId, CambioEstadoColegio cambio, List<String> esperan, LocalDate hoy) {
		String que = String.join(", ", esperan);
		if (hoy.equals(esperaAvisada.put(cambio.getId(), hoy))) {
			return;
		}
		try {
			EjecucionComoSistema.como(ActorSistema.PLATAFORMA, colegioId, () -> transaccion.executeWithoutResult(t -> {
				aplicador.avisarAlColegio(colegioId, AvisoPlataforma.Tipo.CAMBIO_ESTADO_EN_ESPERA, List.of(
						"la pausa del acceso del personal", que), cambio, "E" + hoy);
				auditoria.registrar(AccionAuditoria.SUSPENSION_EN_ESPERA, "cambio_estado_colegio", cambio.getId().toString(),
						null, que, "La suspensión no se aplica todavía: el colegio tiene " + que + ".");
			}));
			Long plataforma = transaccion.execute(t -> colegios.findFirstByTipo(TipoColegio.PLATAFORMA).map(Colegio::getId)
					.orElse(null));
			if (plataforma != null) {
				EjecucionComoSistema.como(ActorSistema.PLATAFORMA, plataforma, () -> transaccion.executeWithoutResult(t ->
						eventos.publishEvent(new AvisoOperadores(plataforma, AvisoPlataforma.Tipo.CAMBIO_ESTADO_EN_ESPERA,
								List.of("la pausa del acceso del personal del colegio " + colegioId, que), "colegio", colegioId,
								"E" + cambio.getId() + ":" + hoy))));
			}
		}
		catch (RuntimeException e) {
			// La clave del aviso del día ya existe (se avisó hoy) u otro error: la espera sigue y se reintenta.
			LOG.info("La alerta de espera de la suspensión del colegio {} no se creó: {}", colegioId,
					e.getClass().getSimpleName());
		}
	}
}
