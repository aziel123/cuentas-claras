package pe.edu.virgenmaria.cuentasclaras.caja.inicial;

import org.springframework.context.annotation.Profile;
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
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.DescuentoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.SolicitudDescuentoVista;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ModalidadDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sprint 8, tanda 3 (sección 8.2): la caja de HOY en el colegio de un visitante de la demo, hecha por sus personas con los
 * mismos servicios del colegio. La cajera es «Lucía Ramos» ({@code prefijo.caja}):
 * <ul>
 *   <li>cobra dos pensiones en efectivo y una por Yape (cada cobro emite su boleta SIMULADA y avisa al apoderado por el
 *       WhatsApp simulado); su caja queda abierta para que el visitante haga el cierre a ciegas;</li>
 *   <li>cobra otra en efectivo y pide su anulación (devolución), que queda en la bandeja de Dirección o Promotoría;</li>
 *   <li>Administración pide un descuento por hermanos y Promotoría lo aprueba.</li>
 * </ul>
 * Los montos son los de los planes de ejemplo. Solo en el perfil {@code demo}.
 */
@Component
@Profile("demo")
public class CajaDemo {

	/** Un alumno al que se cobra: su DNI y su nombre (para elegir su cuota en la cuenta de la familia). */
	public record AlumnoCobro(String dni, String nombres) {
	}

	private final ServicioCobro cobro;

	private final ServicioAnulacionPagos anulaciones;

	private final ServicioDescuentos descuentos;

	private final BandejaAprobaciones bandeja;

	private final PersonaDemo personas;

	public CajaDemo(ServicioCobro cobro, ServicioAnulacionPagos anulaciones, ServicioDescuentos descuentos,
			BandejaAprobaciones bandeja, PersonaDemo personas) {
		this.cobro = cobro;
		this.anulaciones = anulaciones;
		this.descuentos = descuentos;
		this.bandeja = bandeja;
		this.personas = personas;
	}

	/**
	 * @param cobros    tres alumnos: los dos primeros pagan en efectivo y el tercero por Yape
	 * @param anulacion el alumno cuyo cobro se pide anular
	 * @param hermano   el alumno con descuento por hermanos
	 */
	public void cargar(long colegioId, String prefijo, List<AlumnoCobro> cobros, AlumnoCobro anulacion, AlumnoCobro hermano) {
		String caja = prefijo + ".caja";
		como(colegioId, caja, () -> {
			cobrar(cobros.get(0), MedioPago.EFECTIVO, null);
			cobrar(cobros.get(1), MedioPago.EFECTIVO, null);
			cobrar(cobros.get(2), MedioPago.YAPE, "YP" + cobros.get(2).dni());
			return null;
		});
		Long pago = como(colegioId, caja, () -> cobrar(anulacion, MedioPago.EFECTIVO, null));
		como(colegioId, caja, () -> {
			anulaciones.solicitarDevolucion(pago, "El apoderado ya había pagado esta pensión por transferencia (ejemplo)");
			return null;
		});
		como(colegioId, prefijo + ".administracion", () -> {
			SolicitudDescuentoVista vista = descuentos.prepararSolicitud(hermano.dni());
			return descuentos.solicitar(new DescuentoRequest(vista.alumnoId(), TipoDescuento.HERMANOS,
					ModalidadDescuento.PORCENTAJE, new BigDecimal("10"), List.of(vista.cuotas().getFirst().id()),
					"Descuento por hermanos del reglamento: dos hermanos estudian en el colegio (ejemplo)",
					"Reglamento de pensiones, art. 12 (ejemplo)"));
		});
		String promotoria = prefijo + ".promotoria";
		Long solicitud = como(colegioId, promotoria, () -> bandeja.bandeja().pendientes().stream()
				.filter(s -> s.tipo().equals(TipoSolicitud.DESCUENTO.name())).map(SolicitudVista::id).findFirst()
				.orElseThrow());
		como(colegioId, promotoria, () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
	}

	/** Cobra (con la sesión de la cajera ya iniciada) la primera cuota cobrable de ese alumno. @return el pago */
	private Long cobrar(AlumnoCobro alumno, MedioPago medio, String operacion) {
		ResultadoBusqueda resultado = cobro.buscar(alumno.dni()).resultados().getFirst();
		CuentaFamilia cuenta = cobro.cuentaDeFamilia(resultado.familiaId());
		Long cuota = cuenta.alumnos().stream().filter(a -> a.nombre().startsWith(alumno.nombres()))
				.flatMap(a -> a.cuotas().stream()).filter(CuentaFamilia.CuotaPorCobrar::cobrable).findFirst().orElseThrow()
				.id();
		RevisionCobro revision = cobro.revisar(resultado.familiaId(), new SeleccionCobroRequest(List.of(cuota), medio),
				null);
		return cobro.cobrar(new CobroRequest(revision.clave(), resultado.familiaId(), revision.cuotaIds(), medio, operacion,
				medio == MedioPago.EFECTIVO ? revision.total() : null, null, revision.total(), TipoComprobante.BOLETA,
				revision.receptorPorDefecto(), null, null));
	}

	private <T> T como(long colegioId, String usuario, Supplier<T> operacion) {
		return personas.como(colegioId, usuario, operacion);
	}
}
