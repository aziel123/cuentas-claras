package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AnulacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AplicacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAnulacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAplicacion;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AnulacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AplicacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CajaDiariaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.ServicioComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Telefono;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Aplica una anulación de pago aprobada en la bandeja por otra persona de Promotoría o Dirección (que no es quien la
 * pidió ni la cajera del pago: {@link #involucrados}). Nada se borra. En este orden (y orden de bloqueos):
 * <ol>
 *   <li>bloquea la caja y después el pago; valida que siga VIGENTE;</li>
 *   <li>bloquea todas las cuotas que toca (las del pago y, si es corrección, las de destino) por id;</li>
 *   <li>emite la nota de crédito (BC01 o FC01, sin huecos) que anula el comprobante del pago;</li>
 *   <li>inserta la anulación (antes de marcar el pago: el trigger del pago la exige) y anula el pago con flush;</li>
 *   <li>revierte sus aplicaciones con filas negativas (las cuotas vuelven a deberse);</li>
 *   <li>si es CORRECCIÓN, registra el pago de reemplazo en la misma caja con un comprobante nuevo (si las cuotas ya no
 *       suman el pago, no se aprueba nada);</li>
 *   <li>audita y publica {@link PagoAnulado}.</li>
 * </ol>
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorAnulacionPago implements ManejadorSolicitud {

	private final PagoRepository pagos;

	private final CajaDiariaRepository cajas;

	private final AplicacionPagoRepository aplicaciones;

	private final AnulacionPagoRepository anulaciones;

	private final CuotaRepository cuotas;

	private final FamiliaRepository familias;

	private final ServicioComprobantes comprobantes;

	private final LibroPagos libro;

	private final NombresUsuarios nombres;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public ManejadorAnulacionPago(PagoRepository pagos, CajaDiariaRepository cajas, AplicacionPagoRepository aplicaciones,
			AnulacionPagoRepository anulaciones, CuotaRepository cuotas, FamiliaRepository familias,
			ServicioComprobantes comprobantes, LibroPagos libro, NombresUsuarios nombres, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, Clock reloj) {
		this.pagos = pagos;
		this.cajas = cajas;
		this.aplicaciones = aplicaciones;
		this.anulaciones = anulaciones;
		this.cuotas = cuotas;
		this.familias = familias;
		this.comprobantes = comprobantes;
		this.libro = libro;
		this.nombres = nombres;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.ANULACION_PAGO;
	}

	/** La cajera del pago tampoco aprueba (aunque la anulación la pida Administración). */
	@Override
	public Set<String> involucrados(SolicitudCambio solicitud) {
		return pagos.findById(solicitud.getEntidadId()).map(p -> Set.of(p.getCajero())).orElse(Set.of());
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		TipoAnulacion tipo = TipoAnulacion.valueOf(datos.get(ServicioAnulacionPagos.DATO_TIPO));
		Long pagoId = solicitud.getEntidadId();
		// 1. Caja y pago (en ese orden).
		Long cajaId = pagos.cajaDe(pagoId).orElseThrow(() -> new ReglaNegocioException("El pago de la solicitud no existe."));
		CajaDiaria caja = cajas.bloquearPorId(cajaId).orElseThrow();
		Pago pago = pagos.bloquear(pagoId).orElseThrow();
		if (!pago.vigente()) {
			throw new ReglaNegocioException("El pago ya está anulado.");
		}
		// 2. Todas las cuotas que se tocan, por id.
		List<Long> destino = tipo == TipoAnulacion.CORRECCION ? cuotasDestino(datos) : List.of();
		Set<Long> todas = new TreeSet<>(aplicaciones.cuotasDePago(pagoId));
		todas.addAll(destino);
		cuotas.bloquear(todas);
		Familia familiaDestino = tipo == TipoAnulacion.CORRECCION
				? familias.findById(Long.valueOf(datos.get(ServicioAnulacionPagos.DATO_FAMILIA)))
						.orElseThrow(() -> new ReglaNegocioException("La familia de la corrección ya no existe."))
				: null;
		// 3. Nota de crédito; 4. anulación y pago anulado.
		LocalDate hoy = LocalDate.now(reloj);
		Comprobante original = pago.getComprobante();
		Comprobante nota = comprobantes.emitirNotaCredito(original, "Anulación de la operación: " + solicitud.getMotivo(),
				hoy);
		boolean posteriorAlCierre = !caja.aceptaEfectivo();
		anulaciones.save(AnulacionPago.registrar(pago, solicitud.getId(), nota, tipo, solicitud.getMotivo(),
				solicitud.getSolicitadoPor(), aprobador, posteriorAlCierre));
		pago.anular();
		pagos.saveAndFlush(pago);
		// 5. Reversiones: las cuotas vuelven a deberse.
		List<String> vuelven = libro.revertir(pago);
		// 6. Corrección: el mismo dinero a las cuotas de destino, con comprobante nuevo.
		Pago reemplazo = null;
		if (tipo == TipoAnulacion.CORRECCION) {
			DatosComprobante datosComprobante = original.getTipo() == TipoComprobante.FACTURA
					&& familiaDestino.getId().equals(pago.getFamilia().getId())
					? new DatosComprobante(TipoComprobante.FACTURA, null, original.getReceptor().numero(),
							original.getReceptor().nombre())
					: new DatosComprobante(TipoComprobante.BOLETA, null, null, null);
			reemplazo = libro.reemplazar(pago, familiaDestino, destino, datosComprobante, hoy);
		}
		// 7. Bitácora.
		String detalle = tipo.etiqueta() + " del pago " + original.numeroCompleto() + " (" + Dinero.formatear(pago.getTotal())
				+ ", " + pago.getMedio().etiqueta() + ", " + pago.getFamilia().getNombre() + ", cobrado por "
				+ pago.getCajero() + "). Nota de crédito " + nota.numeroCompleto() + ". Vuelven a deberse: "
				+ String.join("; ", vuelven) + (reemplazo == null ? ""
						: ". Pasa a " + familiaDestino.getNombre() + " con el pago " + reemplazo.getId() + " ("
								+ reemplazo.getComprobante().numeroCompleto() + ")")
				+ (posteriorAlCierre ? ". La caja ya estaba cerrada: su cierre no cambia" : "")
				+ ". Pedido por " + solicitud.getSolicitadoPor() + ", aprobado por " + aprobador + ". Motivo: "
				+ solicitud.getMotivo();
		auditoria.registrar(AccionAuditoria.NOTA_CREDITO_EMITIDA, "comprobante", nota.getId().toString(), null,
				nota.numeroCompleto() + " · " + Dinero.formatear(nota.getTotal()), "Anula " + original.numeroCompleto()
						+ " (pago " + pago.getId() + ").");
		auditoria.registrar(AccionAuditoria.PAGO_ANULADO, "pago", pago.getId().toString(), "VIGENTE", "ANULADO", detalle);
		eventos.publishEvent(new PagoAnulado(pago.getId()));
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		Pago pago = pagos.findById(solicitud.getEntidadId()).orElse(null);
		if (pago == null) {
			return List.of();
		}
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		TipoAnulacion tipo = TipoAnulacion.valueOf(datos.get(ServicioAnulacionPagos.DATO_TIPO));
		List<String> lineas = new ArrayList<>();
		lineas.add(tipo.etiqueta() + " · " + pago.getComprobante().numeroCompleto() + " · "
				+ Dinero.formatear(pago.getTotal()) + " · " + pago.getMedio().etiqueta()
				+ (pago.getNumeroOperacion() == null ? "" : " (operación " + pago.getNumeroOperacion() + ")"));
		lineas.add("Cobrado el " + Calendario.formatear(pago.getFecha()) + " por " + nombres.de(pago.getCajero()) + " ("
				+ pago.getCajero() + ") · " + (pago.getCaja().aceptaEfectivo() ? "Caja abierta" : "Caja ya cerrada"));
		List<AplicacionPago> aplicadas = aplicaciones.findByPagoIdAndTipoOrderByIdAsc(pago.getId(),
				TipoAplicacion.APLICACION);
		lineas.add("Vuelven a deberse: " + String.join("; ", aplicadas.stream().map(a -> a.getCuota().getDescripcion()
				+ " de " + a.getCuota().getAlumno().nombreCompleto() + " (" + Dinero.formatear(a.getMonto()) + ")")
				.toList()));
		if (tipo == TipoAnulacion.CORRECCION) {
			String familia = familias.findById(Long.valueOf(datos.get(ServicioAnulacionPagos.DATO_FAMILIA)))
					.map(Familia::getNombre).orElse("(familia no encontrada)");
			lineas.add("Pasa a " + familia + ": " + String.join("; ", cuotasDestino(datos).stream()
					.map(id -> cuotas.findById(id).map(c -> c.getDescripcion() + " de " + c.getAlumno().nombreCompleto())
							.orElse("cuota " + id)).toList()));
		}
		// Decisión 18 (Ley 29733: finalidad de verificar con el padre): el celular completo, solo aquí, para quien aprueba.
		Apoderado apoderado = aplicadas.isEmpty() ? null : aplicadas.getFirst().getCuota().getAlumno().getResponsablePago();
		if (apoderado != null && apoderado.getTelefonoWhatsapp() != null) {
			String contacto = apoderado.nombreCompleto() + ": " + Telefono.formatear(apoderado.getTelefonoWhatsapp());
			lineas.add(tipo == TipoAnulacion.DEVOLUCION && pago.getMedio() == MedioPago.EFECTIVO
					? "Antes de aprobar, llama al apoderado: " + contacto
					: "Contacto del apoderado: " + contacto);
		}
		return lineas;
	}

	/** Una corrección hacia OTRA familia se muestra resaltada: es la vía para mover dinero entre familias. */
	@Override
	public String advertencia(SolicitudCambio solicitud) {
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		if (!TipoAnulacion.CORRECCION.name().equals(datos.get(ServicioAnulacionPagos.DATO_TIPO))) {
			return null;
		}
		Long destino = Long.valueOf(datos.get(ServicioAnulacionPagos.DATO_FAMILIA));
		return pagos.findById(solicitud.getEntidadId()).filter(p -> !Objects.equals(p.getFamilia().getId(), destino))
				.map(p -> "El dinero pasa a OTRA familia: confirma con ambas familias antes de aprobar.").orElse(null);
	}

	private static List<Long> cuotasDestino(Map<String, String> datos) {
		return Arrays.stream(datos.get(ServicioAnulacionPagos.DATO_CUOTAS).split(",")).filter(s -> !s.isBlank())
				.map(Long::valueOf).toList();
	}
}
