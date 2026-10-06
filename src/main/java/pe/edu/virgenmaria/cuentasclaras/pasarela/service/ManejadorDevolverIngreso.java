package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Aprobación de {@code DEVOLVER_INGRESO}: comprueba que la orden siga por revisar. La devolución la EJECUTA después
 * Administración ({@link DevolucionesPasarela}), que no puede ser quien aprobó (en MySQL lo exige el trigger), y la
 * pasarela la devuelve SOLO al mismo medio de origen. Va primero en la bandeja: sale dinero.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorDevolverIngreso implements ManejadorSolicitud {

	private final OrdenPagoRepository ordenes;

	public ManejadorDevolverIngreso(OrdenPagoRepository ordenes) {
		this.ordenes = ordenes;
	}

	@Override
	public String entidad() {
		return ServicioIngresosPorRevisar.ENTIDAD;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.DEVOLVER_INGRESO;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		OrdenPago orden = ordenes.bloquear(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("El pago en línea de la solicitud no existe."));
		if (orden.getEstado() != EstadoOrden.POR_REVISAR) {
			throw new ReglaNegocioException("El pago en línea ya no está por revisar: recházala.");
		}
		orden.exigirSinContracargo();
	}

	@Override
	public int prioridad(SolicitudCambio solicitud) {
		return 0;
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		OrdenPago orden = ordenes.findById(solicitud.getEntidadId()).orElse(null);
		if (orden == null) {
			return List.of();
		}
		List<String> lineas = new ArrayList<>();
		lineas.add("Devolver " + Dinero.formatear(orden.getMontoConfirmado()) + " a " + orden.getFamilia().getNombre()
				+ " por la misma vía con la que pagó (" + (orden.getMedioConfirmado() == null ? "pasarela"
						: orden.getMedioConfirmado().etiqueta()) + ", cargo " + orden.getCargoId() + ")");
		if (orden.getMotivoRevision() != null) {
			lineas.add("Por qué quedó por revisar: " + orden.getMotivoRevision().descripcion());
		}
		lineas.add("Después de aprobarla, la ejecuta Administración (otra persona que no seas tú).");
		return lineas;
	}
}
