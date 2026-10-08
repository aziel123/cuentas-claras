package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.FechaIngresoCambiada;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoCuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Ingreso tardío aprobado (auditoría A3): anula, con quien lo pidió y quien lo aprobó, las pensiones que vencen antes
 * del mes de ingreso (las mismas que {@code CalculadoraCronograma} no generaría). La cuota de matrícula no cambia: su
 * vencimiento es inmutable. Si una de esas pensiones tiene pagos, no se aprueba.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AjusteIngresoTardio {

	private final CuotaRepository cuotas;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public AjusteIngresoTardio(CuotaRepository cuotas, AuditoriaService auditoria, Clock reloj) {
		this.cuotas = cuotas;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@EventListener
	public void alCambiarFechaIngreso(FechaIngresoCambiada evento) {
		LocalDate desde = Calendario.primerDiaDelMes(evento.fecha());
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		for (Cuota cuota : cuotas.findByMatriculaIdAndTipoOrderByFechaVencimientoAscIdAsc(evento.matriculaId(),
				TipoCuota.PENSION)) {
			if (cuota.anulada() || !cuota.getFechaVencimiento().isBefore(desde)) {
				continue;
			}
			if (cuota.getMontoPagado().signum() > 0) {
				throw new ReglaNegocioException(cuota.getDescripcion() + " de " + cuota.getAlumno().nombreCompleto()
						+ " ya tiene pagos: no se puede aprobar el ingreso tardío.");
			}
			String estadoAnterior = cuota.getEstado().name();
			String motivo = "Ingreso tardío aprobado: ingresó el " + Calendario.formatear(evento.fecha()) + ".";
			cuota.anular(motivo, evento.solicitante(), evento.aprobador(), ahora, evento.solicitudId());
			cuotas.saveAndFlush(cuota);
			auditoria.registrar(AccionAuditoria.CUOTA_ANULADA, "cuota", cuota.getId().toString(), estadoAnterior,
					cuota.getEstado().name(), "Alumno " + cuota.getAlumno().nombreCompleto() + ": "
							+ cuota.getDescripcion() + " " + Dinero.formatear(cuota.getMonto()) + ". Pedido por "
							+ evento.solicitante() + ", aprobado por " + evento.aprobador() + ". " + motivo);
		}
	}
}
