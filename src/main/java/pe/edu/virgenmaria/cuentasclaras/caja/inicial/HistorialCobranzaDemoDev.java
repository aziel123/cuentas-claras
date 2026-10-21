package pe.edu.virgenmaria.cuentasclaras.caja.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CuentaFamilia;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.DepositoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.EstadoCierreVista;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ResultadoBusqueda;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.RevisionCobro;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.SeleccionCobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.VerificacionRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioVerificacionBancaria;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.config.RelojMovible;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Rediseño (tablero del inicio): historial de cobros de las últimas semanas, solo en el perfil {@code dev} con H2 en
 * memoria, para que el gráfico de los últimos 30 días tenga forma. Corre AL FINAL (después de la caja, la recaudación y la
 * conciliación de demostración, que no cambian) y solo en días hábiles ANTERIORES a esos: 4 semanas atrás hasta hace 6
 * días. Usa los mismos servicios y personas que en el colegio: «caja» (Lucía Ramos) cobra en efectivo, por Yape o por
 * transferencia, cierra cuadrado, «director» aprueba el cierre, ella deposita el efectivo y «administracion» verifica en
 * el banco el depósito y cada pago digital. Así el historial no agrega alertas: el caso estrella sigue siendo el faltante
 * de S/ 50 de ayer.
 */
@Component
@Profile("dev")
@Order(20)
public class HistorialCobranzaDemoDev implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(HistorialCobranzaDemoDev.class);

	/** Alumnos de la demo, en el orden en que se acercan a pagar (DatosDemoColegioDev). */
	private static final List<String> DNIS = List.of("78451236", "76902114", "79876543", "79215560", "77640391",
			"80127745", "75330981", "81554203", "82345671", "74018832");

	/** Días hacia atrás: del 28 al 6 (de ayer a hace 5 días ya hay datos de las otras demos). */
	private static final int DESDE = 28;

	private static final int HASTA = 6;

	private final ServicioCobro cobro;

	private final ServicioCierreCaja cierres;

	private final BandejaAprobaciones bandeja;

	private final ServicioVerificacionBancaria verificacion;

	private final RelojMovible movible;

	private final java.time.Clock reloj;

	private final PersonaDemo personaDemo;

	private final String urlBaseDatos;

	private final boolean habilitado;

	public HistorialCobranzaDemoDev(ServicioCobro cobro, ServicioCierreCaja cierres, BandejaAprobaciones bandeja,
			ServicioVerificacionBancaria verificacion, RelojMovible movible, java.time.Clock reloj, PersonaDemo personaDemo,
			@Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.datos-colegio:true}") boolean habilitado) {
		this.cobro = cobro;
		this.cierres = cierres;
		this.bandeja = bandeja;
		this.verificacion = verificacion;
		this.movible = movible;
		this.reloj = reloj;
		this.personaDemo = personaDemo;
		this.urlBaseDatos = urlBaseDatos;
		this.habilitado = habilitado;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		if (!habilitado || urlBaseDatos == null || !urlBaseDatos.startsWith("jdbc:h2:mem:")) {
			return;
		}
		if (!personaDemo.existen(DatosDemoDev.COLEGIO_PRINCIPAL, "caja", "director", "administracion")) {
			return;
		}
		int dias = 0;
		int turno = 0;
		for (int atras = DESDE; atras >= HASTA; atras--) {
			LocalDate dia = LocalDate.now(reloj).minusDays(atras);
			if (dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
				continue;
			}
			// De 1 a 3 cobros por día, con más movimiento a inicios de mes (cuando vence la pensión).
			int cobros = dia.getDayOfMonth() <= 7 ? 3 : (atras % 3 == 0 ? 2 : 1);
			movible.mover(Duration.ofDays(-atras));
			try {
				if (unDia(cobros, turno)) {
					dias++;
				}
			}
			catch (RuntimeException e) {
				LOG.warn("Historial de demostración: el {} no se pudo completar ({}).", dia, e.getMessage());
			}
			finally {
				movible.mover(Duration.ofDays(atras));
			}
			turno += cobros;
		}
		LOG.info("Historial de cobranza de demostración: {} días hábiles con cobros, cierres aprobados y verificados.",
				dias);
	}

	/** Un día de caja completo. {@code false} si ya no quedan cuotas por cobrar. */
	private boolean unDia(int cobros, int turno) {
		List<Long> digitales = new ArrayList<>();
		BigDecimal efectivo = como("caja", () -> {
			BigDecimal total = BigDecimal.ZERO.setScale(2);
			for (int i = 0; i < cobros; i++) {
				int n = turno + i;
				MedioPago medio = n % 5 == 1 ? MedioPago.YAPE : n % 5 == 3 ? MedioPago.TRANSFERENCIA : MedioPago.EFECTIVO;
				Cobrado c = cobrar(DNIS.get(n % DNIS.size()), medio, n);
				if (c == null) {
					continue;
				}
				if (medio == MedioPago.EFECTIVO) {
					total = total.add(c.total());
				}
				else {
					digitales.add(c.pagoId());
				}
			}
			return total;
		});
		boolean huboCobros = efectivo.signum() > 0 || !digitales.isEmpty();
		if (!huboCobros) {
			return false;
		}
		como("caja", () -> cierres.contar(new ConteoRequest(efectivo, null)));
		Long cierre = como("director", () -> bandeja.bandeja().pendientes().stream()
				.filter(s -> s.tipo().equals(TipoSolicitud.CIERRE_CAJA.name()) && s.solicitadoPor().equals("caja"))
				.map(SolicitudVista::id).reduce((a, b) -> b).orElseThrow());
		como("director", () -> {
			bandeja.aprobar(cierre, "Cuadró al primer conteo.");
			return null;
		});
		if (efectivo.signum() > 0) {
			String[] voucher = new String[1];
			Long deposito = como("caja", () -> {
				EstadoCierreVista estado = cierres.estado();
				EstadoCierreVista.CajaPorDepositar caja = estado.porDepositar().stream()
						.filter(c -> c.fecha().equals(LocalDate.now(reloj))).findFirst().orElseThrow();
				voucher[0] = "DEP-" + caja.cajaId();
				return cierres.registrarDeposito(new DepositoRequest(caja.cajaId(), estado.cuentas().getFirst(),
						voucher[0], LocalDate.now(reloj), efectivo, null));
			});
			como("administracion", () -> {
				verificacion.verificarDeposito(deposito, VerificacionRequest.delBanco(voucher[0], LocalDate.now(reloj),
						efectivo));
				return null;
			});
		}
		for (Long pago : digitales) {
			como("administracion", () -> {
				verificacion.verificarPago(pago, VerificacionRequest.delBanco(operacion(pago), LocalDate.now(reloj),
						montoDe(pago)));
				return null;
			});
		}
		return true;
	}

	private record Cobrado(Long pagoId, BigDecimal total, String operacion) {
	}

	private final java.util.Map<Long, Cobrado> cobrados = new java.util.HashMap<>();

	/** Cobra la cuota más antigua por cobrar del alumno; {@code null} si no le queda ninguna. */
	private Cobrado cobrar(String dni, MedioPago medio, int n) {
		ResultadoBusqueda resultado = cobro.buscar(dni).resultados().getFirst();
		CuentaFamilia cuenta = cobro.cuentaDeFamilia(resultado.familiaId());
		Long cuota = cuenta.alumnos().stream().flatMap(a -> a.cuotas().stream())
				.filter(CuentaFamilia.CuotaPorCobrar::cobrable).map(CuentaFamilia.CuotaPorCobrar::id).findFirst()
				.orElse(null);
		if (cuota == null) {
			return null;
		}
		RevisionCobro revision = cobro.revisar(resultado.familiaId(), new SeleccionCobroRequest(List.of(cuota), medio),
				null);
		String operacion = medio == MedioPago.EFECTIVO ? null
				: (medio == MedioPago.YAPE ? "YP" : "TR") + String.format("%06d", 410000 + n);
		Long pago = cobro.cobrar(new CobroRequest(revision.clave(), resultado.familiaId(), revision.cuotaIds(), medio,
				operacion, medio == MedioPago.EFECTIVO ? revision.total() : null, null, revision.total(),
				TipoComprobante.BOLETA, revision.receptorPorDefecto(), null, null));
		Cobrado c = new Cobrado(pago, revision.total(), operacion);
		cobrados.put(pago, c);
		return c;
	}

	private String operacion(Long pago) {
		return cobrados.get(pago).operacion();
	}

	private BigDecimal montoDe(Long pago) {
		return cobrados.get(pago).total();
	}

	private <T> T como(String usuario, Supplier<T> operacion) {
		return personaDemo.como(DatosDemoDev.COLEGIO_PRINCIPAL, usuario, operacion);
	}
}
