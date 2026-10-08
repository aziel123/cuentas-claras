package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoVista;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.HermanoVista;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DocumentoIdentidad;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Telefono;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Armado de las vistas de alumnos y apoderados que comparten {@link ServicioAlumnos} y {@link ServicioFamilias}.
 * Solo lo usan esos servicios, dentro de su transacción y después de su control de permisos.
 */
@Component
class VistasAlumnos {

	private final AnioEscolarRepository anios;

	private final MatriculaRepository matriculas;

	private final RegistroSolicitudes solicitudes;

	VistasAlumnos(AnioEscolarRepository anios, MatriculaRepository matriculas, RegistroSolicitudes solicitudes) {
		this.anios = anios;
		this.matriculas = matriculas;
		this.solicitudes = solicitudes;
	}

	/** Solicitudes pendientes (retiro, responsable, contacto, ingreso tardío) de esas entidades, para la ficha. */
	List<String> pendientes(String entidad, List<Long> ids) {
		return ids.stream().flatMap(id -> solicitudes.pendientesDe(entidad, id).stream()).toList();
	}

	/** «5.° Primaria A · 2026» si tiene matrícula activa en el año en curso; si no, {@code null}. */
	String seccionEnCurso(Alumno alumno) {
		return anios.findByEstado(EstadoAnioEscolar.EN_CURSO)
				.flatMap(anio -> matriculas.findByAlumnoIdAndAnioEscolarId(alumno.getId(), anio.getId()))
				.filter(Matricula::activa)
				.map(m -> m.getSeccion().etiqueta() + " · " + m.getAnioEscolar().getAnio())
				.orElse(null);
	}

	HermanoVista hermano(Alumno a) {
		return new HermanoVista(a.getId(), a.nombreCompleto(), seccionEnCurso(a), a.getEstado(),
				a.getEstado().etiqueta(), a.getResponsablePago().nombreCompleto());
	}

	/**
	 * Auditoría B1: Promotoría solo consulta, así que ve el documento, el celular y el correo enmascarados. Quien
	 * además es de Dirección o Administración (los que corrigen y avisan) los ve completos.
	 */
	static boolean enmascararDatosPersonales() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null) {
			return true;
		}
		Set<String> roles = autenticacion.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.collect(Collectors.toSet());
		return !roles.contains(Rol.DIRECTOR.autoridad()) && !roles.contains(Rol.ADMINISTRACION.autoridad());
	}

	/** El documento como lo puede ver quien consulta («DNI 78451236» o «DNI ****1236»). */
	static String documento(DocumentoIdentidad documento) {
		return enmascararDatosPersonales() ? documento.enmascarado() : documento.texto();
	}

	/** El apoderado y de qué alumnos activos de la familia es responsable de pago. */
	static ApoderadoVista apoderado(Apoderado a, List<Alumno> alumnosDeLaFamilia) {
		List<String> responsableDe = alumnosDeLaFamilia.stream()
				.filter(al -> al.activo() && al.getResponsablePago().getId().equals(a.getId()))
				.map(Alumno::nombreCompleto)
				.toList();
		if (enmascararDatosPersonales()) {
			return new ApoderadoVista(a.getId(), a.nombreCompleto(), a.getDocumento().enmascarado(), a.getParentesco(),
					a.getParentesco().etiqueta(), Enmascarar.telefono(a.getTelefonoWhatsapp()),
					Enmascarar.correo(a.getCorreo()), a.isActivo(), responsableDe, a.getRuc(), a.getRazonSocial(),
					pendiente(a));
		}
		return new ApoderadoVista(a.getId(), a.nombreCompleto(), a.getDocumento().texto(), a.getParentesco(),
				a.getParentesco().etiqueta(), Telefono.formatear(a.getTelefonoWhatsapp()), a.getCorreo(), a.isActivo(),
				responsableDe, a.getRuc(), a.getRazonSocial(), pendiente(a));
	}

	/** S5-A1: tiene un celular o un correo que su titular aún no confirmó (no recibe avisos por ahí). */
	static boolean pendiente(Apoderado a) {
		return a.isActivo() && (a.getTelefonoWhatsapp() != null && !a.telefonoVerificado()
				|| a.getCorreo() != null && !a.correoVerificado());
	}
}
