package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.config.PropiedadesMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.EstadoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ProveedorMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Alertas de la mensajería para «Para revisar» de Promotoría (sprint 5, sección 13):
 * <ul>
 *   <li>CRÍTICA: un pago vigente sin aviso que haya salido a la hora (G2 y G10: la familia no se enteró).</li>
 *   <li>CRÍTICA: un aviso de pago, anulación o descuento FALLIDO en todos sus canales.</li>
 *   <li>CRÍTICA: mensajes SIMULADOS en un entorno que no es dev, test ni piloto (G11).</li>
 *   <li>ATENCIÓN: mensajes PENDIENTES por más de 15 minutos (proveedor caído o conector apagado).</li>
 * </ul>
 */
@Service
@PreAuthorize("hasRole('PROMOTOR')")
@Transactional(readOnly = true)
public class AlertasComunicacion implements AlertasRevision {

	static final String MODULO = "Comunicación";

	private static final Set<String> PERFILES_SIMULADO = Set.of("dev", "test", "piloto");

	private static final Set<EstadoMensaje> SALIERON = EnumSet.of(EstadoMensaje.ENVIADO, EstadoMensaje.ENTREGADO,
			EstadoMensaje.LEIDO);

	private static final Set<TipoMensaje> FINANCIEROS = EnumSet.of(TipoMensaje.PAGO_REGISTRADO, TipoMensaje.PAGO_ANULADO,
			TipoMensaje.DESCUENTO_APROBADO);

	private final MensajeRepository mensajes;

	private final PropiedadesMensajeria propiedades;

	private final Environment entorno;

	private final Clock reloj;

	public AlertasComunicacion(MensajeRepository mensajes, PropiedadesMensajeria propiedades, Environment entorno,
			Clock reloj) {
		this.mensajes = mensajes;
		this.propiedades = propiedades;
		this.entorno = entorno;
		this.reloj = reloj;
	}

	@Override
	public List<AlertaRevision> alertas() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		List<AlertaRevision> alertas = new ArrayList<>();
		List<Long> sinAviso = mensajes.pagosSinAvisoEnviado(ahora.minusDays(7),
				ahora.minusMinutes(propiedades.alertaPagoSinEntregarMinutos()), SALIERON);
		if (!sinAviso.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, sinAviso.size() + " pago(s) sin aviso enviado a la "
					+ "familia después de " + propiedades.alertaPagoSinEntregarMinutos() + " minutos: la familia no se "
					+ "enteró. Revisa la bandeja de envíos.", "/mensajes"));
		}
		List<Mensaje> fallidos = mensajes.fallidosEnTodosSusCanales(FINANCIEROS, ahora.minusDays(7), SALIERON);
		if (!fallidos.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, fallidos.size() + " aviso(s) de pago, anulación o "
					+ "descuento no salieron por ningún canal. Llama a la familia.", "/mensajes"));
		}
		boolean simuladoPermitido = Arrays.stream(entorno.getActiveProfiles()).anyMatch(PERFILES_SIMULADO::contains);
		if (!simuladoPermitido && mensajes.countByProveedor(ProveedorMensajeria.SIMULADO) > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "Hay mensajes SIMULADOS en este entorno: las familias "
					+ "no reciben sus avisos. Avisa al responsable técnico.", "/mensajes"));
		}
		long pendientes = mensajes.countByEstadoAndCreadoEnLessThan(EstadoMensaje.PENDIENTE,
				ahora.minusMinutes(propiedades.alertaPendienteMinutos()));
		if (pendientes > 0) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, pendientes + " mensaje(s) pendientes por más de "
					+ propiedades.alertaPendienteMinutos() + " minutos: el proveedor no responde o el conector está apagado.",
					"/mensajes"));
		}
		return alertas;
	}
}
