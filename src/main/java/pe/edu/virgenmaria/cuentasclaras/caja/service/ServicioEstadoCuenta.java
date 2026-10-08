package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ComprobanteImprimible;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.EstadoCuentaAlumno;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AnulacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAnulacion;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AnulacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AplicacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoVisibleCuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.DescuentoRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.ComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Estado de cuenta de un alumno (pestaña de la ficha) y sus comprobantes, para Promotoría, Dirección y Administración.
 * Solo lectura: las anulaciones se piden con {@link ServicioAnulacionPagos}.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioEstadoCuenta {

	private final AlumnoRepository alumnos;

	private final CuotaRepository cuotas;

	private final AplicacionPagoRepository aplicaciones;

	private final PagoRepository pagos;

	private final AnulacionPagoRepository anulaciones;

	private final DescuentoRepository descuentos;

	private final ComprobanteRepository comprobantes;

	private final RegistroSolicitudes solicitudes;

	private final BusquedaFamilias busqueda;

	private final NombresUsuarios nombres;

	private final ColegioService colegios;

	private final Clock reloj;

	public ServicioEstadoCuenta(AlumnoRepository alumnos, CuotaRepository cuotas, AplicacionPagoRepository aplicaciones,
			PagoRepository pagos, AnulacionPagoRepository anulaciones, DescuentoRepository descuentos,
			ComprobanteRepository comprobantes, RegistroSolicitudes solicitudes, BusquedaFamilias busqueda,
			NombresUsuarios nombres, ColegioService colegios, Clock reloj) {
		this.alumnos = alumnos;
		this.cuotas = cuotas;
		this.aplicaciones = aplicaciones;
		this.pagos = pagos;
		this.anulaciones = anulaciones;
		this.descuentos = descuentos;
		this.comprobantes = comprobantes;
		this.solicitudes = solicitudes;
		this.busqueda = busqueda;
		this.nombres = nombres;
		this.colegios = colegios;
		this.reloj = reloj;
	}

	public EstadoCuentaAlumno deAlumno(Long alumnoId) {
		Alumno alumno = alumnos.findById(alumnoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Alumno no encontrado"));
		LocalDate hoy = LocalDate.now(reloj);
		List<Cuota> suyas = cuotas.findByAlumnoIdOrderByFechaVencimientoAscIdAsc(alumnoId);
		List<EstadoCuentaAlumno.Cuota> filas = suyas.stream().map(c -> {
			EstadoVisibleCuota estado = c.estadoAl(hoy);
			return new EstadoCuentaAlumno.Cuota(c.getId(), c.getDescripcion(), c.getFechaVencimiento(), c.getMonto(),
					c.getMontoDescuento(), c.getMontoPagado(), c.saldo(), estado.etiqueta(), estado.variante());
		}).toList();
		List<Cuota> vigentes = suyas.stream().filter(c -> !c.anulada()).toList();
		boolean administracion = esAdministracion();
		List<Long> idsPagos = aplicaciones.pagosDeAlumno(alumnoId);
		List<Pago> delAlumno = idsPagos.isEmpty() ? List.of() : pagos.findByIdIn(idsPagos).stream()
				.sorted(Comparator.comparing(Pago::getId).reversed()).toList();
		Map<Long, AnulacionPago> anuladas = delAlumno.isEmpty() ? Map.of()
				: anulaciones.findByPagoIdIn(delAlumno.stream().map(Pago::getId).toList()).stream()
						.collect(Collectors.toMap(a -> a.getPago().getId(), Function.identity()));
		// Los pagos que estos reemplazan pueden ser de otro alumno (una corrección entre hermanos o familias).
		List<Long> conReemplazados = new ArrayList<>(idsPagos);
		delAlumno.stream().map(Pago::getReemplazaPagoId).filter(Objects::nonNull)
				.filter(id -> !idsPagos.contains(id)).forEach(conReemplazados::add);
		Map<Long, String> numeros = (conReemplazados.size() == idsPagos.size() ? delAlumno
				: pagos.findByIdIn(conReemplazados)).stream()
				.collect(Collectors.toMap(Pago::getId, p -> p.getComprobante().numeroCompleto()));
		List<EstadoCuentaAlumno.PagoAlumno> pagosAlumno = delAlumno.stream().map(p -> {
			boolean pendiente = p.vigente() && !solicitudes.pendientesDe("pago", p.getId()).isEmpty();
			AnulacionPago anulacion = anuladas.get(p.getId());
			return new EstadoCuentaAlumno.PagoAlumno(p.getId(), p.getFecha(), p.getComprobante().getId(),
					p.getComprobante().numeroCompleto(), p.getMedio().etiqueta(), p.getTotal(), nombres.de(p.getCajero()),
					!p.vigente() ? "Anulado" : pendiente ? "Esperando aprobación" : "Vigente",
					!p.vigente() ? "neutro" : pendiente ? "alerta" : "exito", !p.vigente(),
					administracion && p.vigente() && !pendiente,
					anulacion == null ? null : anulacion.getNotaCredito().getId(),
					anulacion == null ? null : anulacion.getNotaCredito().numeroCompleto(),
					p.getReemplazaPagoId() == null ? null
							: numeros.getOrDefault(p.getReemplazaPagoId(), "pago " + p.getReemplazaPagoId()));
		}).toList();
		List<EstadoCuentaAlumno.DescuentoAlumno> descuentosAlumno = descuentos.findByAlumnoIdOrderByIdDesc(alumnoId)
				.stream().map(d -> new EstadoCuentaAlumno.DescuentoAlumno(d.getTipo().etiqueta(), d.valorTexto(),
						d.getTotalEstimado(), d.getEstado().etiqueta(), d.getEstado().variante(), d.getCreadoPor(),
						d.getResueltoPor()))
				.toList();
		return new EstadoCuentaAlumno(alumno.getId(), alumno.nombreCompleto(), alumno.getDocumento().texto(),
				alumno.getFamilia().getNombre(), busqueda.grado(alumno), hoy, filas,
				Dinero.sumar(vigentes.stream().map(Cuota::getMonto).toList()),
				Dinero.sumar(vigentes.stream().map(Cuota::getMontoDescuento).toList()),
				Dinero.sumar(vigentes.stream().map(Cuota::getMontoPagado).toList()),
				Dinero.sumar(vigentes.stream().map(Cuota::saldo).toList()), pagosAlumno, descuentosAlumno,
				administracion);
	}

	/** Cualquier comprobante del colegio (boleta, factura o nota de crédito) para verlo o reimprimirlo. */
	public ComprobanteImprimible comprobante(Long comprobanteId) {
		Comprobante comprobante = comprobantes.findById(comprobanteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Comprobante no encontrado"));
		Pago pago = pagos.findByComprobanteId(comprobanteId).orElse(null);
		Comprobante anulado = comprobante.getModificaId() == null ? null
				: comprobantes.findById(comprobante.getModificaId()).orElse(null);
		boolean devolucion = anulado != null && anulaciones.existsByNotaCreditoIdAndTipo(comprobante.getId(),
				TipoAnulacion.DEVOLUCION);
		return ServicioCobro.imprimibleDe(comprobante, pago, colegios.nombreDe(comprobante.getColegioId()), nombres,
				anulado, devolucion);
	}

	private static boolean esAdministracion() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion != null && autenticacion.getAuthorities().stream()
				.anyMatch(a -> "ROLE_ADMINISTRACION".equals(a.getAuthority()));
	}
}
