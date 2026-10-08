package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;

import java.time.Instant;
import java.util.List;

import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;

/**
 * Semilla conocida del panel (sprint 6, tanda 1). Sobre {@link EscenarioCaja} (cuotas 2027 de Primaria: matrícula
 * S/ 350 al 28/02, pensión S/ 450 desde el 31/03), el jueves 15/04/2027 a las 08:00 de Lima:
 * <ul>
 *   <li>Mateo paga su matrícula en efectivo (S/ 350) y su pensión de marzo por Yape (S/ 450): vigentes, S/ 800;</li>
 *   <li>Valeria paga su matrícula en efectivo (S/ 350) y ese pago se ANULA (devolución aprobada por Dirección);</li>
 *   <li>Dirección aprueba un descuento del 10 % a la pensión de abril de Valeria (S/ 45);</li>
 *   <li>quedan vencidas la matrícula y marzo de Valeria y de Sebastián: S/ 1,600 de 2 familias, la más antigua del
 *       28/02 (46 días: tramo de 31 a 60).</li>
 * </ul>
 * Deja la sesión limpia y el reloj en ese día: quien lo use debe volverlo a {@link ConfiguracionRelojAjustable#INICIO}.
 */
public final class EscenarioPanel {

	/** Jueves 15/04/2027, 08:00 en Lima. */
	public static final Instant DIA = Instant.parse("2027-04-15T13:00:00Z");

	public record Datos(EscenarioCaja.Familias f, Long pagoEfectivo, Long pagoYape, Long pagoAnulado, Long descuento) {
	}

	private EscenarioPanel() {
	}

	public static Datos preparar(ServicioEstructura estructura, ServicioAlumnos alumnos, ServicioPlanesPension planes,
			ServicioCobro cobro, ServicioAnulacionPagos anulaciones, ServicioDescuentos descuentos,
			BandejaAprobaciones bandeja, RelojAjustable reloj, JdbcTemplate jdbc) {
		EscenarioCaja.Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		reloj.fijar(DIA);
		EscenarioCobranza.como(EscenarioCaja.CAJA);
		Long efectivo = cobro.cobrar(EscenarioCaja.efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "MAT-2027")),
				"350.00", "350.00"));
		Long yape = cobro.cobrar(EscenarioCaja.digital(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")),
				MedioPago.YAPE, "YP550011", "450.00"));
		Long anulado = cobro.cobrar(EscenarioCaja.efectivo(f.quispe(), List.of(cuota(jdbc, f.valeria(), "MAT-2027")),
				"350.00", "400.00"));
		anulaciones.solicitarDevolucion(anulado, EscenarioAprobaciones.MOTIVO_ANULACION);
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "pago", anulado);
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long descuento = descuentos.solicitar(EscenarioAprobaciones.descuento(f.valeria(), TipoDescuento.HERMANOS, "10",
				List.of(cuota(jdbc, f.valeria(), "PEN-2027-04"))));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "descuento", descuento);
		SecurityContextHolder.clearContext();
		return new Datos(f, efectivo, yape, anulado, descuento);
	}
}
