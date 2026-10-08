package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.SesionApoderado;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ComprobanteImprimible;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.ComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.ServicioComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;

import java.util.Objects;

/**
 * Los comprobantes de los pagos de SU familia, para el apoderado (sprint 4): su familia sale de su cuenta, nunca de la
 * URL; uno de otra familia (o una nota de crédito) es 404. Si el comprobante se reemitió, muestra el vigente.
 */
@Service
@PreAuthorize("hasRole('APODERADO')")
@Transactional(readOnly = true)
public class ComprobantesDeFamilia {

	private final ComprobanteRepository comprobantes;

	private final PagoRepository pagos;

	private final ServicioComprobantes servicio;

	private final SesionApoderado sesion;

	private final ColegioService colegios;

	private final NombresUsuarios nombres;

	public ComprobantesDeFamilia(ComprobanteRepository comprobantes, PagoRepository pagos, ServicioComprobantes servicio,
			SesionApoderado sesion, ColegioService colegios, NombresUsuarios nombres) {
		this.comprobantes = comprobantes;
		this.pagos = pagos;
		this.servicio = servicio;
		this.sesion = sesion;
		this.colegios = colegios;
		this.nombres = nombres;
	}

	public ComprobanteImprimible imprimible(Long comprobanteId) {
		Comprobante original = comprobantes.findById(comprobanteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Comprobante no encontrado"));
		Pago pago = pagos.findByComprobanteId(original.getId()).orElse(null);
		if (pago == null || !Objects.equals(pago.getFamilia().getId(), sesion.familiaId())) {
			throw new RecursoNoEncontradoException("Comprobante no encontrado");
		}
		Comprobante vigente = servicio.vigenteDe(original);
		return ServicioCobro.imprimibleDe(vigente, pago, colegios.nombreDe(vigente.getColegioId()), nombres, null, false);
	}
}
