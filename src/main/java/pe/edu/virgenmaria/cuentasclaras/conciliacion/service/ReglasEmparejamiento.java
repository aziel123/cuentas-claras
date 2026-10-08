package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import pe.edu.virgenmaria.cuentasclaras.caja.model.NumeroOperacion;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.DiasHabiles;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ObjetoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ReglaPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.TipoMovimiento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reglas de emparejamiento de la conciliación automática (sección 10.4 del diseño del sprint 4), en este orden:
 * <ol>
 *   <li><b>EXACTA por operación</b>: el movimiento trae la MISMA operación canónica que el objeto, el mismo monto y la
 *       fecha cae en la ventana del objeto. La operación es única: hay un solo candidato. Se confirma sola al confirmarse
 *       el extracto.</li>
 *   <li><b>EXACTA por referencia</b>: la liquidación cuya referencia aparece en la glosa o la referencia del movimiento,
 *       con el mismo neto; o el lote de recaudación cuyo total coincide, en su ventana, con la glosa que cumple el patrón
 *       configurado.</li>
 *   <li><b>SUGERIDA</b>: el mismo monto (la liquidación admite la tolerancia configurada), la fecha a no más de
 *       {@code diasToleranciaFecha} días hábiles y candidato ÚNICO en ambos sentidos. Si ambos tienen operación y
 *       difieren, se avisa «operación distinta»; si difieren en un solo carácter, «número parecido» (en rojo).</li>
 *   <li><b>Varios candidatos</b>: no se propone nada; quedan como «posibles» para que una persona elija.</li>
 * </ol>
 * Los pagos en línea (pasarela) no se emparejan directo: los cubre su liquidación. Un par que una persona ya descartó
 * no se vuelve a proponer. Pura: sin base de datos ni Spring.
 */
public final class ReglasEmparejamiento {

	/** Un movimiento del extracto sin partida vigente. */
	public record MovimientoAbierto(Long id, LocalDate fecha, TipoMovimiento tipo, BigDecimal monto, String operacion,
			String descripcion, String referencia) {

		public MovimientoAbierto {
			Objects.requireNonNull(fecha, "fecha");
			Objects.requireNonNull(tipo, "tipo");
			Objects.requireNonNull(monto, "monto");
		}
	}

	/**
	 * Algo que debía verse en el banco y aún no tiene partida vigente, con la ventana de fechas en que se espera.
	 *
	 * @param detalle     cómo se muestra («Yape B001-00000012 · cobró Lucía Ramos»)
	 * @param responsable quién lo registró (cajera, quien depositó, quien subió el lote): no confirma su pareja
	 */
	public record ObjetoAbierto(ObjetoPartida tipo, Long id, LocalDate fecha, BigDecimal monto, String operacion,
			String referencia, LocalDate ventanaDesde, LocalDate ventanaHasta, String detalle, String responsable) {

		public ObjetoAbierto {
			Objects.requireNonNull(tipo, "tipo");
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(fecha, "fecha");
			Objects.requireNonNull(monto, "monto");
			Objects.requireNonNull(ventanaDesde, "ventanaDesde");
			Objects.requireNonNull(ventanaHasta, "ventanaHasta");
		}

		public String clave() {
			return tipo.clave(id);
		}

		boolean enVentana(LocalDate dia) {
			return !dia.isBefore(ventanaDesde) && !dia.isAfter(ventanaHasta);
		}
	}

	/**
	 * @param descartadas pares que una persona ya descartó, como {@code movimientoId + "|" + clave del objeto}
	 */
	public record Parametros(int diasToleranciaFecha, BigDecimal toleranciaLiquidacion, String patronAbonoRecaudacion,
			Set<String> descartadas, DiasHabiles calendario) {

		public Parametros {
			toleranciaLiquidacion = toleranciaLiquidacion == null ? BigDecimal.ZERO : toleranciaLiquidacion;
			patronAbonoRecaudacion = patronAbonoRecaudacion == null ? "" : patronAbonoRecaudacion.strip();
			descartadas = descartadas == null ? Set.of() : Set.copyOf(descartadas);
			calendario = calendario == null ? DiasHabiles.NACIONALES : calendario;
		}

		/** Sin feriados (pruebas puras de bordes). */
		public Parametros(int diasToleranciaFecha, BigDecimal toleranciaLiquidacion, String patronAbonoRecaudacion,
				Set<String> descartadas) {
			this(diasToleranciaFecha, toleranciaLiquidacion, patronAbonoRecaudacion, descartadas,
					DiasHabiles.LUNES_A_VIERNES);
		}
	}

	/** Una pareja propuesta, con los avisos que ve quien la confirma. {@code parecido}: número parecido (en rojo). */
	public record Propuesta(MovimientoAbierto movimiento, ObjetoAbierto objeto, ReglaPartida regla, List<String> avisos,
			boolean parecido) {
	}

	/** Las parejas propuestas y, para los movimientos con varios candidatos, los posibles (para elegir a mano). */
	public record Resultado(List<Propuesta> propuestas, Map<Long, List<ObjetoAbierto>> posibles) {
	}

	public static final String OPERACION_DISTINTA = "Operación distinta";

	public static final String NUMERO_PARECIDO = "Número parecido (difiere en un carácter)";

	private ReglasEmparejamiento() {
	}

	public static Resultado proponer(List<MovimientoAbierto> movimientos, List<ObjetoAbierto> objetos, Parametros p) {
		List<MovimientoAbierto> movs = movimientos.stream()
				.sorted(Comparator.comparing(MovimientoAbierto::fecha).thenComparing(m -> m.id() == null ? 0L : m.id()))
				.toList();
		List<ObjetoAbierto> objs = objetos.stream().sorted(Comparator.comparing(ObjetoAbierto::fecha)
				.thenComparing(o -> o.tipo().ordinal()).thenComparing(ObjetoAbierto::id)).toList();
		List<Propuesta> propuestas = new ArrayList<>();
		// Por posición: en la vista previa los movimientos aún no tienen id (y dos pueden ser iguales en todo).
		boolean[] usados = new boolean[movs.size()];
		Set<String> objetosUsados = new HashSet<>();

		// 1. EXACTA por operación.
		for (int i = 0; i < movs.size(); i++) {
			MovimientoAbierto m = movs.get(i);
			if (m.operacion() == null) {
				continue;
			}
			List<ObjetoAbierto> candidatos = objs.stream().filter(o -> !objetosUsados.contains(o.clave())
					&& o.operacion() != null && o.operacion().equals(m.operacion()) && compatible(m, o)
					&& m.monto().compareTo(o.monto()) == 0 && o.enVentana(m.fecha()) && !descartado(p, m, o)).toList();
			if (candidatos.size() == 1) {
				agregar(propuestas, usados, i, objetosUsados, m, candidatos.getFirst(), ReglaPartida.EXACTA, List.of(),
						false);
			}
		}
		// 2. EXACTA por referencia (liquidación) o por la glosa del abono de la recaudación.
		for (int i = 0; i < movs.size(); i++) {
			MovimientoAbierto m = movs.get(i);
			if (usados[i] || m.tipo() != TipoMovimiento.ABONO) {
				continue;
			}
			String texto = alfanumerico(m.descripcion()) + "|" + alfanumerico(m.referencia());
			List<ObjetoAbierto> candidatos = objs.stream().filter(o -> !objetosUsados.contains(o.clave())
					&& m.monto().compareTo(o.monto()) == 0 && o.enVentana(m.fecha()) && !descartado(p, m, o)
					&& ((o.tipo() == ObjetoPartida.LIQUIDACION && o.referencia() != null
							&& alfanumerico(o.referencia()).length() >= 4 && texto.contains(alfanumerico(o.referencia())))
							|| (o.tipo() == ObjetoPartida.LOTE_RECAUDACION && !p.patronAbonoRecaudacion().isEmpty()
									&& texto.contains(alfanumerico(p.patronAbonoRecaudacion())))))
					.toList();
			if (candidatos.size() == 1) {
				agregar(propuestas, usados, i, objetosUsados, m, candidatos.getFirst(), ReglaPartida.EXACTA, List.of(),
						false);
			}
		}
		// 3. SUGERIDA: candidato único en ambos sentidos.
		Map<Integer, List<ObjetoAbierto>> porMovimiento = new LinkedHashMap<>();
		Map<String, List<Integer>> porObjeto = new LinkedHashMap<>();
		for (int i = 0; i < movs.size(); i++) {
			MovimientoAbierto m = movs.get(i);
			if (usados[i]) {
				continue;
			}
			for (ObjetoAbierto o : objs) {
				if (!objetosUsados.contains(o.clave()) && compatible(m, o) && montoParecido(m, o, p)
						&& cercano(p.calendario(), m.fecha(), o.fecha(), p.diasToleranciaFecha()) && !descartado(p, m, o)) {
					porMovimiento.computeIfAbsent(i, k -> new ArrayList<>()).add(o);
					porObjeto.computeIfAbsent(o.clave(), k -> new ArrayList<>()).add(i);
				}
			}
		}
		Map<Long, List<ObjetoAbierto>> posibles = new LinkedHashMap<>();
		porMovimiento.forEach((i, candidatos) -> {
			MovimientoAbierto m = movs.get(i);
			if (candidatos.size() == 1 && porObjeto.get(candidatos.getFirst().clave()).size() == 1) {
				ObjetoAbierto o = candidatos.getFirst();
				List<String> avisos = new ArrayList<>();
				boolean parecido = false;
				if (m.operacion() != null && o.operacion() != null && !m.operacion().equals(o.operacion())) {
					parecido = NumeroOperacion.parecidos(m.operacion(), o.operacion());
					avisos.add(parecido ? NUMERO_PARECIDO : OPERACION_DISTINTA);
				}
				if (m.monto().compareTo(o.monto()) != 0) {
					avisos.add("Diferencia de monto: " + m.monto().subtract(o.monto()).toPlainString());
				}
				agregar(propuestas, usados, i, objetosUsados, m, o, ReglaPartida.SUGERIDA, avisos, parecido);
			}
			else if (m.id() != null) {
				posibles.put(m.id(), List.copyOf(candidatos));
			}
		});
		return new Resultado(List.copyOf(propuestas), posibles);
	}

	private static void agregar(List<Propuesta> propuestas, boolean[] usados, int indice, Set<String> objetosUsados,
			MovimientoAbierto m, ObjetoAbierto o, ReglaPartida regla, List<String> avisos, boolean parecido) {
		propuestas.add(new Propuesta(m, o, regla, List.copyOf(avisos), parecido));
		usados[indice] = true;
		objetosUsados.add(o.clave());
	}

	/** Un abono con lo que entra; un cargo con un reembolso. */
	private static boolean compatible(MovimientoAbierto m, ObjetoAbierto o) {
		return o.tipo() != ObjetoPartida.EXPLICACION && m.tipo() == o.tipo().movimiento();
	}

	private static boolean montoParecido(MovimientoAbierto m, ObjetoAbierto o, Parametros p) {
		return montoAdmitido(m.monto(), o, p.toleranciaLiquidacion());
	}

	/**
	 * S4-C1: una pareja que no es EXACTA (sugerida o elegida a mano) solo une el MISMO monto; la liquidación de la
	 * pasarela admite su tolerancia configurada. La base lo exige con {@code ck_partida_conciliacion_monto_exacto}.
	 */
	public static boolean montoAdmitido(BigDecimal movimiento, ObjetoAbierto o, BigDecimal toleranciaLiquidacion) {
		BigDecimal diferencia = movimiento.subtract(o.monto()).abs();
		return diferencia.signum() == 0 || (o.tipo() == ObjetoPartida.LIQUIDACION
				&& diferencia.compareTo(toleranciaLiquidacion) <= 0);
	}

	private static boolean descartado(Parametros p, MovimientoAbierto m, ObjetoAbierto o) {
		return m.id() != null && p.descartadas().contains(m.id() + "|" + o.clave());
	}

	/** Si dos fechas están a no más de {@code dias} días hábiles una de otra. */
	static boolean cercano(DiasHabiles calendario, LocalDate a, LocalDate b, int dias) {
		return calendario.habilesEntre(a, b) <= dias;
	}

	/**
	 * Días hábiles entre dos fechas (sin contar la primera): 0 si es el mismo día o solo hay un fin de semana o un feriado
	 * de por medio. Sprint 5, tanda 3: con el calendario del colegio (feriados nacionales y extra).
	 */
	public static int diasHabilesEntre(DiasHabiles calendario, LocalDate a, LocalDate b) {
		return calendario.habilesEntre(a, b);
	}

	/** Letras y dígitos en mayúsculas (para buscar una referencia dentro de una glosa). */
	static String alfanumerico(String texto) {
		return texto == null ? "" : texto.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
	}

	// --- Ventanas de fecha de cada objeto (sección 10.4) ---

	/** Pago digital de ventanilla: de su fecha a 3 días después (un Yape del viernes puede verse el lunes). */
	public static LocalDate[] ventanaPago(LocalDate fecha) {
		return new LocalDate[] { fecha, fecha.plusDays(3) };
	}

	/** Depósito de caja: su fecha y el día hábil siguiente. */
	public static LocalDate[] ventanaDeposito(DiasHabiles calendario, LocalDate fecha) {
		return new LocalDate[] { fecha, calendario.siguienteDiaHabil(fecha) };
	}

	/** Liquidación de la pasarela: de un día hábil antes a tres hábiles después de su fecha de abono. */
	public static LocalDate[] ventanaLiquidacion(DiasHabiles calendario, LocalDate abono) {
		return new LocalDate[] { calendario.anteriorDiaHabil(abono), calendario.sumarHabiles(abono, 3) };
	}

	/** Lote (o pago) de recaudación: su fecha de proceso y el día hábil siguiente. */
	public static LocalDate[] ventanaRecaudacion(DiasHabiles calendario, LocalDate fechaProceso) {
		return new LocalDate[] { fechaProceso, calendario.siguienteDiaHabil(fechaProceso) };
	}

	/** Reembolso digital: de su fecha a 3 días después. */
	public static LocalDate[] ventanaReembolso(LocalDate fecha) {
		return new LocalDate[] { fecha, fecha.plusDays(3) };
	}
}
