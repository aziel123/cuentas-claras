package pe.edu.virgenmaria.cuentasclaras.operacion.service;

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
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoTecnico;

/**
 * Sprint 11, tanda 1b (F-D5): un respaldo con filas faltantes de este colegio sin resolver, para la bandeja de
 * Aprobaciones. Lo resuelve una persona de Promotoría, con su motivo, en el estado técnico del panel.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class PendientesRespaldo implements PendientesExternos {

	private final EstadoTecnico estado;

	public PendientesRespaldo(EstadoTecnico estado) {
		this.estado = estado;
	}

	@Override
	public List<PendienteExterno> pendientes(String usuario) {
		String enlace = "/panel/sistema";
		if (!PendientesExternos.puedeAbrir(enlace)) {
			return List.of();
		}
		EstadoRespaldo respaldo = estado.respaldo(ContextoColegio.actual());
		if (respaldo.porResolverId() == null) {
			return List.of();
		}
		Set<Rol> roles = PendientesExternos.rolesActuales();
		List<PendienteExterno> lista = new ArrayList<>();
		lista.add(new PendienteExterno("RESPALDO", "Respaldo con filas faltantes", "Archivo "
				+ respaldo.porResolverArchivo(), "sistema", respaldo.fin(), enlace, roles.contains(Rol.PROMOTOR)));
		return lista;
	}

	/**
	 * Sprint 11, tanda 2 (F-D5): el respaldo con filas faltantes no es una aprobación (el resumen diario lo informa en sus
	 * alertas): no suma a las pendientes. trg_resumen_diario_registro cuenta lo mismo.
	 */
	@Override
	@PreAuthorize("hasRole('SISTEMA_PANEL')")
	public long contarPendientesDelColegio() {
		return 0;
	}
}
