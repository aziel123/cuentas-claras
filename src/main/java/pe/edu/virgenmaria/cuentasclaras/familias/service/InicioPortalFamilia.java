package pe.edu.virgenmaria.cuentasclaras.familias.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.InicioFamilia;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioRenovacionFamilia;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.CuentaEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;

import java.util.Comparator;
import java.util.List;

/**
 * Inicio del portal (pantalla 1): lo que debe la familia (con el pago en línea del sprint 4), la deuda vencida, el
 * próximo vencimiento y la renovación de matrícula por responder.
 */
@Service
@PreAuthorize("hasRole('APODERADO')")
public class InicioPortalFamilia {

	private static final String VENCIDA = "Vencida";

	private final ServicioPagoEnLinea pagos;

	private final ServicioRenovacionFamilia renovaciones;

	public InicioPortalFamilia(ServicioPagoEnLinea pagos, ServicioRenovacionFamilia renovaciones) {
		this.pagos = pagos;
		this.renovaciones = renovaciones;
	}

	public InicioFamilia inicio() {
		CuentaEnLinea cuenta = pagos.cuenta();
		List<InicioFamilia.Pendiente> todas = cuenta.hijos().stream().flatMap(h -> h.cuotas().stream()
				.map(c -> new InicioFamilia.Pendiente(h.nombre(), c.descripcion(), c.vencimiento(), c.saldo(),
						VENCIDA.equals(c.estado()))))
				.toList();
		InicioFamilia.Pendiente proximo = todas.stream().filter(p -> !p.vencida())
				.min(Comparator.comparing(InicioFamilia.Pendiente::vencimiento)).orElse(null);
		return new InicioFamilia(cuenta, Dinero.sumar(todas.stream().filter(InicioFamilia.Pendiente::vencida)
				.map(InicioFamilia.Pendiente::saldo).toList()), proximo,
				renovaciones.deMiFamilia().stream().filter(r -> r.porResponder() || r.continua()).toList());
	}
}
