package pe.edu.virgenmaria.cuentasclaras.cobranza.inicial;

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
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PlanRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.ResumenPensiones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConfiguracionPlan;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Pensiones de demostración (solo perfil {@code dev}, después de {@code DatosDemoColegioDev}), creadas con los mismos
 * servicios que usa la aplicación, como si las hicieran las personas:
 * <ul>
 *   <li>Planes 2026 (cobra desde el 01/12/2026: lo anterior es saldo inicial) y 2027: los propone
 *       «administracion» y los aprueba «director». Inicial S/ 380, Primaria S/ 450, Secundaria S/ 480; matrícula
 *       S/ 350. Al aprobarse, se generan los cronogramas de las matrículas 2026.</li>
 *   <li>Un lote de saldo inicial 2026 (deudas al 30/09/2026) que cuadra y está ENVIADO por «administracion», sin
 *       confirmar: para que Promotoría lo confirme en la demostración.</li>
 * </ul>
 * Los montos son de ejemplo, no los del colegio.
 */
@Component
@Profile("dev")
@Order(3)
public class DatosDemoPensionesDev implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(DatosDemoPensionesDev.class);

	static final BigDecimal MATRICULA = new BigDecimal("350.00");

	static final Map<Nivel, BigDecimal> PENSIONES = Map.of(Nivel.INICIAL, new BigDecimal("380.00"), Nivel.PRIMARIA,
			new BigDecimal("450.00"), Nivel.SECUNDARIA, new BigDecimal("480.00"));

	static final LocalDate COBRO_DESDE_2026 = LocalDate.of(2026, 12, 1);

	/** DNI del alumno, mes de la pensión adeudada y monto. Todos deben setiembre; algunos también agosto. */
	record DeudaDemo(String dni, int mes, String monto) {
	}

	static final List<DeudaDemo> DEUDAS = List.of(
			new DeudaDemo("78451236", 8, "450.00"), new DeudaDemo("78451236", 9, "450.00"),
			new DeudaDemo("80127745", 9, "450.00"),
			new DeudaDemo("75330981", 9, "480.00"),
			new DeudaDemo("82345671", 9, "380.00"),
			new DeudaDemo("81554203", 8, "380.00"), new DeudaDemo("81554203", 9, "380.00"),
			new DeudaDemo("76902114", 9, "480.00"),
			new DeudaDemo("79876543", 9, "450.00"),
			new DeudaDemo("79215560", 9, "450.00"),
			new DeudaDemo("74018832", 9, "480.00"),
			new DeudaDemo("77640391", 9, "450.00"));

	private final ServicioPlanesPension planes;

	private final ServicioSaldoInicial saldoInicial;

	private final Clock reloj;

	private final String urlBaseDatos;

	private final boolean habilitado;

	public DatosDemoPensionesDev(ServicioPlanesPension planes, ServicioSaldoInicial saldoInicial, Clock reloj,
			@Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.datos-colegio:true}") boolean habilitado) {
		this.planes = planes;
		this.saldoInicial = saldoInicial;
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
		ResumenPensiones resumen = como("administracion", "ADMINISTRACION", () -> planes.resumen(null));
		if (resumen.anio() == null || resumen.niveles().stream().anyMatch(n -> n.vigente() != null
				|| !n.borradores().isEmpty())) {
			LOG.info("No se crean pensiones de demostración: no hay años o ya hay planes.");
			return false;
		}
		for (AnioOpcion anio : resumen.anios()) {
			if (anio.anio() == 2026 || anio.anio() == 2027) {
				crearPlanes(anio);
			}
		}
		resumen.anios().stream().filter(a -> a.anio() == 2026).findFirst().ifPresent(this::crearLote);
		LOG.info("Pensiones de demostración creadas: planes 2026 y 2027 aprobados y un lote de saldo inicial enviado.");
		return true;
	}

	private void crearPlanes(AnioOpcion anio) {
		for (Nivel nivel : Nivel.values()) {
			ConfiguracionPlan porDefecto = ConfiguracionPlan.porDefecto(anio.anio(), MATRICULA, PENSIONES.get(nivel));
			PlanRequest solicitud = new PlanRequest(porDefecto.montoMatricula(), porDefecto.vencimientoMatricula(),
					porDefecto.montoPension(), porDefecto.vencimientos(), anio.anio() == 2026 ? COBRO_DESDE_2026 : null);
			Long id = como("administracion", "ADMINISTRACION", () -> {
				Long creado = planes.crearBorrador(anio.id(), nivel, solicitud);
				planes.enviar(creado);
				return creado;
			});
			como("director", "DIRECTOR", () -> planes.aprobar(id, planes.obtener(id).version()));
		}
	}

	private void crearLote(AnioOpcion anio) {
		LocalDate corte = LocalDate.of(2026, 9, 30);
		LocalDate hoy = LocalDate.now(reloj);
		BigDecimal total = DEUDAS.stream().map(d -> new BigDecimal(d.monto())).reduce(BigDecimal.ZERO, BigDecimal::add);
		como("administracion", "ADMINISTRACION", () -> {
			Long lote = saldoInicial.crearLote(new LoteRequest(anio.id(), corte.isAfter(hoy) ? hoy : corte,
					"Informe del contador N.° 014-2026 (ejemplo)", total));
			for (DeudaDemo deuda : DEUDAS) {
				saldoInicial.agregarLinea(lote, new LineaSaldoRequest(deuda.dni(), ConceptoSaldo.PENSION, 2026,
						deuda.mes(), null, new BigDecimal(deuda.monto()), null));
			}
			saldoInicial.enviar(lote);
			return lote;
		});
	}

	/** Ejecuta como ese usuario del colegio principal (sin transacción abierta: cada servicio abre la suya). */
	private static <T> T como(String usuario, String rol, java.util.function.Supplier<T> operacion) {
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
