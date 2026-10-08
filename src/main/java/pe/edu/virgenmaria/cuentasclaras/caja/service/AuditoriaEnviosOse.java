package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.ComprobanteNoCoincideOse;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.EnvioComprobanteResuelto;

/**
 * Registra en la bitácora lo que responde el OSE (comprobantes no depende de auditoría): ACEPTADO, OBSERVADO o RECHAZADO
 * (los dos últimos resaltados) y lo que la reconsulta nocturna encontró distinto (resaltado: alerta crítica). Corre en la
 * misma transacción del envío, con su actor ({@code sistema.ose}).
 */
@Component
public class AuditoriaEnviosOse {

	private final AuditoriaService auditoria;

	public AuditoriaEnviosOse(AuditoriaService auditoria) {
		this.auditoria = auditoria;
	}

	@EventListener
	public void alResolverse(EnvioComprobanteResuelto evento) {
		AccionAuditoria accion = switch (evento.estado()) {
			case ACEPTADO -> AccionAuditoria.COMPROBANTE_ACEPTADO;
			case OBSERVADO -> AccionAuditoria.COMPROBANTE_OBSERVADO;
			case RECHAZADO -> AccionAuditoria.COMPROBANTE_RECHAZADO;
			default -> null;
		};
		if (accion == null) {
			return;
		}
		auditoria.registrar(accion, "comprobante", evento.comprobanteId().toString(), null, evento.estado().name(),
				evento.tipo() + " " + evento.numero() + ": " + evento.estado().etiqueta().toLowerCase(java.util.Locale.ROOT)
						+ (evento.codigoRespuesta() == null ? "" : " (código " + evento.codigoRespuesta() + ")")
						+ (evento.respuesta() == null ? "" : ". Respuesta: " + evento.respuesta()) + ". Intentos: "
						+ evento.intentos() + ".");
	}

	@EventListener
	public void alNoCoincidir(ComprobanteNoCoincideOse evento) {
		auditoria.registrar(AccionAuditoria.COMPROBANTE_NO_COINCIDE_OSE, "comprobante", evento.comprobanteId().toString(),
				evento.estadoSistema(), evento.estadoOse(), "Reconsulta nocturna: " + evento.numero() + " está "
						+ evento.estadoSistema() + " en el sistema y el OSE responde " + evento.estadoOse()
						+ ". Revisa la configuración del OSE.");
	}
}
