package pe.edu.virgenmaria.cuentasclaras.caja.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CuentaFamilia;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ResultadoBusqueda;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.RevisionCobro;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.SeleccionCobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.DepositoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ReconteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.DescuentoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.SolicitudDescuentoVista;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ModalidadDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.config.RelojMovible;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Caja de demostración (solo perfil {@code dev}, después de las pensiones), hecha con los mismos servicios y personas
 * que en el colegio:
 * <ul>
 *   <li>el DÍA ANTERIOR (el reloj de desarrollo se mueve un día atrás y vuelve): «caja» (Lucía Ramos) cobra en efectivo
 *       a Sebastián Flores y por Yape a Lucía Flores, cierra cuadrado, «director» aprueba el cierre y ella registra el
 *       depósito; «caja2» (Pedro Huanca) cobra en efectivo a Camila Mendoza y cierra con un FALTANTE de S/ 50 que queda
 *       por aprobar (Promotoría lo ve como alerta crítica);</li>
 *   <li>un descuento por hermanos del 10 % para Valeria Quispe Huamán (diciembre 2026): lo pide «administracion» y lo
 *       aprueba «promotor»;</li>
 *   <li>un cobro en efectivo de «caja» a la familia Rojas Salazar y su pedido de anulación (devolución), pendiente en la
 *       bandeja para que Dirección o Promotoría lo resuelvan.</li>
 * </ul>
 */
@Component
@Profile("dev")
@Order(4)
public class DatosDemoCajaDev implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(DatosDemoCajaDev.class);

	static final String DNI_VALERIA = "80127745";

	static final String DNI_THIAGO = "74018832";

	static final String DNI_SEBASTIAN = "75330981";

	static final String DNI_LUCIA_FLORES = "82345671";

	static final String DNI_CAMILA = "81554203";

	static final BigDecimal FALTANTE = new BigDecimal("50.00");

	private final ServicioDescuentos descuentos;

	private final BandejaAprobaciones bandeja;

	private final ServicioCobro cobro;

	private final ServicioAnulacionPagos anulaciones;

	private final ServicioCierreCaja cierres;

	private final RelojMovible reloj;

	private final String urlBaseDatos;

	private final boolean habilitado;

	public DatosDemoCajaDev(ServicioDescuentos descuentos, BandejaAprobaciones bandeja, ServicioCobro cobro,
			ServicioAnulacionPagos anulaciones, ServicioCierreCaja cierres, RelojMovible reloj,
			@Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.datos-colegio:true}") boolean habilitado) {
		this.descuentos = descuentos;
		this.bandeja = bandeja;
		this.cobro = cobro;
		this.anulaciones = anulaciones;
		this.cierres = cierres;
		this.reloj = reloj;
		this.urlBaseDatos = urlBaseDatos;
		this.habilitado = habilitado;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		crearSiCorresponde();
	}

	/** @return {@code true} si creó los datos */
	boolean crearSiCorresponde() {
		if (!habilitado || urlBaseDatos == null || !urlBaseDatos.startsWith("jdbc:h2:mem:")) {
			return false;
		}
		if (!como("administracion", "ADMINISTRACION", descuentos::lista).isEmpty()) {
			LOG.info("No se crea la caja de demostración: ya hay descuentos.");
			return false;
		}
		try {
			diaAnterior();
			descuentoPorHermanos();
			anulacionPendiente();
			LOG.info("Caja de demostración creada: ayer, una caja cerrada y aprobada y otra con un faltante de S/ 50 por "
					+ "aprobar; hoy, un descuento por hermanos aprobado y una anulación pendiente en la bandeja.");
			return true;
		}
		catch (RuntimeException e) {
			LOG.warn("No se pudo crear la caja de demostración: {}", e.getMessage());
			return false;
		}
	}

	private void descuentoPorHermanos() {
		como("administracion", "ADMINISTRACION", () -> {
			SolicitudDescuentoVista valeria = descuentos.prepararSolicitud(DNI_VALERIA);
			return descuentos.solicitar(new DescuentoRequest(valeria.alumnoId(), TipoDescuento.HERMANOS,
					ModalidadDescuento.PORCENTAJE, new BigDecimal("10"), List.of(valeria.cuotas().getFirst().id()),
					"Descuento por hermanos del reglamento: Mateo y Valeria estudian en el colegio",
					"Reglamento de pensiones 2026, art. 12"));
		});
		Long solicitud = como("promotor", "PROMOTOR", () -> pendiente(TipoSolicitud.DESCUENTO));
		como("promotor", "PROMOTOR", () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
	}

	/** Un día antes: una caja cuadrada, aprobada y depositada, y otra con faltante por aprobar. */
	private void diaAnterior() {
		reloj.mover(Duration.ofDays(-1));
		try {
			BigDecimal efectivo = como("caja", "CAJA", () -> {
				BigDecimal total = cobrar(DNI_SEBASTIAN, "Sebastián", MedioPago.EFECTIVO, null);
				cobrar(DNI_LUCIA_FLORES, "Lucía", MedioPago.YAPE, "YP" + DNI_LUCIA_FLORES);
				cierres.contar(new ConteoRequest(total, null));
				return total;
			});
			Long cierre = como("director", "DIRECTOR", () -> pendiente(TipoSolicitud.CIERRE_CAJA));
			como("director", "DIRECTOR", () -> {
				bandeja.aprobar(cierre, "Cuadró al primer conteo: revisado con la cajera.");
				return null;
			});
			como("caja", "CAJA", () -> {
				var estado = cierres.estado();
				Long caja = estado.porDepositar().getFirst().cajaId();
				return cierres.registrarDeposito(new DepositoRequest(caja, estado.cuentas().getFirst(), "DEP-" + caja,
						estado.hoy(), efectivo, null));
			});
			como("caja2", "CAJA", () -> {
				BigDecimal total = cobrar(DNI_CAMILA, "Camila", MedioPago.EFECTIVO, null);
				BigDecimal contado = total.subtract(FALTANTE);
				cierres.contar(new ConteoRequest(contado, null));
				return cierres.recontar(new ReconteoRequest(contado, null, "Volví a contar dos veces y faltan S/ 50; no "
						+ "sé en qué momento salieron del cajón."));
			});
		}
		finally {
			reloj.mover(Duration.ofDays(1));
		}
	}

	/** Cobra (con la sesión ya iniciada) la primera cuota cobrable de ese alumno y devuelve el total. */
	private BigDecimal cobrar(String dni, String nombre, MedioPago medio, String operacion) {
		ResultadoBusqueda resultado = cobro.buscar(dni).resultados().getFirst();
		CuentaFamilia cuenta = cobro.cuentaDeFamilia(resultado.familiaId());
		Long cuota = cuenta.alumnos().stream().filter(a -> a.nombre().startsWith(nombre))
				.flatMap(a -> a.cuotas().stream()).filter(CuentaFamilia.CuotaPorCobrar::cobrable).findFirst().orElseThrow()
				.id();
		RevisionCobro revision = cobro.revisar(resultado.familiaId(), new SeleccionCobroRequest(List.of(cuota), medio),
				null);
		cobro.cobrar(new CobroRequest(revision.clave(), resultado.familiaId(), revision.cuotaIds(), medio, operacion,
				medio == MedioPago.EFECTIVO ? revision.total() : null, null, revision.total(), TipoComprobante.BOLETA,
				revision.receptorPorDefecto(), null, null));
		return revision.total();
	}

	private void anulacionPendiente() {
		Long pago = como("caja", "CAJA", () -> {
			ResultadoBusqueda thiago = cobro.buscar(DNI_THIAGO).resultados().getFirst();
			CuentaFamilia cuenta = cobro.cuentaDeFamilia(thiago.familiaId());
			Long cuota = cuenta.alumnos().stream().flatMap(a -> a.cuotas().stream()).filter(CuentaFamilia.CuotaPorCobrar::cobrable)
					.findFirst().orElseThrow().id();
			RevisionCobro revision = cobro.revisar(thiago.familiaId(), new SeleccionCobroRequest(List.of(cuota),
					MedioPago.EFECTIVO), null);
			return cobro.cobrar(new CobroRequest(revision.clave(), thiago.familiaId(), revision.cuotaIds(),
					MedioPago.EFECTIVO, null, revision.total(), null, revision.total(), TipoComprobante.BOLETA,
					revision.receptorPorDefecto(), null, null));
		});
		como("caja", "CAJA", () -> {
			anulaciones.solicitarDevolucion(pago, "El apoderado ya había pagado esta pensión por transferencia");
			return null;
		});
	}

	private Long pendiente(TipoSolicitud tipo) {
		return bandeja.bandeja().pendientes().stream().filter(s -> s.tipo().equals(tipo.name())).map(SolicitudVista::id)
				.findFirst().orElseThrow();
	}

	/** Ejecuta como ese usuario del colegio principal (sin transacción abierta: cada servicio abre la suya). */
	private static <T> T como(String usuario, String rol, Supplier<T> operacion) {
		SecurityContext anterior = SecurityContextHolder.getContext();
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(usuario, null,
				List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
		SecurityContextHolder.setContext(contexto);
		try {
			return ContextoColegio.en(DatosDemoDev.COLEGIO_PRINCIPAL, operacion);
		}
		finally {
			SecurityContextHolder.setContext(anterior);
		}
	}
}
