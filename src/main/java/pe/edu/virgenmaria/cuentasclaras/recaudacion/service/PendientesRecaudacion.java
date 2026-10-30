package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

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
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLote;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LoteRecaudacionRepository;

/**
 * Sprint 11, tanda 1b (F-D5): los lotes de recaudación bancaria por confirmar (a ciegas), para la bandeja de
 * Aprobaciones. Quien subió el archivo no lo confirma. Se confirma en su pantalla.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class PendientesRecaudacion implements PendientesExternos {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final LoteRecaudacionRepository lotes;

	public PendientesRecaudacion(LoteRecaudacionRepository lotes) {
		this.lotes = lotes;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PendienteExterno> pendientes(String usuario) {
		Set<Rol> roles = PendientesExternos.rolesActuales();
		boolean aprueba = roles.contains(Rol.PROMOTOR) || roles.contains(Rol.DIRECTOR);
		List<PendienteExterno> lista = new ArrayList<>();
		lotes.findByEstadoOrderByIdAsc(EstadoLote.CARGADO).forEach(l -> {
			String enlace = "/recaudacion/lotes/" + l.getId() + "/confirmar";
			if (PendientesExternos.puedeAbrir(enlace)) {
				lista.add(new PendienteExterno("RECAUDACION", "Recaudación " + l.getBanco().etiqueta() + " del "
						+ l.getFechaProceso().format(FECHA), PendientesExternos.detalle("Subido", l.getCreadoPor(),
								l.getCreadoEn()), l.getCreadoPor(), l.getCreadoEn(), enlace,
						aprueba && !usuario.equals(l.getCreadoPor())));
			}
		});
		return lista;
	}

	/** Sprint 11, tanda 2 (F-D5): lo que espera en el colegio en contexto, sin filtro de rol (lo lee el resumen diario). */
	@Override
	@PreAuthorize("hasRole('SISTEMA_PANEL')")
	@Transactional(readOnly = true)
	public long contarPendientesDelColegio() {
		return lotes.findByEstadoOrderByIdAsc(EstadoLote.CARGADO).size();
	}
}
