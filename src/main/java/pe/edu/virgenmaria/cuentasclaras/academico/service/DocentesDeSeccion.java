package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Sprint 9, tanda 2: lo mismo que {@code cc_docente_de_seccion} de MySQL, en la aplicación: ¿esta cuenta (por su nombre)
 * es un docente activo con una asignación VIGENTE que da acceso hoy en la sección? La usan la aprobación de una corrección
 * (A13: la pide un docente de la sección) y la reversión (A13: un director que enseña allí no revierte).
 */
@Component
class DocentesDeSeccion {

	private final AsignacionDocenteRepository asignaciones;

	private final UsuarioRepository usuarios;

	private final Clock reloj;

	DocentesDeSeccion(AsignacionDocenteRepository asignaciones, UsuarioRepository usuarios, Clock reloj) {
		this.asignaciones = asignaciones;
		this.usuarios = usuarios;
		this.reloj = reloj;
	}

	boolean es(String nombreUsuario, Long seccionId) {
		LocalDate hoy = LocalDate.now(reloj);
		return usuarios.findByNombreUsuario(nombreUsuario)
				.filter(u -> u.isActivo() && u.getApoderadoId() == null && u.getRoles().contains(Rol.DOCENTE))
				.map(u -> asignaciones.findByUsuarioIdAndEstadoOrderBySeccionIdAsc(u.getId(), EstadoAsignacion.VIGENTE)
						.stream().anyMatch(a -> a.getSeccionId().equals(seccionId) && a.daAcceso(hoy)))
				.orElse(false);
	}
}
