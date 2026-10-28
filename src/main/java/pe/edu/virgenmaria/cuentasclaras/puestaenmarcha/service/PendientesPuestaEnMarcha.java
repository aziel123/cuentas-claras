package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.api.PendienteExterno;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.api.PendientesExternos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sprint 11, tanda 1b (F-D5): el correo externo de control que propuso el operador, para la bandeja de Aprobaciones. Lo
 * aprueba una persona de Promotoría en la Puesta en marcha.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class PendientesPuestaEnMarcha implements PendientesExternos {

	private final CorreoDeControl correo;

	public PendientesPuestaEnMarcha(CorreoDeControl correo) {
		this.correo = correo;
	}

	@Override
	public List<PendienteExterno> pendientes(String usuario) {
		String enlace = "/puesta-en-marcha";
		if (!PendientesExternos.puedeAbrir(enlace)) {
			return List.of();
		}
		boolean promotoria = PendientesExternos.rolesActuales().contains(Rol.PROMOTOR);
		List<PendienteExterno> lista = new ArrayList<>();
		correo.vista().propuesta().ifPresent(p -> lista.add(new PendienteExterno("CORREO_CONTROL",
				"Correo externo de control", PendientesExternos.detalle("Propuesto", p.propuestoPor(), p.propuestoEn()),
				p.propuestoPor(), p.propuestoEn(), enlace, promotoria && !usuario.equals(p.propuestoPor()))));
		return lista;
	}
}
