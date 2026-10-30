package pe.edu.virgenmaria.cuentasclaras.colegio.service;

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
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.FeriadoRepository;

/**
 * Sprint 11, tanda 1b (F-D5): los feriados propuestos, para la bandeja de Aprobaciones. Quien lo propuso no lo aprueba.
 * Se aprueban en «Feriados».
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class PendientesFeriados implements PendientesExternos {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final FeriadoRepository feriados;

	public PendientesFeriados(FeriadoRepository feriados) {
		this.feriados = feriados;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PendienteExterno> pendientes(String usuario) {
		String enlace = "/feriados";
		if (!PendientesExternos.puedeAbrir(enlace)) {
			return List.of();
		}
		Set<Rol> roles = PendientesExternos.rolesActuales();
		boolean aprueba = roles.contains(Rol.PROMOTOR) || roles.contains(Rol.DIRECTOR);
		List<PendienteExterno> lista = new ArrayList<>();
		feriados.findByVigenteTrue().stream().filter(f -> f.isPendiente()).forEach(f -> lista.add(new PendienteExterno(
				"FERIADO", "Feriado del " + f.getFecha().format(FECHA) + ": " + f.getDescripcion(),
				PendientesExternos.detalle("Propuesto", f.getCreadoPor(), f.getCreadoEn()), f.getCreadoPor(), f.getCreadoEn(),
				enlace, aprueba && !usuario.equals(f.getCreadoPor()))));
		return lista;
	}

	/** Sprint 11, tanda 2 (F-D5): lo que espera en el colegio en contexto, sin filtro de rol (lo lee el resumen diario). */
	@Override
	@PreAuthorize("hasRole('SISTEMA_PANEL')")
	@Transactional(readOnly = true)
	public long contarPendientesDelColegio() {
		return feriados.findByVigenteTrue().stream().filter(f -> f.isPendiente()).count();
	}
}
