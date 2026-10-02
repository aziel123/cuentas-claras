package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.config.PropiedadesCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AplicacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ImputacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ImputacionPago.CuotaPorPagar;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ImputacionPago.Imputacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.NumeroOperacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ReglasEfectivo;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AplicacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.DocumentoReceptor;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.LineaDocumento;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Receptor;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.ServicioComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Núcleo del libro de pagos: registra un pago con su comprobante y sus aplicaciones, y refleja lo pagado en cada cuota.
 * <p>
 * Orden (y orden de bloqueos, para evitar interbloqueos): la caja ya viene bloqueada → cuotas (por id ascendente) →
 * serie del comprobante → bitácora. El total lo calcula el sistema con los saldos de las cuotas BLOQUEADAS: la cajera
 * nunca lo escribe. En MySQL, los triggers repiten las reglas críticas (cuotas de la familia del pago, boleta por el
 * mismo total, monto pagado = libro).
 * <p>
 * Sin rol propio: exige la transacción de {@link ServicioCobro} (que ya exigió el rol CAJA). ArchUnit permite usarlo
 * solo desde {@code caja.service}.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class LibroPagos {

	private final CuotaRepository cuotas;

	private final PagoRepository pagos;

	private final AplicacionPagoRepository aplicaciones;

	private final ApoderadoRepository apoderados;

	private final ServicioComprobantes comprobantes;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final PropiedadesCaja propiedades;

	public LibroPagos(CuotaRepository cuotas, PagoRepository pagos, AplicacionPagoRepository aplicaciones,
			ApoderadoRepository apoderados, ServicioComprobantes comprobantes, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, PropiedadesCaja propiedades) {
		this.cuotas = cuotas;
		this.pagos = pagos;
		this.aplicaciones = aplicaciones;
		this.apoderados = apoderados;
		this.comprobantes = comprobantes;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.propiedades = propiedades;
	}

	/**
	 * Registra el pago de {@code familia} en {@code caja} (ya bloqueada por quien llama).
	 *
	 * @throws MontoCambiadoException si el total ya no es el que la cajera vio al revisar
	 */
	public Pago registrar(CajaDiaria caja, Familia familia, OrdenCobro orden) {
		Objects.requireNonNull(caja, "caja");
		Objects.requireNonNull(familia, "familia");
		List<Long> ids = orden.cuotaIds().stream().filter(Objects::nonNull).distinct().sorted().toList();
		if (ids.isEmpty()) {
			throw new ReglaNegocioException("Elige al menos una cuota.");
		}
		// 1. Bloquea las cuotas: otra cajera que cobre la misma cuota espera aquí y luego la ve pagada.
		List<Cuota> elegidas = cuotas.bloquear(ids);
		if (elegidas.size() != ids.size()) {
			throw new RecursoNoEncontradoException("Cuota no encontrada");
		}
		// 2. Todas de esa familia y cobrables.
		elegidas.forEach(c -> exigirCobrable(c, familia));
		// 3. Total (lo calcula el sistema) e imputación.
		BigDecimal total = Dinero.sumar(elegidas.stream().map(Cuota::saldo).toList());
		if (orden.totalVisto() == null || orden.totalVisto().compareTo(total) != 0) {
			throw new MontoCambiadoException(total);
		}
		boolean aCuenta = orden.montoACuenta() != null;
		BigDecimal importe = aCuenta ? exigirPagoACuenta(orden.montoACuenta(), total) : total;
		String operacion = null;
		if (orden.medio().digital()) {
			operacion = NumeroOperacion.normalizar(orden.numeroOperacion());
			if (pagos.existsByMedioAndOperacionVigente(orden.medio(), operacion)) {
				throw new ReglaNegocioException("Ese número de operación de " + orden.medio().etiqueta()
						+ " ya está registrado en otro pago. Revisa el número.");
			}
		}
		else {
			// Valida lo recibido ANTES de emitir el comprobante.
			ReglasEfectivo.vuelto(importe, orden.recibido());
		}
		List<Imputacion> imputaciones = ImputacionPago.imputar(importe, elegidas.stream()
				.map(c -> new CuotaPorPagar(c.getId(), c.getFechaVencimiento(), c.saldo())).toList());
		Map<Long, Cuota> porId = elegidas.stream().collect(Collectors.toMap(Cuota::getId, Function.identity()));

		// 4. Comprobante (bloquea la serie y toma el número siguiente, en esta misma transacción).
		Receptor receptor = receptor(orden.comprobante(), familia, elegidas);
		List<LineaDocumento> lineas = imputaciones.stream().map(i -> {
			Cuota cuota = porId.get(i.cuotaId());
			boolean parcial = i.monto().compareTo(cuota.saldo()) < 0;
			return new LineaDocumento(cuota.getDescripcion() + " · " + cuota.getAlumno().nombreCompleto()
					+ (parcial ? " (a cuenta)" : ""), i.monto());
		}).toList();
		Comprobante comprobante = comprobantes.emitir(orden.comprobante().tipo(), receptor, caja.getFecha(), lineas);

		// 5. Pago y aplicaciones; 6. cada cuota refleja su libro.
		Pago pago = pagos.save(Pago.enCaja(caja, familia, comprobante, orden.medio(), operacion, importe,
				orden.recibido(), aCuenta, orden.clave()));
		List<String> detalleCuotas = new ArrayList<>();
		for (Imputacion imputacion : imputaciones) {
			Cuota cuota = porId.get(imputacion.cuotaId());
			aplicaciones.save(AplicacionPago.aplicar(pago, cuota, imputacion.monto()));
			cuota.reflejarPagos(Objects.requireNonNullElse(aplicaciones.sumaDeCuota(cuota.getId()), Dinero.CERO));
			detalleCuotas.add(cuota.getDescripcion() + " de " + cuota.getAlumno().nombreCompleto() + " (vence "
					+ Calendario.formatear(cuota.getFechaVencimiento()) + ") " + Dinero.formatear(imputacion.monto())
					+ " → " + cuota.getEstado().name() + ", saldo " + Dinero.formatear(cuota.saldo()));
		}

		// 7. Bitácora (al final: es el último bloqueo) y evento.
		auditar(pago, comprobante, receptor, detalleCuotas);
		eventos.publishEvent(new PagoRegistrado(pago.getId()));
		return pago;
	}

	/** El responsable de pago del alumno de la cuota que vence primero: a su nombre sale la boleta por defecto. */
	static Apoderado responsablePorDefecto(List<Cuota> elegidas) {
		return elegidas.stream().min(Comparator.comparing(Cuota::getFechaVencimiento).thenComparing(Cuota::getId))
				.orElseThrow().getAlumno().getResponsablePago();
	}

	static DocumentoReceptor documentoDe(Apoderado apoderado) {
		return DocumentoReceptor.valueOf(apoderado.getDocumento().tipo().name());
	}

	private void exigirCobrable(Cuota cuota, Familia familia) {
		if (!Objects.equals(cuota.getAlumno().getFamilia().getId(), familia.getId())) {
			throw new ReglaNegocioException("Solo se cobran juntas cuotas de una misma familia: «" + cuota.getDescripcion()
					+ "» es de otra familia.");
		}
		if (cuota.anulacionPendiente()) {
			throw new ReglaNegocioException("La cuota «" + cuota.getDescripcion() + "» de "
					+ cuota.getAlumno().nombreCompleto()
					+ " tiene una anulación esperando aprobación: no se cobra hasta que se resuelva.");
		}
		if (!cuota.admiteCobro()) {
			throw new ReglaNegocioException("La cuota «" + cuota.getDescripcion() + "» de "
					+ cuota.getAlumno().nombreCompleto() + " ya no se puede cobrar (está "
					+ cuota.getEstado().name().toLowerCase(java.util.Locale.ROOT) + ").");
		}
	}

	private BigDecimal exigirPagoACuenta(BigDecimal monto, BigDecimal total) {
		if (!propiedades.permitirPagoACuenta()) {
			throw new ReglaNegocioException("El pago a cuenta no está habilitado: se cobra el total de las cuotas elegidas ("
					+ Dinero.formatear(total) + ").");
		}
		BigDecimal aCuenta = Dinero.exigirDecimos(monto, "El pago a cuenta debe ser múltiplo de S/ 0.10.");
		if (aCuenta.compareTo(propiedades.pagoACuentaMinimo()) < 0) {
			throw new ReglaNegocioException("El pago a cuenta mínimo es " + Dinero.formatear(propiedades.pagoACuentaMinimo())
					+ ".");
		}
		if (aCuenta.compareTo(total) >= 0) {
			throw new ReglaNegocioException("El pago a cuenta debe ser menor que el total (" + Dinero.formatear(total)
					+ "). Si paga todo, deja el monto a cuenta vacío.");
		}
		return aCuenta;
	}

	private Receptor receptor(DatosComprobante datos, Familia familia, List<Cuota> elegidas) {
		if (datos == null || datos.tipo() == null) {
			throw new ReglaNegocioException("Elige boleta o factura.");
		}
		if (datos.tipo() == TipoComprobante.FACTURA) {
			return Receptor.de(DocumentoReceptor.RUC, datos.ruc(), datos.razonSocial());
		}
		if (datos.tipo() != TipoComprobante.BOLETA) {
			throw new ReglaNegocioException("En caja se emite boleta o factura.");
		}
		Apoderado apoderado;
		if (datos.apoderadoId() == null) {
			apoderado = responsablePorDefecto(elegidas);
		}
		else {
			apoderado = apoderados.findById(datos.apoderadoId())
					.filter(a -> a.isActivo() && Objects.equals(a.getFamilia().getId(), familia.getId()))
					.orElseThrow(() -> new ReglaNegocioException("La boleta solo puede salir a nombre de un apoderado "
							+ "activo de esta familia."));
		}
		return Receptor.de(documentoDe(apoderado), apoderado.getDocumento().numero(), apoderado.nombreCompleto());
	}

	private void auditar(Pago pago, Comprobante comprobante, Receptor receptor, List<String> detalleCuotas) {
		String medio = pago.getMedio().etiqueta() + (pago.getMedio().digital()
				? " (operación " + pago.getNumeroOperacion() + ")"
				: " (recibido " + Dinero.formatear(pago.getRecibido()) + ", vuelto " + Dinero.formatear(pago.getVuelto())
						+ ")");
		String detalle = comprobante.getTipo().etiqueta() + " " + comprobante.numeroCompleto() + " por "
				+ Dinero.formatear(pago.getTotal()) + " en " + medio + ". " + pago.getFamilia().getNombre()
				+ ". A nombre de " + receptor.nombre() + " (" + receptor.documentoEnmascarado() + "). Cuotas: "
				+ String.join("; ", detalleCuotas) + ".";
		String nuevo = pago.getEstado().name() + " · " + Dinero.formatear(pago.getTotal()) + " · "
				+ pago.getMedio().etiqueta() + " · " + comprobante.numeroCompleto();
		auditoria.registrar(AccionAuditoria.PAGO_REGISTRADO, "pago", pago.getId().toString(), null, nuevo, detalle);
		auditoria.registrar(AccionAuditoria.COMPROBANTE_EMITIDO, "comprobante", comprobante.getId().toString(), null,
				comprobante.numeroCompleto() + " · " + Dinero.formatear(comprobante.getTotal()),
				comprobante.getTipo().etiqueta() + " " + comprobante.numeroCompleto() + " del pago " + pago.getId()
						+ " a nombre de " + receptor.nombre() + " (" + receptor.documentoEnmascarado() + "), "
						+ comprobante.getAfectacionIgv().etiqueta().toLowerCase(java.util.Locale.ROOT) + ", proveedor "
						+ comprobante.getProveedor().name() + ".");
		if (pago.isACuenta()) {
			auditoria.registrar(AccionAuditoria.PAGO_A_CUENTA, "pago", pago.getId().toString(), null, nuevo,
					"Pago a cuenta (parcial): " + detalle);
		}
	}
}
