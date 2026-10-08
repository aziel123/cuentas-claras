package pe.edu.virgenmaria.cuentasclaras.familias.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.SesionApoderado;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;

/**
 * Preferencias del apoderado en el portal (sprint 5, tanda 3; decisión 45). Solo los RECORDATORIOS de vencimiento se
 * pueden apagar, y solo el propio apoderado (el de la sesión, nunca uno de la URL). Los avisos de pago, anulación y
 * descuento siguen saliendo siempre: son el control antifraude (el padre es el auditor).
 */
@Service
@PreAuthorize("hasRole('APODERADO')")
public class ServicioPreferencias {

	private final SesionApoderado sesion;

	private final ApoderadoRepository apoderados;

	private final AuditoriaService auditoria;

	public ServicioPreferencias(SesionApoderado sesion, ApoderadoRepository apoderados, AuditoriaService auditoria) {
		this.sesion = sesion;
		this.apoderados = apoderados;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public boolean recordatoriosActivos() {
		return sesion.apoderado().isRecordatoriosActivos();
	}

	@Transactional
	public void recordatorios(boolean activos) {
		Apoderado apoderado = sesion.apoderado();
		if (apoderado.isRecordatoriosActivos() == activos) {
			return;
		}
		apoderado.cambiarRecordatorios(activos);
		apoderados.save(apoderado);
		auditoria.registrar(AccionAuditoria.RECORDATORIOS_DESACTIVADOS, "apoderado", apoderado.getId().toString(),
				activos ? "apagados" : "encendidos", activos ? "encendidos" : "apagados",
				"El apoderado " + (activos ? "encendió" : "apagó") + " sus recordatorios de vencimiento desde el portal. "
						+ "Los avisos de pago, anulación y descuento siguen saliendo.");
	}
}
