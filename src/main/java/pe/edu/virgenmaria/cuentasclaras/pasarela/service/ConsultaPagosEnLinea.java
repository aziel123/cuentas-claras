package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Telefono;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.DetalleOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.OrdenesEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoCuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pagos en línea para el personal (Promotoría, Dirección y Administración): lo POR_REVISAR primero, con el motivo en
 * lenguaje claro y el contacto del apoderado para llamarlo; los de hoy y los vencidos. Solo lectura.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
@Transactional(readOnly = true)
public class ConsultaPagosEnLinea {

	static final String ENTIDAD = "orden_pago";

	private final OrdenPagoRepository ordenes;

	private final OrdenPagoCuotaRepository cuotasDeOrden;

	private final PagoRepository pagos;

	private final ApoderadoRepository apoderados;

	private final CuotaRepository cuotas;

	private final SolicitudCambioRepository solicitudes;

	private final Pasarelas pasarelas;

	private final Clock reloj;

	public ConsultaPagosEnLinea(OrdenPagoRepository ordenes, OrdenPagoCuotaRepository cuotasDeOrden, PagoRepository pagos,
			ApoderadoRepository apoderados, CuotaRepository cuotas, SolicitudCambioRepository solicitudes,
			Pasarelas pasarelas, Clock reloj) {
		this.ordenes = ordenes;
		this.cuotasDeOrden = cuotasDeOrden;
		this.pagos = pagos;
		this.apoderados = apoderados;
		this.cuotas = cuotas;
		this.solicitudes = solicitudes;
		this.pasarelas = pasarelas;
		this.reloj = reloj;
	}

	public OrdenesEnLinea lista() {
		LocalDate hoy = LocalDate.now(reloj);
		List<OrdenPago> recientes = ordenes.findTop50ByOrderByIdDesc();
		List<OrdenPago> porRevisar = ordenes.findByEstadoInOrderByIdDesc(EnumSet.of(EstadoOrden.POR_REVISAR));
		Map<Long, Pago> pagosPorOrden = pagos.findByOrdenPagoIdIn(recientes.stream().map(OrdenPago::getId).toList())
				.stream().collect(Collectors.toMap(Pago::getOrdenPagoId, Function.identity()));
		List<OrdenesEnLinea.Fila> deHoy = recientes.stream()
				.filter(o -> (o.getEstado() == EstadoOrden.PAGADA || o.getEstado() == EstadoOrden.APLICADA)
						&& o.getConfirmadoEn() != null && o.getConfirmadoEn().toLocalDate().equals(hoy))
				.map(o -> fila(o, pagosPorOrden.get(o.getId()))).toList();
		BigDecimal totalHoy = Dinero.sumar(deHoy.stream().map(OrdenesEnLinea.Fila::monto).toList());
		return new OrdenesEnLinea(hoy, porRevisar.stream().map(o -> fila(o, null)).toList(), deHoy,
				recientes.stream().filter(o -> o.getEstado() == EstadoOrden.CREADA).map(o -> fila(o, null)).toList(),
				recientes.stream().filter(o -> o.getEstado() == EstadoOrden.VENCIDA || o.getEstado() == EstadoOrden.RECHAZADA)
						.map(o -> fila(o, null)).toList(),
				totalHoy, pasarelas.simulada(), pasarelas.configurada().isPresent());
	}

	public DetalleOrden detalle(Long id) {
		OrdenPago orden = ordenes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Pago no encontrado"));
		Pago pago = pagos.findByOrdenPagoId(orden.getId()).orElse(null);
		List<String> lineas = cuotasDeOrden.findByOrdenIdOrderByIdAsc(orden.getId()).stream()
				.map(f -> f.getCuota().getDescripcion() + " de " + f.getCuota().getAlumno().nombreCompleto() + " · "
						+ Dinero.formatear(f.getMonto()) + " (hoy " + f.getCuota().getEstado().name()
								.toLowerCase(java.util.Locale.ROOT) + ")")
				.toList();
		List<String> pendientes = solicitudes.findByEntidadAndEntidadIdAndEstadoOrderByIdAsc(ENTIDAD, orden.getId(),
				EstadoSolicitud.PENDIENTE).stream().map(s -> s.getTipo().etiqueta() + " (pedida por "
						+ s.getSolicitadoPor() + ")").toList();
		Optional<SolicitudCambio> devolucion = solicitudes.findFirstByTipoAndEntidadAndEntidadIdAndEstadoOrderByIdDesc(
				TipoSolicitud.DEVOLVER_INGRESO, ENTIDAD, orden.getId(), EstadoSolicitud.APROBADA);
		boolean porRevisar = orden.getEstado() == EstadoOrden.POR_REVISAR;
		List<DetalleOrden.CuotaDestino> destino = porRevisar ? cuotas.porPagarDeFamilia(orden.getFamilia().getId()).stream()
				.filter(c -> c.admiteCobro())
				.map(c -> new DetalleOrden.CuotaDestino(c.getId(), c.getAlumno().nombreCompleto(), c.getDescripcion(),
						c.saldo())).toList() : List.of();
		Apoderado apoderado = orden.getApoderado();
		return new DetalleOrden(orden.getId(), orden.getReferencia(), orden.getFamilia().getNombre(),
				orden.getFamilia().getId(), apoderado.nombreCompleto(), contacto(orden.getFamilia().getId()), orden.getMonto(),
				orden.getEstado().name(), orden.getEstado().etiqueta(), orden.getEstado().variante(),
				orden.getProveedor().name(), orden.getProveedor() == ProveedorPasarela.SIMULADA, orden.getCreadoEn(),
				orden.getVenceEn(), orden.getMontoConfirmado(),
				orden.getMedioConfirmado() == null ? null : orden.getMedioConfirmado().etiqueta(), orden.getOperacion(),
				orden.getCargoId(), orden.getConfirmadoEn(), orden.isTardia(),
				orden.getMotivoRevision() == null ? null : orden.getMotivoRevision().descripcion(),
				orden.getDetalleRevision(), lineas, pago == null ? null : pago.getComprobante().numeroCompleto(), pendientes,
				destino, porRevisar && pendientes.isEmpty() && devolucion.isEmpty() && esAdministracion(),
				porRevisar && devolucion.isPresent() && esAdministracion(),
				orden.getDevolucionOperacion());
	}

	private OrdenesEnLinea.Fila fila(OrdenPago o, Pago pago) {
		String pendiente = o.getEstado() == EstadoOrden.POR_REVISAR ? solicitudes.findByEntidadAndEntidadIdAndEstadoOrderByIdAsc(
				ENTIDAD, o.getId(), EstadoSolicitud.PENDIENTE).stream().map(s -> s.getTipo().etiqueta()).findFirst()
				.orElse(null) : null;
		return new OrdenesEnLinea.Fila(o.getId(), o.getReferencia(), o.getFamilia().getNombre(), o.getMonto(),
				o.getEstado().name(), o.getEstado().etiqueta(), o.getEstado().variante(),
				o.getMedioConfirmado() == null ? null : o.getMedioConfirmado().etiqueta(), o.getOperacion(), o.getCreadoEn(),
				o.getMotivoRevision() == null ? null : o.getMotivoRevision().descripcion(),
				o.getMotivoRevision() != null && o.getMotivoRevision().critico(),
				o.getProveedor() == ProveedorPasarela.SIMULADA,
				pago == null ? null : pago.getComprobante().numeroCompleto(), pendiente);
	}

	/** Los celulares de la familia (solo para el personal que debe llamar: Ley 29733, finalidad de verificar). */
	private String contacto(Long familiaId) {
		List<String> lista = apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familiaId).stream()
				.filter(a -> a.isActivo() && a.getTelefonoWhatsapp() != null)
				.map(a -> a.nombreCompleto() + ": " + Telefono.formatear(a.getTelefonoWhatsapp())).toList();
		return lista.isEmpty() ? null : String.join(" · ", lista);
	}

	private static boolean esAdministracion() {
		org.springframework.security.core.Authentication autenticacion = org.springframework.security.core.context
				.SecurityContextHolder.getContext().getAuthentication();
		return autenticacion != null && autenticacion.getAuthorities().stream()
				.anyMatch(a -> "ROLE_ADMINISTRACION".equals(a.getAuthority()));
	}
}
