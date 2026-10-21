package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.api.Consentimientos;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Canal;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConsentimientoAviso;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Finalidad;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.ConsentimientoAvisoRepository;

/**
 * Sprint 9, tanda 2 (decisión 152): el consentimiento vigente (la última fila) para los avisos de asistencia. Sin fila,
 * WhatsApp vale «no» (el opt-in de Meta es explícito) y el correo vale «sí» (salvo oposición). Lo leen la mensajería
 * ({@code sistema.asistencia} al crear el aviso y {@code sistema.mensajeria} al crear su respaldo por correo).
 */
@Service
@PreAuthorize("hasAnyRole('SISTEMA_ASISTENCIA','SISTEMA_MENSAJERIA','APODERADO','DIRECTOR','ADMINISTRACION')")
public class ConsentimientosVigentes implements Consentimientos {

	private final ConsentimientoAvisoRepository consentimientos;

	public ConsentimientosVigentes(ConsentimientoAvisoRepository consentimientos) {
		this.consentimientos = consentimientos;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean recibeAsistencia(long apoderadoId, boolean porWhatsapp) {
		Canal canal = porWhatsapp ? Canal.WHATSAPP : Canal.CORREO;
		return consentimientos.findFirstByApoderadoIdAndFinalidadAndCanalOrderByIdDesc(apoderadoId, Finalidad.ASISTENCIA,
				canal).map(ConsentimientoAviso::isOtorgado).orElse(canal.salvoOposicion());
	}
}
