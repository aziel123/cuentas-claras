package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Ejecuta la devolución APROBADA de un ingreso por revisar: la pasarela reembolsa el cargo SOLO al mismo medio de
 * origen (nadie puede desviarla a otra cuenta) y la orden queda DEVUELTA con el id del reembolso. La ejecuta
 * Administración y nunca quien la aprobó (también lo exige el trigger de la orden).
 */
@Service
@PreAuthorize("hasRole('ADMINISTRACION')")
public class DevolucionesPasarela {

	private final OrdenPagoRepository ordenes;

	private final SolicitudCambioRepository solicitudes;

	private final Pasarelas pasarelas;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public DevolucionesPasarela(OrdenPagoRepository ordenes, SolicitudCambioRepository solicitudes, Pasarelas pasarelas,
			AuditoriaService auditoria, Clock reloj) {
		this.ordenes = ordenes;
		this.solicitudes = solicitudes;
		this.pasarelas = pasarelas;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@Transactional
	public String devolverOrden(Long ordenId) {
		OrdenPago orden = ordenes.bloquear(ordenId).orElseThrow(() -> new RecursoNoEncontradoException("Pago no encontrado"));
		if (orden.getEstado() != EstadoOrden.POR_REVISAR) {
			throw new ReglaNegocioException("Este pago en línea no está por revisar.");
		}
		orden.exigirSinContracargo();
		SolicitudCambio aprobada = solicitudes.findFirstByTipoAndEntidadAndEntidadIdAndEstadoOrderByIdDesc(
				TipoSolicitud.DEVOLVER_INGRESO, ServicioIngresosPorRevisar.ENTIDAD, ordenId, EstadoSolicitud.APROBADA)
				.orElseThrow(() -> new ReglaNegocioException("La devolución todavía no está aprobada por Promotoría o "
						+ "Dirección."));
		String usuario = SecurityContextHolder.getContext().getAuthentication().getName();
		if (usuario.equals(aprobada.getResueltoPor())) {
			throw new ReglaNegocioException("Aprobaste esta devolución: la ejecuta otra persona de Administración.");
		}
		ReembolsoPasarela reembolso = pasarelas.de(orden.getProveedor()).reembolsar(orden.getCargoId(),
				orden.getMontoConfirmado(), "Devolución aprobada (solicitud " + aprobada.getId() + ")");
		if (!orden.getCargoId().equals(reembolso.cargoId())) {
			throw new IllegalStateException("La pasarela reembolsó otro cargo");
		}
		orden.marcarDevuelta(reembolso.reembolsoId(), usuario, LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
		auditoria.registrar(AccionAuditoria.INGRESO_DEVUELTO, "orden_pago", ordenId.toString(), "POR_REVISAR", "DEVUELTA",
				"Se devolvió " + Dinero.formatear(reembolso.monto()) + " de " + orden.getFamilia().getNombre()
						+ " al mismo medio de origen (" + orden.getMedioConfirmado().etiqueta() + ", cargo " + orden.getCargoId()
						+ ", reembolso " + reembolso.reembolsoId() + "). Aprobado por " + aprobada.getResueltoPor() + ".");
		return reembolso.reembolsoId();
	}
}
