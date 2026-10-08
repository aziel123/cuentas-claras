package pe.edu.virgenmaria.cuentasclaras.caja.service;

import pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillasMuestreo;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.config.PropiedadesCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AnulacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.DepositoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoCierre;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoVerificacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAnulacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.VerificacionBancaria;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AnulacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CajaDiariaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CierreCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.DepositoCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.VerificacionBancariaRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.SerieComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.ComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.SerieComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillaMuestreo;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.MuestraAlAzar;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Alertas de caja para «Para revisar» de Promotoría, calculadas al consultar (no hay tareas programadas):
 * <ul>
 *   <li>CRÍTICAS: faltante o sobrante en un cierre por aprobar; caja de un día anterior sin cerrar; pago digital o
 *       depósito que no aparece en el banco; depósito distinto de lo contado; hueco en una serie de comprobantes.</li>
 *   <li>ATENCIÓN: caja de hoy abierta después de la hora límite; pagos digitales y depósitos sin verificar después del
 *       límite de días; efectivo de días anteriores sin depositar; anulaciones de pago pendientes; devoluciones
 *       aprobadas después del cierre (el reembolso lo hace Administración desde el banco); cierres observados.</li>
 *   <li>PARA SABER: cierres sin diferencia que esperan aprobación.</li>
 * </ul>
 * Los textos no llevan documentos ni teléfonos.
 */
@Service
@Order(1)
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class AlertasCaja implements AlertasRevision {

	/** Hasta cuántos días atrás se muestran las alertas que ya no dependen de una acción pendiente. */
	static final int VENTANA_DIAS = 30;

	private static final String MODULO = "Caja";

	private final CajaDiariaRepository cajas;

	private final CierreCajaRepository cierres;

	private final DepositoCajaRepository depositos;

	private final PagoRepository pagos;

	private final AnulacionPagoRepository anulaciones;

	private final VerificacionBancariaRepository verificaciones;

	private final SolicitudCambioRepository solicitudes;

	private final SerieComprobanteRepository series;

	private final ComprobanteRepository comprobantes;

	private final NombresUsuarios nombres;

	private final PropiedadesCaja propiedades;

	private final pe.edu.virgenmaria.cuentasclaras.comprobantes.config.PropiedadesComprobantes seriesConfiguradas;

	private final pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService auditoria;

	private final Clock reloj;

	private final CalendarioHabil calendario;

	private final SemillasMuestreo semillas;

	public AlertasCaja(CajaDiariaRepository cajas, CierreCajaRepository cierres, DepositoCajaRepository depositos,
			PagoRepository pagos, AnulacionPagoRepository anulaciones, VerificacionBancariaRepository verificaciones,
			SolicitudCambioRepository solicitudes, SerieComprobanteRepository series, ComprobanteRepository comprobantes,
			NombresUsuarios nombres, PropiedadesCaja propiedades,
			pe.edu.virgenmaria.cuentasclaras.comprobantes.config.PropiedadesComprobantes seriesConfiguradas,
			pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService auditoria, Clock reloj, CalendarioHabil calendario, SemillasMuestreo semillas) {
		this.semillas = semillas;
		this.calendario = calendario;
		this.seriesConfiguradas = seriesConfiguradas;
		this.auditoria = auditoria;
		this.cajas = cajas;
		this.cierres = cierres;
		this.depositos = depositos;
		this.pagos = pagos;
		this.anulaciones = anulaciones;
		this.verificaciones = verificaciones;
		this.solicitudes = solicitudes;
		this.series = series;
		this.comprobantes = comprobantes;
		this.nombres = nombres;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	@Override
	public List<AlertaRevision> alertas() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		LocalDate hoy = ahora.toLocalDate();
		LocalDate desde = hoy.minusDays(VENTANA_DIAS);
		List<AlertaRevision> alertas = new ArrayList<>();
		cierresPorRevisar(alertas);
		cajasSinCerrar(alertas, hoy, ahora.toLocalTime());
		noEncontrados(alertas, desde);
		depositosDistintos(alertas, desde);
		depositosTardios(alertas, desde);
		huecosEnSeries(alertas);
		consistencia(alertas);
		devolucionesSinReembolso(alertas);
		sinVerificar(alertas, ahora);
		sinDepositar(alertas, hoy);
		anulacionesPendientes(alertas);
		devolucionesEnEfectivoDeHoy(alertas, hoy);
		verificacionesQueNoCoincidieron(alertas, hoy);
		oseNoReconoce(alertas, hoy);
		observados(alertas, desde);
		muestraDeVerificaciones(alertas, hoy);
		return alertas;
	}

	private void cierresPorRevisar(List<AlertaRevision> alertas) {
		List<CierreCaja> porRevisar = cierres.findByEstadoOrderByIdAsc(EstadoCierre.POR_REVISAR);
		for (CierreCaja c : porRevisar) {
			if (c.conDiferencia()) {
				CajaDiaria caja = c.getCaja();
				alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, capitalizar(ServicioCierreCaja.diferenciaTexto(
						c.getDiferencia())) + " en la caja de " + nombres.de(caja.getCajero()) + " del "
						+ Calendario.formatear(caja.getFecha()) + " (esperado " + Dinero.formatear(c.getEsperado())
						+ ", contado " + Dinero.formatear(c.getContado()) + "). Explicación: «" + c.getExplicacion()
						+ "». Por aprobar.", "/aprobaciones",
						new Aviso(TipoAviso.CIERRE_CON_DIFERENCIA, "C:" + c.getId(), Dinero.formatear(c.getDiferencia()))));
			}
		}
		for (CierreCaja c : porRevisar) {
			if (c.isTrasReapertura()) {
				alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "Cierre tras reapertura (no es ciego: la cajera ya "
						+ "había visto el esperado) de la caja de " + nombres.de(c.getCaja().getCajero()) + " del "
						+ Calendario.formatear(c.getCaja().getFecha()) + ". Revísalo con comentario.", "/aprobaciones"));
			}
		}
		long cuadrados = porRevisar.stream().filter(c -> !c.conDiferencia() && !c.isTrasReapertura()).count();
		if (cuadrados > 0) {
			alertas.add(new AlertaRevision(Gravedad.INFORMATIVA, MODULO, cuadrados + " cierre(s) de caja sin diferencia "
					+ "esperan tu aprobación (un clic).", "/aprobaciones"));
		}
	}

	private void cajasSinCerrar(List<AlertaRevision> alertas, LocalDate hoy, LocalTime hora) {
		for (CajaDiaria caja : cajas.findByCanalAndEstadoAndFechaBeforeOrderByFechaAsc(
				pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja.VENTANILLA, EstadoCaja.ABIERTA, hoy)) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "La caja de " + nombres.de(caja.getCajero())
					+ " del " + Calendario.formatear(caja.getFecha()) + " sigue abierta: no hizo su cierre. Hasta que la "
					+ "cierre con su conteo no puede cobrar.", "/aprobaciones/cajas?fecha=" + caja.getFecha(),
					new Aviso(TipoAviso.CAJA_SIN_CERRAR, "K:" + caja.getId(), Calendario.formatear(caja.getFecha()))));
		}
		if (!hora.isBefore(propiedades.horaLimiteCierre())) {
			for (CajaDiaria caja : cajas.findByCanalAndFechaOrderByCajeroAsc(
					pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja.VENTANILLA, hoy)) {
				if (caja.getEstado() == EstadoCaja.ABIERTA) {
					alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "Pasó la hora límite ("
							+ propiedades.horaLimiteCierre() + ") y la caja de " + nombres.de(caja.getCajero())
							+ " de hoy sigue sin cerrar.", "/aprobaciones/cajas",
							new Aviso(TipoAviso.CIERRE_NO_REALIZADO, "K:" + caja.getId(),
									propiedades.horaLimiteCierre().toString())));
				}
			}
		}
	}

	private void noEncontrados(List<AlertaRevision> alertas, LocalDate desde) {
		for (VerificacionBancaria v : verificaciones.findByResultadoOrderByIdDesc(ResultadoVerificacion.NO_ENCONTRADO)) {
			if (v.getCreadoEn().toLocalDate().isBefore(desde)) {
				continue;
			}
			String texto;
			if (v.getPago() != null) {
				Pago p = v.getPago();
				texto = "El " + p.getMedio().etiqueta() + " " + p.getNumeroOperacion() + " por "
						+ Dinero.formatear(p.getTotal()) + " del " + Calendario.formatear(p.getFecha()) + " (cobró "
						+ nombres.de(p.getCajero()) + ") NO aparece en el banco. Nota: «" + v.getNota() + "».";
			}
			else {
				DepositoCaja d = v.getDeposito();
				texto = "El depósito " + d.getNumeroOperacion() + " por " + Dinero.formatear(d.getMonto())
						+ " de la caja de " + nombres.de(d.getCaja().getCajero()) + " del "
						+ Calendario.formatear(d.getCaja().getFecha()) + " NO aparece en el banco. Nota: «" + v.getNota()
						+ "».";
			}
			BigDecimal monto = v.getPago() != null ? v.getPago().getTotal() : v.getDeposito().getMonto();
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, texto, "/conciliacion",
					new Aviso(TipoAviso.NO_APARECE_EN_BANCO, "V:" + v.getId(), Dinero.formatear(monto))));
		}
	}

	private void depositosDistintos(List<AlertaRevision> alertas, LocalDate desde) {
		for (DepositoCaja d : depositos.distintos()) {
			if (d.getFechaDeposito().isBefore(desde)) {
				continue;
			}
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "Depósito distinto de lo contado: la caja de "
					+ nombres.de(d.getCaja().getCajero()) + " del " + Calendario.formatear(d.getCaja().getFecha())
					+ " depositó " + Dinero.formatear(d.getMonto()) + " de " + Dinero.formatear(d.getEsperado())
					+ ". Explicación: «" + d.getExplicacion() + "».", "/aprobaciones/cajas/" + d.getCaja().getId(),
					new Aviso(TipoAviso.DEPOSITO_DISTINTO, "D:" + d.getId(), Dinero.formatear(d.getMonto()))));
		}
	}

	private void huecosEnSeries(List<AlertaRevision> alertas) {
		java.util.Set<String> configuradas = java.util.Set.of(seriesConfiguradas.serieBoleta(),
				seriesConfiguradas.serieFactura(), seriesConfiguradas.serieNotaBoleta(), seriesConfiguradas.serieNotaFactura());
		for (SerieComprobante serie : series.findAll()) {
			if (!configuradas.contains(serie.getSerie())) {
				alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "Existe la serie " + serie.getSerie() + " con "
						+ serie.getUltimoNumero() + " comprobante(s), que no es ninguna de las configuradas ("
						+ String.join(", ", new java.util.TreeSet<>(configuradas)) + "): alguien emitió fuera del sistema. "
						+ "Revísalo con soporte.", null, new Aviso(TipoAviso.OTRA_CRITICA, "SERIE:" + serie.getId())));
			}
			long emitidos = comprobantes.countBySerie(serie.getSerie());
			if (emitidos != serie.getUltimoNumero()) {
				alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "La serie " + serie.getSerie() + " va en el "
						+ serie.getUltimoNumero() + " pero tiene " + emitidos + " comprobante(s): hay un hueco o un "
						+ "número repetido. Revísalo con soporte.", null,
						new Aviso(TipoAviso.OTRA_CRITICA, "HUECO:" + serie.getId() + ":" + serie.getUltimoNumero())));
			}
		}
	}

	/**
	 * Pagos digitales y depósitos sin verificar: ATENCIÓN cuando llevan más del límite en HORAS desde que se registraron
	 * (un Yape de las 23:59:59 no tiene «más de un día» a las 00:00:01) y CRÍTICA cuando pasó la hora límite del día
	 * hábil siguiente al cobro (A4).
	 */
	private void sinVerificar(List<AlertaRevision> alertas, LocalDateTime ahora) {
		int dias = propiedades.diasSinVerificar();
		List<Pago> pendientes = pagos.digitalesSinVerificar();
		List<Pago> criticos = pendientes.stream().filter(p -> !ahora.isBefore(
				calendario.siguienteDiaHabil(p.getFecha()).atTime(propiedades.horaLimiteCierre()))).toList();
		if (!criticos.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, criticos.size() + " pago(s) digital(es) por "
					+ Dinero.formatear(Dinero.sumar(criticos.stream().map(Pago::getTotal).toList())) + " siguen sin "
					+ "verificarse en el banco pasado el día hábil siguiente al cobro (el más antiguo, del "
					+ Calendario.formatear(criticos.getFirst().getFecha()) + ").", "/conciliacion",
					new Aviso(TipoAviso.OTRA_CRITICA, "SINVERIF:" + criticos.stream().mapToLong(Pago::getId).max().orElse(0))));
		}
		if (dias <= 0) {
			return;
		}
		List<Pago> atrasados = pendientes.stream().filter(p -> !criticos.contains(p)
				&& !java.time.Duration.between(p.getCreadoEn(), ahora).minusDays(dias).isNegative()).toList();
		if (!atrasados.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, atrasados.size() + " pago(s) digital(es) por "
					+ Dinero.formatear(Dinero.sumar(atrasados.stream().map(Pago::getTotal).toList())) + " llevan más de "
					+ dias + " día(s) sin verificarse en el banco.", "/conciliacion"));
		}
		List<DepositoCaja> depositosAtrasados = depositos.sinVerificar().stream()
				.filter(d -> !java.time.Duration.between(d.getCreadoEn(), ahora).minusDays(dias).isNegative()).toList();
		if (!depositosAtrasados.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, depositosAtrasados.size() + " depósito(s) llevan "
					+ "más de " + dias + " día(s) sin verificarse en el banco.", "/conciliacion"));
		}
	}

	private void sinDepositar(List<AlertaRevision> alertas, LocalDate hoy) {
		for (CajaDiaria caja : cajas.cerradasSinDeposito()) {
			if (!caja.getFecha().isBefore(hoy)) {
				continue;
			}
			BigDecimal contado = cierres.findFirstByCajaIdOrderByNumeroDesc(caja.getId()).map(CierreCaja::getContado)
					.orElse(Dinero.CERO);
			BigDecimal aDepositar = contado.subtract(caja.getFondoFijo());
			if (aDepositar.signum() > 0) {
				// M3 (lapping): pasado un día hábil completo sin depositar, es crítico.
				boolean critico = hoy.isAfter(calendario.siguienteDiaHabil(caja.getFecha()));
				alertas.add(new AlertaRevision(critico ? Gravedad.CRITICA : Gravedad.ATENCION, MODULO, "El efectivo de la "
						+ "caja de " + nombres.de(caja.getCajero()) + " del " + Calendario.formatear(caja.getFecha()) + " ("
						+ Dinero.formatear(aDepositar) + ") aún no se deposita" + (critico ? ", y ya pasó un día hábil." : ".")
						, "/aprobaciones/cajas/" + caja.getId(),
						critico ? new Aviso(TipoAviso.OTRA_CRITICA, "SD:" + caja.getId(), Dinero.formatear(aDepositar)) : null));
			}
		}
	}

	/**
	 * Sprint 6 (decisión 71): sale también al celular de Dirección, con la solicitud MÁS RECIENTE como referencia (llega
	 * otra, sale un aviso nuevo) y sin avisar a quien la pidió ni a la cajera del pago.
	 */
	private void anulacionesPendientes(List<AlertaRevision> alertas) {
		List<SolicitudCambio> pendientes = solicitudes.findByEstadoOrderByIdAsc(EstadoSolicitud.PENDIENTE).stream()
				.filter(s -> s.getTipo() == TipoSolicitud.ANULACION_PAGO).toList();
		List<Long> ids = pendientes.stream().map(SolicitudCambio::getEntidadId).toList();
		if (!ids.isEmpty()) {
			List<Pago> delPago = pagos.findByIdIn(ids);
			BigDecimal monto = Dinero.sumar(delPago.stream().map(Pago::getTotal).toList());
			java.util.Set<String> excluidos = new java.util.HashSet<>();
			pendientes.forEach(s -> excluidos.add(s.getSolicitadoPor()));
			delPago.forEach(p -> excluidos.add(p.getCajero()));
			long masReciente = pendientes.stream().mapToLong(SolicitudCambio::getId).max().orElse(0);
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, ids.size() + " anulación(es) de pago por "
					+ Dinero.formatear(monto) + " esperan aprobación.", "/aprobaciones",
					new Aviso(TipoAviso.ANULACION_PAGO_PENDIENTE, "S:" + masReciente, Dinero.formatear(monto), excluidos)));
		}
	}

	/** A1 y A2: una devolución aprobada sin su reembolso registrado por Administración es crítica (caja abierta o no). */
	private void devolucionesSinReembolso(List<AlertaRevision> alertas) {
		for (AnulacionPago a : anulaciones.devolucionesSinReembolso()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "Devolución sin reembolso registrado: "
					+ a.getPago().getComprobante().numeroCompleto() + " (" + a.getPago().getMedio().etiqueta() + ", "
					+ Dinero.formatear(a.getMonto()) + ", " + a.getPago().getFamilia().getNombre() + ")"
					+ (a.isPosteriorAlCierre() ? ", anulado después del cierre de su caja" : "")
					+ ". Administración debe registrar a quién y cómo se devolvió.", "/conciliacion",
					new Aviso(TipoAviso.OTRA_CRITICA, "DEV:" + a.getId(), Dinero.formatear(a.getMonto()))));
		}
	}

	/** A2: todas las devoluciones en efectivo del día, para que Promotoría las revise a diario. */
	private void devolucionesEnEfectivoDeHoy(List<AlertaRevision> alertas, LocalDate hoy) {
		List<AnulacionPago> deHoy = anulaciones.findByCreadoEnGreaterThanEqualOrderByIdAsc(hoy.atStartOfDay()).stream()
				.filter(a -> a.getTipo() == TipoAnulacion.DEVOLUCION
						&& a.getPago().getMedio() == pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.EFECTIVO)
				.toList();
		if (!deHoy.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "Devoluciones en efectivo de hoy: " + deHoy.size()
					+ " por " + Dinero.formatear(Dinero.sumar(deHoy.stream().map(AnulacionPago::getMonto).toList())) + " ("
					+ deHoy.stream().map(a -> a.getPago().getComprobante().numeroCompleto() + " de "
							+ nombres.de(a.getCajeroPago()) + ", aprobada por " + a.getAprobadoPor())
							.collect(java.util.stream.Collectors.joining("; ")) + ").", "/conciliacion"));
		}
	}

	/** M1: toda boleta o factura tiene su pago y toda nota de crédito su anulación aprobada. */
	private void consistencia(List<AlertaRevision> alertas) {
		// Sprint 4: la reemisión de un comprobante rechazado no tiene pago propio: vale por el del rechazado.
		var sinPago = pagos.comprobantesSinPago().stream().filter(c -> !esReemisionConPago(c)).toList();
		if (!sinPago.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, sinPago.size() + " boleta(s) o factura(s) sin su pago "
					+ "en el libro: " + sinPago.stream().limit(5).map(c -> c.numeroCompleto())
							.collect(java.util.stream.Collectors.joining(", ")) + ". Revísalo con soporte.", null,
					new Aviso(TipoAviso.OTRA_CRITICA, "SINPAGO:" + sinPago.stream().mapToLong(c -> c.getId()).max().orElse(0))));
		}
		var notas = pagos.notasSinAnulacion();
		if (!notas.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, notas.size() + " nota(s) de crédito sin su anulación "
					+ "aprobada: " + notas.stream().limit(5).map(c -> c.numeroCompleto())
							.collect(java.util.stream.Collectors.joining(", ")) + ". Revísalo con soporte.", null,
					new Aviso(TipoAviso.OTRA_CRITICA, "NOTA:" + notas.stream().mapToLong(c -> c.getId()).max().orElse(0))));
		}
	}

	private boolean esReemisionConPago(pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante c) {
		Long raiz = c.getReemplazaId();
		for (int i = 0; raiz != null && i < 20; i++) {
			if (pagos.findByComprobanteId(raiz).isPresent()) {
				return true;
			}
			raiz = comprobantes.findById(raiz).map(pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante::getReemplazaId)
					.orElse(null);
		}
		return false;
	}

	/** M3: depósito que llegó al banco más de un día hábil después de la caja (fecha declarada o la del banco). */
	private void depositosTardios(List<AlertaRevision> alertas, LocalDate desde) {
		for (DepositoCaja d : depositos.findByFechaDepositoGreaterThanEqual(desde)) {
			LocalDate banco = verificaciones.findByDepositoIdIn(List.of(d.getId())).stream().findFirst()
					.map(VerificacionBancaria::getBancoFecha).orElse(null);
			boolean tardio = ServicioVerificacionBancaria.tardio(calendario, d.getCaja().getFecha(), d.getFechaDeposito())
					|| ServicioVerificacionBancaria.tardio(calendario, d.getCaja().getFecha(), banco);
			if (tardio) {
				alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "Depósito tardío: el efectivo de la caja de "
						+ nombres.de(d.getCaja().getCajero()) + " del " + Calendario.formatear(d.getCaja().getFecha())
						+ " llegó al banco el " + Calendario.formatear(banco != null ? banco : d.getFechaDeposito())
						+ ", más de un día hábil después (posible uso del efectivo de un día para cubrir otro).",
						"/aprobaciones/cajas/" + d.getCaja().getId(), new Aviso(TipoAviso.OTRA_CRITICA, "DT:" + d.getId())));
			}
		}
	}

	/**
	 * Sprint 4 (F6): la reconsulta nocturna encontró comprobantes que el sistema tiene como aceptados y el OSE no
	 * reconoce (por ejemplo, el «OSE» apuntaba a un servidor propio). Se avisa durante una semana.
	 */
	private void oseNoReconoce(List<AlertaRevision> alertas, LocalDate hoy) {
		long casos = auditoria.contarDesde(pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria
				.COMPROBANTE_NO_COINCIDE_OSE, hoy.minusDays(7).atStartOfDay());
		if (casos > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, "Comprobantes", casos + " comprobante(s) que el sistema tiene "
					+ "como aceptados no coinciden con lo que dice el OSE al volver a consultarlos. Revisa la bitácora y la "
					+ "configuración del OSE.", "/auditoria?soloRevisar=true",
					auditoria.ultimaSecuenciaDesde(pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria
							.COMPROBANTE_NO_COINCIDE_OSE, hoy.minusDays(7).atStartOfDay())
							.map(n -> new Aviso(TipoAviso.OTRA_CRITICA, "OSE:" + n)).orElse(null)));
		}
	}

	/** C1: intentos de verificación de hoy en los que lo escrito del banco no coincidió con lo registrado. */
	private void verificacionesQueNoCoincidieron(List<AlertaRevision> alertas, LocalDate hoy) {
		long intentos = auditoria.contarDesde(pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria
				.VERIFICACION_NO_COINCIDE, hoy.atStartOfDay());
		if (intentos > 0) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, intentos + " verificación(es) bancaria(s) de hoy no "
					+ "coincidieron con lo registrado (operación, fecha o monto). Revisa la bitácora.",
					"/auditoria?soloRevisar=true"));
		}
	}

	/**
	 * A4: muestra al azar (estable durante el día) de 3 verificaciones «Encontrado» del día hábil anterior, con quién
	 * verificó y qué escribió del banco, para que Promotoría las compare con el estado de cuenta.
	 */
	private void muestraDeVerificaciones(List<AlertaRevision> alertas, LocalDate hoy) {
		LocalDate dia = calendario.anteriorDiaHabil(hoy);
		List<VerificacionBancaria> delDia = new ArrayList<>(verificaciones.findByResultadoAndCreadoEnBetweenOrderByIdAsc(
				ResultadoVerificacion.ENCONTRADO, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay()));
		if (delDia.isEmpty()) {
			return;
		}
		// G21: la semilla es SECRETA (SecureRandom, guardada por día); antes era la fecha y la cajera podía calcularla.
		long semilla = semillas.de(SemillaMuestreo.Ambito.CAJA, hoy);
		for (VerificacionBancaria v : MuestraAlAzar.delDia(semilla, delDia, 3)) {
			String que = v.getPago() != null
					? v.getPago().getMedio().etiqueta() + " de " + v.getPago().getFamilia().getNombre()
					: "Depósito de la caja de " + nombres.de(v.getDeposito().getCaja().getCajero());
			alertas.add(new AlertaRevision(Gravedad.INFORMATIVA, MODULO, "Revisa al azar: " + que + ", verificado por "
					+ nombres.de(v.getCreadoPor()) + " (operación " + v.getBancoOperacion() + " del "
					+ Calendario.formatear(v.getBancoFecha()) + " por " + Dinero.formatear(v.getBancoMonto())
					+ "). Compáralo con el estado de cuenta.", "/conciliacion"));
		}
	}

	private void observados(List<AlertaRevision> alertas, LocalDate desde) {
		for (CierreCaja c : cierres.findByEstadoOrderByIdAsc(EstadoCierre.OBSERVADO)) {
			if (c.getRevisadoEn().toLocalDate().isBefore(desde)) {
				continue;
			}
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "Cierre observado: la caja de "
					+ nombres.de(c.getCaja().getCajero()) + " del " + Calendario.formatear(c.getCaja().getFecha()) + " ("
					+ ServicioCierreCaja.diferenciaTexto(c.getDiferencia()) + "). Observación de " + c.getRevisadoPor()
					+ ": «" + c.getComentarioRevision() + "».", "/aprobaciones/cajas/" + c.getCaja().getId()));
		}
	}

	private static String capitalizar(String texto) {
		return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
	}
}
