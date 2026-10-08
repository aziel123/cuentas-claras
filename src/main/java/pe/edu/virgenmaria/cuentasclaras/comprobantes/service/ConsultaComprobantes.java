package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.config.PropiedadesComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.dto.BandejaComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.ComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Bandeja de envíos al OSE para Promotoría, Dirección y Administración: lo rechazado y lo que vence su plazo legal
 * primero. Administración puede adelantar un reintento (lo toma el outbox en su siguiente pasada); la reemisión de un
 * rechazado la hace {@code ServicioReemision} (caja), que conoce al pagador.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ConsultaComprobantes {

	private final ComprobanteRepository comprobantes;

	private final PropiedadesComprobantes propiedades;

	private final Clock reloj;

	public ConsultaComprobantes(ComprobanteRepository comprobantes, PropiedadesComprobantes propiedades, Clock reloj) {
		this.comprobantes = comprobantes;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public BandejaComprobantes bandeja() {
		LocalDate hoy = LocalDate.now(reloj);
		int plazo = propiedades.plazoEnvioDias();
		List<BandejaComprobantes.Fila> rechazados = new ArrayList<>();
		for (Comprobante c : comprobantes.findByEstadoEnvioInOrderByFechaEmisionAscIdAsc(EnumSet.of(EstadoEnvio.RECHAZADO))) {
			String reemitido = comprobantes.findByReemplazaId(c.getId()).map(Comprobante::numeroCompleto).orElse(null);
			if (reemitido == null) {
				rechazados.add(fila(c, plazo, null));
			}
		}
		List<BandejaComprobantes.Fila> porVencer = new ArrayList<>();
		List<BandejaComprobantes.Fila> pendientes = new ArrayList<>();
		List<BandejaComprobantes.Fila> enviados = new ArrayList<>();
		for (Comprobante c : comprobantes.findByEstadoEnvioInOrderByFechaEmisionAscIdAsc(
				EnumSet.of(EstadoEnvio.PENDIENTE, EstadoEnvio.ENVIADO))) {
			if (c.vencePlazo(hoy, plazo)) {
				porVencer.add(fila(c, plazo, null));
			}
			else if (c.getEstadoEnvio() == EstadoEnvio.PENDIENTE) {
				pendientes.add(fila(c, plazo, null));
			}
			else {
				enviados.add(fila(c, plazo, null));
			}
		}
		List<BandejaComprobantes.Fila> aceptadosHoy = comprobantes
				.findByEstadoEnvioInAndAceptadoEnGreaterThanEqualAndAceptadoEnLessThanOrderByIdAsc(
						EnumSet.of(EstadoEnvio.ACEPTADO, EstadoEnvio.OBSERVADO), hoy.atStartOfDay(),
						hoy.plusDays(1).atStartOfDay())
				.stream().map(c -> fila(c, plazo, null)).toList();
		return new BandejaComprobantes(hoy, plazo, propiedades.proveedor() == ProveedorComprobantes.SIMULADO, rechazados,
				porVencer, pendientes, enviados, aceptadosHoy);
	}

	/** Administración adelanta el próximo envío o consulta de un comprobante sin resolver. */
	@PreAuthorize("hasRole('ADMINISTRACION')")
	@Transactional
	public String adelantarReintento(Long comprobanteId) {
		Comprobante c = comprobantes.findById(comprobanteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Comprobante no encontrado"));
		if (c.getEstadoEnvio().definitivo()) {
			throw new ReglaNegocioException("El comprobante " + c.numeroCompleto() + " ya tiene respuesta definitiva del OSE ("
					+ c.getEstadoEnvio().etiqueta().toLowerCase(java.util.Locale.ROOT) + ").");
		}
		c.adelantarReintento(LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
		return c.numeroCompleto();
	}

	private static BandejaComprobantes.Fila fila(Comprobante c, int plazo, String reemitido) {
		String variante = switch (c.getEstadoEnvio()) {
			case ACEPTADO -> "exito";
			case OBSERVADO, ENVIADO, PENDIENTE -> "alerta";
			case RECHAZADO -> "peligro";
		};
		return new BandejaComprobantes.Fila(c.getId(), c.numeroCompleto(), c.getTipo().etiqueta(), c.getReceptor().nombre(),
				c.getTotal(), c.getFechaEmision(), c.getEstadoEnvio().name(), c.getEstadoEnvio().etiqueta(), variante,
				c.getIntentos(), c.getUltimoError(), c.getRespuesta(), c.fechaLimiteEnvio(plazo), c.getProximoIntentoEn(),
				reemitido, c.getReemplazaId() == null ? null : String.valueOf(c.getReemplazaId()),
				c.getEstadoEnvio() == EstadoEnvio.RECHAZADO && c.getTipo() != pe.edu.virgenmaria.cuentasclaras.comprobantes
						.model.TipoComprobante.NOTA_CREDITO,
				!c.getEstadoEnvio().definitivo());
	}
}
