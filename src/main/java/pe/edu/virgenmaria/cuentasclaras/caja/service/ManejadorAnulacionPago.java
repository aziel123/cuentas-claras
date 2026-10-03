package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
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
import pe.edu.virgenmaria.cuentasclaras.caja.model.CausaDevolucion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAnulacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAplicacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.VerificacionBancaria;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AnulacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AplicacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CajaDiariaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.VerificacionBancariaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.ServicioComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Telefono;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Aplica una anulación de pago aprobada en la bandeja por otra persona de Promotoría o Dirección (que no es quien la
 * pidió ni la cajera del pago: {@link #involucrados}). Nada se borra. En este orden (y orden de bloqueos):
 * <ol>
 *   <li>bloquea la caja (es su primera lectura en la transacción: así su estado es el confirmado, por ejemplo si se
 *       cerró mientras tanto) y después el pago; valida que siga VIGENTE y, si es digital, que esté verificado en el
 *       banco (A1);</li>
 *   <li>bloquea todas las cuotas que toca (las del pago y, si es corrección, las de destino) por id;</li>
 *   <li>emite la nota de crédito (BC01 o FC01, sin huecos) que anula el comprobante del pago;</li>
 *   <li>inserta la anulación (antes de marcar el pago: el trigger del pago la exige) y anula el pago con flush;</li>
 *   <li>revierte sus aplicaciones con filas negativas (las cuotas vuelven a deberse);</li>
 *   <li>si es CORRECCIÓN, registra el pago de reemplazo en la misma caja con un comprobante nuevo (si las cuotas ya no
 *       suman el pago, no se aprueba nada);</li>
 *   <li>audita y publica {@link PagoAnulado}.</li>
 * </ol>
 * Una devolución en efectivo exige que quien aprueba llame a un apoderado de la familia, y una corrección hacia otra
 * familia, a ambas (A2, {@link #llamadas}): la bandeja valida los números contra los celulares registrados.
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

	private final ApoderadoRepository apoderados;

	private final VerificacionBancariaRepository verificaciones;

	private final ServicioComprobantes comprobantes;

	private final LibroPagos libro;

	private final NombresUsuarios nombres;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public ManejadorAnulacionPago(PagoRepository pagos, CajaDiariaRepository cajas, AplicacionPagoRepository aplicaciones,
			AnulacionPagoRepository anulaciones, CuotaRepository cuotas, FamiliaRepository familias,
			ApoderadoRepository apoderados, VerificacionBancariaRepository verificaciones, ServicioComprobantes comprobantes, LibroPagos libro, NombresUsuarios nombres, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, Clock reloj) {
		this.pagos = pagos;
		this.cajas = cajas;
		this.aplicaciones = aplicaciones;
		this.anulaciones = anulaciones;
		this.cuotas = cuotas;
		this.familias = familias;
		this.apoderados = apoderados;
		this.verificaciones = verificaciones;
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
		ServicioAnulacionPagos.exigirVerificado(pago, verificaciones);
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
					&& libro.rucRegistrado(familiaDestino.getId(), original.getReceptor().numero()).isPresent()
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
		lineas.add(verificacionBancaria(pago));
		String causa = datos.get(ServicioAnulacionPagos.DATO_CAUSA);
		if (causa != null) {
			CausaDevolucion elegida = CausaDevolucion.valueOf(causa);
			lineas.add("Causa: " + elegida.etiqueta() + (elegida == CausaDevolucion.PAGO_DUPLICADO
					? " (el sistema comprobó que hay otro pago vigente de esas cuotas)" : ""));
		}
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
		// Decisión 18 (Ley 29733: finalidad de verificar con el padre): los celulares completos, solo aquí, para quien
		// aprueba. En la bitácora quedan enmascarados.
		boolean llamar = !familiasPorLlamar(solicitud).isEmpty();
		String contactos = contactos(pago.getFamilia().getId());
		if (contactos != null) {
			lineas.add((llamar ? "Antes de aprobar, llama al apoderado: " : "Contacto del apoderado: ") + contactos);
		}
		if (tipo == TipoAnulacion.CORRECCION) {
			Long destinoId = Long.valueOf(datos.get(ServicioAnulacionPagos.DATO_FAMILIA));
			String deDestino = destinoId.equals(pago.getFamilia().getId()) ? null : contactos(destinoId);
			if (deDestino != null) {
				lineas.add("Antes de aprobar, llama también a la otra familia: " + deDestino);
			}
		}
		return lineas;
	}

	/** A1: lo que dice la conciliación del pago (un digital solo se puede anular si se encontró en el banco). */
	private String verificacionBancaria(Pago pago) {
		if (pago.getMedio() == MedioPago.EFECTIVO) {
			return "Verificación bancaria: no aplica (efectivo)";
		}
		return verificaciones.findByPagoId(pago.getId())
				.map(v -> "Verificación bancaria: " + v.getResultado().etiqueta() + " por " + v.getCreadoPor() + " el "
						+ Calendario.formatear(v.getCreadoEn().toLocalDate()))
				.orElse("Verificación bancaria: SIN VERIFICAR. No se puede aprobar hasta que Administración lo encuentre en "
						+ "el banco.");
	}

	/** «Rosa Huamán: +51 987 654 321 · Pedro Quispe: +51 912 345 678»; {@code null} si nadie tiene celular. */
	private String contactos(Long familiaId) {
		List<String> lista = apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familiaId).stream()
				.filter(a -> a.getTelefonoWhatsapp() != null)
				.map(a -> a.nombreCompleto() + ": " + Telefono.formatear(a.getTelefonoWhatsapp())).toList();
		return lista.isEmpty() ? null : String.join(" · ", lista);
	}

	/**
	 * A2: una devolución en efectivo exige hablar con la familia del pago; una corrección hacia OTRA familia, con ambas
	 * (la del pago y la de destino), en ese orden.
	 */
	private Map<Long, String> familiasPorLlamar(SolicitudCambio solicitud) {
		Map<Long, String> porLlamar = new LinkedHashMap<>();
		Pago pago = pagos.findById(solicitud.getEntidadId()).orElse(null);
		if (pago == null) {
			return porLlamar;
		}
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		TipoAnulacion tipo = TipoAnulacion.valueOf(datos.get(ServicioAnulacionPagos.DATO_TIPO));
		if (tipo == TipoAnulacion.DEVOLUCION && pago.getMedio() == MedioPago.EFECTIVO) {
			porLlamar.put(pago.getFamilia().getId(), pago.getFamilia().getNombre());
		}
		if (tipo == TipoAnulacion.CORRECCION) {
			Long destino = Long.valueOf(datos.get(ServicioAnulacionPagos.DATO_FAMILIA));
			if (!destino.equals(pago.getFamilia().getId())) {
				porLlamar.put(pago.getFamilia().getId(), pago.getFamilia().getNombre());
				porLlamar.put(destino, familias.findById(destino).map(Familia::getNombre).orElse("familia de destino"));
			}
		}
		return porLlamar;
	}

	@Override
	public List<String> llamadas(SolicitudCambio solicitud) {
		return List.copyOf(familiasPorLlamar(solicitud).values());
	}

	/**
	 * Cada número debe ser el celular registrado de un apoderado de la familia que corresponde (en el orden de
	 * {@link #llamadas}). En la bitácora queda enmascarado, con el nombre del apoderado (decisión 18).
	 */
	@Override
	public String confirmarLlamadas(SolicitudCambio solicitud, List<String> telefonos) {
		Map<Long, String> porLlamar = familiasPorLlamar(solicitud);
		List<String> partes = new ArrayList<>();
		int i = 0;
		for (Map.Entry<Long, String> familia : porLlamar.entrySet()) {
			String numero = Telefono.normalizar(telefonos.get(i++));
			Apoderado llamado = numero == null ? null
					: apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familia.getKey()).stream()
							.filter(a -> numero.equals(a.getTelefonoWhatsapp())).findFirst().orElse(null);
			if (llamado == null) {
				throw new ReglaNegocioException("El número que escribiste para " + familia.getValue() + " no es el celular "
						+ "registrado de ninguno de sus apoderados. Llama a un número registrado (si cambió, primero "
						+ "actualízalo con su aprobación).");
			}
			partes.add(llamado.nombreCompleto() + " (" + Enmascarar.telefono(numero) + ", " + familia.getValue() + ")");
		}
		return (partes.size() == 1 ? "Habló con el apoderado: " : "Habló con ambas familias: ") + String.join(" y ", partes)
				+ ".";
	}

	/** Después de los cierres con diferencia y antes del resto: mueve dinero ya cobrado. */
	@Override
	public int prioridad(SolicitudCambio solicitud) {
		return 1;
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
