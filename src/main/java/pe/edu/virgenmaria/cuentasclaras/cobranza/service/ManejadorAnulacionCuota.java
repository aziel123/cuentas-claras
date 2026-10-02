package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Anulación de una cuota aprobada por otra persona en la bandeja. Bloquea el año antes de auditar (regla de orden de
 * bloqueos); si se rechaza, la cuota deja de estar marcada como «anulación pendiente».
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorAnulacionCuota implements ManejadorSolicitud {

	private final CuotaRepository cuotas;

	private final AnioEscolarRepository anios;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ManejadorAnulacionCuota(CuotaRepository cuotas, AnioEscolarRepository anios, AuditoriaService auditoria,
			Clock reloj) {
		this.cuotas = cuotas;
		this.anios = anios;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.ANULACION_CUOTA;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Cuota cuota = buscar(solicitud);
		anios.bloquear(cuota.getAnioEscolar().getId());
		String estadoAnterior = cuota.getEstado().name();
		cuota.anular(solicitud.getMotivo(), solicitud.getSolicitadoPor(), aprobador,
				LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
		cuotas.saveAndFlush(cuota);
		auditoria.registrar(AccionAuditoria.CUOTA_ANULADA, "cuota", cuota.getId().toString(), estadoAnterior,
				cuota.getEstado().name(), "Alumno " + cuota.getAlumno().nombreCompleto() + ": " + cuota.getDescripcion()
						+ " " + Dinero.formatear(cuota.getMonto()) + ". Pedido por " + solicitud.getSolicitadoPor()
						+ ", aprobado por " + aprobador + ". Motivo: " + solicitud.getMotivo());
	}

	@Override
	public void alRechazar(SolicitudCambio solicitud) {
		Cuota cuota = buscar(solicitud);
		if (cuota.anulacionPendiente()) {
			cuota.descartarSolicitudAnulacion();
			cuotas.saveAndFlush(cuota);
		}
	}

	private Cuota buscar(SolicitudCambio solicitud) {
		return cuotas.findById(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("La cuota de la solicitud no existe."));
	}
}
