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

	private final pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.CambioCorreoControlRepository cambios;

	public PendientesPuestaEnMarcha(CorreoDeControl correo,
			pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.CambioCorreoControlRepository cambios) {
		this.correo = correo;
		this.cambios = cambios;
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

	/** Sprint 11, tanda 2 (F-D5): lo que espera en el colegio en contexto, sin filtro de rol (lo lee el resumen diario). */
	@Override
	@PreAuthorize("hasRole('SISTEMA_PANEL')")
	@Transactional(readOnly = true)
	public long contarPendientesDelColegio() {
		return cambios.findFirstByEstado(pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CambioCorreoControl.Estado.PROPUESTO)
				.isPresent() ? 1 : 0;
	}
}
