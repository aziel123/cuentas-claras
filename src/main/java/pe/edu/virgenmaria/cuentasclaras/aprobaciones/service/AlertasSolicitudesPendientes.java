package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 9, tanda 2 (A8): las solicitudes de la bandeja que llevan más de {@value #DIAS_HABILES} días hábiles pendientes.
 * Una alerta de atención a Promotoría (al celular, {@link TipoAviso#SOLICITUDES_PENDIENTES}, una vez por la solicitud más
 * antigua): con una sola persona de gobierno, o con Dirección sin tiempo, la segregación se queda sin quien apruebe.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class AlertasSolicitudesPendientes implements AlertasRevision {

	static final int DIAS_HABILES = 2;

	private final SolicitudCambioRepository solicitudes;

	private final CalendarioHabil calendario;

	private final Clock reloj;

	public AlertasSolicitudesPendientes(SolicitudCambioRepository solicitudes, CalendarioHabil calendario, Clock reloj) {
		this.solicitudes = solicitudes;
		this.calendario = calendario;
		this.reloj = reloj;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AlertaRevision> alertas() {
		LocalDate hoy = LocalDate.now(reloj);
		List<SolicitudCambio> viejas = solicitudes.findByEstadoOrderByIdAsc(EstadoSolicitud.PENDIENTE).stream()
				.filter(s -> s.getCreadoEn() != null
						&& calendario.habilesEntre(s.getCreadoEn().toLocalDate(), hoy) > DIAS_HABILES)
				.toList();
		if (viejas.isEmpty()) {
			return List.of();
		}
		return List.of(new AlertaRevision(AlertaRevision.Gravedad.ATENCION, "Aprobaciones", viejas.size()
				+ " solicitud(es) llevan más de " + DIAS_HABILES + " días hábiles por aprobar: revísalas en la bandeja.",
				"/aprobaciones", new Aviso(TipoAviso.SOLICITUDES_PENDIENTES, "PENDIENTES:" + viejas.getFirst().getId(),
						String.valueOf(viejas.size()))));
	}
}
