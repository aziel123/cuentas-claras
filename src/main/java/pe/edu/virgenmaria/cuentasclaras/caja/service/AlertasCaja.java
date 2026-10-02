package pe.edu.virgenmaria.cuentasclaras.caja.service;

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
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

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
@PreAuthorize("hasRole('PROMOTOR')")
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

	private final Clock reloj;

	public AlertasCaja(CajaDiariaRepository cajas, CierreCajaRepository cierres, DepositoCajaRepository depositos,
			PagoRepository pagos, AnulacionPagoRepository anulaciones, VerificacionBancariaRepository verificaciones,
			SolicitudCambioRepository solicitudes, SerieComprobanteRepository series, ComprobanteRepository comprobantes,
			NombresUsuarios nombres, PropiedadesCaja propiedades, Clock reloj) {
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
		huecosEnSeries(alertas);
		sinVerificar(alertas, hoy);
		sinDepositar(alertas, hoy);
		anulacionesPendientes(alertas);
		devolucionesPosteriores(alertas, desde);
		observados(alertas, desde);
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
						+ "». Por aprobar.", "/aprobaciones"));
			}
		}
		long cuadrados = porRevisar.stream().filter(c -> !c.conDiferencia()).count();
		if (cuadrados > 0) {
			alertas.add(new AlertaRevision(Gravedad.INFORMATIVA, MODULO, cuadrados + " cierre(s) de caja sin diferencia "
					+ "esperan tu aprobación (un clic).", "/aprobaciones"));
		}
	}

	private void cajasSinCerrar(List<AlertaRevision> alertas, LocalDate hoy, LocalTime hora) {
		for (CajaDiaria caja : cajas.findByEstadoAndFechaBeforeOrderByFechaAsc(EstadoCaja.ABIERTA, hoy)) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "La caja de " + nombres.de(caja.getCajero())
					+ " del " + Calendario.formatear(caja.getFecha()) + " sigue abierta: no hizo su cierre. Hasta que la "
					+ "cierre con su conteo no puede cobrar.", "/aprobaciones/cajas?fecha=" + caja.getFecha()));
		}
		if (!hora.isBefore(propiedades.horaLimiteCierre())) {
			for (CajaDiaria caja : cajas.findByFechaOrderByCajeroAsc(hoy)) {
				if (caja.getEstado() == EstadoCaja.ABIERTA) {
					alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "Pasó la hora límite ("
							+ propiedades.horaLimiteCierre() + ") y la caja de " + nombres.de(caja.getCajero())
							+ " de hoy sigue sin cerrar.", "/aprobaciones/cajas"));
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
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, texto, "/conciliacion"));
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
					+ ". Explicación: «" + d.getExplicacion() + "».", "/aprobaciones/cajas/" + d.getCaja().getId()));
		}
	}

	private void huecosEnSeries(List<AlertaRevision> alertas) {
		for (SerieComprobante serie : series.findAll()) {
			long emitidos = comprobantes.countBySerie(serie.getSerie());
			if (emitidos != serie.getUltimoNumero()) {
				alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "La serie " + serie.getSerie() + " va en el "
						+ serie.getUltimoNumero() + " pero tiene " + emitidos + " comprobante(s): hay un hueco o un "
						+ "número repetido. Revísalo con soporte.", null));
			}
		}
	}

	private void sinVerificar(List<AlertaRevision> alertas, LocalDate hoy) {
		int dias = propiedades.diasSinVerificar();
		if (dias <= 0) {
			return;
		}
		LocalDate limite = hoy.minusDays(dias);
		List<Pago> atrasados = pagos.digitalesSinVerificar().stream().filter(p -> !p.getFecha().isAfter(limite)).toList();
		if (!atrasados.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, atrasados.size() + " pago(s) digital(es) por "
					+ Dinero.formatear(Dinero.sumar(atrasados.stream().map(Pago::getTotal).toList())) + " llevan más de "
					+ dias + " día(s) sin verificarse en el banco.", "/conciliacion"));
		}
		List<DepositoCaja> depositosAtrasados = depositos.sinVerificar().stream()
				.filter(d -> !d.getFechaDeposito().isAfter(limite)).toList();
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
				alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "El efectivo de la caja de "
						+ nombres.de(caja.getCajero()) + " del " + Calendario.formatear(caja.getFecha()) + " ("
						+ Dinero.formatear(aDepositar) + ") aún no se deposita.", "/aprobaciones/cajas/" + caja.getId()));
			}
		}
	}

	private void anulacionesPendientes(List<AlertaRevision> alertas) {
		List<Long> ids = solicitudes.findByEstadoOrderByIdAsc(EstadoSolicitud.PENDIENTE).stream()
				.filter(s -> s.getTipo() == TipoSolicitud.ANULACION_PAGO).map(SolicitudCambio::getEntidadId).toList();
		if (!ids.isEmpty()) {
			BigDecimal monto = Dinero.sumar(pagos.findByIdIn(ids).stream().map(Pago::getTotal).toList());
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, ids.size() + " anulación(es) de pago por "
					+ Dinero.formatear(monto) + " esperan aprobación.", "/aprobaciones"));
		}
	}

	private void devolucionesPosteriores(List<AlertaRevision> alertas, LocalDate desde) {
		for (AnulacionPago a : anulaciones.findByPosteriorAlCierreTrueAndTipoOrderByIdDesc(TipoAnulacion.DEVOLUCION)) {
			if (a.getCreadoEn().toLocalDate().isBefore(desde)) {
				continue;
			}
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "Devolución pendiente: se anuló "
					+ a.getPago().getComprobante().numeroCompleto() + " (" + Dinero.formatear(a.getMonto())
					+ ") después del cierre de su caja. El cierre no cambia: el reembolso lo hace Administración desde el "
					+ "banco.", "/aprobaciones/cajas/" + a.getPago().getCaja().getId()));
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
