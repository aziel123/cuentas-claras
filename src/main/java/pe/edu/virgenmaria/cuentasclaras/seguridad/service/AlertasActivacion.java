package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.EnlaceActivacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.EnlaceActivacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * S4-M2: una cuenta en línea de apoderado activada desde la MISMA IP de quien la creó en los últimos 7 días es una
 * alerta de ATENCIÓN para Promotoría: puede ser que el personal la activara por el apoderado (y conozca su clave).
 * Promotoría lo confirma con el apoderado y, si no fue él, restablece su acceso. Solo lectura.
 */
@Service
@PreAuthorize("hasRole('PROMOTOR')")
public class AlertasActivacion implements AlertasRevision {

	static final String MODULO = "Accesos";

	static final int DIAS = 7;

	private final EnlaceActivacionRepository enlaces;

	private final UsuarioRepository usuarios;

	private final Clock reloj;

	public AlertasActivacion(EnlaceActivacionRepository enlaces, UsuarioRepository usuarios, Clock reloj) {
		this.enlaces = enlaces;
		this.usuarios = usuarios;
		this.reloj = reloj;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AlertaRevision> alertas() {
		List<AlertaRevision> alertas = new ArrayList<>();
		for (EnlaceActivacion e : enlaces.findByUsadoEnAfterOrderByIdDesc(LocalDateTime.now(reloj).minusDays(DIAS))) {
			if (!e.usadoDesdeLaIpDeQuienLoCreo()) {
				continue;
			}
			Usuario usuario = usuarios.findById(e.getUsuarioId()).orElse(null);
			if (usuario == null) {
				continue;
			}
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "La cuenta en línea de " + usuario.getNombreCompleto()
					+ " se activó desde la misma conexión de quien la creó (" + e.getCreadoPor() + "). Confirma con el "
					+ "apoderado que fue él; si no, restablece su acceso desde su familia.",
					"/auditoria?accion=ACCESO_APODERADO_ACTIVADO"));
		}
		return alertas;
	}
}
