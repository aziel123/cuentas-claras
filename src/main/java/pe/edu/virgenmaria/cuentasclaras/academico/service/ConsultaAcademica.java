package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AlumnoDeSeccion;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.InicioAcademico;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ListaSeccion;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionDelColegio;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sprint 9, tanda 1 (sección 3.3, decisión 142): lo que ven el docente y Dirección en {@code /academico}.
 * <ul>
 *   <li>El docente: sus secciones (asignación VIGENTE) y, en cada una, el número de orden y el nombre de sus alumnos. Nada
 *       más: ni documento, ni fecha de nacimiento, ni apoderados, ni contactos, ni deudas o pagos (los DTO no tienen esos
 *       campos: {@code DtoDocenteSinDatosTest}). Una sección ajena o de otro colegio responde 404.</li>
 *   <li>Dirección: todas las secciones del año, con su tutor, y la lista de cualquiera de ellas.</li>
 *   <li>La lista incluye las matrículas ACTIVAS y RESERVADAS sin distinguirlas (INDECOPI: una reservada es la que aún no
 *       pagó su matrícula; el docente no debe deducir quién debe). Cada vez que se abre queda en «Quién vio datos
 *       personales» como {@code LISTA_SECCION}, con la cantidad (lo anota el controlador con {@code @RegistraAcceso}).</li>
 * </ul>
 */
@Service
@PreAuthorize("hasAnyRole('DIRECTOR','DOCENTE')")
public class ConsultaAcademica {

	/** Las matrículas que se ven en la lista, sin distinguirlas (sección 3.3). */
	static final Set<EstadoMatricula> EN_LA_LISTA = EnumSet.of(EstadoMatricula.ACTIVA, EstadoMatricula.RESERVADA);

	private final SeccionesDelDocente seccionesDelDocente;

	private final AsignacionDocenteRepository asignaciones;

	private final SeccionRepository secciones;

	private final AnioEscolarRepository anios;

	private final MatriculaRepository matriculas;

	private final UsuarioRepository usuarios;

	public ConsultaAcademica(SeccionesDelDocente seccionesDelDocente, AsignacionDocenteRepository asignaciones,
			SeccionRepository secciones, AnioEscolarRepository anios, MatriculaRepository matriculas,
			UsuarioRepository usuarios) {
		this.seccionesDelDocente = seccionesDelDocente;
		this.asignaciones = asignaciones;
		this.secciones = secciones;
		this.anios = anios;
		this.matriculas = matriculas;
		this.usuarios = usuarios;
	}

	/** El inicio académico: las secciones del docente y, para Dirección, las del año en curso (o el último abierto). */
	@Transactional(readOnly = true)
	public InicioAcademico inicio() {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		boolean direccion = persona.roles().contains(Rol.DIRECTOR);
		if (!direccion) {
			return new InicioAcademico(false, seccionesDelDocente.mias(), null, List.of());
		}
		Optional<AnioEscolar> anio = anioDeTrabajo();
		List<SeccionDelColegio> delColegio = new ArrayList<>();
		anio.ifPresent(a -> {
			for (Seccion s : secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(a.getId())) {
				List<AsignacionDocente> vigentes = asignaciones.findByAnioEscolarIdOrderBySeccionIdAscIdAsc(a.getId())
						.stream().filter(x -> x.getSeccionId().equals(s.getId()) && x.esVigente()).toList();
				String tutor = vigentes.stream().filter(x -> x.getTipo() == TipoAsignacion.TUTOR).findFirst()
						.map(x -> nombre(x.getUsuarioId())).orElse(null);
				delColegio.add(new SeccionDelColegio(s.getId(), s.etiqueta(), tutor, vigentes.size(), s.isActiva()));
			}
		});
		return new InicioAcademico(true, seccionesDelDocente.mias(), anio.map(AnioEscolar::getAnio).orElse(null),
				delColegio);
	}

	/**
	 * La lista de una sección: Dirección ve cualquiera del colegio; el docente, solo una con su asignación VIGENTE (también
	 * la de auxiliar). Si no, 404. Anota la cantidad para el registro de accesos ({@code LISTA_SECCION}).
	 */
	@Transactional(readOnly = true)
	public ListaSeccion lista(long seccionId) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		Seccion seccion;
		String miRol;
		if (persona.roles().contains(Rol.DIRECTOR)) {
			seccion = secciones.findById(seccionId).orElseThrow(SeccionesDelDocente::noEncontrada);
			miRol = "Dirección";
		}
		else {
			Map.Entry<Seccion, String> suya = seccionesDelDocente.vigentesEn(seccionId, true)
					.orElseThrow(SeccionesDelDocente::noEncontrada);
			seccion = suya.getKey();
			miRol = suya.getValue();
		}
		List<AlumnoDeSeccion> alumnos = new ArrayList<>();
		matriculas.deLaSeccionEnOrden(seccion.getId(), EN_LA_LISTA).forEach(m -> {
			Alumno a = m.getAlumno();
			String apellidos = a.getApellidoMaterno() == null || a.getApellidoMaterno().isBlank() ? a.getApellidoPaterno()
					: a.getApellidoPaterno() + " " + a.getApellidoMaterno();
			alumnos.add(new AlumnoDeSeccion(alumnos.size() + 1, apellidos, a.getNombres()));
		});
		String tutor = asignaciones.findFirstBySeccionIdAndTipoAndEstado(seccion.getId(), TipoAsignacion.TUTOR,
				EstadoAsignacion.VIGENTE).map(x -> nombre(x.getUsuarioId())).orElse(null);
		AccesoMostrado.filas(alumnos.size());
		return new ListaSeccion(seccion.getId(), seccion.etiqueta(), seccion.getAnioEscolar().getAnio(), tutor, miRol,
				alumnos);
	}

	/** El año en curso o, si no hay, el más reciente que no está cerrado. */
	private Optional<AnioEscolar> anioDeTrabajo() {
		return anios.findByEstado(EstadoAnioEscolar.EN_CURSO)
				.or(() -> anios.findAllByOrderByAnioDesc().stream().filter(a -> !a.cerrado()).findFirst());
	}

	private String nombre(Long usuarioId) {
		return usuarios.findById(usuarioId).map(Usuario::getNombreCompleto).orElse("—");
	}
}
