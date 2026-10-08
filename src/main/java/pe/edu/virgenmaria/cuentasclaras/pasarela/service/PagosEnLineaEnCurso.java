package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.caja.service.PagosEnCurso;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CuotasEnPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPagoCuota;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoCuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Implementa los puertos de caja ({@link PagosEnCurso}) y cobranza ({@link CuotasEnPagoEnLinea}): las órdenes en curso
 * (CREADA y no vencidas) que llevan esas cuotas. Caja solo avisa (no bloquea, decisión 7); cobranza no aprueba un
 * descuento ni una anulación mientras la orden esté abierta.
 */
@Component
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class PagosEnLineaEnCurso implements PagosEnCurso, CuotasEnPagoEnLinea {

	private final OrdenPagoRepository ordenes;

	private final OrdenPagoCuotaRepository cuotasDeOrden;

	private final Clock reloj;

	public PagosEnLineaEnCurso(OrdenPagoRepository ordenes, OrdenPagoCuotaRepository cuotasDeOrden, Clock reloj) {
		this.ordenes = ordenes;
		this.cuotasDeOrden = cuotasDeOrden;
		this.reloj = reloj;
	}

	@Override
	public List<String> avisos(Collection<Long> cuotaIds) {
		if (cuotaIds == null || cuotaIds.isEmpty()) {
			return List.of();
		}
		LocalDateTime ahora = LocalDateTime.now(reloj);
		Set<Long> buscadas = Set.copyOf(cuotaIds);
		List<String> avisos = new ArrayList<>();
		for (OrdenPago orden : ordenes.abiertasConCuotas(buscadas, ahora)) {
			long minutos = orden.getCreadoEn() == null ? 0 : Math.max(0, Duration.between(orden.getCreadoEn(), ahora)
					.toMinutes());
			for (OrdenPagoCuota fila : cuotasDeOrden.findByOrdenIdOrderByIdAsc(orden.getId())) {
				if (buscadas.contains(fila.getCuota().getId())) {
					avisos.add(fila.getCuota().getDescripcion() + " de " + fila.getCuota().getAlumno().nombreCompleto()
							+ " (iniciado hace " + minutos + " min)");
				}
			}
		}
		return avisos;
	}

	@Override
	public boolean algunaEnCurso(Collection<Long> cuotaIds) {
		return cuotaIds != null && !cuotaIds.isEmpty()
				&& !ordenes.abiertasConCuotas(Set.copyOf(cuotaIds), LocalDateTime.now(reloj)).isEmpty();
	}
}
