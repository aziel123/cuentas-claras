package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.config.PropiedadesComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.ComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Alertas del envío al OSE para «Para revisar» (sprint 4, decisión 26):
 * <ul>
 *   <li>CRÍTICA: un comprobante RECHAZADO sin reemitir; uno sin aceptar a un día del plazo legal (o ya pasado); un
 *       comprobante SIMULADO emitido después de activar el OSE real.</li>
 *   <li>ATENCIÓN: sin aceptar después de 4 horas; aceptado con observaciones (OBSERVADO) en los últimos 7 días.</li>
 * </ul>
 * (La de «el OSE no reconoce un aceptado» la da caja, que lee la bitácora.)
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class AlertasComprobantes implements AlertasRevision {

	static final String MODULO = "Comprobantes";

	static final String ENLACE = "/comprobantes";

	private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

	private final ComprobanteRepository comprobantes;

	private final PropiedadesComprobantes propiedades;

	private final Clock reloj;

	public AlertasComprobantes(ComprobanteRepository comprobantes, PropiedadesComprobantes propiedades, Clock reloj) {
		this.comprobantes = comprobantes;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AlertaRevision> alertas() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		LocalDate hoy = ahora.toLocalDate();
		List<AlertaRevision> alertas = new ArrayList<>();
		List<Comprobante> sinReemitir = comprobantes.findByEstadoEnvioInOrderByFechaEmisionAscIdAsc(
				EnumSet.of(EstadoEnvio.RECHAZADO)).stream().filter(c -> !comprobantes.existsByReemplazaId(c.getId()))
				.toList();
		List<String> rechazados = sinReemitir.stream()
				.map(c -> c.numeroCompleto() + (c.getCodigoRespuesta() == null ? "" : " (código " + c.getCodigoRespuesta()
						+ ")"))
				.toList();
		if (!rechazados.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "SUNAT rechazó " + cuantos(rechazados.size(),
					"comprobante") + ": " + resumen(rechazados) + ". Corrige el dato y reemítelo: mientras tanto el pago no "
					+ "tiene comprobante válido.", ENLACE, new Aviso(TipoAviso.OTRA_CRITICA, "RECH:"
							+ sinReemitir.stream().mapToLong(Comprobante::getId).max().orElse(0))));
		}
		List<String> porVencer = new ArrayList<>();
		long porVencerMasReciente = 0;
		List<String> demorados = new ArrayList<>();
		for (Comprobante c : comprobantes.findByEstadoEnvioInOrderByFechaEmisionAscIdAsc(
				EnumSet.of(EstadoEnvio.PENDIENTE, EstadoEnvio.ENVIADO))) {
			if (c.vencePlazo(hoy, propiedades.plazoEnvioDias())) {
				porVencer.add(c.numeroCompleto() + " (vence el " + DIA.format(c.fechaLimiteEnvio(propiedades.plazoEnvioDias()))
						+ ")");
				porVencerMasReciente = Math.max(porVencerMasReciente, c.getId());
			}
			else if (c.getCreadoEn() != null
					&& c.getCreadoEn().isBefore(ahora.minusHours(propiedades.alertaHorasSinAceptar()))) {
				demorados.add(c.numeroCompleto());
			}
		}
		if (!porVencer.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, cuantos(porVencer.size(), "comprobante")
					+ " sin aceptar por el OSE a un día del plazo legal: " + resumen(porVencer)
					+ ". Fuera de plazo dejan de tener calidad de comprobante.", ENLACE,
					new Aviso(TipoAviso.OTRA_CRITICA, "PLAZO:" + porVencerMasReciente)));
		}
		if (!demorados.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, cuantos(demorados.size(), "comprobante")
					+ " sin aceptar después de " + propiedades.alertaHorasSinAceptar() + " horas: " + resumen(demorados)
					+ ".", ENLACE));
		}
		List<String> observados = comprobantes.findByEstadoEnvioInAndAceptadoEnGreaterThanEqualAndAceptadoEnLessThanOrderByIdAsc(
				EnumSet.of(EstadoEnvio.OBSERVADO), hoy.minusDays(7).atStartOfDay(), hoy.plusDays(1).atStartOfDay())
				.stream().map(Comprobante::numeroCompleto).toList();
		if (!observados.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "SUNAT aceptó con observaciones "
					+ cuantos(observados.size(), "comprobante") + ": " + resumen(observados) + ". Revisa la observación con "
					+ "el contador.", ENLACE));
		}
		comprobantes.findFirstByProveedorOrderByIdAsc(ProveedorComprobantes.NUBEFACT).ifPresent(primero -> {
			if (comprobantes.existsByProveedorAndIdGreaterThan(ProveedorComprobantes.SIMULADO, primero.getId())) {
				alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "Se emitieron comprobantes SIMULADOS (sin valor "
						+ "tributario) después de activar el OSE real. Revisa la configuración del proveedor.", ENLACE));
			}
		});
		return alertas;
	}

	private static String cuantos(int n, String singular) {
		return n + " " + singular + (n == 1 ? "" : "s");
	}

	private static String resumen(List<String> numeros) {
		return numeros.size() <= 3 ? String.join(", ", numeros)
				: String.join(", ", numeros.subList(0, 3)) + " y " + (numeros.size() - 3) + " más";
	}
}
