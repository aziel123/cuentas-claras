package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoCorregido;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoDetalle;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.FichaFamilia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosApoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Familias y apoderados.
 * <ul>
 *   <li>Lectura: Promotoría, Dirección y Administración. Escritura: Dirección y Administración.</li>
 *   <li>Corregir un apoderado exige motivo; si cambia su celular o correo, la bitácora lo resalta.</li>
 *   <li>Un apoderado no se borra: se desactiva, y no si es responsable de pago de un alumno activo.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioFamilias {

	private final FamiliaRepository familias;

	private final ApoderadoRepository apoderados;

	private final AlumnoRepository alumnos;

	private final VistasAlumnos vistas;

	private final RegistroAlumnos registro;

	ServicioFamilias(FamiliaRepository familias, ApoderadoRepository apoderados, AlumnoRepository alumnos,
			VistasAlumnos vistas, RegistroAlumnos registro) {
		this.familias = familias;
		this.apoderados = apoderados;
		this.alumnos = alumnos;
		this.vistas = vistas;
		this.registro = registro;
	}

	@Transactional(readOnly = true)
	public FichaFamilia obtener(Long id) {
		Familia familia = buscarFamilia(id);
		List<Alumno> deLaFamilia = alumnos.findByFamiliaIdOrderByFechaNacimientoAsc(id);
		List<Apoderado> suyos = apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(id);
		return new FichaFamilia(familia.getId(), familia.getNombre(),
				suyos.stream().map(a -> VistasAlumnos.apoderado(a, deLaFamilia)).toList(),
				deLaFamilia.stream().map(vistas::hermano).toList(),
				vistas.pendientes("apoderado", suyos.stream().map(Apoderado::getId).toList()));
	}

	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional(readOnly = true)
	public ApoderadoDetalle obtenerApoderado(Long id) {
		Apoderado a = buscarApoderado(id);
		return new ApoderadoDetalle(a.getId(), a.nombreCompleto(), a.getFamilia().getId(), a.getFamilia().getNombre(),
				a.isActivo(), new ApoderadoRequest(a.getDocumento().tipo(), a.getDocumento().numero(),
						a.getApellidoPaterno(), a.getApellidoMaterno(), a.getNombres(), a.getParentesco(),
						a.getTelefonoWhatsapp(), a.getCorreo(), ""));
	}

	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional
	public Long agregarApoderado(Long familiaId, ApoderadoRequest solicitud) {
		Familia familia = buscarFamilia(familiaId);
		return registro.registrarApoderado(familia, datos(solicitud)).getId();
	}

	/**
	 * Corrige nombres, documento y parentesco al momento; el celular o el correo quedan como solicitud que aprueba
	 * otra persona (auditoría A4).
	 */
	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional
	public ApoderadoCorregido actualizarApoderado(Long apoderadoId, ApoderadoRequest solicitud) {
		Apoderado apoderado = buscarApoderado(apoderadoId);
		String motivo = Motivo.exigir(solicitud.motivo());
		if (!apoderado.isActivo()) {
			throw new ReglaNegocioException(apoderado.nombreCompleto() + " está desactivado: no se corrigen sus datos.");
		}
		CorreccionApoderado correccion = registro.actualizarApoderado(apoderado, datos(solicitud), motivo);
		if (correccion.sinCambios()) {
			throw new ReglaNegocioException("No cambiaste ningún dato.");
		}
		return new ApoderadoCorregido(apoderado.getFamilia().getId(), correccion.datosCorregidos(),
				correccion.contactoSolicitado());
	}

	/** @return id de la familia del apoderado */
	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional
	public Long desactivarApoderado(Long apoderadoId, String motivo) {
		Apoderado apoderado = buscarApoderado(apoderadoId);
		String texto = Motivo.exigir(motivo);
		if (alumnos.existsByResponsablePagoIdAndEstado(apoderadoId, EstadoAlumno.ACTIVO)) {
			String de = alumnos.findByResponsablePagoIdOrderByFechaNacimientoAsc(apoderadoId).stream()
					.filter(Alumno::activo).map(Alumno::nombreCompleto).collect(Collectors.joining(", "));
			throw new ReglaNegocioException(apoderado.nombreCompleto() + " es responsable de pago de " + de
					+ ". Primero cambia el responsable de pago desde la ficha del alumno.");
		}
		registro.desactivarApoderado(apoderado, texto);
		return apoderado.getFamilia().getId();
	}

	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional
	public void renombrar(Long familiaId, String nombre) {
		Familia familia = buscarFamilia(familiaId);
		if (!registro.renombrarFamilia(familia, nombre)) {
			throw new ReglaNegocioException("La familia ya se llama así.");
		}
	}

	private static DatosApoderado datos(ApoderadoRequest s) {
		return ServicioAlumnos.datosApoderado(s.tipoDocumento(), s.numeroDocumento(), s.apellidoPaterno(),
				s.apellidoMaterno(), s.nombres(), s.parentesco(), s.telefonoWhatsapp(), s.correo(), "");
	}

	private Familia buscarFamilia(Long id) {
		return familias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Familia no encontrada"));
	}

	private Apoderado buscarApoderado(Long id) {
		return apoderados.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Apoderado no encontrado"));
	}
}
