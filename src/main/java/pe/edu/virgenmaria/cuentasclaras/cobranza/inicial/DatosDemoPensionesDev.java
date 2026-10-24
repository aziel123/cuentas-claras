package pe.edu.virgenmaria.cuentasclaras.cobranza.inicial;

import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.BaseH2Local;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PlanRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.ResumenPensiones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConfiguracionPlan;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Pensiones de demostración (solo perfil {@code dev}, después de {@code DatosDemoColegioDev}), creadas con los mismos
 * servicios que usa la aplicación, como si las hicieran las personas:
 * <ul>
 *   <li>Planes 2026 (cobra las pensiones desde marzo: el colegio usa el sistema desde el inicio de clases; la matrícula,
 *       que venció en febrero, se cobró antes) y 2027: los propone «administracion» y los aprueba «director». Inicial
 *       S/ 380, Primaria S/ 450, Secundaria S/ 480; matrícula S/ 350. Al aprobarse, se generan los cronogramas de las
 *       matrículas 2026 (de marzo a diciembre).</li>
 *   <li>Un lote de saldo inicial 2026 con las matrículas 2026 que quedaron debiendo (corte 28/02/2026) que cuadra y está
 *       ENVIADO por «administracion», sin confirmar: para que Promotoría lo confirme en la demostración.</li>
 * </ul>
 * Los montos son de ejemplo, no los del colegio.
 */
@Component
@Profile("dev")
@Order(4)
public class DatosDemoPensionesDev implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(DatosDemoPensionesDev.class);

	static final BigDecimal MATRICULA = new BigDecimal("350.00");

	static final Map<Nivel, BigDecimal> PENSIONES = Map.of(Nivel.INICIAL, new BigDecimal("380.00"), Nivel.PRIMARIA,
			new BigDecimal("450.00"), Nivel.SECUNDARIA, new BigDecimal("480.00"));

	/**
	 * Rediseño (datos con historia): el plan 2026 cobra las pensiones desde marzo. La matrícula vence el 28/02, antes de
	 * ese día, así que el plan no la genera: la que quedó debiendo entra como saldo inicial.
	 */
	static final LocalDate COBRO_DESDE_2026 = LocalDate.of(2026, 3, 1);

	/** Corte del saldo inicial: el día antes de que el sistema empiece a cobrar. */
	static final LocalDate CORTE_SALDO_INICIAL = LocalDate.of(2026, 2, 28);

	/** DNI del alumno que debe su matrícula 2026 y el monto (seis de los diez alumnos de las historias). */
	record DeudaDemo(String dni, String monto) {
	}

	static final List<DeudaDemo> DEUDAS = List.of(
			new DeudaDemo("75330981", "350.00"),
			new DeudaDemo("81554203", "350.00"),
			new DeudaDemo("76902114", "350.00"),
			new DeudaDemo("79215560", "350.00"),
			new DeudaDemo("74018832", "350.00"),
			new DeudaDemo("77640391", "350.00"));

	private final ServicioPlanesPension planes;

	private final ServicioSaldoInicial saldoInicial;

	private final Clock reloj;

	private final PersonaDemo personaDemo;

	private final String urlBaseDatos;

	private final boolean habilitado;

	public DatosDemoPensionesDev(ServicioPlanesPension planes, ServicioSaldoInicial saldoInicial, Clock reloj,
			PersonaDemo personaDemo, @Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.datos-colegio:true}") boolean habilitado) {
		this.planes = planes;
		this.saldoInicial = saldoInicial;
		this.reloj = reloj;
		this.personaDemo = personaDemo;
		this.urlBaseDatos = urlBaseDatos;
		this.habilitado = habilitado;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		crearSiCorresponde();
	}

	/** @return {@code true} si creó los datos */
	boolean crearSiCorresponde() {
		if (!habilitado || !BaseH2Local.es(urlBaseDatos)) {
			return false;
		}
		if (!personaDemo.existen(DatosDemoDev.COLEGIO_PRINCIPAL, "administracion", "director")) {
			LOG.info("No se crean las pensiones de demostración: faltan las personas de demostración.");
			return false;
		}
		ResumenPensiones resumen = como("administracion", () -> planes.resumen(null));
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
			Long id = como("administracion", () -> {
				Long creado = planes.crearBorrador(anio.id(), nivel, solicitud);
				planes.enviar(creado);
				return creado;
			});
			como("director", () -> planes.aprobar(id, planes.obtener(id).version()));
		}
	}

	private void crearLote(AnioOpcion anio) {
		LocalDate corte = CORTE_SALDO_INICIAL;
		LocalDate hoy = LocalDate.now(reloj);
		BigDecimal total = DEUDAS.stream().map(d -> new BigDecimal(d.monto())).reduce(BigDecimal.ZERO, BigDecimal::add);
		como("administracion", () -> {
			Long lote = saldoInicial.crearLote(new LoteRequest(anio.id(), corte.isAfter(hoy) ? hoy : corte,
					"Informe del contador N.° 014-2026 (ejemplo)", total));
			for (DeudaDemo deuda : DEUDAS) {
				saldoInicial.agregarLinea(lote, new LineaSaldoRequest(deuda.dni(), ConceptoSaldo.MATRICULA, 2026, null,
						null, new BigDecimal(deuda.monto()), CORTE_SALDO_INICIAL));
			}
			saldoInicial.enviar(lote);
			return lote;
		});
	}

	/** Ejecuta como esa persona de la demo del colegio principal, con su sesión (firma sus aprobaciones). */
	private <T> T como(String usuario, Supplier<T> operacion) {
		return personaDemo.como(DatosDemoDev.COLEGIO_PRINCIPAL, usuario, operacion);
	}
}
