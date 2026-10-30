package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.ActaSupresion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ActaSupresionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Sprint 11, tanda 2 (AP2-8): {@code sistema.auditoria} anota en el acta del colegio en contexto que la verificación
 * completa de la cadena salió íntegra. El DBA solo puede ejecutar el acta con esa anotación de hace 1 hora o menos
 * (trg_acta_supresion_estado; la hora la pone la base).
 */
@Service
@PreAuthorize("hasRole('SISTEMA_AUDITORIA')")
@Transactional
public class CadenaDelActa {

	private final ActaSupresionRepository actas;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public CadenaDelActa(ActaSupresionRepository actas, AuditoriaService auditoria, Clock reloj) {
		this.actas = actas;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	public void anotar(Long actaId, String detalleVerificacion) {
		ActaSupresion acta = actas.bloquear(actaId).orElseThrow(() -> new RecursoNoEncontradoException("Acta no encontrada"));
		acta.cadenaVerificada(LocalDateTime.now(reloj));
		actas.saveAndFlush(acta);
		auditoria.registrar(AccionAuditoria.ACTA_CADENA_VERIFICADA, "acta_supresion", actaId.toString(), null, "INTEGRA",
				"La verificación completa de la cadena de la bitácora salió íntegra (" + detalleVerificacion + "). El DBA "
						+ "tiene 1 hora para ejecutar el acta.");
	}
}
