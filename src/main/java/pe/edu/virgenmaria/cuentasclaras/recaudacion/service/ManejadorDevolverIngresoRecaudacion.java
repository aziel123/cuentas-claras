package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLinea;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LineaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LineaRecaudacionRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Aprobación de {@code DEVOLVER_INGRESO} de una línea de recaudación: comprueba que siga por revisar. La devolución la
 * EJECUTA después Administración con el número de la transferencia ({@link ServicioExcepcionesRecaudacion}), y no puede
 * ser quien la aprobó (en MySQL lo exige el trigger). Va primero en la bandeja: sale dinero.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorDevolverIngresoRecaudacion implements ManejadorSolicitud {

	private final LineaRecaudacionRepository lineas;

	public ManejadorDevolverIngresoRecaudacion(LineaRecaudacionRepository lineas) {
		this.lineas = lineas;
	}

	@Override
	public String entidad() {
		return ServicioExcepcionesRecaudacion.ENTIDAD;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.DEVOLVER_INGRESO;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		LineaRecaudacion linea = lineas.bloquear(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("El pago por banco de la solicitud no existe."));
		if (linea.getEstado() != EstadoLinea.EXCEPCION) {
			throw new ReglaNegocioException("El pago por banco ya no está por revisar: recházala.");
		}
		if (DatosSolicitud.leer(solicitud.getDatos()).get(ServicioExcepcionesRecaudacion.DATO_CUENTA) == null) {
			throw new ReglaNegocioException("La solicitud no tiene la cuenta de destino: recházala y pídela otra vez.");
		}
	}

	@Override
	public int prioridad(SolicitudCambio solicitud) {
		return 0;
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		LineaRecaudacion linea = lineas.findById(solicitud.getEntidadId()).orElse(null);
		if (linea == null) {
			return List.of();
		}
		List<String> texto = new ArrayList<>();
		java.util.Map<String, String> datos = pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud.leer(
				solicitud.getDatos());
		texto.add("Devolver " + linea.getMoneda() + " " + linea.getMonto().toPlainString() + " pagados en el banco el "
				+ linea.getFechaPago() + " con el código " + CodigoPago.legible(linea.getCodigo()) + " (operación "
				+ linea.getNumeroOperacion() + "), por transferencia.");
		texto.add("Cuenta de destino: " + datos.getOrDefault(ServicioExcepcionesRecaudacion.DATO_BANCO, "—") + " "
				+ datos.getOrDefault(ServicioExcepcionesRecaudacion.DATO_CUENTA, "—") + " · titular "
				+ datos.getOrDefault(ServicioExcepcionesRecaudacion.DATO_TITULAR, "—")
				+ ". Confírmala con la familia antes de aprobar.");
		if (linea.getMotivoExcepcion() != null) {
			texto.add("Por qué quedó por revisar: " + linea.getMotivoExcepcion().descripcion());
		}
		texto.add("Después de aprobarla, la ejecuta otra persona de Administración (ni tú ni quien la pidió) con el número "
				+ "de la transferencia; la conciliación espera ver ese cargo en el banco.");
		return texto;
	}
}
