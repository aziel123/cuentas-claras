package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

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
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.ExtractoBancarioRepository;

/**
 * Sprint 11, tanda 1b (F-D5): los extractos del banco por confirmar (a ciegas), para la bandeja de Aprobaciones. Quien
 * subió el extracto no lo confirma (la pantalla aplica además la regla de los 30 días tras un cambio de cuenta).
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class PendientesConciliacion implements PendientesExternos {

	private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

	private final ExtractoBancarioRepository extractos;

	public PendientesConciliacion(ExtractoBancarioRepository extractos) {
		this.extractos = extractos;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PendienteExterno> pendientes(String usuario) {
		Set<Rol> roles = PendientesExternos.rolesActuales();
		boolean aprueba = roles.contains(Rol.PROMOTOR) || roles.contains(Rol.DIRECTOR);
		List<PendienteExterno> lista = new ArrayList<>();
		extractos.findByEstadoOrderByIdAsc(EstadoExtracto.CARGADO).forEach(e -> {
			String enlace = "/conciliacion/cuentas/" + e.getCuenta().getId() + "/confirmar";
			if (PendientesExternos.puedeAbrir(enlace)) {
				lista.add(new PendienteExterno("EXTRACTO", "Extracto " + e.getCuenta().getBanco().etiqueta() + " del "
						+ e.getDesde().format(DIA) + " al " + e.getHasta().format(DIA), PendientesExternos.detalle("Subido",
								e.getCreadoPor(), e.getCreadoEn()), e.getCreadoPor(), e.getCreadoEn(), enlace,
						aprueba && !usuario.equals(e.getCreadoPor())));
			}
		});
		return lista;
	}

	/** Sprint 11, tanda 2 (F-D5): lo que espera en el colegio en contexto, sin filtro de rol (lo lee el resumen diario). */
	@Override
	@PreAuthorize("hasRole('SISTEMA_PANEL')")
	@Transactional(readOnly = true)
	public long contarPendientesDelColegio() {
		return extractos.findByEstadoOrderByIdAsc(EstadoExtracto.CARGADO).size();
	}
}
