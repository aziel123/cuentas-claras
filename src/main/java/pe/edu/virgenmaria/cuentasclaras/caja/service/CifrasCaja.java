package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.AnuladoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CambiosPosteriores;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoDia;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.EstadoCajas;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.PagoDeFamilia;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.PagoExportable;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AnulacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AplicacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CajaDiariaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CierreCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoCuota;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Puerto de cifras de caja para el panel y los reportes (sprint 6, decisión 2). Solo lectura y JPQL agregado: ninguna
 * cifra se guarda ni se escribe a mano, todas salen del libro de pagos al consultar. Las consultas las filtra
 * {@code @TenantId} por el colegio en sesión.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION','SISTEMA_PANEL')")
public class CifrasCaja {

	private final PagoRepository pagos;

	private final AnulacionPagoRepository anulaciones;

	private final AplicacionPagoRepository aplicaciones;

	private final CajaDiariaRepository cajas;

	private final CierreCajaRepository cierres;

	public CifrasCaja(PagoRepository pagos, AnulacionPagoRepository anulaciones, AplicacionPagoRepository aplicaciones,
			CajaDiariaRepository cajas, CierreCajaRepository cierres) {
		this.pagos = pagos;
		this.anulaciones = anulaciones;
		this.aplicaciones = aplicaciones;
		this.cajas = cajas;
		this.cierres = cierres;
	}

	/** Pagos VIGENTES con día de caja entre {@code desde} y {@code hasta} (ambos incluidos), por medio y por canal. */
	public CobradoPeriodo cobrado(LocalDate desde, LocalDate hasta) {
		exigirRango(desde, hasta);
		Map<MedioPago, long[]> cantidadPorMedio = new EnumMap<>(MedioPago.class);
		Map<MedioPago, BigDecimal> totalPorMedio = new EnumMap<>(MedioPago.class);
		Map<CanalCaja, long[]> cantidadPorCanal = new EnumMap<>(CanalCaja.class);
		Map<CanalCaja, BigDecimal> totalPorCanal = new EnumMap<>(CanalCaja.class);
		for (Object[] fila : pagos.vigentesPorMedioYCanal(desde, hasta)) {
			MedioPago medio = (MedioPago) fila[0];
			CanalCaja canal = (CanalCaja) fila[1];
			long cantidad = ((Number) fila[2]).longValue();
			BigDecimal suma = monto(fila[3]);
			cantidadPorMedio.computeIfAbsent(medio, m -> new long[1])[0] += cantidad;
			totalPorMedio.merge(medio, suma, BigDecimal::add);
			cantidadPorCanal.computeIfAbsent(canal, c -> new long[1])[0] += cantidad;
			totalPorCanal.merge(canal, suma, BigDecimal::add);
		}
		List<CobradoPeriodo.PorMedio> porMedio = new ArrayList<>();
		totalPorMedio.forEach((medio, total) -> porMedio.add(new CobradoPeriodo.PorMedio(medio,
				cantidadPorMedio.get(medio)[0], total)));
		List<CobradoPeriodo.PorCanal> porCanal = new ArrayList<>();
		totalPorCanal.forEach((canal, total) -> porCanal.add(new CobradoPeriodo.PorCanal(canal,
				cantidadPorCanal.get(canal)[0], total)));
		BigDecimal total = Dinero.sumar(totalPorMedio.values());
		long cantidad = cantidadPorMedio.values().stream().mapToLong(c -> c[0]).sum();
		BigDecimal efectivo = totalPorMedio.getOrDefault(MedioPago.EFECTIVO, Dinero.CERO);
		long pagosEfectivo = cantidadPorMedio.containsKey(MedioPago.EFECTIVO) ? cantidadPorMedio.get(MedioPago.EFECTIVO)[0]
				: 0;
		return new CobradoPeriodo(desde, hasta, total, cantidad, efectivo, pagosEfectivo, porMedio, porCanal);
	}

	/** Anulaciones de pagos APROBADAS (por su fecha de aprobación) entre {@code desde} y {@code hasta}. */
	public AnuladoPeriodo anulado(LocalDate desde, LocalDate hasta) {
		exigirRango(desde, hasta);
		List<Object[]> filas = anulaciones.aprobadasEntre(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
		Object[] fila = filas.isEmpty() ? new Object[] { 0L, null } : filas.get(0);
		return new AnuladoPeriodo(desde, hasta, fila[0] == null ? 0 : ((Number) fila[0]).longValue(), monto(fila[1]));
	}

	/** Cajas de ventanilla de un día: abiertas, cerradas y cuántos cierres tienen diferencia. */
	public EstadoCajas cajas(LocalDate fecha) {
		List<CajaDiaria> delDia = cajas.findByCanalAndFechaOrderByCajeroAsc(CanalCaja.VENTANILLA, fecha);
		long abiertas = delDia.stream().filter(c -> c.getEstado() == EstadoCaja.ABIERTA).count();
		long conDiferencia = delDia.isEmpty() ? 0
				: cierres.findByCajaIdInOrderByNumeroAsc(delDia.stream().map(CajaDiaria::getId).toList()).stream()
						.filter(CierreCaja::conDiferencia).count();
		return new EstadoCajas(abiertas, delDia.size() - abiertas, conDiferencia);
	}

	/** Cuántos pagos (vigentes y anulados) tiene el rango: el tope de filas del Excel se revisa antes de armarlo. */
	public long pagosEnRango(LocalDate desde, LocalDate hasta) {
		exigirRango(desde, hasta);
		return pagos.contarEntre(desde, hasta);
	}

	/**
	 * Pagos de un rango de días de caja (vigentes y anulados, con su nota de crédito) para el Excel del contador, con
	 * los datos mínimos de {@link PagoExportable}. Proyección JPQL: no se carga ninguna familia ni ningún alumno.
	 */
	public List<PagoExportable> pagosParaContador(LocalDate desde, LocalDate hasta) {
		exigirRango(desde, hasta);
		Map<Long, Object[]> notas = new HashMap<>();
		for (Object[] nota : anulaciones.notasDePagosEntre(desde, hasta)) {
			notas.put((Long) nota[0], nota);
		}
		Map<Long, List<String>> conceptos = new LinkedHashMap<>();
		for (Object[] concepto : aplicaciones.conceptosDePagosEntre(desde, hasta)) {
			String texto = concepto[1] == TipoCuota.SALDO_INICIAL ? TipoCuota.SALDO_INICIAL.etiqueta()
					: (String) concepto[2];
			List<String> delPago = conceptos.computeIfAbsent((Long) concepto[0], id -> new ArrayList<>());
			if (!delPago.contains(texto)) {
				delPago.add(texto);
			}
		}
		List<PagoExportable> filas = new ArrayList<>();
		for (Object[] p : pagos.paraContador(desde, hasta)) {
			Long id = (Long) p[0];
			Object[] nota = notas.get(id);
			List<String> delPago = conceptos.getOrDefault(id, List.of());
			filas.add(new PagoExportable((LocalDate) p[1], p[2] + "-" + p[3], (TipoComprobante) p[4], (MedioPago) p[5],
					(CanalCaja) p[6], (String) p[7], monto(p[8]), (EstadoPago) p[9],
					nota == null ? null : (LocalDate) nota[1], nota == null ? null : nota[2] + "-" + nota[3],
					delPago.isEmpty() ? "A cuenta" : String.join("; ", delPago), (Long) p[10], (String) p[11],
					(String) p[12]));
		}
		filas.sort(Comparator.comparing(PagoExportable::fecha));
		return filas;
	}

	/**
	 * Sprint 6, tanda 2: lo cobrado (pagos VIGENTES) en cada día de caja de un rango, con la parte en efectivo. Los días
	 * sin pagos no aparecen. Mismas definiciones que {@link #cobrado} (y que el trigger de la foto del resumen).
	 */
	public java.util.Map<LocalDate, CobradoDia> cobradoPorDia(LocalDate desde, LocalDate hasta) {
		exigirRango(desde, hasta);
		java.util.Map<LocalDate, BigDecimal[]> montos = new java.util.TreeMap<>();
		java.util.Map<LocalDate, long[]> cantidades = new java.util.TreeMap<>();
		for (Object[] fila : pagos.vigentesPorDiaYMedio(desde, hasta)) {
			LocalDate fecha = (LocalDate) fila[0];
			boolean efectivo = fila[1] == MedioPago.EFECTIVO;
			long cantidad = ((Number) fila[2]).longValue();
			BigDecimal suma = monto(fila[3]);
			BigDecimal[] m = montos.computeIfAbsent(fecha, f -> new BigDecimal[] { Dinero.CERO, Dinero.CERO });
			long[] c = cantidades.computeIfAbsent(fecha, f -> new long[2]);
			m[0] = m[0].add(suma);
			c[0] += cantidad;
			if (efectivo) {
				m[1] = m[1].add(suma);
				c[1] += cantidad;
			}
		}
		java.util.Map<LocalDate, CobradoDia> porDia = new java.util.TreeMap<>();
		montos.forEach((fecha, m) -> porDia.put(fecha, new CobradoDia(fecha, Dinero.normalizar(m[0]),
				cantidades.get(fecha)[0], Dinero.normalizar(m[1]), cantidades.get(fecha)[1])));
		return porDia;
	}

	/**
	 * Sprint 6, tanda 2 (P4): pagos de un rango de días registrados o anulados DESPUÉS de {@code despuesDe}. Explica por
	 * qué la foto de un día ya informado no coincide con los libros de hoy.
	 */
	public CambiosPosteriores cambiosPosteriores(LocalDate desde, LocalDate hasta, java.time.LocalDateTime despuesDe) {
		exigirRango(desde, hasta);
		Objects.requireNonNull(despuesDe, "despuesDe");
		List<CambiosPosteriores.Movimiento> registrados = new ArrayList<>();
		for (Object[] p : pagos.vigentesRegistradosDespuesDe(desde, hasta, despuesDe)) {
			registrados.add(new CambiosPosteriores.Movimiento((LocalDate) p[0], (MedioPago) p[1], monto(p[2]),
					(java.time.LocalDateTime) p[3], null));
		}
		List<CambiosPosteriores.Movimiento> anulados = new ArrayList<>();
		for (Object[] a : anulaciones.anuladasDespuesDe(desde, hasta, despuesDe)) {
			anulados.add(new CambiosPosteriores.Movimiento((LocalDate) a[0], (MedioPago) a[1], monto(a[2]),
					(java.time.LocalDateTime) a[3], (java.time.LocalDateTime) a[4]));
		}
		return new CambiosPosteriores(registrados, anulados);
	}

	/**
	 * Sprint 6, tanda 3 (decisión 77): las familias con algún pago en EFECTIVO (vigente o anulado) con día de caja en el
	 * rango, por id. Son las candidatas de la llamada de control; la muestra la elige el panel con la semilla secreta.
	 */
	public List<Long> familiasConEfectivo(LocalDate desde, LocalDate hasta) {
		exigirRango(desde, hasta);
		return pagos.familiasConEfectivoEntre(desde, hasta);
	}

	/**
	 * Sprint 6, tanda 3: los pagos de una familia en un rango (todos los medios y estados), para comparar DESPUÉS de que la
	 * familia dijo cuánto y cuándo pagó. Solo quien hace la llamada de control: Promotoría o Dirección.
	 */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public List<PagoDeFamilia> pagosDeFamilia(Long familiaId, LocalDate desde, LocalDate hasta) {
		exigirRango(desde, hasta);
		Objects.requireNonNull(familiaId, "familiaId");
		return pagos.deFamiliaEntre(familiaId, desde, hasta).stream()
				.map(p -> new PagoDeFamilia((LocalDate) p[0], (MedioPago) p[1], monto(p[2]), (EstadoPago) p[3],
						p[4] + "-" + String.format("%08d", ((Number) p[5]).intValue()), (String) p[6]))
				.toList();
	}

	private static void exigirRango(LocalDate desde, LocalDate hasta) {
		Objects.requireNonNull(desde, "desde");
		Objects.requireNonNull(hasta, "hasta");
		if (hasta.isBefore(desde)) {
			throw new IllegalArgumentException("El rango termina antes de empezar");
		}
	}

	/** Un SUM de JPQL vuelve {@code null} sin filas y puede volver con otra escala. */
	private static BigDecimal monto(Object valor) {
		return valor == null ? Dinero.CERO : Dinero.normalizar((BigDecimal) valor);
	}
}
