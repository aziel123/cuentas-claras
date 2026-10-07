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
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	private final AnioEscolarRepository anios;

	private final AuditoriaService auditoria;

	private final RegistroSolicitudes solicitudes;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public RegistroAlumnos(FamiliaRepository familias, ApoderadoRepository apoderados, AlumnoRepository alumnos,
			MatriculaRepository matriculas, AnioEscolarRepository anios, AuditoriaService auditoria,
			RegistroSolicitudes solicitudes, ApplicationEventPublisher eventos, Clock reloj) {
		this.anios = anios;
		this.solicitudes = solicitudes;
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
	 * Corrige los datos del apoderado. Nombres, documento y parentesco se corrigen al momento; el celular y el correo
	 * (la vía para desviar los avisos de pago lejos del padre real) quedan como solicitud que aprueba otra persona
	 * (auditoría A4).
	 */
	public CorreccionApoderado actualizarApoderado(Apoderado apoderado, DatosApoderado datos, String motivo) {
		exigirDocumentoLibreApoderado(datos.documento(), apoderado.getId());
		String telefonoActual = apoderado.getTelefonoWhatsapp();
		String correoActual = apoderado.getCorreo();
		DatosApoderado antes = new DatosApoderado(apoderado.getDocumento(), apoderado.getApellidoPaterno(),
				apoderado.getApellidoMaterno(), apoderado.getNombres(), apoderado.getParentesco(), telefonoActual,
				correoActual);
		DatosApoderado sinContacto = datos.conContacto(telefonoActual, correoActual);
		List<String> campos = apoderado.actualizar(sinContacto);
		if (!campos.isEmpty()) {
			guardar(() -> apoderados.saveAndFlush(apoderado), () -> apoderadoRepetido(datos.documento()));
			auditoria.registrar(AccionAuditoria.APODERADO_ACTUALIZADO, "apoderado", apoderado.getId().toString(),
					DescripcionAuditoria.camposApoderado(antes, campos),
					DescripcionAuditoria.camposApoderado(sinContacto, campos),
					"Apoderado " + apoderado.nombreCompleto() + ". Cambió: " + String.join(", ", campos) + ". Motivo: "
							+ motivo);
		}
		boolean contacto = datos.cambiaContacto(telefonoActual, correoActual);
		if (contacto) {
			Map<String, String> pedido = new HashMap<>();
			pedido.put("telefonoAnterior", vacioSiNulo(telefonoActual));
			pedido.put("correoAnterior", vacioSiNulo(correoActual));
			pedido.put("telefono", vacioSiNulo(datos.telefonoWhatsapp()));
			pedido.put("correo", vacioSiNulo(datos.correo()));
			solicitudes.crear(TipoSolicitud.CAMBIO_CONTACTO_APODERADO, "apoderado", apoderado.getId(),
					"Contacto de " + apoderado.nombreCompleto() + " (" + apoderado.getFamilia().getNombre() + "): "
							+ DescripcionAuditoria.contactoVisible(telefonoActual, correoActual) + " → "
							+ DescripcionAuditoria.contactoVisible(datos.telefonoWhatsapp(), datos.correo()),
					pedido, motivo);
		}
		return new CorreccionApoderado(!campos.isEmpty(), contacto);
	}

	/**
	 * Aplica el cambio de contacto aprobado (solo lo llama el manejador de la solicitud). Si el contacto cambió desde
	 * que se pidió, no se aplica.
	 */
	public void cambiarContactoAprobado(Apoderado apoderado, Long solicitudId, Map<String, String> pedido, String motivo,
			String solicitante, String aprobador) {
		if (!apoderado.isActivo()) {
			throw new ReglaNegocioException(apoderado.nombreCompleto() + " está desactivado: ya no se cambia su contacto.");
		}
		String telefonoActual = apoderado.getTelefonoWhatsapp();
		String correoActual = apoderado.getCorreo();
		if (!vacioSiNulo(telefonoActual).equals(pedido.get("telefonoAnterior"))
				|| !vacioSiNulo(correoActual).equals(pedido.get("correoAnterior"))) {
			throw new ReglaNegocioException("El contacto de " + apoderado.nombreCompleto() + " cambió desde que se pidió: "
					+ "rechaza esta solicitud y que se pida de nuevo.");
		}
		DatosApoderado actuales = new DatosApoderado(apoderado.getDocumento(), apoderado.getApellidoPaterno(),
				apoderado.getApellidoMaterno(), apoderado.getNombres(), apoderado.getParentesco(), telefonoActual,
				correoActual);
		DatosApoderado nuevos = actuales.conContacto(nuloSiVacio(pedido.get("telefono")), nuloSiVacio(pedido.get("correo")));
		List<String> campos = apoderado.actualizar(nuevos);
		apoderado.registrarSolicitudContacto(solicitudId);
		apoderados.saveAndFlush(apoderado);
		// Sprint 5: la mensajería avisa al contacto ANTERIOR, en esta misma transacción.
		eventos.publishEvent(new ContactoCambiado(apoderado.getId(), solicitudId, telefonoActual, correoActual));
		auditoria.registrar(AccionAuditoria.APODERADO_CONTACTO_CAMBIADO, "apoderado", apoderado.getId().toString(),
				DescripcionAuditoria.camposApoderado(actuales, campos), DescripcionAuditoria.camposApoderado(nuevos, campos),
				"Apoderado " + apoderado.nombreCompleto() + ". Cambió: " + String.join(", ", campos) + ". Pedido por "
						+ solicitante + ", aprobado por " + aprobador + ". Motivo: " + motivo);
	}

	/**
	 * B2: pide registrar o cambiar el RUC (y la razón social) de un apoderado para emitir factura. Lo aprueba otra
	 * persona de Promotoría o Dirección; hasta entonces, en caja solo sale boleta.
	 */
	public void solicitarDatosFacturacion(Apoderado apoderado, String ruc, String razonSocial, String motivo) {
		if (!apoderado.isActivo()) {
			throw new ReglaNegocioException(apoderado.nombreCompleto() + " está desactivado: no se registra su RUC.");
		}
		String numero = ruc == null ? "" : ruc.replaceAll("\\s", "");
		if (!pe.edu.virgenmaria.cuentasclaras.comun.texto.Ruc.valido(numero)) {
			throw new ReglaNegocioException("El RUC no es válido: son 11 dígitos con el dígito verificador de SUNAT.");
		}
		String razon = pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador.limpiar(razonSocial);
		if (razon == null || razon.length() < 3 || razon.length() > 150) {
			throw new ReglaNegocioException("Escribe la razón social (de 3 a 150 caracteres), como figura en SUNAT.");
		}
		if (numero.equals(apoderado.getRuc()) && razon.equals(apoderado.getRazonSocial())) {
			throw new ReglaNegocioException("Esos datos de facturación ya están registrados.");
		}
		Map<String, String> pedido = new HashMap<>();
		pedido.put("ruc", numero);
		pedido.put("razonSocial", razon);
		pedido.put("rucAnterior", vacioSiNulo(apoderado.getRuc()));
		solicitudes.crear(TipoSolicitud.DATOS_FACTURACION, "apoderado", apoderado.getId(), "RUC de "
				+ apoderado.nombreCompleto() + " (" + apoderado.getFamilia().getNombre() + "): "
				+ (apoderado.getRuc() == null ? "sin RUC" : apoderado.getRuc()) + " → " + numero + " · " + razon, pedido,
				motivo);
	}

	/** Aplica los datos de facturación aprobados (solo lo llama su manejador). Si cambiaron desde que se pidió, no. */
	public void aplicarDatosFacturacion(Apoderado apoderado, Map<String, String> pedido, Long solicitudId, String motivo,
			String solicitante, String aprobador) {
		if (!apoderado.isActivo()) {
			throw new ReglaNegocioException(apoderado.nombreCompleto() + " está desactivado: no se registra su RUC.");
		}
		if (!vacioSiNulo(apoderado.getRuc()).equals(pedido.get("rucAnterior"))) {
			throw new ReglaNegocioException("El RUC de " + apoderado.nombreCompleto() + " cambió desde que se pidió: "
					+ "rechaza esta solicitud y que se pida de nuevo.");
		}
		String anterior = apoderado.getRuc() == null ? "sin RUC" : apoderado.getRuc() + " · " + apoderado.getRazonSocial();
		apoderado.registrarFacturacion(pedido.get("ruc"), pedido.get("razonSocial"), solicitudId);
		apoderados.saveAndFlush(apoderado);
		auditoria.registrar(AccionAuditoria.DATOS_FACTURACION_CAMBIADOS, "apoderado", apoderado.getId().toString(),
				anterior, apoderado.getRuc() + " · " + apoderado.getRazonSocial(), "Apoderado "
						+ apoderado.nombreCompleto() + " (" + apoderado.getFamilia().getNombre() + "). Pedido por "
						+ solicitante + ", aprobado por " + aprobador + ". Motivo: " + motivo
						+ ". Desde ahora la familia puede pedir factura con este RUC.");
	}

	private static String vacioSiNulo(String texto) {
		return texto == null ? "" : texto;
	}

	private static String nuloSiVacio(String texto) {
		return texto == null || texto.isEmpty() ? null : texto;
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
	 * Pide cambiar el responsable de pago (auditoría A4): decide a quién se cobra y quién recibe los avisos. Lo aprueba
	 * otra persona de Promotoría o Dirección.
	 */
	public void solicitarCambioResponsable(Alumno alumno, Apoderado nuevo, String motivo) {
		if (!alumno.activo()) {
			throw new ReglaNegocioException(alumno.nombreCompleto() + " no está activo: no se cambia su responsable.");
		}
		if (nuevo.getId().equals(alumno.getResponsablePago().getId())) {
			throw new ReglaNegocioException(nuevo.nombreCompleto() + " ya es el responsable de pago.");
		}
		boolean otraFamilia = !alumno.getFamilia().getId().equals(nuevo.getFamilia().getId());
		solicitudes.crear(TipoSolicitud.CAMBIO_RESPONSABLE_PAGO, "alumno", alumno.getId(),
				"Responsable de pago de " + alumno.nombreCompleto() + ": "
						+ responsableTexto(alumno.getResponsablePago(), alumno.getFamilia()) + " → "
						+ responsableTexto(nuevo, nuevo.getFamilia()) + (otraFamilia ? " (pasa a otra familia)" : ""),
				Map.of("anteriorId", alumno.getResponsablePago().getId().toString(), "nuevoId", nuevo.getId().toString()),
				motivo);
	}

	/**
	 * Cambia el responsable de pago (solo lo llama el manejador de la solicitud aprobada). Si es de otra familia, el
	 * alumno pasa a esa familia. Queda RESALTADO en la bitácora.
	 */
	public void cambiarResponsable(Alumno alumno, Apoderado nuevo, String motivo, String solicitante, String aprobador) {
		Apoderado anterior = alumno.getResponsablePago();
		Familia familiaAnterior = alumno.getFamilia();
		alumno.cambiarResponsable(nuevo);
		boolean cambioDeFamilia = !familiaAnterior.getId().equals(nuevo.getFamilia().getId());
		auditoria.registrar(AccionAuditoria.RESPONSABLE_PAGO_CAMBIADO, "alumno", alumno.getId().toString(),
				responsableTexto(anterior, familiaAnterior), responsableTexto(nuevo, nuevo.getFamilia()),
				"Alumno " + alumno.nombreCompleto() + "." + (cambioDeFamilia ? " Pasó a otra familia." : "")
						+ " Pedido por " + solicitante + ", aprobado por " + aprobador + ". Motivo: " + motivo);
	}

	private static String responsableTexto(Apoderado apoderado, Familia familia) {
		return apoderado.nombreCompleto() + " (" + apoderado.getParentesco().etiqueta() + ", " + familia.getNombre()
				+ ")";
	}

	/**
	 * Bloquea el año escolar (SELECT ... FOR UPDATE). Regla de orden de bloqueos: primero el año y después la bitácora
	 * (que se bloquea al auditar). Quien vaya a matricular después de auditar algo debe llamarlo antes.
	 */
	public void bloquearAnio(AnioEscolar anio) {
		anios.bloquear(anio.getId());
	}

	/**
	 * Matricula al alumno en la sección (una matrícula por alumno y año) y publica {@link MatriculaRegistrada} en la
	 * misma transacción.
	 */
	public Matricula matricular(Alumno alumno, Seccion seccion, LocalDate fecha) {
		AnioEscolar anio = seccion.getAnioEscolar();
		// Primero el año (SELECT ... FOR UPDATE), igual que aprobar un plan o generar cronogramas: así una matrícula y
		// una aprobación simultáneas se serializan (nadie queda sin cronograma) y siempre en el mismo orden (sin
		// bloqueo mutuo en MySQL).
		anios.bloquear(anio.getId());
		LocalDate pedida = fecha == null ? anio.fechaMatriculaPorDefecto() : fecha;
		// Auditoría A3: un ingreso tardío recorta pensiones. Se matricula desde el inicio de clases (cronograma completo)
		// y la fecha pedida queda como solicitud que aprueba otra persona.
		boolean tardio = anio.ingresoTardio(pedida);
		if (tardio) {
			exigirFechaMatricula(pedida, anio);
			fecha = anio.fechaMatriculaPorDefecto();
		}
		else {
			fecha = pedida;
		}
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
		if (!tardio) {
			exigirFechaMatriculaRegular(fecha, anio);
		}
		Matricula matricula = Matricula.nueva(alumno, seccion, fecha);
		guardar(() -> matriculas.saveAndFlush(matricula), () -> yaMatriculado(alumno, anio, seccion));
		auditoria.registrar(AccionAuditoria.MATRICULA_REGISTRADA, "matricula", matricula.getId().toString(), null,
				seccion.etiqueta() + " " + anio.getAnio() + "; desde el " + Calendario.formatear(fecha),
				"Alumno " + alumno.nombreCompleto() + ".");
		eventos.publishEvent(new MatriculaRegistrada(matricula.getId()));
		if (tardio) {
			solicitudes.crear(TipoSolicitud.FECHA_MATRICULA, "matricula", matricula.getId(),
					"Ingreso de " + alumno.nombreCompleto() + " a " + seccion.etiqueta() + " " + anio.getAnio() + " desde el "
							+ Calendario.formatear(pedida) + " (el inicio de clases es el "
							+ Calendario.formatear(anio.getInicioClases()) + ")",
					Map.of("matriculaId", matricula.getId().toString(), "fecha", pedida.toString()),
					"Ingreso tardío pedido al matricular: hasta que se apruebe, se cobra desde el inicio de clases.");
		}
		return matricula;
	}

	/**
	 * Cambia la fecha de ingreso de una matrícula (solo la llama el manejador de la solicitud aprobada por otra
	 * persona). Bloquea primero el año, luego audita.
	 */
	public void cambiarFechaIngreso(Matricula matricula, LocalDate fecha, String solicitante, String aprobador,
			Long solicitudId) {
		anios.bloquear(matricula.getAnioEscolar().getId());
		exigirFechaMatricula(fecha, matricula.getAnioEscolar());
		LocalDate anterior = matricula.getFechaMatricula();
		if (!matricula.activa()) {
			throw new ReglaNegocioException("La matrícula ya no está activa.");
		}
		if (!matricula.getAnioEscolar().ingresoTardio(fecha)) {
			throw new ReglaNegocioException("La fecha pedida no es posterior al inicio de clases.");
		}
		matricula.cambiarFechaIngreso(fecha);
		matriculas.saveAndFlush(matricula);
		auditoria.registrar(AccionAuditoria.MATRICULA_FECHA_CAMBIADA, "matricula", matricula.getId().toString(),
				Calendario.formatear(anterior), Calendario.formatear(fecha), "Alumno "
						+ matricula.getAlumno().nombreCompleto() + ". Pedido por " + solicitante + ", aprobado por "
						+ aprobador + ".");
		eventos.publishEvent(new FechaIngresoCambiada(matricula.getId(), fecha, solicitante, aprobador, solicitudId));
	}

	/**
	 * Pide el retiro (auditoría A6): lo aprueba otra persona. La fecha no es futura ni anterior a la matrícula.
	 */
	public void solicitarRetiro(Alumno alumno, LocalDate fecha, String motivo) {
		if (!alumno.activo()) {
			throw new ReglaNegocioException(alumno.nombreCompleto() + " ya no está activo.");
		}
		exigirFechaRetiro(alumno, fecha);
		solicitudes.crear(TipoSolicitud.RETIRO_ALUMNO, "alumno", alumno.getId(),
				"Retirar a " + alumno.nombreCompleto() + " desde el " + Calendario.formatear(fecha),
				Map.of("fecha", fecha.toString()), motivo);
	}

	private void exigirFechaRetiro(Alumno alumno, LocalDate fecha) {
		if (fecha == null) {
			throw new ReglaNegocioException("Elige la fecha de retiro.");
		}
		if (fecha.isAfter(LocalDate.now(reloj))) {
			throw new ReglaNegocioException("La fecha de retiro no puede ser futura.");
		}
		exigirRetiroPosteriorALaMatricula(alumno, fecha, matriculas.findByAlumnoIdOrderByAnioEscolarAnioDesc(
				alumno.getId()).stream().filter(Matricula::activa).toList());
	}

	/**
	 * Retira al alumno y sus matrículas activas (solo lo llama el manejador de la solicitud aprobada). Queda resaltado.
	 * La fecha no puede ser anterior a la fecha de matrícula del año.
	 */
	public void retirar(Alumno alumno, LocalDate fecha, String motivo, String solicitante, String aprobador) {
		List<Matricula> activas = matriculas.findByAlumnoIdOrderByAnioEscolarAnioDesc(alumno.getId()).stream()
				.filter(Matricula::activa).toList();
		activas.stream().map(m -> m.getAnioEscolar().getId()).sorted().forEach(anios::bloquear);
		if (!alumno.activo()) {
			throw new ReglaNegocioException(alumno.nombreCompleto() + " ya no está activo.");
		}
		exigirFechaRetiro(alumno, fecha);
		alumno.retirar(fecha, solicitante, motivo);
		List<String> retiradas = new ArrayList<>();
		for (Matricula m : activas) {
			m.retirar(fecha);
			retiradas.add(m.getSeccion().etiqueta() + " " + m.getAnioEscolar().getAnio());
		}
		auditoria.registrar(AccionAuditoria.ALUMNO_RETIRADO, "alumno", alumno.getId().toString(), "activo",
				"retirado el " + Calendario.formatear(fecha), "Alumno " + alumno.nombreCompleto() + "."
						+ (retiradas.isEmpty() ? "" : " Matrículas retiradas: " + String.join(", ", retiradas) + ".")
						+ " Pedido por " + solicitante + ", aprobado por " + aprobador + ". Motivo: " + motivo);
	}

	/** Auditoría A6: la fecha de retiro no puede ser anterior a la de matrícula (evitaría todas las cuotas). */
	public void exigirRetiroPosteriorALaMatricula(Alumno alumno, LocalDate fecha, List<Matricula> activas) {
		for (Matricula m : activas) {
			if (m.getAnioEscolar().getAnio() <= fecha.getYear() && fecha.isBefore(m.getFechaMatricula())) {
				throw new ReglaNegocioException("La fecha de retiro (" + Calendario.formatear(fecha) + ") es anterior a la "
						+ "matrícula de " + alumno.nombreCompleto() + " en " + m.getAnioEscolar().getAnio() + " ("
						+ Calendario.formatear(m.getFechaMatricula()) + ").");
			}
		}
	}

	/** Ingreso regular (hasta el inicio de clases): entre el 01/07 del año anterior y el inicio; puede ser futura. */
	private void exigirFechaMatriculaRegular(LocalDate fecha, AnioEscolar anio) {
		if (fecha.isBefore(anio.primeraFechaDeMatricula())) {
			throw new ReglaNegocioException("La fecha de matrícula de " + anio.getAnio() + " debe estar entre el "
					+ Calendario.formatear(anio.primeraFechaDeMatricula()) + " y el "
					+ Calendario.formatear(anio.getFinClases()) + ".");
		}
	}

	/** Ingreso tardío: no futura y hasta el fin de clases. */
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
