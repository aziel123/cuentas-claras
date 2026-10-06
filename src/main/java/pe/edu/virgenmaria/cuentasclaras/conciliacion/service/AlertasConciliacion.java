package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.springframework.core.annotation.Order;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.config.PropiedadesConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CuentaBancaria;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ExtractoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.MovimientoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.PartidaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ReglaPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.TipoMovimiento;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.CuentaBancariaRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.ExtractoBancarioRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.MovimientoBancarioRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.PartidaConciliacionRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.LiquidacionLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.TipoLineaLiquidacion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.LiquidacionLineaRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Alertas de la conciliación para «Para revisar» de Promotoría (sección 13 del diseño del sprint 4), calculadas al
 * consultar:
 * <ul>
 *   <li>CRÍTICA: cada Yape, Plin, transferencia o tarjeta de caja, depósito, lote de recaudación, liquidación o devolución
 *       que el extracto confirmado ya debía mostrar y no tiene pareja (con quién lo registró: así se ve en rojo, al día
 *       hábil siguiente, un Yape inventado para tapar efectivo); abonos sin pareja de más de 2 días hábiles; extractos
 *       rechazados, con saldo a ciegas distinto o discontinuos; cargos de la pasarela sin pago registrado.</li>
 *   <li>ATENCIÓN: abonos sin pareja recientes; parejas sugeridas pendientes más de 1 día hábil; extracto no subido a la
 *       hora límite o cargado sin confirmar; parejas manuales y explicaciones de la semana; pagos en línea sin
 *       liquidar después de 5 días hábiles.</li>
 *   <li>PARA SABER: muestreo diario de movimientos confirmados para compararlos con la app del banco (estable durante el
 *       día).</li>
 * </ul>
 * Los textos no llevan documentos ni teléfonos.
 */
@Service
@Order(5)
@Transactional(readOnly = true)
@PreAuthorize("hasRole('PROMOTOR')")
public class AlertasConciliacion implements AlertasRevision {

	static final String MODULO = "Conciliación";

	static final String ENLACE = "/conciliacion";

	private final DiferenciasConciliacion diferencias;

	private final CuentaBancariaRepository cuentas;

	private final ExtractoBancarioRepository extractos;

	private final MovimientoBancarioRepository movimientos;

	private final PartidaConciliacionRepository partidas;

	private final LiquidacionLineaRepository lineasLiquidacion;

	private final PagoRepository pagos;

	private final AuditoriaService auditoria;

	private final PropiedadesConciliacion propiedades;

	private final Clock reloj;

	public AlertasConciliacion(DiferenciasConciliacion diferencias, CuentaBancariaRepository cuentas,
			ExtractoBancarioRepository extractos, MovimientoBancarioRepository movimientos,
			PartidaConciliacionRepository partidas, LiquidacionLineaRepository lineasLiquidacion, PagoRepository pagos,
			AuditoriaService auditoria, PropiedadesConciliacion propiedades, Clock reloj) {
		this.diferencias = diferencias;
		this.cuentas = cuentas;
		this.extractos = extractos;
		this.movimientos = movimientos;
		this.partidas = partidas;
		this.lineasLiquidacion = lineasLiquidacion;
		this.pagos = pagos;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	@Override
	public List<AlertaRevision> alertas() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		LocalDate hoy = ahora.toLocalDate();
		List<AlertaRevision> alertas = new ArrayList<>();
		faltantes(alertas, hoy);
		parejasConDiferencia(alertas, hoy);
		abonosSinPareja(alertas, hoy);
		cargosSinExplicar(alertas, hoy);
		cargosQueCompensanAbonos(alertas, hoy);
		extractosConProblemas(alertas, ahora);
		liquidaciones(alertas, hoy);
		sugeridasPendientes(alertas, hoy);
		extractoAlDia(alertas, ahora);
		manualesDeLaSemana(alertas, ahora);
		muestreo(alertas, hoy);
		return alertas;
	}

	/** Lo que el extracto confirmado ya debía mostrar y no está: una alerta CRÍTICA por cada cosa, con quién la registró. */
	private void faltantes(List<AlertaRevision> alertas, LocalDate hoy) {
		Optional<DiferenciasConciliacion.Cobertura> cobertura = diferencias.cobertura();
		if (cobertura.isEmpty()) {
			return;
		}
		String hasta = Calendario.formatear(cobertura.get().hasta());
		for (DiferenciasConciliacion.Faltante f : diferencias.faltantes(hoy)) {
			var o = f.objeto();
			String texto = switch (o.tipo()) {
				case PAGO -> o.detalle() + " por " + Dinero.formatear(o.monto()) + " del " + Calendario.formatear(o.fecha())
						+ " (operación " + o.operacion() + ") NO aparece en el banco (extracto confirmado hasta el " + hasta
						+ "). ¿Pago inventado para tapar efectivo? Revísalo con la cajera hoy mismo.";
				case DEPOSITO -> o.detalle() + ": el depósito " + o.operacion() + " por " + Dinero.formatear(o.monto())
						+ " del " + Calendario.formatear(o.fecha()) + " NO aparece en el banco (extracto confirmado hasta el "
						+ hasta + ").";
				case LOTE_RECAUDACION -> "Recaudación cargada que el banco no abonó: " + o.detalle() + " por "
						+ Dinero.formatear(o.monto()) + " no tiene su abono en el extracto confirmado hasta el " + hasta
						+ ". ¿Archivo fabricado? Revísalo con el banco.";
				case LIQUIDACION -> "Liquidación de la pasarela sin abono en el banco: " + o.detalle() + ", neto "
						+ Dinero.formatear(o.monto()) + " con abono el " + Calendario.formatear(o.fecha()) + ".";
				case REEMBOLSO -> o.detalle() + " por " + Dinero.formatear(o.monto()) + " del "
						+ Calendario.formatear(o.fecha()) + " NO sale del banco (operación " + o.operacion() + "): la "
						+ "devolución registrada no se hizo desde la cuenta del colegio.";
				default -> o.detalle();
			};
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, texto, ENLACE));
		}
	}

	private void abonosSinPareja(List<AlertaRevision> alertas, LocalDate hoy) {
		List<MovimientoBancario> abonos = diferencias.sinPareja(hoy).stream()
				.filter(m -> m.getTipo() == TipoMovimiento.ABONO && !hoy.isBefore(Calendario.siguienteDiaHabil(m.getFecha())))
				.toList();
		List<MovimientoBancario> criticos = abonos.stream().filter(m -> DiferenciasConciliacion.abonoCritico(m, hoy))
				.toList();
		if (!criticos.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, criticos.size() + " abono(s) en el banco por "
					+ Dinero.formatear(Dinero.sumar(criticos.stream().map(MovimientoBancario::getMonto).toList()))
					+ " llevan más de 2 días hábiles sin pareja (el más antiguo, del "
					+ Calendario.formatear(criticos.getFirst().getFecha()) + "): es dinero que entró y nadie registró. "
					+ "Administración lo empareja o lo explica.", ENLACE));
		}
		List<MovimientoBancario> recientes = abonos.stream().filter(m -> !criticos.contains(m)).toList();
		if (!recientes.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, recientes.size() + " abono(s) en el banco por "
					+ Dinero.formatear(Dinero.sumar(recientes.stream().map(MovimientoBancario::getMonto).toList()))
					+ " sin pareja: Administración debe emparejarlos o explicarlos.", ENLACE));
		}
	}

	/**
	 * S4-C1: toda pareja vigente con diferencia de monto es CRÍTICA (hoy solo puede tenerla una liquidación de la
	 * pasarela dentro de su tolerancia; la base rechaza las demás).
	 */
	private void parejasConDiferencia(List<AlertaRevision> alertas, LocalDate hoy) {
		List<PartidaConciliacion> conDiferencia = partidas.vigentesConDiferenciaDesde(hoy.minusDays(
				DiferenciasConciliacion.DIAS_ATRAS));
		if (!conDiferencia.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, conDiferencia.size() + " pareja(s) del extracto con "
					+ "diferencia de monto por " + Dinero.formatear(Dinero.sumar(conDiferencia.stream()
							.map(p -> p.getDiferencia().abs()).toList())) + " (la primera, del "
					+ Calendario.formatear(conDiferencia.getFirst().getMovimiento().getFecha()) + "). Revisa la liquidación "
					+ "con la pasarela.", ENLACE));
		}
	}

	/**
	 * S4-A2: un cargo del banco sin pareja (no es una devolución registrada) debe explicarlo otra persona que no subió el
	 * extracto (Promotoría o Dirección mirando su app, o alguien de Administración): ATENCIÓN desde el día hábil
	 * siguiente y CRÍTICA pasados 2 días hábiles. Antes no se revisaban y un cargo inventado «compensaba» un abono
	 * inventado.
	 */
	private void cargosSinExplicar(List<AlertaRevision> alertas, LocalDate hoy) {
		List<MovimientoBancario> cargos = diferencias.sinPareja(hoy).stream()
				.filter(m -> m.getTipo() == TipoMovimiento.CARGO && !hoy.isBefore(Calendario.siguienteDiaHabil(m.getFecha())))
				.toList();
		List<MovimientoBancario> criticos = cargos.stream()
				.filter(m -> ReglasEmparejamiento.diasHabilesEntre(m.getFecha(), hoy) > 2).toList();
		if (!criticos.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, criticos.size() + " cargo(s) en el banco por "
					+ Dinero.formatear(Dinero.sumar(criticos.stream().map(MovimientoBancario::getMonto).toList()))
					+ " llevan más de 2 días hábiles sin explicar (el más antiguo, del "
					+ Calendario.formatear(criticos.getFirst().getFecha()) + "): es dinero que salió. Búscalos en tu app "
					+ "del banco y explícalos; si no están, el extracto fue alterado.", ENLACE));
		}
		List<MovimientoBancario> recientes = cargos.stream().filter(m -> !criticos.contains(m)).toList();
		if (!recientes.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, recientes.size() + " cargo(s) en el banco por "
					+ Dinero.formatear(Dinero.sumar(recientes.stream().map(MovimientoBancario::getMonto).toList()))
					+ " sin explicar: los explica alguien que no subió el extracto, mirando la app del banco.", ENLACE));
		}
	}

	/**
	 * S4-A2 y QA-S4-5: un cargo sin explicar del MISMO monto que un abono de la misma cuenta y de fechas cercanas (2 días
	 * hábiles) es CRÍTICO de inmediato: es el patrón de un abono inventado (para «verificar» un Yape inventado) más un
	 * cargo que lo compensa para que el saldo final siga siendo el del banco.
	 */
	private void cargosQueCompensanAbonos(List<AlertaRevision> alertas, LocalDate hoy) {
		List<String> sospechosos = new ArrayList<>();
		for (MovimientoBancario cargo : diferencias.sinPareja(hoy)) {
			if (cargo.getTipo() != TipoMovimiento.CARGO) {
				continue;
			}
			boolean compensa = movimientos.vigentesEntre(cargo.getCuentaId(), cargo.getFecha().minusDays(4),
					cargo.getFecha().plusDays(4)).stream()
					.anyMatch(a -> a.getTipo() == TipoMovimiento.ABONO && a.getMonto().compareTo(cargo.getMonto()) == 0
							&& ReglasEmparejamiento.diasHabilesEntre(a.getFecha(), cargo.getFecha()) <= 2);
			if (compensa) {
				sospechosos.add("«" + cargo.getDescripcion() + "» del " + Calendario.formatear(cargo.getFecha()) + " por "
						+ Dinero.formatear(cargo.getMonto()));
			}
		}
		if (!sospechosos.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, sospechosos.size() + " cargo(s) del extracto del mismo "
					+ "monto que un abono de esos días: " + String.join("; ", sospechosos.stream().limit(3).toList())
					+ (sospechosos.size() > 3 ? " y " + (sospechosos.size() - 3) + " más" : "") + ". ¿Abono inventado y un "
					+ "cargo para cuadrar el saldo? Busca ambos en tu app del banco hoy mismo.", ENLACE));
		}
	}

	private void extractosConProblemas(List<AlertaRevision> alertas, LocalDateTime ahora) {
		List<ExtractoBancario> rechazados = extractos.findByEstadoAndRechazadoEnAfterOrderByIdDesc(EstadoExtracto.RECHAZADO,
				ahora.minusDays(30));
		if (!rechazados.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, rechazados.size() + " extracto(s) RECHAZADO(S) porque "
					+ "el saldo escrito a ciegas no coincidió (el último lo subió " + rechazados.getFirst().getCreadoPor()
					+ "). No se concilió: revisa con el banco y con quien lo subió.", ENLACE + "/extractos"));
		}
		long noCoinciden = auditoria.contarDesde(AccionAuditoria.EXTRACTO_SALDO_NO_COINCIDE, ahora.minusDays(7));
		if (noCoinciden > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, noCoinciden + " saldo(s) de extracto escrito(s) a "
					+ "ciegas que no coincidieron en los últimos 7 días: ¿extracto editado? Revisa la bitácora.",
					"/auditoria?soloRevisar=true"));
		}
		long discontinuos = auditoria.contarDesde(AccionAuditoria.EXTRACTO_DISCONTINUO, ahora.minusDays(30));
		if (discontinuos > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, discontinuos + " intento(s) de subir un extracto con "
					+ "días ya cargados DISTINTOS de los guardados en los últimos 30 días: el banco no cambia el pasado. "
					+ "Revisa la bitácora y a quien lo subió.", "/auditoria?soloRevisar=true"));
		}
	}

	private void liquidaciones(List<AlertaRevision> alertas, LocalDate hoy) {
		List<LiquidacionLinea> sinPago = lineasLiquidacion.findByTipoAndPagoIdIsNullOrderByIdAsc(TipoLineaLiquidacion.CARGO);
		if (!sinPago.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, "Pagos en línea", sinPago.size() + " cargo(s) de la pasarela "
					+ "por " + Dinero.formatear(Dinero.sumar(sinPago.stream().map(LiquidacionLinea::getBruto).toList()))
					+ " liquidados sin pago registrado (la pasarela cobró algo que no registramos). Revísalo con la "
					+ "pasarela.", "/pagos-en-linea"));
		}
		// Pagos en línea de hace más de 5 días hábiles que ninguna liquidación trae.
		LocalDate limite = hoy;
		for (int i = 0; i < 5; i++) {
			limite = Calendario.anteriorDiaHabil(limite);
		}
		List<Pago> enLinea = pagos.deCanalEntre(CanalCaja.PASARELA, hoy.minusDays(DiferenciasConciliacion.DIAS_ATRAS),
				limite.minusDays(1));
		if (!enLinea.isEmpty()) {
			Set<Long> liquidados = lineasLiquidacion.findByTipoAndPagoIdIn(TipoLineaLiquidacion.CARGO,
					enLinea.stream().map(Pago::getId).toList()).stream().map(LiquidacionLinea::getPagoId)
					.collect(Collectors.toSet());
			List<Pago> sinLiquidar = enLinea.stream().filter(p -> !liquidados.contains(p.getId())).toList();
			if (!sinLiquidar.isEmpty()) {
				alertas.add(new AlertaRevision(Gravedad.ATENCION, "Pagos en línea", sinLiquidar.size() + " pago(s) en línea "
						+ "por " + Dinero.formatear(Dinero.sumar(sinLiquidar.stream().map(Pago::getTotal).toList()))
						+ " llevan más de 5 días hábiles sin aparecer en una liquidación de la pasarela.", "/pagos-en-linea"));
			}
		}
	}

	private void sugeridasPendientes(List<AlertaRevision> alertas, LocalDate hoy) {
		List<PartidaConciliacion> viejas = diferencias.sugeridas().stream().filter(p -> p.getCreadoEn() != null
				&& ReglasEmparejamiento.diasHabilesEntre(p.getCreadoEn().toLocalDate(), hoy) > 1).toList();
		if (!viejas.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, viejas.size() + " pareja(s) sugerida(s) del "
					+ "extracto llevan más de 1 día hábil sin confirmar: Administración las revisa.", ENLACE));
		}
	}

	/** El extracto del día hábil anterior ya debía estar subido a la hora límite, y lo cargado, confirmado. */
	private void extractoAlDia(List<AlertaRevision> alertas, LocalDateTime ahora) {
		LocalDate hoy = ahora.toLocalDate();
		boolean habil = hoy.getDayOfWeek() != java.time.DayOfWeek.SATURDAY
				&& hoy.getDayOfWeek() != java.time.DayOfWeek.SUNDAY;
		for (CuentaBancaria cuenta : cuentas.findByActivaTrueOrderByIdAsc()) {
			List<ExtractoBancario> cadena = extractos.findByCuentaIdAndSecuenciaVigenteIsNotNullOrderBySecuenciaAsc(
					cuenta.getId());
			LocalDate esperado = Calendario.anteriorDiaHabil(hoy);
			LocalDate cargado = cadena.isEmpty() ? null : cadena.getLast().getHasta();
			if (habil && !ahora.toLocalTime().isBefore(propiedades.horaLimiteExtracto())
					&& (cargado == null || cargado.isBefore(esperado))) {
				alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "El extracto de " + cuenta.descripcion()
						+ " del " + Calendario.formatear(esperado) + " no está subido (pasó la hora límite, "
						+ propiedades.horaLimiteExtracto() + ")" + (cargado == null ? "." : ": está cargado hasta el "
								+ Calendario.formatear(cargado) + "."), ENLACE + "/extractos"));
			}
			List<ExtractoBancario> pendientes = cadena.stream().filter(e -> e.getEstado() == EstadoExtracto.CARGADO
					&& e.getCreadoEn() != null && e.getCreadoEn().isBefore(ahora.minusHours(2))).toList();
			if (!pendientes.isEmpty()) {
				alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, pendientes.size() + " extracto(s) de "
						+ cuenta.descripcion() + " cargado(s) sin confirmar (hasta el "
						+ Calendario.formatear(pendientes.getLast().getHasta()) + "): escribe a ciegas el saldo que ves en "
						+ "tu app del banco.", ENLACE + "/cuentas/" + cuenta.getId() + "/confirmar"));
			}
		}
	}

	private void manualesDeLaSemana(List<AlertaRevision> alertas, LocalDateTime ahora) {
		List<PartidaConciliacion> manuales = partidas.findByEstadoAndReglaInAndResueltoEnAfterOrderByIdDesc(
				EstadoPartida.CONFIRMADA, EnumSet.of(ReglaPartida.MANUAL, ReglaPartida.EXPLICADA, ReglaPartida.SUGERIDA),
				ahora.minusDays(7));
		if (!manuales.isEmpty()) {
			long aMano = manuales.stream().filter(p -> p.getRegla() == ReglaPartida.MANUAL).count();
			long explicadas = manuales.stream().filter(p -> p.getRegla() == ReglaPartida.EXPLICADA).count();
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "Esta semana Administración confirmó "
					+ (manuales.size() - aMano - explicadas) + " pareja(s) sugerida(s), emparejó " + aMano
					+ " a mano y explicó " + explicadas + " movimiento(s) del banco. Revisa en la bitácora que cada una "
					+ "tenga sentido.", "/auditoria?soloRevisar=true"));
		}
	}

	/**
	 * Muestreo diario: movimientos al azar del último extracto confirmado, para que Promotoría los compare con su app del
	 * banco. Estable durante el día, pero con la semilla SECRETA del extracto (elegida con SecureRandom al registrarlo y
	 * guardada): antes la semilla era la fecha y quien sube podía calcular qué movimientos saldrían (S4-A2).
	 */
	private void muestreo(List<AlertaRevision> alertas, LocalDate hoy) {
		if (propiedades.muestreoDiario() <= 0) {
			return;
		}
		Optional<ExtractoBancario> ultimo = extractos.findByEstadoOrderByHastaDesc(EstadoExtracto.CONFIRMADO).stream()
				.findFirst();
		if (ultimo.isEmpty()) {
			return;
		}
		List<MovimientoBancario> lista = new ArrayList<>(movimientos.findByExtractoIdOrderByNumeroAsc(ultimo.get().getId()));
		if (lista.isEmpty()) {
			return;
		}
		Long semilla = ultimo.get().getSemillaMuestreo();
		java.util.Collections.shuffle(lista, semilla == null ? new java.security.SecureRandom()
				: new Random(semilla ^ hoy.toEpochDay()));
		String muestra = lista.stream().limit(propiedades.muestreoDiario())
				.sorted(Comparator.comparing(MovimientoBancario::getNumero))
				.map(m -> Calendario.formatear(m.getFecha()) + " " + m.getTipo().etiqueta().toLowerCase(java.util.Locale.ROOT)
						+ " " + Dinero.formatear(m.getMonto()) + (m.getNumeroOperacion() == null ? ""
								: " (op. " + m.getNumeroOperacion() + ")"))
				.collect(Collectors.joining("; "));
		alertas.add(new AlertaRevision(Gravedad.INFORMATIVA, MODULO, "Muestreo de hoy: busca en tu app del banco estos "
				+ "movimientos del extracto confirmado: " + muestra + ". Si alguno no está, avisa a Dirección.",
				ENLACE + "/extractos/" + ultimo.get().getId()));
	}
}
