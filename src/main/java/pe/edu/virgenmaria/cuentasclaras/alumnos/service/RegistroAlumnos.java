package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosApoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DocumentoIdentidad;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Casos de uso de escritura de alumnos, apoderados, familias y matrículas, cada uno con su auditoría (datos
 * personales enmascarados). Los usan los formularios y (tanda 2) la importación: una sola validación y los mismos
 * eventos.
 * <p>
 * No tiene {@code @PreAuthorize}: solo lo llaman los servicios protegidos de {@code alumnos} (regla ArchUnit).
 * Exige una transacción abierta ({@code MANDATORY}): todo lo que hace un caso de uso se guarda junto o no se guarda.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class RegistroAlumnos {

	private final FamiliaRepository familias;

	private final ApoderadoRepository apoderados;

	private final AlumnoRepository alumnos;

	private final MatriculaRepository matriculas;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public RegistroAlumnos(FamiliaRepository familias, ApoderadoRepository apoderados, AlumnoRepository alumnos,
			MatriculaRepository matriculas, AuditoriaService auditoria, ApplicationEventPublisher eventos, Clock reloj) {
		this.familias = familias;
		this.apoderados = apoderados;
		this.alumnos = alumnos;
		this.matriculas = matriculas;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	public Familia crearFamilia(String nombre) {
		Familia familia = familias.save(Familia.nueva(nombre));
		auditoria.registrar(AccionAuditoria.FAMILIA_CREADA, "familia", familia.getId().toString(), null,
				familia.getNombre(), null);
		return familia;
	}

	public boolean renombrarFamilia(Familia familia, String nombre) {
		String anterior = familia.getNombre();
		if (!familia.renombrar(nombre)) {
			return false;
		}
		auditoria.registrar(AccionAuditoria.FAMILIA_ACTUALIZADA, "familia", familia.getId().toString(), anterior,
				familia.getNombre(), null);
		return true;
	}

	public Apoderado registrarApoderado(Familia familia, DatosApoderado datos) {
		exigirDocumentoLibreApoderado(datos.documento(), null);
		Apoderado apoderado = Apoderado.nuevo(familia, datos);
		guardar(() -> apoderados.saveAndFlush(apoderado), () -> apoderadoRepetido(datos.documento()));
		auditoria.registrar(AccionAuditoria.APODERADO_REGISTRADO, "apoderado", apoderado.getId().toString(), null,
				DescripcionAuditoria.apoderado(apoderado), null);
		return apoderado;
	}

	/**
	 * Corrige los datos del apoderado. Si cambia el celular o el correo, el evento se resalta: es la vía para desviar
	 * los avisos de pago lejos del padre real.
	 *
	 * @return {@code false} si no cambió nada
	 */
	public boolean actualizarApoderado(Apoderado apoderado, DatosApoderado datos, String motivo) {
		exigirDocumentoLibreApoderado(datos.documento(), apoderado.getId());
		DatosApoderado antes = new DatosApoderado(apoderado.getDocumento(), apoderado.getApellidoPaterno(),
				apoderado.getApellidoMaterno(), apoderado.getNombres(), apoderado.getParentesco(),
				apoderado.getTelefonoWhatsapp(), apoderado.getCorreo());
		List<String> campos = apoderado.actualizar(datos);
		if (campos.isEmpty()) {
			return false;
		}
		guardar(() -> apoderados.saveAndFlush(apoderado), () -> apoderadoRepetido(datos.documento()));
		boolean contacto = campos.contains("celular") || campos.contains("correo");
		auditoria.registrar(contacto ? AccionAuditoria.APODERADO_CONTACTO_CAMBIADO : AccionAuditoria.APODERADO_ACTUALIZADO,
				"apoderado", apoderado.getId().toString(), DescripcionAuditoria.camposApoderado(antes, campos),
				DescripcionAuditoria.camposApoderado(datos, campos),
				"Apoderado " + apoderado.nombreCompleto() + ". Cambió: " + String.join(", ", campos) + ". Motivo: "
						+ motivo);
		return true;
	}

	public void desactivarApoderado(Apoderado apoderado, String motivo) {
		apoderado.desactivar();
		auditoria.registrar(AccionAuditoria.APODERADO_DESACTIVADO, "apoderado", apoderado.getId().toString(), "activo",
				"inactivo", "Apoderado " + apoderado.nombreCompleto() + ". Motivo: " + motivo);
	}

	public Alumno registrarAlumno(DatosAlumno datos, Apoderado responsable) {
		exigirDocumentoLibreAlumno(datos.documento(), null);
		Alumno alumno = Alumno.nuevo(datos, responsable);
		guardar(() -> alumnos.saveAndFlush(alumno), () -> alumnoRepetido(datos.documento(), null));
		auditoria.registrar(AccionAuditoria.ALUMNO_REGISTRADO, "alumno", alumno.getId().toString(), null,
				DescripcionAuditoria.alumno(alumno), null);
		return alumno;
	}

	/** @return {@code false} si no cambió nada */
	public boolean actualizarAlumno(Alumno alumno, DatosAlumno datos, String motivo) {
		exigirDocumentoLibreAlumno(datos.documento(), alumno.getId());
		DatosAlumno antes = new DatosAlumno(alumno.getDocumento(), alumno.getApellidoPaterno(),
				alumno.getApellidoMaterno(), alumno.getNombres(), alumno.getFechaNacimiento());
		List<String> campos = alumno.actualizar(datos);
		if (campos.isEmpty()) {
			return false;
		}
		guardar(() -> alumnos.saveAndFlush(alumno), () -> alumnoRepetido(datos.documento(), null));
		auditoria.registrar(AccionAuditoria.ALUMNO_ACTUALIZADO, "alumno", alumno.getId().toString(),
				DescripcionAuditoria.camposAlumno(antes, campos), DescripcionAuditoria.camposAlumno(datos, campos),
				"Alumno " + alumno.nombreCompleto() + ". Cambió: " + String.join(", ", campos) + ". Motivo: " + motivo);
		return true;
	}

	/**
	 * Matricula al alumno en la sección (una matrícula por alumno y año) y publica {@link MatriculaRegistrada} en la
	 * misma transacción.
	 */
	public Matricula matricular(Alumno alumno, Seccion seccion, LocalDate fecha) {
		AnioEscolar anio = seccion.getAnioEscolar();
		if (!alumno.activo()) {
			throw new ReglaNegocioException(alumno.nombreCompleto() + " no está activo: no se puede matricular.");
		}
		if (anio.cerrado()) {
			throw new ReglaNegocioException("El año " + anio.getAnio() + " está cerrado: ya no recibe matrículas.");
		}
		if (!seccion.isActiva()) {
			throw new ReglaNegocioException("La sección " + seccion.etiqueta() + " está desactivada: elige otra.");
		}
		Optional<Matricula> existente = matriculas.findByAlumnoIdAndAnioEscolarId(alumno.getId(), anio.getId());
		if (existente.isPresent()) {
			throw yaMatriculado(alumno, anio, existente.get().getSeccion());
		}
		exigirFechaMatricula(fecha, anio);
		Matricula matricula = Matricula.nueva(alumno, seccion, fecha);
		guardar(() -> matriculas.saveAndFlush(matricula), () -> yaMatriculado(alumno, anio, seccion));
		auditoria.registrar(AccionAuditoria.MATRICULA_REGISTRADA, "matricula", matricula.getId().toString(), null,
				seccion.etiqueta() + " " + anio.getAnio() + "; desde el " + Calendario.formatear(fecha),
				"Alumno " + alumno.nombreCompleto() + ".");
		eventos.publishEvent(new MatriculaRegistrada(matricula.getId()));
		return matricula;
	}

	/** No futura y entre el 01/07 del año anterior y el fin de clases. */
	private void exigirFechaMatricula(LocalDate fecha, AnioEscolar anio) {
		if (fecha == null) {
			throw new ReglaNegocioException("Elige la fecha de matrícula.");
		}
		if (fecha.isAfter(LocalDate.now(reloj))) {
			throw new ReglaNegocioException("La fecha de matrícula no puede ser futura.");
		}
		if (fecha.isBefore(anio.primeraFechaDeMatricula()) || fecha.isAfter(anio.getFinClases())) {
			throw new ReglaNegocioException("La fecha de matrícula de " + anio.getAnio() + " debe estar entre el "
					+ Calendario.formatear(anio.primeraFechaDeMatricula()) + " y el "
					+ Calendario.formatear(anio.getFinClases()) + ".");
		}
	}

	private void exigirDocumentoLibreAlumno(DocumentoIdentidad documento, Long propioId) {
		alumnos.findByDocumentoTipoAndDocumentoNumero(documento.tipo(), documento.numero())
				.filter(a -> !a.getId().equals(propioId))
				.ifPresent(a -> {
					throw alumnoRepetido(documento, a.nombreCompleto());
				});
	}

	private void exigirDocumentoLibreApoderado(DocumentoIdentidad documento, Long propioId) {
		apoderados.findByDocumento(documento)
				.filter(a -> !a.getId().equals(propioId))
				.ifPresent(a -> {
					throw new ReglaNegocioException("Ya hay un apoderado registrado con " + documento.texto() + ": "
							+ a.nombreCompleto() + " (" + a.getFamilia().getNombre() + "). Si es la misma persona, "
							+ "elígela como apoderado ya registrado.");
				});
	}

	/** Guarda y traduce la violación de una restricción única (dos personas a la vez) a un mensaje claro. */
	private static void guardar(Runnable guardado, java.util.function.Supplier<ReglaNegocioException> siRepetido) {
		try {
			guardado.run();
		}
		catch (DataIntegrityViolationException e) {
			throw siRepetido.get();
		}
	}

	private static ReglaNegocioException alumnoRepetido(DocumentoIdentidad documento, String nombre) {
		return new ReglaNegocioException("Ya hay un alumno registrado con " + documento.texto()
				+ (nombre == null ? "" : ": " + nombre) + ". Búscalo en la lista de alumnos.");
	}

	private static ReglaNegocioException apoderadoRepetido(DocumentoIdentidad documento) {
		return new ReglaNegocioException("Ya hay un apoderado registrado con " + documento.texto() + ".");
	}

	private static ReglaNegocioException yaMatriculado(Alumno alumno, AnioEscolar anio, Seccion seccion) {
		return new ReglaNegocioException(alumno.nombreCompleto() + " ya está matriculado en " + anio.getAnio() + " ("
				+ seccion.etiqueta() + "). Para moverlo usa «Cambiar sección».");
	}
}
