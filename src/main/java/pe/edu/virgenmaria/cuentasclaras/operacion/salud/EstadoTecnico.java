package pe.edu.virgenmaria.cuentasclaras.operacion.salud;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.Task;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.EstadoCadena;
import org.springframework.data.domain.Limit;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.Latidos;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ObservadorLatidos;
import pe.edu.virgenmaria.cuentasclaras.operacion.config.PropiedadesMonitoreo;
import pe.edu.virgenmaria.cuentasclaras.operacion.log.ContadorErrores;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.ComparacionRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.Respaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.repository.RespaldoRepository;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Calcula el estado técnico (sprint 7, sección 10.1): último respaldo, latido de cada proceso programado, errores de hoy
 * por huella, bitácora al día, disco, base y pool de conexiones, y si las alertas técnicas están encendidas. No es un
 * {@code HealthIndicator}: no cambia el estado público de {@code /actuator/health} (un respaldo atrasado no es
 * «aplicación caída»). Solo lee: no escribe en la base.
 */
@Service
public class EstadoTecnico {

	private static final Logger LOG = LoggerFactory.getLogger(EstadoTecnico.class);

	/** Los procesos sin los que se pierde un control antifraude: si se atrasan, la alerta es CRÍTICA. */
	static final Set<String> CRITICOS = Set.of("DespachoMensajes.despachar", "HuellaDiaria.ejecutar",
			"HuellaDiaria.cadaHora", "ResumenDiarioTarea.ejecutar");

	/** Nombres en lenguaje claro para /panel/sistema; uno que no esté aquí se muestra con su clave. */
	static final Map<String, String> NOMBRES = Map.ofEntries(
			Map.entry("DespachoMensajes.despachar", "Envío de mensajes a las familias"),
			Map.entry("HuellaDiaria.ejecutar", "Huella diaria de la bitácora"),
			Map.entry("HuellaDiaria.cadaHora", "Huella por hora de la bitácora"),
			Map.entry("ResumenDiarioTarea.ejecutar", "Resumen del día a Promotoría"),
			Map.entry("AvisosPromotoria.ejecutar", "Avisos al celular de Promotoría"),
			Map.entry("RecalculoResumenes.ejecutar", "Recálculo de los resúmenes"),
			Map.entry("ReintentosComprobantes.reintentar", "Envío de boletas al OSE"),
			Map.entry("ReconsultaNocturna.reconsultar", "Reconsulta nocturna de boletas"),
			Map.entry("Recordatorios.ejecutar", "Recordatorios de pago"),
			Map.entry("AplicadorConciliacion.conciliarPendientes", "Conciliación bancaria"),
			Map.entry("CierresMensuales.ejecutar", "Cierre bancario mensual"),
			Map.entry("ActivacionMatriculas.barrer", "Activación de matrículas"),
			Map.entry("ReservaMatriculas.barrer", "Matrículas reservadas"),
			Map.entry("VencimientoRenovaciones.ejecutar", "Vencimiento de renovaciones"),
			Map.entry("ConsultaOrdenesAbiertas.consultar", "Consulta de pagos en línea"),
			Map.entry("ImportadorLiquidaciones.importarDiario", "Liquidaciones de la pasarela"),
			Map.entry("AplicadorRecaudacion.aplicarPendientes", "Recaudación bancaria"),
			Map.entry("AlertasTecnicas.revisar", "Alertas técnicas"),
			Map.entry("AlertasTecnicas.resumenDiario", "Resumen técnico diario"));

	/** Una tarea por intervalo se da por atrasada si no late en 4 intervalos (2 minutos como mínimo). */
	static final Duration VENTANA_MINIMA = Duration.ofMinutes(2);

	/** Una tarea por cron tiene este margen, desde la hora en que debía correr, para terminar. */
	static final Duration MARGEN_CRON = Duration.ofMinutes(15);

	/** El pool se da por agotado si hubo hilos esperando conexión en dos revisiones separadas por este tiempo o más. */
	static final Duration POOL_AGOTADO = Duration.ofMinutes(1);

	private final RespaldoRepository respaldos;

	private final PropiedadesMonitoreo propiedades;

	private final Latidos latidos;

	private final ObjectProvider<ScheduledTaskHolder> tareas;

	private final ContadorErrores errores;

	private final EstadoCadena cadena;

	private final DataSource fuenteDatos;

	private final ObjectProvider<JavaMailSender> correo;

	private final String remitente;

	private final Clock reloj;

	/** Desde cuándo espera cada pool (por nombre: «app» o «sistema»). */
	private final Map<String, Instant> esperaDesde = new HashMap<>();

	public EstadoTecnico(RespaldoRepository respaldos, PropiedadesMonitoreo propiedades, Latidos latidos,
			ObjectProvider<ScheduledTaskHolder> tareas, ContadorErrores errores, EstadoCadena cadena,
			DataSource fuenteDatos, ObjectProvider<JavaMailSender> correo,
			@Value("${cuentasclaras.mensajeria.correo.remitente:}") String remitente, Clock reloj) {
		this.respaldos = respaldos;
		this.propiedades = propiedades;
		this.latidos = latidos;
		this.tareas = tareas;
		this.errores = errores;
		this.cadena = cadena;
		this.fuenteDatos = fuenteDatos;
		this.correo = correo;
		this.remitente = remitente == null ? "" : remitente.strip();
		this.reloj = reloj;
	}

	/** Todo el estado técnico ahora. */
	public FotoTecnica foto() {
		List<EstadoProceso> procesos = procesos();
		boolean baseResponde = baseResponde();
		return new FotoTecnica(reloj.instant(), baseResponde ? respaldo() : EstadoRespaldo.ninguno(),
				!procesos.isEmpty(), procesos, errores.deHoy(), baseResponde && bitacoraAlDia(), discoLibre(),
				baseResponde, poolAgotado(), alertasEncendidas(), version());
	}

	/** El último respaldo que cuenta. En prod el destino simulado no cuenta (y la base tampoco lo admite). */
	public EstadoRespaldo respaldo() {
		Optional<Respaldo> ultimo = propiedades.aceptarRespaldoSimulado() ? respaldos.findFirstByOrderByIdDesc()
				: respaldos.findFirstByDestinoNotOrderByIdDesc(Respaldo.DESTINO_SIMULADO);
		if (ultimo.isEmpty()) {
			return EstadoRespaldo.ninguno();
		}
		Respaldo r = ultimo.get();
		LocalDateTime limite = LocalDateTime.now(reloj.withZone(ConfiguracionTiempo.ZONA_LIMA))
				.minusHours(propiedades.respaldoMaxHoras());
		// Correcciones del sprint 7 (QA-S7-1): la alerta sigue mientras haya un respaldo con FALTAN_FILAS sin resolver,
		// aunque el último diga IGUAL (un segundo respaldo no borra la evidencia del panel ni del vigilante).
		Optional<Respaldo> pendiente = respaldos.sinResolver(ComparacionRespaldo.FALTAN_FILAS,
				propiedades.aceptarRespaldoSimulado(), Respaldo.DESTINO_SIMULADO, Limit.of(1)).stream().findFirst();
		if (pendiente.isPresent()) {
			Respaldo p = pendiente.get();
			return new EstadoRespaldo(true, r.getFin(), r.getDestino(), r.getArchivo(), ComparacionRespaldo.FALTAN_FILAS,
					p.getDiferencias(), r.getFin().isAfter(limite), p.getId(), p.getArchivo());
		}
		if (r.getComparacion() == ComparacionRespaldo.FALTAN_FILAS) {
			// El último respaldo encontró filas faltantes, pero Promotoría ya lo resolvió con motivo: no es alerta.
			return new EstadoRespaldo(true, r.getFin(), r.getDestino(), r.getArchivo(), ComparacionRespaldo.IGUAL,
					RESUELTA + r.getDiferencias(), r.getFin().isAfter(limite));
		}
		return new EstadoRespaldo(true, r.getFin(), r.getDestino(), r.getArchivo(), r.getComparacion(),
				r.getDiferencias(), r.getFin().isAfter(limite));
	}

	/** Las diferencias de un respaldo con FALTAN_FILAS cuya alerta ya resolvió Promotoría empiezan así. */
	public static final String RESUELTA = "Alerta resuelta por Promotoría: ";

	/** Si las alertas técnicas pueden salir: hay a quién, desde qué remitente y un servidor de correo configurado. */
	public boolean alertasEncendidas() {
		return !propiedades.operadorCorreo().isBlank() && !remitente.isBlank() && correo.getIfAvailable() != null;
	}

	List<EstadoProceso> procesos() {
		Map<String, Instant> ultimos = latidos.ultimos();
		Instant ahora = reloj.instant();
		Instant listo = latidos.arranque().plus(propiedades.gracia());
		List<EstadoProceso> lista = new ArrayList<>();
		tareas.orderedStream().forEach(holder -> {
			for (ScheduledTask programada : holder.getScheduledTasks()) {
				Task tarea = programada.getTask();
				// Spring envuelve la tarea (para seguir su último resultado), pero conserva el texto del método.
				String clave = tarea.getRunnable() instanceof ScheduledMethodRunnable metodo
						? ObservadorLatidos.nombre(metodo.getMethod()) : ObservadorLatidos.nombre(tarea.toString());
				Instant ultimo = ultimos.get(clave);
				boolean atrasado;
				String programacion;
				if (tarea instanceof CronTask cron) {
					programacion = "Según el horario «" + cron.getExpression() + "»";
					atrasado = cronAtrasado(cron.getExpression(), ultimo, ahora, listo);
				}
				else if (tarea instanceof IntervalTask intervalo) {
					programacion = "Cada " + legible(intervalo.getIntervalDuration());
					atrasado = intervaloAtrasado(intervalo.getIntervalDuration(), ultimo, ahora, listo);
				}
				else {
					continue;
				}
				lista.add(new EstadoProceso(clave, NOMBRES.getOrDefault(clave, clave), programacion, ultimo, atrasado,
						CRITICOS.contains(clave)));
			}
		});
		lista.sort(Comparator.comparing((EstadoProceso p) -> !p.atrasado()).thenComparing(p -> !p.critico())
				.thenComparing(EstadoProceso::nombre));
		return lista;
	}

	/** Sin latido en 4 intervalos (2 minutos como mínimo) desde el último éxito o desde el arranque más la gracia. */
	static boolean intervaloAtrasado(Duration intervalo, Instant ultimo, Instant ahora, Instant listo) {
		Duration ventana = intervalo.multipliedBy(4);
		if (ventana.compareTo(VENTANA_MINIMA) < 0) {
			ventana = VENTANA_MINIMA;
		}
		Instant referencia = ultimo != null ? ultimo : listo;
		return ahora.isAfter(referencia.plus(ventana));
	}

	/**
	 * Debía correr (según su cron, en hora de Lima) después del arranque más la gracia y hace más de 15 minutos, y no
	 * terminó bien desde entonces.
	 */
	static boolean cronAtrasado(String expresion, Instant ultimo, Instant ahora, Instant listo) {
		ZonedDateTime previa = previaEjecucion(CronExpression.parse(expresion),
				ahora.minus(MARGEN_CRON).atZone(ConfiguracionTiempo.ZONA_LIMA));
		if (previa == null || !previa.toInstant().isAfter(listo)) {
			return false;
		}
		return ultimo == null || ultimo.isBefore(previa.toInstant());
	}

	/** La última hora de ejecución del cron que no pasa de {@code limite} (en los últimos 8 días). */
	static ZonedDateTime previaEjecucion(CronExpression cron, ZonedDateTime limite) {
		for (Duration atras : List.of(Duration.ofDays(1), Duration.ofDays(8))) {
			ZonedDateTime previa = null;
			ZonedDateTime siguiente = cron.next(limite.minus(atras));
			while (siguiente != null && !siguiente.isAfter(limite)) {
				previa = siguiente;
				siguiente = cron.next(siguiente);
			}
			if (previa != null) {
				return previa;
			}
		}
		return null;
	}

	private boolean bitacoraAlDia() {
		try {
			return cadena.alDia();
		}
		catch (RuntimeException e) {
			LOG.warn("No se pudo revisar el eslabón de la bitácora: {}", e.getClass().getSimpleName());
			return false;
		}
	}

	/**
	 * Correcciones del sprint 7 (QA-S7-3): las DOS conexiones (la de cc_app y la de cc_sistema) deben responder. Si la de
	 * cc_sistema no conecta (clave cambiada, usuario bloqueado), los procesos y el ingreso están caídos aunque las
	 * pantallas lean.
	 */
	private boolean baseResponde() {
		return pools().values().stream().allMatch(EstadoTecnico::responde);
	}

	private static boolean responde(DataSource fuente) {
		try (Connection conexion = fuente.getConnection()) {
			return conexion.isValid(2);
		}
		catch (SQLException e) {
			return false;
		}
	}

	/** Las conexiones que vigilar: con dos usuarios, «app» (cc_app) y «sistema» (cc_sistema); si no, la única. */
	private Map<String, DataSource> pools() {
		if (fuenteDatos instanceof FuenteDatosEnrutada enrutada && enrutada.separadas()) {
			Map<String, DataSource> pools = new LinkedHashMap<>();
			pools.put("app", enrutada.app());
			pools.put("sistema", enrutada.sistema());
			return pools;
		}
		return Map.of("app", fuenteDatos);
	}

	/** QA-S7-3: el pool de cc_sistema (4 conexiones) también se agota; cada pool lleva su propia cuenta. */
	private synchronized boolean poolAgotado() {
		boolean agotado = false;
		for (Map.Entry<String, DataSource> pool : pools().entrySet()) {
			agotado |= poolAgotado(pool.getKey(), pool.getValue());
		}
		return agotado;
	}

	private boolean poolAgotado(String nombre, DataSource fuente) {
		HikariPoolMXBean pool = null;
		try {
			if (fuente.isWrapperFor(HikariDataSource.class)) {
				pool = fuente.unwrap(HikariDataSource.class).getHikariPoolMXBean();
			}
		}
		catch (SQLException e) {
			pool = null;
		}
		if (pool == null || pool.getThreadsAwaitingConnection() == 0) {
			esperaDesde.remove(nombre);
			return false;
		}
		Instant ahora = reloj.instant();
		Instant desde = esperaDesde.computeIfAbsent(nombre, n -> ahora);
		return !ahora.isBefore(desde.plus(POOL_AGOTADO));
	}

	private static Integer discoLibre() {
		File carpeta = new File(".").getAbsoluteFile();
		long total = carpeta.getTotalSpace();
		return total <= 0 ? null : (int) (carpeta.getUsableSpace() * 100 / total);
	}

	private static String version() {
		String version = EstadoTecnico.class.getPackage().getImplementationVersion();
		return version == null ? "desarrollo" : version;
	}

	static String legible(Duration duracion) {
		if (duracion.toHours() > 0 && duracion.toMinutesPart() == 0) {
			return duracion.toHours() + " h";
		}
		if (duracion.toMinutes() > 0 && duracion.toSecondsPart() == 0) {
			return duracion.toMinutes() + " min";
		}
		return duracion.toSeconds() + " s";
	}
}
