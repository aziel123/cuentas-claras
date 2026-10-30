package pe.edu.virgenmaria.cuentasclaras.cobranza.inicial;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PlanRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConfiguracionPlan;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sprint 8, tanda 3 (sección 8.2): las pensiones del colegio de un visitante de la demo, hechas por sus personas con los
 * mismos servicios del colegio (las aprobaciones llevan su firma):
 * <ul>
 *   <li>un plan por nivel del año en curso, que propone Administración y aprueba Dirección. Montos de EJEMPLO: Inicial
 *       S/ 380, Primaria S/ 450, Secundaria S/ 480 y matrícula S/ 350. Cobra desde el mes de hoy: lo anterior es saldo
 *       inicial;</li>
 *   <li>si el año ya pasó por algún mes de clases, un lote de saldo inicial (las pensiones del mes anterior de 12 alumnos y
 *       también la de dos meses antes de 4 de ellos) ENVIADO por Administración, sin confirmar: lo confirma Promotoría en
 *       la demo.</li>
 * </ul>
 * Solo en el perfil {@code demo}.
 */
@Component
@Profile("demo")
public class PensionesDemo {

	public static final BigDecimal MATRICULA = new BigDecimal("350.00");

	public static final Map<Nivel, BigDecimal> PENSIONES = Map.of(Nivel.INICIAL, new BigDecimal("380.00"), Nivel.PRIMARIA,
			new BigDecimal("450.00"), Nivel.SECUNDARIA, new BigDecimal("480.00"));

	/** Un alumno que debe pensiones de antes (su DNI y su nivel). */
	public record Deudor(String dni, Nivel nivel) {
	}

	private final ServicioPlanesPension planes;

	private final ServicioSaldoInicial saldoInicial;

	private final PersonaDemo personas;

	public PensionesDemo(ServicioPlanesPension planes, ServicioSaldoInicial saldoInicial, PersonaDemo personas) {
		this.planes = planes;
		this.saldoInicial = saldoInicial;
		this.personas = personas;
	}

	/**
	 * El día 1 del mes desde el que el sistema genera las pensiones: el mes de hoy (desde marzo; en enero y febrero, marzo).
	 */
	public static LocalDate cobroDesde(LocalDate hoy) {
		LocalDate marzo = LocalDate.of(hoy.getYear(), 3, 1);
		LocalDate mes = hoy.withDayOfMonth(1);
		return mes.isBefore(marzo) ? marzo : mes;
	}

	/** Crea y aprueba los planes del año y envía el lote de saldo inicial (si hay meses anteriores). */
	public void cargar(long colegioId, String administracion, String direccion, int anio, LocalDate hoy,
			List<Deudor> deudores) {
		AnioOpcion opcion = personas.como(colegioId, administracion, () -> planes.resumen(null)).anios().stream()
				.filter(a -> a.anio() == anio).findFirst()
				.orElseThrow(() -> new IllegalStateException("La demo no tiene el año " + anio));
		LocalDate desde = cobroDesde(hoy);
		for (Nivel nivel : Nivel.values()) {
			// Sprint 11, tanda 1b (F-D2): los vencimientos según las fechas de clases del año (el servicio).
			PlanRequest porDefecto = personas.como(colegioId, administracion, () -> planes.propuestaPorDefecto(opcion.id(), nivel));
			PlanRequest solicitud = new PlanRequest(MATRICULA, porDefecto.vencimientoMatricula(),
					PENSIONES.get(nivel), porDefecto.vencimientos(), desde.getMonthValue() > 3 ? desde : null);
			Long id = personas.como(colegioId, administracion, () -> {
				Long creado = planes.crearBorrador(opcion.id(), nivel, solicitud);
				planes.enviar(creado);
				return creado;
			});
			personas.como(colegioId, direccion, () -> planes.aprobar(id, planes.obtener(id).version()));
		}
		if (desde.getMonthValue() > 3) {
			enviarLote(colegioId, administracion, opcion, desde, hoy, deudores);
		}
	}

	private void enviarLote(long colegioId, String administracion, AnioOpcion anio, LocalDate desde, LocalDate hoy,
			List<Deudor> deudores) {
		LocalDate corte = desde.minusDays(1);
		int mesAnterior = corte.getMonthValue();
		List<LineaSaldoRequest> lineas = new ArrayList<>();
		for (int i = 0; i < deudores.size(); i++) {
			Deudor d = deudores.get(i);
			BigDecimal monto = PENSIONES.get(d.nivel());
			lineas.add(new LineaSaldoRequest(d.dni(), ConceptoSaldo.PENSION, anio.anio(), mesAnterior, null, monto, null));
			if (i % 3 == 0 && mesAnterior > 3) {
				lineas.add(new LineaSaldoRequest(d.dni(), ConceptoSaldo.PENSION, anio.anio(), mesAnterior - 1, null, monto,
						null));
			}
		}
		BigDecimal total = lineas.stream().map(LineaSaldoRequest::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
		personas.como(colegioId, administracion, () -> {
			Long lote = saldoInicial.crearLote(new LoteRequest(anio.id(), corte.isAfter(hoy) ? hoy : corte,
					"Informe del contador N.° 014 (ejemplo de la demo)", total));
			for (LineaSaldoRequest linea : lineas) {
				saldoInicial.agregarLinea(lote, linea);
			}
			saldoInicial.enviar(lote);
			return lote;
		});
	}
}
