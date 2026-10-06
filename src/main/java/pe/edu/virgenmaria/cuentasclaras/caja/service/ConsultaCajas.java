package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CajasDelDia;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.DetalleCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AnulacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.DepositoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResumenCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.VerificacionBancaria;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AnulacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CajaDiariaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CierreCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.DepositoCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.VerificacionBancariaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Cajas del día y su detalle para quien aprueba (Promotoría y Dirección). Solo lectura. */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class ConsultaCajas {

	private final CajaDiariaRepository cajas;

	private final CierreCajaRepository cierres;

	private final DepositoCajaRepository depositos;

	private final PagoRepository pagos;

	private final AnulacionPagoRepository anulaciones;

	private final VerificacionBancariaRepository verificaciones;

	private final LibroCaja libro;

	private final NombresUsuarios nombres;

	private final Clock reloj;

	public ConsultaCajas(CajaDiariaRepository cajas, CierreCajaRepository cierres, DepositoCajaRepository depositos,
			PagoRepository pagos, AnulacionPagoRepository anulaciones, VerificacionBancariaRepository verificaciones,
			LibroCaja libro, NombresUsuarios nombres, Clock reloj) {
		this.cajas = cajas;
		this.cierres = cierres;
		this.depositos = depositos;
		this.pagos = pagos;
		this.anulaciones = anulaciones;
		this.verificaciones = verificaciones;
		this.libro = libro;
		this.nombres = nombres;
		this.reloj = reloj;
	}

	/** Las cajas de ese día (hoy si es {@code null}). */
	public CajasDelDia delDia(LocalDate fecha) {
		LocalDate dia = fecha == null ? LocalDate.now(reloj) : fecha;
		// Solo las cajas de las cajeras: los pagos en línea (caja de canal) se ven en Pagos en línea.
		List<CajaDiaria> delDia = cajas.findByCanalAndFechaOrderByCajeroAsc(
				pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja.VENTANILLA, dia);
		List<Long> ids = delDia.stream().map(CajaDiaria::getId).toList();
		Map<Long, CierreCaja> ultimos = ids.isEmpty() ? Map.of()
				: cierres.findByCajaIdInOrderByNumeroAsc(ids).stream()
						.collect(Collectors.toMap(c -> c.getCaja().getId(), Function.identity(), (a, b) -> b));
		Map<Long, DepositoCaja> depositosPorCaja = ids.isEmpty() ? Map.of()
				: depositos.findByCajaIdIn(ids).stream().collect(Collectors.toMap(d -> d.getCaja().getId(),
						Function.identity()));
		BigDecimal efectivo = Dinero.CERO;
		BigDecimal digital = Dinero.CERO;
		List<CajasDelDia.CajaDelDia> filas = new java.util.ArrayList<>();
		for (CajaDiaria caja : delDia) {
			ResumenCaja resumen = libro.resumen(caja);
			efectivo = efectivo.add(resumen.efectivo());
			digital = digital.add(resumen.digital());
			CierreCaja cierre = ultimos.get(caja.getId());
			DepositoCaja deposito = depositosPorCaja.get(caja.getId());
			filas.add(new CajasDelDia.CajaDelDia(caja.getId(), nombres.de(caja.getCajero()), estado(caja),
					caja.getEstado() == EstadoCaja.ABIERTA ? "info" : "neutro",
					resumen.pagosEfectivo() + resumen.pagosDigitales(), resumen.efectivo(), resumen.digital(),
					cierre == null ? "Sin cerrar" : cierre.getEstado().etiqueta(),
					cierre == null ? "alerta" : cierre.getEstado().variante(),
					cierre == null ? "—" : ServicioCierreCaja.diferenciaTexto(cierre.getDiferencia()),
					cierre == null || !cierre.conDiferencia() ? "exito" : "peligro",
					deposito == null ? "—" : Dinero.formatear(deposito.getMonto()) + (deposito.distinto() ? " (distinto)" : "")
							+ (ServicioVerificacionBancaria.tardio(caja.getFecha(), deposito.getFechaDeposito()) ? " (tardío)"
									: "")));
		}
		return new CajasDelDia(dia, dia.minusDays(1), dia.isBefore(LocalDate.now(reloj)) ? dia.plusDays(1) : null,
				Dinero.normalizar(efectivo), Dinero.normalizar(digital), filas);
	}

	public DetalleCaja detalle(Long cajaId) {
		CajaDiaria caja = cajas.findById(cajaId).orElseThrow(() -> new RecursoNoEncontradoException("Caja no encontrada"));
		ResumenCaja resumen = libro.resumen(caja);
		List<Pago> suyos = pagos.findByCajaIdOrderByIdDesc(caja.getId());
		Map<Long, VerificacionBancaria> verificados = suyos.isEmpty() ? Map.of()
				: verificaciones.findByPagoIdIn(suyos.stream().map(Pago::getId).toList()).stream()
						.collect(Collectors.toMap(v -> v.getPago().getId(), Function.identity()));
		List<DetalleCaja.PagoCaja> filas = suyos.stream().map(p -> {
			VerificacionBancaria v = verificados.get(p.getId());
			String verificacion = !p.getMedio().digital() ? "—"
					: v == null ? "Sin verificar" : v.getResultado().etiqueta();
			return new DetalleCaja.PagoCaja(p.getId(), p.getCreadoEn().toLocalTime().withNano(0),
					p.getComprobante().numeroCompleto(), p.getFamilia().getNombre(), p.getMedio().etiqueta(),
					p.getNumeroOperacion(), p.getTotal(), p.vigente() ? "Vigente" : "Anulado",
					p.vigente() ? "exito" : "neutro", verificacion);
		}).toList();
		List<DetalleCaja.CierreDetalle> susCierres = cierres.findByCajaIdOrderByNumeroAsc(caja.getId()).stream()
				.map(c -> new DetalleCaja.CierreDetalle(c.getNumero(), c.getCreadoEn(), c.getEsperado(), c.getPrimerConteo(),
						c.getContado(), ServicioCierreCaja.diferenciaTexto(c.getDiferencia()),
						c.conDiferencia() ? "peligro" : "exito", c.getExplicacion(), c.getDenominaciones(),
						c.getEstado().etiqueta(), c.getEstado().variante(), c.getRevisadoPor(), c.getComentarioRevision(),
						c.isTrasReapertura()))
				.toList();
		DepositoCaja deposito = depositos.findByCajaId(caja.getId()).orElse(null);
		String depositoTexto = deposito == null ? null : Dinero.formatear(deposito.getMonto()) + " el "
				+ Calendario.formatear(deposito.getFechaDeposito()) + " en " + deposito.getCuenta() + " (operación "
				+ deposito.getNumeroOperacion() + "; se esperaba " + Dinero.formatear(deposito.getEsperado()) + ")"
				+ (deposito.getExplicacion() == null ? "" : ". Explicación: " + deposito.getExplicacion());
		VerificacionBancaria delDeposito = deposito == null ? null
				: verificaciones.findByDepositoIdIn(List.of(deposito.getId())).stream().findFirst().orElse(null);
		String depositoVerificacion = deposito == null ? null
				: delDeposito == null ? "Sin verificar"
						: delDeposito.getResultado().etiqueta() + " (verificó " + delDeposito.getCreadoPor() + ")";
		boolean tardio = deposito != null && (ServicioVerificacionBancaria.tardio(caja.getFecha(), deposito.getFechaDeposito())
				|| ServicioVerificacionBancaria.tardio(caja.getFecha(), delDeposito == null ? null : delDeposito.getBancoFecha()));
		List<String> posteriores = suyos.isEmpty() ? List.of()
				: anulaciones.findByPagoIdIn(suyos.stream().map(Pago::getId).toList()).stream()
						.filter(AnulacionPago::isPosteriorAlCierre)
						.map(a -> "Anulado después del cierre: −" + Dinero.formatear(a.getMonto()) + " ("
								+ a.getPago().getComprobante().numeroCompleto() + ", " + a.getTipo().etiqueta().toLowerCase()
								+ ", aprobado por " + a.getAprobadoPor() + ")")
						.toList();
		return new DetalleCaja(caja.getId(), nombres.de(caja.getCajero()), caja.getFecha(), estado(caja),
				caja.getEstado() == EstadoCaja.ABIERTA ? "info" : "neutro", caja.getFondoFijo(), resumen.efectivo(),
				resumen.digital(), filas, susCierres, depositoTexto, depositoVerificacion, posteriores, tardio);
	}

	private static String estado(CajaDiaria caja) {
		return caja.getEstado() == EstadoCaja.ABIERTA ? "Abierta" : "Cerrada";
	}
}
