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
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.NumeroOperacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ReglasEfectivo;
import pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAplicacion;
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
import java.util.UUID;
import java.time.LocalDate;
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
			// Único para todos los medios digitales juntos y en su forma canónica (C1): «012-345» es «12345».
			if (pagos.existsByOperacionVigente(operacion)) {
				throw new ReglaNegocioException("Ese número de operación ya está registrado en otro pago (con este u otro "
						+ "formato, en cualquier medio digital). Revisa el número.");
			}
		}
		else {
			// Valida lo recibido ANTES de emitir el comprobante.
			ReglasEfectivo.vuelto(importe, orden.recibido());
		}
		List<Imputacion> imputaciones = imputar(importe, elegidas);

		// 4. Comprobante (bloquea la serie y toma el número siguiente, en esta misma transacción).
		Receptor receptor = receptor(orden.comprobante(), familia, elegidas);
		Comprobante comprobante = comprobantes.emitir(orden.comprobante().tipo(), receptor, caja.getFecha(),
				lineas(imputaciones, elegidas));

		// 5. Pago y aplicaciones; 6. cada cuota refleja su libro.
		Pago pago = pagos.save(Pago.enCaja(caja, familia, comprobante, orden.medio(), operacion, importe,
				orden.recibido(), aCuenta, orden.clave()));
		List<String> detalleCuotas = aplicar(pago, imputaciones, elegidas);

		// 7. Bitácora (al final: es el último bloqueo) y evento.
		auditar(AccionAuditoria.PAGO_REGISTRADO, pago, comprobante, receptor, detalleCuotas, "");
		eventos.publishEvent(new PagoRegistrado(pago.getId()));
		return pago;
	}

	/**
	 * Reversiones de un pago ya ANULADO (con su anulación registrada): una fila negativa por cada aplicación, nunca un
	 * borrado. Cada cuota vuelve a reflejar su libro (vuelve a deberse). Las cuotas deben estar ya bloqueadas.
	 *
	 * @return el detalle de las cuotas que vuelven a deberse
	 */
	public List<String> revertir(Pago pago) {
		if (pago.vigente()) {
			throw new IllegalStateException("Primero se registra la anulación y se marca el pago como ANULADO");
		}
		List<String> detalle = new ArrayList<>();
		for (AplicacionPago original : aplicaciones.findByPagoIdAndTipoOrderByIdAsc(pago.getId(),
				TipoAplicacion.APLICACION)) {
			aplicaciones.save(AplicacionPago.revertir(original));
			Cuota cuota = original.getCuota();
			cuota.reflejarPagos(Objects.requireNonNullElse(aplicaciones.sumaDeCuota(cuota.getId()), Dinero.CERO));
			detalle.add(cuota.getDescripcion() + " de " + cuota.getAlumno().nombreCompleto() + " vuelve a deber "
					+ Dinero.formatear(original.getMonto()) + " (" + cuota.getEstado().name() + ")");
		}
		return detalle;
	}

	/**
	 * Pago de reemplazo de una corrección: el mismo dinero del pago ANULADO (misma caja, medio y total) aplicado a otras
	 * cuotas de {@code familia}, con un comprobante nuevo. Lo registra quien aprueba. Las cuotas deben estar ya
	 * bloqueadas (por id) y el pago anulado, guardado con flush.
	 */
	public Pago reemplazar(Pago anulado, Familia familia, List<Long> cuotaIds, DatosComprobante datos, LocalDate fecha) {
		List<Long> ids = cuotaIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
		List<Cuota> elegidas = cuotas.bloquear(ids);
		if (ids.isEmpty() || elegidas.size() != ids.size()) {
			throw new ReglaNegocioException("Alguna cuota de la corrección ya no existe: recházala y pide otra.");
		}
		elegidas.forEach(c -> exigirCobrable(c, familia));
		BigDecimal debe = Dinero.sumar(elegidas.stream().map(Cuota::saldo).toList());
		BigDecimal importe = anulado.getTotal();
		if (importe.compareTo(debe) > 0 || (importe.compareTo(debe) < 0 && !anulado.isACuenta())) {
			throw new ReglaNegocioException("Las cuotas de la corrección ya no suman el pago: deben "
					+ Dinero.formatear(debe) + " y el pago es de " + Dinero.formatear(importe)
					+ ". Recházala y pide otra corrección.");
		}
		List<Imputacion> imputaciones = imputar(importe, elegidas);
		Receptor receptor = receptor(datos, familia, elegidas);
		Comprobante comprobante = comprobantes.emitir(datos.tipo(), receptor, fecha, lineas(imputaciones, elegidas));
		Pago reemplazo = pagos.save(Pago.reemplazo(anulado, familia, comprobante, importe.compareTo(debe) < 0,
				UUID.randomUUID()));
		List<String> detalleCuotas = aplicar(reemplazo, imputaciones, elegidas);
		boolean otraFamilia = !Objects.equals(anulado.getFamilia().getId(), familia.getId());
		auditar(AccionAuditoria.PAGO_REEMPLAZO_REGISTRADO, reemplazo, comprobante, receptor, detalleCuotas,
				" Reemplaza al pago " + anulado.getId() + " (" + anulado.getComprobante().numeroCompleto() + ") de "
						+ anulado.getFamilia().getNombre() + (otraFamilia ? ": el dinero pasa a OTRA familia." : "."));
		eventos.publishEvent(new PagoRegistrado(reemplazo.getId()));
		return reemplazo;
	}

	/**
	 * Sprint 4: pago que entra solo por un canal (pasarela), en la caja del canal ya bloqueada y con las cuotas ya
	 * bloqueadas (por id) por quien llama. El monto lo confirmó la pasarela; el comprobante sale con la fecha de hoy
	 * ({@code fechaComprobante}) y el pago con la del canal. Mismas reglas, imputación, comprobante y bitácora que en
	 * ventanilla. En MySQL, trg_pago_registro exige además la confirmación de la orden por ese monto (debe estar ya
	 * guardada con flush).
	 *
	 * @param aCuenta el monto no cubre todas las cuotas (solo al aplicar un ingreso por revisar)
	 */
	public Pago registrarEnCanal(CajaDiaria canal, Familia familia, List<Cuota> bloqueadas, MedioPago medio,
			String operacion, BigDecimal monto, boolean aCuenta, DatosComprobante datos, Long ordenPagoId, UUID clave,
			LocalDate fechaComprobante, String detalleExtra) {
		Objects.requireNonNull(canal, "canal");
		Objects.requireNonNull(familia, "familia");
		if (bloqueadas == null || bloqueadas.isEmpty()) {
			throw new IllegalArgumentException("El pago en línea necesita sus cuotas");
		}
		bloqueadas.forEach(c -> exigirCobrable(c, familia));
		BigDecimal debe = Dinero.sumar(bloqueadas.stream().map(Cuota::saldo).toList());
		BigDecimal importe = Dinero.positivo(monto, "el monto del pago en línea");
		if (importe.compareTo(debe) > 0 || (!aCuenta && importe.compareTo(debe) != 0)) {
			throw new IllegalStateException("El monto del pago en línea no corresponde al saldo de sus cuotas");
		}
		List<Imputacion> imputaciones = imputar(importe, bloqueadas);
		Receptor receptor = receptor(datos, familia, bloqueadas);
		Comprobante comprobante = comprobantes.emitir(datos.tipo(), receptor, fechaComprobante,
				lineas(imputaciones, bloqueadas));
		Pago pago = pagos.save(Pago.dePasarela(canal, familia, comprobante, medio, operacion, importe, aCuenta,
				ordenPagoId, clave));
		List<String> detalleCuotas = aplicar(pago, imputaciones, bloqueadas);
		auditar(AccionAuditoria.PAGO_REGISTRADO, pago, comprobante, receptor, detalleCuotas,
				" Pago en línea (" + canal.getCanal().etiqueta() + ", orden " + ordenPagoId + ")."
						+ (detalleExtra == null ? "" : " " + detalleExtra));
		eventos.publishEvent(new PagoRegistrado(pago.getId()));
		return pago;
	}

	private static List<Imputacion> imputar(BigDecimal importe, List<Cuota> elegidas) {
		return ImputacionPago.imputar(importe, elegidas.stream()
				.map(c -> new CuotaPorPagar(c.getId(), c.getFechaVencimiento(), c.saldo())).toList());
	}

	private static List<LineaDocumento> lineas(List<Imputacion> imputaciones, List<Cuota> elegidas) {
		Map<Long, Cuota> porId = elegidas.stream().collect(Collectors.toMap(Cuota::getId, Function.identity()));
		return imputaciones.stream().map(i -> {
			Cuota cuota = porId.get(i.cuotaId());
			boolean parcial = i.monto().compareTo(cuota.saldo()) < 0;
			return new LineaDocumento(cuota.getDescripcion() + " · " + cuota.getAlumno().nombreCompleto()
					+ (parcial ? " (a cuenta)" : ""), i.monto());
		}).toList();
	}

	/** Inserta las aplicaciones del pago y refleja el libro en cada cuota. */
	private List<String> aplicar(Pago pago, List<Imputacion> imputaciones, List<Cuota> elegidas) {
		Map<Long, Cuota> porId = elegidas.stream().collect(Collectors.toMap(Cuota::getId, Function.identity()));
		List<String> detalleCuotas = new ArrayList<>();
		for (Imputacion imputacion : imputaciones) {
			Cuota cuota = porId.get(imputacion.cuotaId());
			aplicaciones.save(AplicacionPago.aplicar(pago, cuota, imputacion.monto()));
			cuota.reflejarPagos(Objects.requireNonNullElse(aplicaciones.sumaDeCuota(cuota.getId()), Dinero.CERO));
			detalleCuotas.add(cuota.getDescripcion() + " de " + cuota.getAlumno().nombreCompleto() + " (vence "
					+ Calendario.formatear(cuota.getFechaVencimiento()) + ") " + Dinero.formatear(imputacion.monto())
					+ " → " + cuota.getEstado().name() + ", saldo " + Dinero.formatear(cuota.saldo()));
		}
		return detalleCuotas;
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

	/** B2: el apoderado activo de la familia que tiene ese RUC registrado, si lo hay. */
	java.util.Optional<Apoderado> rucRegistrado(Long familiaId, String ruc) {
		if (ruc == null || ruc.isBlank()) {
			return java.util.Optional.empty();
		}
		return apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familiaId).stream()
				.filter(a -> a.isActivo() && ruc.equals(a.getRuc())).findFirst();
	}

	private Receptor receptor(DatosComprobante datos, Familia familia, List<Cuota> elegidas) {
		if (datos == null || datos.tipo() == null) {
			throw new ReglaNegocioException("Elige boleta o factura.");
		}
		if (datos.tipo() == TipoComprobante.FACTURA) {
			// B2: solo con el RUC registrado (y aprobado) de un apoderado activo de la familia; la razón social sale de
			// ese registro, no de lo que se escriba en caja.
			String ruc = datos.ruc() == null ? null : datos.ruc().strip();
			Apoderado conRuc = rucRegistrado(familia.getId(), ruc).orElseThrow(() -> new ReglaNegocioException(
					"La factura solo se emite con un RUC registrado de la familia (lo registra Administración y lo aprueba "
							+ "otra persona). Si no tiene, emite boleta."));
			return Receptor.de(DocumentoReceptor.RUC, conRuc.getRuc(), conRuc.getRazonSocial());
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

	private void auditar(AccionAuditoria accion, Pago pago, Comprobante comprobante, Receptor receptor,
			List<String> detalleCuotas, String extra) {
		String medio = pago.getMedio().etiqueta() + (pago.getMedio().digital()
				? " (operación " + pago.getNumeroOperacion() + ")"
				: " (recibido " + Dinero.formatear(pago.getRecibido()) + ", vuelto " + Dinero.formatear(pago.getVuelto())
						+ ")");
		String detalle = comprobante.getTipo().etiqueta() + " " + comprobante.numeroCompleto() + " por "
				+ Dinero.formatear(pago.getTotal()) + " en " + medio + ". " + pago.getFamilia().getNombre()
				+ ". A nombre de " + receptor.nombre() + " (" + receptor.documentoEnmascarado() + "). Cuotas: "
				+ String.join("; ", detalleCuotas) + "." + extra;
		String nuevo = pago.getEstado().name() + " · " + Dinero.formatear(pago.getTotal()) + " · "
				+ pago.getMedio().etiqueta() + " · " + comprobante.numeroCompleto();
		auditoria.registrar(accion, "pago", pago.getId().toString(), null, nuevo, detalle);
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
