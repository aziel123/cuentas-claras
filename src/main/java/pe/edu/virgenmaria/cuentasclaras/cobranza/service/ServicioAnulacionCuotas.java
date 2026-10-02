package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.util.Map;

/**
 * Pide anular una cuota (sin pantalla propia en el sprint 2). Solo deja la <b>solicitud pendiente</b> (marcada en la
 * cuota y como {@code ANULACION_CUOTA} en la bandeja de Aprobaciones): la cuota sigue vigente y se sigue debiendo.
 * La aprueba otra persona ({@link ManejadorAnulacionCuota}). Nada se borra.
 */
@Service
@Transactional
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioAnulacionCuotas {

	private final CuotaRepository cuotas;

	private final AuditoriaService auditoria;

	private final RegistroSolicitudes solicitudes;

	public ServicioAnulacionCuotas(CuotaRepository cuotas, AuditoriaService auditoria, RegistroSolicitudes solicitudes) {
		this.cuotas = cuotas;
		this.auditoria = auditoria;
		this.solicitudes = solicitudes;
	}

	public void solicitar(Long cuotaId, String motivo) {
		Cuota cuota = cuotas.findById(cuotaId).orElseThrow(() -> new RecursoNoEncontradoException("Cuota no encontrada"));
		cuota.solicitarAnulacion(motivo, SesionActual.usuario());
		auditoria.registrar(AccionAuditoria.CUOTA_ANULACION_SOLICITADA, "cuota", cuotaId.toString(),
				cuota.getEstado().name(), "Anulación pendiente de aprobación",
				"Alumno " + cuota.getAlumno().nombreCompleto() + ": " + cuota.getDescripcion() + " "
						+ Dinero.formatear(cuota.getMonto()) + ", vence " + Calendario.formatear(cuota.getFechaVencimiento())
						+ ". Motivo: " + cuota.getAnulacionMotivo());
		solicitudes.crear(TipoSolicitud.ANULACION_CUOTA, "cuota", cuotaId,
				"Anular " + cuota.getDescripcion() + " de " + cuota.getAlumno().nombreCompleto() + " ("
						+ Dinero.formatear(cuota.getMonto()) + ", vence " + Calendario.formatear(cuota.getFechaVencimiento())
						+ ")",
				Map.of("cuotaId", cuotaId.toString()), cuota.getAnulacionMotivo());
	}
}
