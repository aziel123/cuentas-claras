package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.AplicacionIngresoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Un ingreso que no se pudo aplicar solo (orden POR_REVISAR): Administración pide APLICARLO a otras cuotas (de la misma
 * familia o de otra, con llamada a ambas) o DEVOLVERLO al mismo medio de origen. Lo aprueba otra persona de Promotoría o
 * Dirección (quien pide no aprueba: la bandeja lo impide). El pago de una aplicación aprobada lo registra el sistema.
 */
@Service
@Transactional
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioIngresosPorRevisar {

	static final String ENTIDAD = "orden_pago";

	static final String DATO_FAMILIA = "familiaId";

	static final String DATO_CUOTAS = "cuotas";

	private final OrdenPagoRepository ordenes;

	private final FamiliaRepository familias;

	private final CuotaRepository cuotas;

	private final SolicitudCambioRepository pendientes;

	private final RegistroSolicitudes solicitudes;

	private final AuditoriaService auditoria;

	public ServicioIngresosPorRevisar(OrdenPagoRepository ordenes, FamiliaRepository familias, CuotaRepository cuotas,
			SolicitudCambioRepository pendientes, RegistroSolicitudes solicitudes, AuditoriaService auditoria) {
		this.ordenes = ordenes;
		this.familias = familias;
		this.cuotas = cuotas;
		this.pendientes = pendientes;
		this.solicitudes = solicitudes;
		this.auditoria = auditoria;
	}

	/** Pide aplicar el ingreso a esas cuotas (deben sumar al menos lo cobrado; si suman más, queda a cuenta). */
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void solicitarAplicacion(Long ordenId, AplicacionIngresoRequest pedido) {
		OrdenPago orden = porRevisar(ordenId);
		String motivo = Motivo.exigir(pedido.motivo());
		if (!OrdenPago.MONEDA.equals(orden.getMonedaConfirmada())) {
			throw new ReglaNegocioException("La pasarela cobró en otra moneda (" + orden.getMonedaConfirmada() + "): este "
					+ "ingreso no se aplica a cuotas en soles, solo se devuelve.");
		}
		Familia destino = familias.findById(pedido.familiaId())
				.orElseThrow(() -> new RecursoNoEncontradoException("Familia no encontrada"));
		List<Cuota> elegidas = cuotasDestino(destino.getId(), pedido.cuotaIds());
		BigDecimal debe = Dinero.sumar(elegidas.stream().map(Cuota::saldo).toList());
		if (debe.compareTo(orden.getMontoConfirmado()) < 0) {
			throw new ReglaNegocioException("Las cuotas elegidas suman " + Dinero.formatear(debe) + " y el ingreso es de "
					+ Dinero.formatear(orden.getMontoConfirmado()) + ": elige cuotas que sumen al menos el ingreso o pide "
					+ "devolverlo.");
		}
		boolean otraFamilia = !Objects.equals(destino.getId(), orden.getFamilia().getId());
		String resumen = "Aplicar el pago en línea de " + orden.getFamilia().getNombre() + " por "
				+ Dinero.formatear(orden.getMontoConfirmado()) + " a " + (otraFamilia ? "OTRA familia (" + destino.getNombre()
						+ ")" : "otras cuotas") + ": " + String.join("; ", elegidas.stream().map(c -> c.getDescripcion()
								+ " de " + c.getAlumno().nombreCompleto()).toList());
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put(DATO_FAMILIA, destino.getId().toString());
		datos.put(DATO_CUOTAS, elegidas.stream().map(c -> c.getId().toString()).collect(Collectors.joining(",")));
		exigirSinOtraPendiente(ordenId);
		solicitudes.crear(TipoSolicitud.APLICAR_INGRESO, ENTIDAD, ordenId, resumen, datos, motivo);
		auditoria.registrar(AccionAuditoria.INGRESO_APLICACION_SOLICITADA, ENTIDAD, ordenId.toString(), "POR_REVISAR",
				"Aplicación pendiente de aprobación", resumen + ". Motivo: " + motivo);
	}

	/** Pide devolver el ingreso: lo devuelve la pasarela al mismo medio de origen (Yape o tarjeta del apoderado). */
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void solicitarDevolucion(Long ordenId, String motivo) {
		OrdenPago orden = porRevisar(ordenId);
		String texto = Motivo.exigir(motivo);
		exigirSinOtraPendiente(ordenId);
		String resumen = "Devolver el pago en línea de " + orden.getFamilia().getNombre() + " por "
				+ Dinero.formatear(orden.getMontoConfirmado()) + " al mismo medio de origen ("
				+ orden.getMedioConfirmado().etiqueta() + ")";
		solicitudes.crear(TipoSolicitud.DEVOLVER_INGRESO, ENTIDAD, ordenId, resumen, Map.of(), texto);
		auditoria.registrar(AccionAuditoria.INGRESO_DEVOLUCION_SOLICITADA, ENTIDAD, ordenId.toString(), "POR_REVISAR",
				"Devolución pendiente de aprobación", resumen + ". Motivo: " + texto);
	}

	private OrdenPago porRevisar(Long ordenId) {
		OrdenPago orden = ordenes.bloquear(ordenId).orElseThrow(() -> new RecursoNoEncontradoException("Pago no encontrado"));
		if (orden.getEstado() != EstadoOrden.POR_REVISAR) {
			throw new ReglaNegocioException("Este pago en línea no está por revisar (está "
					+ orden.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT) + ").");
		}
		orden.exigirSinContracargo();
		return orden;
	}

	private void exigirSinOtraPendiente(Long ordenId) {
		if (!pendientes.findByEntidadAndEntidadIdAndEstadoOrderByIdAsc(ENTIDAD, ordenId, EstadoSolicitud.PENDIENTE)
				.isEmpty()) {
			throw new ReglaNegocioException("Ya hay una solicitud pendiente para este ingreso: espera a que Promotoría o "
					+ "Dirección la resuelva.");
		}
	}

	private List<Cuota> cuotasDestino(Long familiaId, List<Long> ids) {
		List<Long> orden = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
		List<Cuota> elegidas = orden.stream().map(id -> cuotas.findById(id).orElse(null)).filter(Objects::nonNull).toList();
		if (orden.isEmpty() || elegidas.size() != orden.size()
				|| elegidas.stream().anyMatch(c -> !Objects.equals(c.getAlumno().getFamilia().getId(), familiaId))) {
			throw new ReglaNegocioException("Elige cuotas por pagar de la familia de destino.");
		}
		for (Cuota c : elegidas) {
			if (!c.admiteCobro() || c.saldo().signum() <= 0) {
				throw new ReglaNegocioException("La cuota «" + c.getDescripcion() + "» de " + c.getAlumno().nombreCompleto()
						+ " ya no está por pagar.");
			}
		}
		return elegidas;
	}

	/** Las cuotas destino guardadas en la solicitud. */
	static List<Long> cuotasDe(Map<String, String> datos) {
		String lista = datos.getOrDefault(DATO_CUOTAS, "");
		return java.util.Arrays.stream(lista.split(",")).filter(s -> !s.isBlank()).map(Long::valueOf).toList();
	}
}
