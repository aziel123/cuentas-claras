package pe.edu.virgenmaria.cuentasclaras.caja.inicial;

import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.BaseH2Local;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.alumnos.inicial.FamiliasAmpliadasDev;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CuentaFamilia;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.DepositoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.EstadoCierreVista;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.RevisionCobro;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.SeleccionCobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.VerificacionRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioVerificacionBancaria;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.CalculadoraCronograma;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.config.RelojMovible;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.DiasHabiles;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.TareasDelDiaDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Rediseño (datos con historia): la cobranza del año desde marzo, solo en el perfil {@code dev} con H2 en memoria, para
 * que los tableros, los reportes y la morosidad tengan forma desde el primer ingreso.
 * <p>
 * Corre DESPUÉS de los planes (que cobran desde marzo) y ANTES de la caja, la recaudación y la conciliación de
 * demostración: así sus historias no cambian y las boletas quedan correlativas con las fechas. Cada día hábil con cobros,
 * con el reloj de desarrollo movido a ese día (como ya hace la caja de demostración con «ayer»):
 * <ol>
 *   <li>la cajera del día («caja» o «caja2», por semanas) cobra en ventanilla, en efectivo, Yape, Plin, transferencia o
 *       tarjeta, por los servicios reales (boleta correlativa, aviso a la familia y bitácora);</li>
 *   <li>cierra cuadrado, «director» aprueba el cierre, la cajera deposita el efectivo y «administracion» verifica en el
 *       banco el depósito y cada pago digital;</li>
 *   <li>el envío de boletas al OSE simulado y de avisos sale ese mismo día (las tareas programadas, llamadas en orden).</li>
 * </ol>
 * Quién paga qué (determinista):
 * <ul>
 *   <li>las familias de las historias pagan hasta el mes pasado; la de este mes queda para las historias de «ayer» y de
 *       la recaudación. Los Quispe Huamán pagan también este mes y el próximo (en su portal quedan S/ 855.00:
 *       diciembre);</li>
 *   <li>las 40 familias ampliadas pagan cada mes entre el 1 y el 12; seis familias (de grados distintos) deben el mes
 *       pasado y dos de ellas también el anterior: la morosidad ronda el 10 %, distinta por grado;</li>
 *   <li>el mes en curso lo pagaron las familias cuyo día de pago ya pasó: el resto aún está a tiempo (vence a fin de
 *       mes).</li>
 * </ul>
 * El historial termina tres días antes de hoy: anteayer tiene el extracto de la conciliación de demostración y ayer, la
 * caja con el faltante de S/ 50 (la primera alerta crítica). No hay recaudación ni pagos en línea históricos: el lote 1
 * de recaudación (S/ 1,855.00) debe ser el primero del colegio.
 */
@Component
@Profile("dev")
@Order(5)
public class HistorialCobranzaDemoDev implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(HistorialCobranzaDemoDev.class);

	private static final int ANIO = 2026;

	/** Primer día de clases y de cobro del plan 2026 de la demo. */
	private static final LocalDate DESDE = LocalDate.of(ANIO, 3, 2);

	/** Familias de las historias (DNI de un hijo), en el orden de DatosDemoColegioDev. */
	static final String QUISPE = "78451236";

	static final List<String> HISTORIAS = List.of(QUISPE, "75330981", "81554203", "76902114", "79215560", "74018832",
			"77640391");

	/** Índices de las familias ampliadas que deben el mes pasado (y las dos primeras, también el anterior). */
	static final List<Integer> MOROSAS = List.of(3, 14, 9, 22, 27, 35);

	private final ServicioCobro cobro;

	private final ServicioCierreCaja cierres;

	private final BandejaAprobaciones bandeja;

	private final ServicioVerificacionBancaria verificacion;

	private final PagoRepository pagos;

	private final ObjectProvider<TareasDelDiaDev> tareas;

	private final DiasHabiles calendario;

	private final RelojMovible movible;

	private final Clock reloj;

	private final PersonaDemo personaDemo;

	private final String urlBaseDatos;

	private final boolean habilitado;

	private final Map<String, Long> familiaDe = new HashMap<>();

	private final Map<Long, Map<String, List<Long>>> cuotasDe = new HashMap<>();

	private int operaciones;

	/** Tiempos (ns) por etapa, para el log: cobrar, cerrar y aprobar, depositar y verificar, y las tareas del día. */
	private final long[] tiempos = new long[4];

	public HistorialCobranzaDemoDev(ServicioCobro cobro, ServicioCierreCaja cierres, BandejaAprobaciones bandeja,
			ServicioVerificacionBancaria verificacion, PagoRepository pagos, ObjectProvider<TareasDelDiaDev> tareas,
			DiasHabiles calendario, RelojMovible movible, Clock reloj, PersonaDemo personaDemo,
			@Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.datos-colegio:true}") boolean habilitado) {
		this.cobro = cobro;
		this.cierres = cierres;
		this.bandeja = bandeja;
		this.verificacion = verificacion;
		this.pagos = pagos;
		this.tareas = tareas;
		this.calendario = calendario;
		this.movible = movible;
		this.reloj = reloj;
		this.personaDemo = personaDemo;
		this.urlBaseDatos = urlBaseDatos;
		this.habilitado = habilitado;
	}

	/** Un cobro planificado: la familia (DNI de un hijo), los meses que paga y cómo. */
	record Evento(String dni, Set<Integer> meses, MedioPago medio) {
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		if (!habilitado || !BaseH2Local.es(urlBaseDatos)
				|| !personaDemo.existen(DatosDemoDev.COLEGIO_PRINCIPAL, "caja", "caja2", "director", "administracion")
				|| ContextoColegio.en(DatosDemoDev.COLEGIO_PRINCIPAL,
						() -> pagos.contarEntre(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1))) > 0) {
			return;
		}
		long inicio = System.nanoTime();
		LocalDate hoy = LocalDate.now(reloj);
		TreeMap<LocalDate, List<Evento>> plan = ContextoColegio.en(DatosDemoDev.COLEGIO_PRINCIPAL, () -> planificar(hoy));
		int dias = 0;
		int cobros = 0;
		for (Map.Entry<LocalDate, List<Evento>> dia : plan.entrySet()) {
			long atras = ChronoUnit.DAYS.between(dia.getKey(), hoy);
			movible.mover(Duration.ofDays(-atras));
			try {
				long t0 = System.nanoTime();
				salirPendientes();
				tiempos[3] += System.nanoTime() - t0;
				int hechos = unDia(dia.getKey(), dia.getValue());
				cobros += hechos;
				dias += hechos > 0 ? 1 : 0;
			}
			catch (RuntimeException e) {
				LOG.warn("Historial de demostración: el {} no se pudo completar ({}).", dia.getKey(), e.getMessage());
			}
			finally {
				movible.mover(Duration.ofDays(atras));
			}
		}
		// Las boletas y avisos del último día salen al día siguiente (dentro del plazo de envío).
		if (!plan.isEmpty()) {
			long atras = ChronoUnit.DAYS.between(plan.lastKey().plusDays(1), hoy);
			movible.mover(Duration.ofDays(-atras));
			try {
				salirPendientes();
			}
			finally {
				movible.mover(Duration.ofDays(atras));
			}
		}
		LOG.info("Historial de cobranza de demostración: {} cobros en {} días hábiles (desde el {}), con sus cierres "
				+ "aprobados, depósitos y pagos digitales verificados ({} s).", cobros, dias, DESDE,
				(System.nanoTime() - inicio) / 1_000_000_000);
		LOG.info("Historial: cobrar {} s, cerrar y aprobar {} s, depositar y verificar {} s, boletas y avisos {} s.",
				tiempos[0] / 1_000_000_000, tiempos[1] / 1_000_000_000, tiempos[2] / 1_000_000_000,
				tiempos[3] / 1_000_000_000);
	}

	/** Qué paga cada familia y cuándo, hasta tres días antes de hoy. */
	TreeMap<LocalDate, List<Evento>> planificar(LocalDate hoy) {
		LocalDate limite = hoy.minusDays(3);
		YearMonth actual = YearMonth.from(hoy);
		TreeMap<LocalDate, List<Evento>> plan = new TreeMap<>();
		// Las familias de las historias: hasta el mes pasado (los Quispe Huamán, también este mes y el próximo).
		for (int f = 0; f < HISTORIAS.size(); f++) {
			String dni = HISTORIAS.get(f);
			List<YearMonth> meses = new ArrayList<>();
			for (YearMonth mes = YearMonth.from(DESDE); mes.isBefore(actual); mes = mes.plusMonths(1)) {
				meses.add(mes);
			}
			programar(plan, limite, actual, dni, f + 100, meses);
			if (dni.equals(QUISPE) && actual.plusMonths(1).getYear() == ANIO) {
				// Pagan pronto en el mes (a más tardar el día límite del historial), para que se vea en su portal.
				LocalDate pronto = actual.atDay(Math.max(1, Math.min(5, limite.getDayOfMonth())));
				agregar(plan, limite, pronto, new Evento(dni, Set.of(actual.getMonthValue(),
						actual.plusMonths(1).getMonthValue()), MedioPago.TRANSFERENCIA));
			}
		}
		// Las familias ampliadas: cada mes hasta el en curso, menos lo que deben las morosas (que este mes aún no pagan).
		for (int f = 0; f < FamiliasAmpliadasDev.FAMILIAS.size(); f++) {
			String dni = FamiliasAmpliadasDev.FAMILIAS.get(f).alumnos().getFirst().dni();
			int morosa = MOROSAS.indexOf(f);
			List<YearMonth> meses = new ArrayList<>();
			for (YearMonth mes = YearMonth.from(DESDE); !mes.isAfter(actual); mes = mes.plusMonths(1)) {
				boolean debe = morosa >= 0 && (mes.equals(actual.minusMonths(1))
						|| (morosa < 2 && mes.equals(actual.minusMonths(2))) || mes.equals(actual));
				if (!debe) {
					meses.add(mes);
				}
			}
			programar(plan, limite, actual, dni, f, meses);
		}
		return plan;
	}

	/**
	 * Los cobros de una familia. Hasta dos meses antes del en curso, muchas familias pagan de dos en dos (marzo y abril
	 * en marzo, mayo y junio en mayo, julio y agosto en julio): es habitual y deja el arranque en un tiempo razonable. El
	 * mes pasado y el en curso, mes a mes, en su día (así la cobranza diaria de los últimos 30 días tiene forma).
	 */
	private void programar(TreeMap<LocalDate, List<Evento>> plan, LocalDate limite, YearMonth actual, String dni,
			int familia, List<YearMonth> meses) {
		YearMonth reciente = actual.minusMonths(1);
		for (int i = 0; i < meses.size(); i++) {
			YearMonth mes = meses.get(i);
			boolean impar = (mes.getMonthValue() - YearMonth.from(DESDE).getMonthValue()) % 2 == 0;
			if (impar && i + 1 < meses.size() && meses.get(i + 1).equals(mes.plusMonths(1))
					&& mes.plusMonths(1).isBefore(reciente)) {
				agregar(plan, limite, mes.atDay(1 + Math.floorMod(familia * 7 + mes.getMonthValue() * 3, 12)),
						new Evento(dni, Set.of(mes.getMonthValue(), mes.getMonthValue() + 1), medio(familia, mes)));
				i++;
			}
			else {
				agregar(plan, limite, dia(mes, familia), new Evento(dni, Set.of(mes.getMonthValue()), medio(familia, mes)));
			}
		}
	}

	private void agregar(TreeMap<LocalDate, List<Evento>> plan, LocalDate limite, LocalDate dia, Evento evento) {
		LocalDate habil = dia;
		while (habil.getDayOfWeek() == DayOfWeek.SATURDAY || habil.getDayOfWeek() == DayOfWeek.SUNDAY
				|| calendario.esFeriado(habil)) {
			habil = habil.plusDays(1);
		}
		if (habil.isBefore(DESDE) || habil.isAfter(limite)) {
			return;
		}
		plan.computeIfAbsent(habil, d -> new ArrayList<>()).add(evento);
	}

	/**
	 * El día del mes en que paga cada familia: tres de cada cinco, en los primeros doce días; las demás, del 13 al 27
	 * (antes de que venza a fin de mes). Así hay cobros casi todos los días hábiles.
	 */
	private static LocalDate dia(YearMonth mes, int familia) {
		int base = Math.floorMod(familia * 7 + mes.getMonthValue() * 3, 12);
		return Math.floorMod(familia + mes.getMonthValue(), 5) < 3 ? mes.atDay(1 + base)
				: mes.atDay(13 + Math.floorMod(familia * 11 + mes.getMonthValue() * 5, 15));
	}

	/** Efectivo 45 %, Yape 20 %, Plin 10 %, transferencia 15 % y tarjeta 10 %, estable por familia y mes. */
	private static MedioPago medio(int familia, YearMonth mes) {
		int h = Math.floorMod(familia * 13 + mes.getMonthValue() * 7, 20);
		return h < 9 ? MedioPago.EFECTIVO : h < 13 ? MedioPago.YAPE : h < 15 ? MedioPago.PLIN
				: h < 18 ? MedioPago.TRANSFERENCIA : MedioPago.TARJETA;
	}

	/** Un día de caja completo. @return cuántos cobros se hicieron */
	private int unDia(LocalDate fecha, List<Evento> eventos) {
		String cajero = fecha.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 2 == 0 ? "caja" : "caja2";
		BigDecimal[] efectivo = { BigDecimal.ZERO.setScale(2) };
		Map<Long, BigDecimal> digitales = new HashMap<>();
		Map<Long, String> operacionDe = new HashMap<>();
		long t0 = System.nanoTime();
		int hechos = como(cajero, () -> {
			int n = 0;
			for (Evento e : eventos) {
				Long familia = familiaDe.computeIfAbsent(e.dni(), d -> cobro.buscar(d).resultados().getFirst().familiaId());
				// Las cuotas de la familia se leen una vez: sus ids no cambian al pagarse otras.
				Map<String, List<Long>> porDescripcion = cuotasDe.computeIfAbsent(familia, id -> {
					Map<String, List<Long>> mapa = new HashMap<>();
					cobro.cuentaDeFamilia(id).alumnos().stream().flatMap(a -> a.cuotas().stream())
							.filter(CuentaFamilia.CuotaPorCobrar::cobrable)
							.forEach(q -> mapa.computeIfAbsent(q.descripcion(), d -> new ArrayList<>()).add(q.id()));
					return mapa;
				});
				List<Long> cuotas = new ArrayList<>();
				e.meses().forEach(m -> cuotas.addAll(porDescripcion.getOrDefault(
						CalculadoraCronograma.descripcionPension(ANIO, m), List.of())));
				if (cuotas.isEmpty()) {
					continue;
				}
				RevisionCobro revision = cobro.revisar(familia, new SeleccionCobroRequest(cuotas, e.medio()), null);
				String operacion = e.medio() == MedioPago.EFECTIVO ? null : prefijo(e.medio())
						+ String.format("%07d", 2_604_000 + (++operaciones));
				Long pago = cobro.cobrar(new CobroRequest(revision.clave(), familia, revision.cuotaIds(), e.medio(),
						operacion, e.medio() == MedioPago.EFECTIVO ? revision.total() : null, null, revision.total(),
						TipoComprobante.BOLETA, revision.receptorPorDefecto(), null, null));
				if (e.medio() == MedioPago.EFECTIVO) {
					efectivo[0] = efectivo[0].add(revision.total());
				}
				else {
					digitales.put(pago, revision.total());
					operacionDe.put(pago, operacion);
				}
				n++;
			}
			if (n > 0) {
				cierres.contar(new ConteoRequest(efectivo[0], null));
			}
			return n;
		});
		tiempos[0] += System.nanoTime() - t0;
		if (hechos == 0) {
			return 0;
		}
		long t1 = System.nanoTime();
		Long cierre = como("director", () -> bandeja.bandeja().pendientes().stream()
				.filter(s -> s.tipo().equals(TipoSolicitud.CIERRE_CAJA.name()) && cajero.equals(s.solicitadoPor()))
				.map(SolicitudVista::id).reduce((a, b) -> b).orElseThrow());
		como("director", () -> {
			bandeja.aprobar(cierre, "Cuadró al primer conteo.");
			return null;
		});
		tiempos[1] += System.nanoTime() - t1;
		long t2 = System.nanoTime();
		if (efectivo[0].signum() > 0) {
			String[] voucher = new String[1];
			Long deposito = como(cajero, () -> {
				EstadoCierreVista estado = cierres.estado();
				EstadoCierreVista.CajaPorDepositar caja = estado.porDepositar().stream()
						.filter(c -> c.fecha().equals(fecha)).findFirst().orElseThrow();
				voucher[0] = "DEP-" + caja.cajaId();
				return cierres.registrarDeposito(new DepositoRequest(caja.cajaId(), estado.cuentas().getFirst(), voucher[0],
						fecha, efectivo[0], null));
			});
			como("administracion", () -> {
				verificacion.verificarDeposito(deposito, VerificacionRequest.delBanco(voucher[0], fecha, efectivo[0]));
				return null;
			});
		}
		como("administracion", () -> {
			digitales.forEach((pago, monto) -> verificacion.verificarPago(pago,
					VerificacionRequest.delBanco(operacionDe.get(pago), fecha, monto)));
			return null;
		});
		tiempos[2] += System.nanoTime() - t2;
		return hechos;
	}

	private static String prefijo(MedioPago medio) {
		return switch (medio) {
			case YAPE -> "YP";
			case PLIN -> "PL";
			case TRANSFERENCIA -> "TR";
			case TARJETA -> "PO";
			default -> "OP";
		};
	}

	/** Lo que las tareas programadas harían ese día: enviar las boletas al OSE (simulado) y los avisos a las familias. */
	private void salirPendientes() {
		tareas.orderedStream().forEach(t -> t.pasar(DatosDemoDev.COLEGIO_PRINCIPAL));
	}

	private <T> T como(String usuario, Supplier<T> operacion) {
		return personaDemo.como(DatosDemoDev.COLEGIO_PRINCIPAL, usuario, operacion);
	}
}
