package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoVerificacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.VerificacionBancaria;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.VerificacionBancariaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CuentaBancaria;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ExtractoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.MovimientoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ObjetoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.PartidaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ReglaPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.TipoMovimiento;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.CuentaBancariaRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.ExtractoBancarioRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.MovimientoBancarioRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.PartidaConciliacionRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.ObjetoAbierto;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Las diferencias de la conciliación (lo que la pantalla principal y las alertas de Promotoría muestran), calculadas al
 * consultar y solo de lectura:
 * <ul>
 *   <li><b>Cobertura</b>: los días que cubren los extractos CONFIRMADOS a ciegas de las cuentas activas.</li>
 *   <li><b>Debía estar en el banco y no está</b>: lo que su día (o su ventana) ya cubre un extracto confirmado y no tiene
 *       pareja: un Yape, Plin o transferencia de caja, un depósito, un lote de recaudación, una liquidación de la
 *       pasarela o una devolución. Es el control del «Yape inventado» para tapar efectivo: aparece en rojo al día
 *       hábil siguiente, cuando se confirma el extracto del día.</li>
 *   <li><b>Abonos sin pareja</b>: dinero que entró al banco sin registrarse (atención el día hábil siguiente; crítico
 *       después de 2 días hábiles).</li>
 *   <li><b>Sugeridas por confirmar</b>.</li>
 * </ul>
 * Un pago o depósito que Administración ya verificó a mano («Encontrado», verificación del sprint 3 para las
 * excepciones) no se repite como faltante.
 */
@Component
@Transactional(readOnly = true)
public class DiferenciasConciliacion {

	/** Lo que se espera ver en el banco no se busca más atrás que esto. */
	static final int DIAS_ATRAS = 60;

	/** Los días que cubren los extractos confirmados (desde el primero hasta el último). */
	public record Cobertura(LocalDate desde, LocalDate hasta) {
	}

	/** Algo que debía estar en el banco y no está, con su gravedad (CRÍTICA, salvo lo de canal aún en su ventana). */
	public record Faltante(ObjetoAbierto objeto, boolean critico) {
	}

	private final CuentaBancariaRepository cuentas;

	private final ExtractoBancarioRepository extractos;

	private final MovimientoBancarioRepository movimientos;

	private final PartidaConciliacionRepository partidas;

	private final ObjetosConciliables objetos;

	private final VerificacionBancariaRepository verificaciones;

	public DiferenciasConciliacion(CuentaBancariaRepository cuentas, ExtractoBancarioRepository extractos,
			MovimientoBancarioRepository movimientos, PartidaConciliacionRepository partidas, ObjetosConciliables objetos,
			VerificacionBancariaRepository verificaciones) {
		this.cuentas = cuentas;
		this.extractos = extractos;
		this.movimientos = movimientos;
		this.partidas = partidas;
		this.objetos = objetos;
		this.verificaciones = verificaciones;
	}

	/**
	 * Hasta dónde llegan los extractos CONFIRMADOS de las cuentas activas (el último día confirmado de cualquiera de
	 * ellas: con una sola cuenta, que es lo previsto, es su cadena).
	 */
	public Optional<Cobertura> cobertura() {
		Set<Long> activas = cuentas.findByActivaTrueOrderByIdAsc().stream().map(CuentaBancaria::getId)
				.collect(Collectors.toSet());
		List<ExtractoBancario> confirmados = extractos.findByEstadoOrderByHastaDesc(EstadoExtracto.CONFIRMADO).stream()
				.filter(e -> activas.contains(e.getCuenta().getId())).toList();
		if (confirmados.isEmpty()) {
			return Optional.empty();
		}
		LocalDate desde = confirmados.stream().map(ExtractoBancario::getDesde).min(Comparator.naturalOrder()).orElseThrow();
		return Optional.of(new Cobertura(desde, confirmados.getFirst().getHasta()));
	}

	/** Lo que debía verse en el banco hasta el último día confirmado y no tiene pareja. */
	public List<Faltante> faltantes(LocalDate hoy) {
		Optional<Cobertura> cobertura = cobertura();
		if (cobertura.isEmpty()) {
			return List.of();
		}
		LocalDate desde = max(cobertura.get().desde(), hoy.minusDays(DIAS_ATRAS));
		LocalDate cubierto = cobertura.get().hasta();
		// Una pareja MANUAL por aprobar no cubre nada todavía (S4-C1): lo emparejado sigue en rojo hasta que otra persona
		// la apruebe en la bandeja.
		List<ObjetoAbierto> abiertos = objetos.sinConfirmarPorPersona(desde, cubierto);
		List<Long> idsPagos = ids(abiertos, ObjetoPartida.PAGO);
		List<Long> idsDepositos = ids(abiertos, ObjetoPartida.DEPOSITO);
		Set<Long> pagosAMano = idsPagos.isEmpty() ? Set.of()
				: verificadosAMano(verificaciones.findByPagoIdIn(idsPagos), true);
		Set<Long> depositosAMano = idsDepositos.isEmpty() ? Set.of()
				: verificadosAMano(verificaciones.findByDepositoIdIn(idsDepositos), false);
		return abiertos.stream().filter(o -> !(o.tipo() == ObjetoPartida.PAGO && pagosAMano.contains(o.id()))
				&& !(o.tipo() == ObjetoPartida.DEPOSITO && depositosAMano.contains(o.id())))
				.filter(o -> !limite(o).isAfter(cubierto))
				.map(o -> new Faltante(o, true)).toList();
	}

	/**
	 * Desde qué día el extracto confirmado ya debía mostrar el objeto: un pago digital de caja, un depósito o una
	 * devolución, su mismo día; un lote de recaudación, o un pago de recaudación cuando el banco abona pago por pago
	 * (QA-S4-4: lo registra {@code sistema.recaudacion}), el día hábil siguiente; una liquidación, 2 días hábiles después
	 * de su fecha de abono.
	 */
	static LocalDate limite(ObjetoAbierto o) {
		return switch (o.tipo()) {
			case LOTE_RECAUDACION -> Calendario.siguienteDiaHabil(o.fecha());
			case LIQUIDACION -> Calendario.siguienteDiaHabil(Calendario.siguienteDiaHabil(o.fecha()));
			case PAGO -> ActorSistema.RECAUDACION.usuario().equals(o.responsable()) ? Calendario.siguienteDiaHabil(o.fecha())
					: o.fecha();
			default -> o.fecha();
		};
	}

	/** Movimientos de extractos vigentes sin pareja (abonos y cargos), de los últimos días. */
	public List<MovimientoBancario> sinPareja(LocalDate hoy) {
		return movimientos.sinParejaDesde(hoy.minusDays(DIAS_ATRAS));
	}

	/** Un abono sin pareja ya es crítico: pasaron más de 2 días hábiles desde su fecha. */
	public static boolean abonoCritico(MovimientoBancario m, LocalDate hoy) {
		return m.getTipo() == TipoMovimiento.ABONO && ReglasEmparejamiento.diasHabilesEntre(m.getFecha(), hoy) > 2;
	}

	/** Las parejas sugeridas que espera Administración (las manuales esperan en la bandeja de aprobaciones). */
	public List<PartidaConciliacion> sugeridas() {
		return partidas.findByEstadoAndReglaNotOrderByIdAsc(EstadoPartida.PROPUESTA, ReglaPartida.EXACTA).stream()
				.filter(p -> p.getRegla() == ReglaPartida.SUGERIDA).toList();
	}

	private static List<Long> ids(Collection<ObjetoAbierto> objetos, ObjetoPartida tipo) {
		return objetos.stream().filter(o -> o.tipo() == tipo).map(ObjetoAbierto::id).toList();
	}

	private static Set<Long> verificadosAMano(List<VerificacionBancaria> lista, boolean pagos) {
		return lista.stream().filter(v -> !v.automatica() && v.getResultado() == ResultadoVerificacion.ENCONTRADO)
				.map(v -> pagos ? v.getPago().getId() : v.getDeposito().getId()).collect(Collectors.toSet());
	}

	private static LocalDate max(LocalDate a, LocalDate b) {
		return a.isAfter(b) ? a : b;
	}
}
