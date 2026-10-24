package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionAreaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.Optional;

/**
 * Sprint 10, tanda 2: lo mismo que {@code cc_docente_de_area} de MySQL, para OTRA cuenta (por su nombre): una asignación
 * VIGENTE de TUTOR o DOCENTE en la sección, sin cobertura, con la cuenta activa y el rol DOCENTE, y el área asignada (o una
 * transversal si es el TUTOR). La usan los manejadores al aprobar (quien pidió sigue calificando esa área) y el borrador.
 */
@Component
class DocentesDeArea {

	private final AsignacionDocenteRepository asignaciones;

	private final AsignacionAreaRepository areas;

	private final UsuarioRepository usuarios;

	DocentesDeArea(AsignacionDocenteRepository asignaciones, AsignacionAreaRepository areas, UsuarioRepository usuarios) {
		this.asignaciones = asignaciones;
		this.areas = areas;
		this.usuarios = usuarios;
	}

	boolean califica(String nombreUsuario, Long seccionId, AreaDelCatalogo area) {
		return personal(nombreUsuario).filter(u -> u.getRoles().contains(Rol.DOCENTE))
				.map(u -> asignaciones.findByUsuarioIdAndEstadoOrderBySeccionIdAsc(u.getId(), EstadoAsignacion.VIGENTE)
						.stream().filter(a -> a.getSeccionId().equals(seccionId) && a.getHasta() == null
								&& a.getTipo() != TipoAsignacion.AUXILIAR)
						.anyMatch(a -> area.transversal() ? a.getTipo() == TipoAsignacion.TUTOR
								: areas.findByAsignacionIdOrderByAreaIdAsc(a.getId()).stream()
										.anyMatch(x -> area.id().equals(x.getAreaId()))))
				.orElse(false);
	}

	boolean tieneRol(String nombreUsuario, Rol rol) {
		return personal(nombreUsuario).map(u -> u.getRoles().contains(rol)).orElse(false);
	}

	private Optional<Usuario> personal(String nombreUsuario) {
		return nombreUsuario == null ? Optional.empty() : usuarios.findByNombreUsuario(nombreUsuario)
				.filter(u -> u.isActivo() && u.getApoderadoId() == null);
	}
}
