package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.pasarela.config.PropiedadesPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoEvento;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.EventoPasarelaRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Alertas de pagos en línea para «Para revisar» (sección 13 del diseño del sprint 4):
 * <ul>
 *   <li>CRÍTICA: orden por revisar por monto o moneda distintos u operación duplicada; contracargo; avisos en ERROR;
 *       órdenes de la pasarela SIMULADA en un entorno que no es dev, test ni piloto.</li>
 *   <li>ATENCIÓN: orden por revisar por un doble pago (cuota ya pagada o saldo cambiado); 20 o más avisos no auténticos
 *       en la última hora.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class AlertasPagosEnLinea implements AlertasRevision {

	static final String MODULO = "Pagos en línea";

	static final String ENLACE = "/pagos-en-linea";

	private static final Set<String> ENTORNOS_SIMULADA = Set.of("dev", "test", "piloto");

	private final OrdenPagoRepository ordenes;

	private final EventoPasarelaRepository eventos;

	private final ContadorAvisosNoAutenticos noAutenticos;

	private final AuditoriaService auditoria;

	private final PropiedadesPasarela propiedades;

	private final Environment entorno;

	private final Clock reloj;

	public AlertasPagosEnLinea(OrdenPagoRepository ordenes, EventoPasarelaRepository eventos,
			ContadorAvisosNoAutenticos noAutenticos, AuditoriaService auditoria, PropiedadesPasarela propiedades,
			Environment entorno, Clock reloj) {
		this.ordenes = ordenes;
		this.eventos = eventos;
		this.noAutenticos = noAutenticos;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
		this.entorno = entorno;
		this.reloj = reloj;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AlertaRevision> alertas() {
		List<AlertaRevision> alertas = new ArrayList<>();
		List<OrdenPago> porRevisar = ordenes.findByEstadoInOrderByIdDesc(EnumSet.of(EstadoOrden.POR_REVISAR));
		List<OrdenPago> criticas = porRevisar.stream().filter(o -> o.getMotivoRevision() != null
				&& o.getMotivoRevision().critico()).toList();
		List<OrdenPago> dobles = porRevisar.stream().filter(o -> !criticas.contains(o)).toList();
		if (!criticas.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, criticas.size() + " pago(s) en línea por revisar por "
					+ "monto, moneda u operación que no coinciden: " + resumen(criticas) + ". No se aplicaron a ninguna "
					+ "cuota.", ENLACE));
		}
		if (!dobles.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, dobles.size() + " pago(s) en línea por revisar (la "
					+ "cuota ya se había pagado o su saldo cambió): " + resumen(dobles) + ". Llama al apoderado y pide "
					+ "devolverlo o aplicarlo a otra cuota.", ENLACE));
		}
		long contracargos = auditoria.contarDesde(AccionAuditoria.CONTRACARGO_RECIBIDO,
				LocalDate.now(reloj).minusDays(30).atStartOfDay());
		if (contracargos > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, contracargos + " contracargo(s) en los últimos 30 días: "
					+ "un apoderado desconoció un pago ante su banco. Revisa la anulación pedida en Aprobaciones.",
					"/aprobaciones"));
		}
		long enError = eventos.countByEstado(EstadoEvento.ERROR);
		if (enError > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, enError + " aviso(s) de la pasarela no se pudieron "
					+ "procesar después de " + propiedades.reintentosAviso() + " intentos. Revisa esos pagos con soporte.",
					ENLACE));
		}
		boolean entornoDePrueba = Arrays.stream(entorno.getActiveProfiles()).anyMatch(ENTORNOS_SIMULADA::contains);
		if (!entornoDePrueba && ordenes.countByProveedor(ProveedorPasarela.SIMULADA) > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "Hay pagos de la pasarela SIMULADA en un entorno que "
					+ "no es de prueba: no son dinero real. Revisa la configuración de inmediato.", ENLACE));
		}
		int falsos = noAutenticos.ultimaHora();
		if (falsos >= propiedades.alertaAvisosNoAutenticosPorHora()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, falsos + " avisos de pasarela no auténticos en la "
					+ "última hora: alguien está enviando avisos falsos (no se registró ningún pago por ellos).", null));
		}
		return alertas;
	}

	private static String resumen(List<OrdenPago> lista) {
		List<String> textos = lista.stream().limit(3).map(o -> o.getFamilia().getNombre() + " "
				+ Dinero.formatear(o.getMontoConfirmado() == null ? o.getMonto() : o.getMontoConfirmado())).toList();
		return String.join(", ", textos) + (lista.size() > 3 ? " y " + (lista.size() - 3) + " más" : "");
	}
}
