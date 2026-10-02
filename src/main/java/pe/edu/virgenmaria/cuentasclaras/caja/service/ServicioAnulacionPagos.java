package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CorreccionRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CorreccionVista;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ResultadoBusqueda;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AplicacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAnulacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAplicacion;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AplicacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pedir la anulación de un pago (decisión 7). La cajera (solo sus pagos; si no, 404) o Administración la PIDEN: nadie
 * anula aquí. La solicitud {@code ANULACION_PAGO} la aprueba otra persona de Promotoría o Dirección, que no puede ser
 * quien la pidió, la cajera del pago ni quien preparó esas cuentas ({@link ManejadorAnulacionPago}).
 * <ul>
 *   <li>Devolución: el dinero vuelve al apoderado.</li>
 *   <li>Corrección: el mismo dinero pasa a otras cuotas (incluso de otra familia, resaltado), con boleta nueva.</li>
 * </ul>
 */
@Service
@Transactional
@PreAuthorize("hasAnyRole('CAJA','ADMINISTRACION')")
public class ServicioAnulacionPagos {

	static final String DATO_TIPO = "tipo";

	static final String DATO_FAMILIA = "familiaId";

	static final String DATO_CUOTAS = "cuotas";

	private final PagoRepository pagos;

	private final AplicacionPagoRepository aplicaciones;

	private final CuotaRepository cuotas;

	private final FamiliaRepository familias;

	private final BusquedaFamilias busqueda;

	private final RegistroSolicitudes solicitudes;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ServicioAnulacionPagos(PagoRepository pagos, AplicacionPagoRepository aplicaciones, CuotaRepository cuotas,
			FamiliaRepository familias, BusquedaFamilias busqueda, RegistroSolicitudes solicitudes,
			AuditoriaService auditoria, Clock reloj) {
		this.pagos = pagos;
		this.aplicaciones = aplicaciones;
		this.cuotas = cuotas;
		this.familias = familias;
		this.busqueda = busqueda;
		this.solicitudes = solicitudes;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** El dinero vuelve al apoderado: pide la anulación del pago (con nota de crédito al aprobarse). */
	public void solicitarDevolucion(Long pagoId, String motivo) {
		Pago pago = pagoQuePuedePedir(pagoId);
		String texto = Motivo.exigir(motivo);
		String resumen = "Devolver " + descripcion(pago);
		crear(pago, resumen, Map.of(DATO_TIPO, TipoAnulacion.DEVOLUCION.name()), texto);
	}

	/**
	 * La corrección: la familia destino (buscada por nombre o DNI) y sus cuotas con el saldo que tendrían si el pago se
	 * anula. Solo lectura.
	 */
	@Transactional(readOnly = true)
	public CorreccionVista prepararCorreccion(Long pagoId, Long familiaId, String texto) {
		Pago pago = pagoQuePuedePedir(pagoId);
		String limpio = Normalizador.limpiar(texto);
		List<ResultadoBusqueda> resultados = limpio == null || limpio.length() < 2 ? List.of()
				: busqueda.buscar(limpio, LocalDate.now(reloj));
		Familia destino = familiaId == null ? null : familia(familiaId);
		List<CorreccionVista.CuotaDestino> disponibles = destino == null ? List.of()
				: disponibles(pago, destino).values().stream().toList();
		return new CorreccionVista(pago.getId(), pago.getComprobante().numeroCompleto(), pago.getTotal(),
				pago.getMedio().etiqueta(), pago.getFecha(), pago.getFamilia().getId(), pago.getFamilia().getNombre(),
				limpio, resultados, destino == null ? null : destino.getId(), destino == null ? null : destino.getNombre(),
				disponibles);
	}

	/** Pide aplicar el dinero del pago a otras cuotas: deben sumar exactamente el pago (después de anularlo). */
	public void solicitarCorreccion(Long pagoId, CorreccionRequest pedido) {
		Pago pago = pagoQuePuedePedir(pagoId);
		String texto = Motivo.exigir(pedido.motivo());
		Familia destino = familia(pedido.familiaId());
		Map<Long, CorreccionVista.CuotaDestino> disponibles = disponibles(pago, destino);
		List<Long> ids = pedido.cuotaIds().stream().filter(Objects::nonNull).distinct().sorted().toList();
		if (ids.isEmpty() || !disponibles.keySet().containsAll(ids)) {
			throw new ReglaNegocioException("Alguna cuota elegida no está por pagar o no es de " + destino.getNombre()
					+ ". Vuelve a elegir.");
		}
		Set<Long> originales = new LinkedHashSet<>(aplicaciones.cuotasDePago(pago.getId()));
		if (destino.getId().equals(pago.getFamilia().getId()) && originales.equals(new LinkedHashSet<>(ids))) {
			throw new ReglaNegocioException("La corrección debe aplicar el pago a otras cuotas.");
		}
		BigDecimal suma = Dinero.sumar(ids.stream().map(id -> disponibles.get(id).saldo()).toList());
		if (suma.compareTo(pago.getTotal()) != 0 && !(pago.isACuenta() && suma.compareTo(pago.getTotal()) > 0)) {
			throw new ReglaNegocioException("Las cuotas elegidas suman " + Dinero.formatear(suma) + " y el pago es de "
					+ Dinero.formatear(pago.getTotal()) + ": elige cuotas que sumen exactamente el pago.");
		}
		boolean otraFamilia = !destino.getId().equals(pago.getFamilia().getId());
		String resumen = "Corregir " + descripcion(pago) + ": pasa a " + (otraFamilia ? "OTRA familia, " : "")
				+ destino.getNombre() + " (" + ids.size() + " cuota(s))";
		crear(pago, resumen, Map.of(DATO_TIPO, TipoAnulacion.CORRECCION.name(), DATO_FAMILIA, destino.getId().toString(),
				DATO_CUOTAS, ids.stream().map(String::valueOf).collect(Collectors.joining(","))), texto);
	}

	private void crear(Pago pago, String resumen, Map<String, String> datos, String motivo) {
		auditoria.registrar(AccionAuditoria.PAGO_ANULACION_SOLICITADA, "pago", pago.getId().toString(),
				pago.getEstado().name(), "Anulación pendiente de aprobación", resumen + ". Cobrado por " + pago.getCajero()
						+ ". Motivo: " + motivo);
		solicitudes.crear(TipoSolicitud.ANULACION_PAGO, "pago", pago.getId(), resumen, datos, motivo);
	}

	/**
	 * Cuotas por pagar de la familia destino, con el saldo que tendrían si este pago se anula (las que el pago cubrió
	 * vuelven a deberse).
	 */
	private Map<Long, CorreccionVista.CuotaDestino> disponibles(Pago pago, Familia destino) {
		Map<Long, BigDecimal> devueltos = new LinkedHashMap<>();
		for (AplicacionPago a : aplicaciones.findByPagoIdAndTipoOrderByIdAsc(pago.getId(), TipoAplicacion.APLICACION)) {
			devueltos.merge(a.getCuota().getId(), a.getMonto(), BigDecimal::add);
		}
		List<Cuota> candidatas = new ArrayList<>(cuotas.porPagarDeFamilia(destino.getId()));
		if (destino.getId().equals(pago.getFamilia().getId())) {
			for (Long id : devueltos.keySet()) {
				if (candidatas.stream().noneMatch(c -> c.getId().equals(id))) {
					cuotas.findById(id).ifPresent(candidatas::add);
				}
			}
		}
		Map<Long, CorreccionVista.CuotaDestino> resultado = new LinkedHashMap<>();
		candidatas.stream()
				.filter(c -> !c.anulada() && !c.anulacionPendiente())
				.sorted(java.util.Comparator.comparing(Cuota::getFechaVencimiento).thenComparing(Cuota::getId))
				.forEach(c -> {
					BigDecimal saldo = c.saldo().add(devueltos.getOrDefault(c.getId(), Dinero.CERO));
					if (saldo.signum() > 0) {
						resultado.put(c.getId(), new CorreccionVista.CuotaDestino(c.getId(), c.getAlumno().nombreCompleto(),
								c.getDescripcion(), c.getFechaVencimiento(), saldo, devueltos.containsKey(c.getId())));
					}
				});
		return resultado;
	}

	/** Un pago VIGENTE que el usuario puede pedir anular: la cajera, solo los suyos (si no, 404). */
	private Pago pagoQuePuedePedir(Long pagoId) {
		Pago pago = pagos.findById(pagoId).orElseThrow(() -> new RecursoNoEncontradoException("Pago no encontrado"));
		if (!esAdministracion() && !pago.getCajero().equals(SesionCaja.usuario())) {
			throw new RecursoNoEncontradoException("Pago no encontrado");
		}
		if (!pago.vigente()) {
			throw new ReglaNegocioException("El pago " + pago.getComprobante().numeroCompleto() + " ya está anulado.");
		}
		return pago;
	}

	private Familia familia(Long id) {
		return familias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Familia no encontrada"));
	}

	private static String descripcion(Pago pago) {
		return "el pago " + pago.getComprobante().numeroCompleto() + " (" + Dinero.formatear(pago.getTotal()) + ", "
				+ pago.getMedio().etiqueta() + ", " + Calendario.formatear(pago.getFecha()) + ") de "
				+ pago.getFamilia().getNombre();
	}

	private static boolean esAdministracion() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion != null && autenticacion.getAuthorities().stream()
				.anyMatch(a -> "ROLE_ADMINISTRACION".equals(a.getAuthority()));
	}
}
