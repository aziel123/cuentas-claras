package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.DepositoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Reembolso;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.DepositoCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.ReembolsoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.service.NombresUsuarios;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.config.PropiedadesConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ObjetoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.PartidaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ReglaPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.PartidaConciliacionRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.ObjetoAbierto;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.LiquidacionPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.LiquidacionPasarelaRepository;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLinea;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLote;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LineaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LoteRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LineaRecaudacionRepository;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LoteRecaudacionRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lo que debía verse en el banco en un rango de fechas (sección 10.4 del diseño del sprint 4), cada cosa con su ventana:
 * los pagos digitales VIGENTES de ventanilla, los depósitos de caja, las liquidaciones de la pasarela (netas), los lotes
 * de recaudación ya confirmados (o sus pagos uno por uno, según cómo abone el banco) y los reembolsos digitales y las
 * devoluciones de líneas de recaudación (como cargo; correcciones del sprint 4, S4-A4). Los pagos en línea no entran: los cubre su liquidación. Solo lectura, en la transacción de quien llama.
 */
@Component
public class ObjetosConciliables {

	private final PagoRepository pagos;

	private final DepositoCajaRepository depositos;

	private final ReembolsoRepository reembolsos;

	private final LiquidacionPasarelaRepository liquidaciones;

	private final LoteRecaudacionRepository lotes;

	private final PartidaConciliacionRepository partidas;

	private final LineaRecaudacionRepository lineasRecaudacion;

	private final NombresUsuarios nombres;

	private final PropiedadesConciliacion propiedades;

	public ObjetosConciliables(PagoRepository pagos, DepositoCajaRepository depositos, ReembolsoRepository reembolsos,
			LiquidacionPasarelaRepository liquidaciones, LoteRecaudacionRepository lotes,
			PartidaConciliacionRepository partidas, LineaRecaudacionRepository lineasRecaudacion, NombresUsuarios nombres,
			PropiedadesConciliacion propiedades) {
		this.pagos = pagos;
		this.depositos = depositos;
		this.reembolsos = reembolsos;
		this.liquidaciones = liquidaciones;
		this.lotes = lotes;
		this.partidas = partidas;
		this.lineasRecaudacion = lineasRecaudacion;
		this.nombres = nombres;
		this.propiedades = propiedades;
	}

	/** Todo lo de ese rango de fechas (con o sin partida). */
	public List<ObjetoAbierto> entre(LocalDate desde, LocalDate hasta) {
		List<ObjetoAbierto> objetos = new ArrayList<>();
		for (Pago p : pagos.digitalesDeVentanillaEntre(desde, hasta)) {
			LocalDate[] v = ReglasEmparejamiento.ventanaPago(p.getFecha());
			objetos.add(new ObjetoAbierto(ObjetoPartida.PAGO, p.getId(), p.getFecha(), p.getTotal(), p.getNumeroOperacion(),
					null, v[0], v[1], p.getMedio().etiqueta() + " " + p.getComprobante().numeroCompleto() + " · "
							+ p.getFamilia().getNombre() + " · cobró " + nombres.de(p.getCajero()), p.getCajero()));
		}
		for (DepositoCaja d : depositos.findByFechaDepositoBetweenOrderByFechaDepositoAscIdAsc(desde, hasta)) {
			LocalDate[] v = ReglasEmparejamiento.ventanaDeposito(d.getFechaDeposito());
			objetos.add(new ObjetoAbierto(ObjetoPartida.DEPOSITO, d.getId(), d.getFechaDeposito(), d.getMonto(),
					d.getNumeroOperacion(), null, v[0], v[1], "Depósito de la caja de " + nombres.de(d.getCaja().getCajero())
							+ " del " + Calendario.formatear(d.getCaja().getFecha()), d.getCaja().getCajero()));
		}
		for (LiquidacionPasarela l : liquidaciones.findByFechaAbonoBetweenOrderByFechaAbonoAscIdAsc(desde, hasta)) {
			if (l.getTotalNeto().signum() <= 0) {
				continue;
			}
			LocalDate[] v = ReglasEmparejamiento.ventanaLiquidacion(l.getFechaAbono());
			objetos.add(new ObjetoAbierto(ObjetoPartida.LIQUIDACION, l.getId(), l.getFechaAbono(), l.getTotalNeto(), null,
					l.getReferencia(), v[0], v[1], "Liquidación " + l.getReferencia() + " de la pasarela · " + l.getLineas()
							+ " cargo(s) · bruto " + Dinero.formatear(l.getTotalBruto()) + " menos comisión e IGV "
							+ Dinero.formatear(l.getTotalComision().add(l.getTotalIgv())), null));
		}
		if (propiedades.abonoRecaudacion() == PropiedadesConciliacion.AbonoRecaudacion.POR_LOTE) {
			for (LoteRecaudacion t : lotes.findByEstadoInAndFechaProcesoBetweenOrderByIdAsc(
					EnumSet.of(EstadoLote.CONFIRMADO, EstadoLote.APLICADO), desde, hasta)) {
				LocalDate[] v = ReglasEmparejamiento.ventanaRecaudacion(t.getFechaProceso());
				objetos.add(new ObjetoAbierto(ObjetoPartida.LOTE_RECAUDACION, t.getId(), t.getFechaProceso(), t.getTotal(),
						null, null, v[0], v[1], "Recaudación del banco (lote N.° " + t.getId() + ") · " + t.getLineas()
								+ " pago(s) del " + Calendario.formatear(t.getDesde()) + " · subió " + nombres.de(
										t.getCreadoPor()), t.getCreadoPor()));
			}
		}
		else {
			for (Pago p : pagos.deCanalEntre(CanalCaja.RECAUDACION, desde, hasta)) {
				LocalDate[] v = ReglasEmparejamiento.ventanaRecaudacion(p.getFecha());
				objetos.add(new ObjetoAbierto(ObjetoPartida.PAGO, p.getId(), p.getFecha(), p.getTotal(),
						p.getNumeroOperacion(), null, v[0], v[1], "Pago por banco " + p.getComprobante().numeroCompleto()
								+ " · " + p.getFamilia().getNombre(), p.getCajero()));
			}
		}
		for (Reembolso r : reembolsos.findByFechaBetweenAndMedioNotOrderByFechaAscIdAsc(desde, hasta, MedioPago.EFECTIVO)) {
			LocalDate[] v = ReglasEmparejamiento.ventanaReembolso(r.getFecha());
			objetos.add(new ObjetoAbierto(ObjetoPartida.REEMBOLSO, r.getId(), r.getFecha(), r.getMonto(),
					r.getNumeroOperacion(), null, v[0], v[1], "Devolución de " + r.getAnulacion().getPago().getComprobante()
							.numeroCompleto() + " por " + r.getMedio().etiqueta() + " · registró " + nombres.de(
									r.getCreadoPor()), r.getCreadoPor()));
		}
		// S4-A4: la devolución de una línea de recaudación sale del banco como cargo, a la cuenta aprobada.
		for (LineaRecaudacion l : lineasRecaudacion.findByEstadoAndDevueltoEnBetweenOrderByIdAsc(EstadoLinea.DEVUELTA,
				desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay())) {
			LocalDate fecha = l.getDevueltoEn().toLocalDate();
			LocalDate[] v = ReglasEmparejamiento.ventanaReembolso(fecha);
			objetos.add(new ObjetoAbierto(ObjetoPartida.LINEA_RECAUDACION, l.getId(), fecha, l.getMonto(),
					l.getDevolucionOperacion(), null, v[0], v[1], "Devolución de la línea " + l.getNumero() + " del lote "
							+ l.getLote().getId() + " a " + l.getDevolucionBanco() + " " + l.getDevolucionCuenta()
							+ " · registró " + nombres.de(l.getDevueltoPor()), l.getDevueltoPor()));
		}
		return objetos;
	}

	/** Lo de ese rango que todavía no tiene partida vigente (PROPUESTA o CONFIRMADA). */
	public List<ObjetoAbierto> abiertos(LocalDate desde, LocalDate hasta) {
		List<ObjetoAbierto> todos = entre(desde, hasta);
		if (todos.isEmpty()) {
			return todos;
		}
		Set<String> tomados = partidas.findByObjetoVigenteIn(todos.stream().map(ObjetoAbierto::clave).toList()).stream()
				.map(PartidaConciliacion::getObjetoVigente).collect(Collectors.toSet());
		return todos.stream().filter(o -> !tomados.contains(o.clave())).toList();
	}

	/**
	 * Lo de ese rango que no tiene una partida vigente que lo cubra: sin partida, o con una pareja MANUAL que todavía
	 * espera la aprobación de otra persona en la bandeja (S4-C1: pedirla no borra la alerta).
	 */
	public List<ObjetoAbierto> sinConfirmarPorPersona(LocalDate desde, LocalDate hasta) {
		List<ObjetoAbierto> todos = entre(desde, hasta);
		if (todos.isEmpty()) {
			return todos;
		}
		Set<String> cubiertos = partidas.findByObjetoVigenteIn(todos.stream().map(ObjetoAbierto::clave).toList()).stream()
				.filter(p -> !(p.getRegla() == ReglaPartida.MANUAL && p.getEstado() == EstadoPartida.PROPUESTA))
				.map(PartidaConciliacion::getObjetoVigente).collect(Collectors.toSet());
		return todos.stream().filter(o -> !cubiertos.contains(o.clave())).toList();
	}
}
