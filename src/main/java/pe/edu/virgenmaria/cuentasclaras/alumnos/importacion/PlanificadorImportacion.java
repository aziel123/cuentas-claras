package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosApoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DocumentoIdentidad;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compara cada fila leída con la base (del colegio actual, {@code @TenantId}) y decide qué se hará: alumno NUEVO,
 * ACTUALIZA (con la lista de cambios), SIN_CAMBIOS o ERROR. No escribe nada.
 * <ul>
 *   <li>La sección debe existir (y estar activa) en el año.</li>
 *   <li>Un alumno ya registrado no se mueve de familia ni de sección: eso se hace desde su ficha.</li>
 *   <li>Un alumno ya registrado con un apoderado que no está registrado es error: el apoderado nuevo se agrega
 *       desde la familia (cambiar a quién se cobra no se hace con un Excel sin revisarlo en la ficha).</li>
 *   <li>Si el apoderado del archivo es otro de SU misma familia, se cambia el responsable de pago (se muestra).</li>
 * </ul>
 */
@Component
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class PlanificadorImportacion {

	private final SeccionRepository secciones;

	private final AlumnoRepository alumnos;

	private final ApoderadoRepository apoderados;

	private final MatriculaRepository matriculas;

	public PlanificadorImportacion(SeccionRepository secciones, AlumnoRepository alumnos,
			ApoderadoRepository apoderados, MatriculaRepository matriculas) {
		this.secciones = secciones;
		this.alumnos = alumnos;
		this.apoderados = apoderados;
		this.matriculas = matriculas;
	}

	public PlanImportacion planificar(AnioEscolar anio, List<FilaImportacion> filas) {
		Map<String, Seccion> seccionesDelAnio = new HashMap<>();
		for (Seccion s : secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(anio.getId())) {
			seccionesDelAnio.put(claveSeccion(s.getGrado().name(), s.getNombre()), s);
		}
		List<FilaImportacion> validas = filas.stream().filter(FilaImportacion::valida).toList();
		Map<String, Alumno> alumnosPorDocumento = alumnos.findByDocumentoNumeroIn(
						validas.stream().map(f -> f.alumno().documento().numero()).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(a -> clave(a.getDocumento()), Function.identity()));
		Map<String, Apoderado> apoderadosPorDocumento = apoderados.findByDocumentoNumeroIn(
						validas.stream().map(f -> f.apoderado().documento().numero()).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(a -> clave(a.getDocumento()), Function.identity()));
		Map<Long, Matricula> matriculaPorAlumno = alumnosPorDocumento.isEmpty() ? Map.of()
				: matriculas.delAnioParaAlumnos(anio.getId(),
								alumnosPorDocumento.values().stream().map(Alumno::getId).toList()).stream()
						.collect(Collectors.toMap(m -> m.getAlumno().getId(), Function.identity()));

		Set<String> apoderadosNuevos = new HashSet<>();
		Set<String> apoderadosRevisados = new HashSet<>();
		Set<String> apoderadosActualizados = new HashSet<>();
		List<FilaPlan> plan = new ArrayList<>();
		List<String> huella = new ArrayList<>();
		huella.add("anio=" + anio.getId() + ":" + anio.getVersion());
		for (FilaImportacion fila : filas) {
			FilaPlan filaPlan = fila.valida()
					? planificarFila(fila, anio, seccionesDelAnio, alumnosPorDocumento, apoderadosPorDocumento,
							matriculaPorAlumno, apoderadosRevisados)
					: new FilaPlan(fila, Clasificacion.ERROR, List.of(), fila.errores(), null, null, null, false, false,
							false);
			plan.add(filaPlan);
			if (!filaPlan.conErrores()) {
				String claveApoderado = clave(fila.apoderado().documento());
				if (filaPlan.apoderadoId() == null) {
					apoderadosNuevos.add(claveApoderado);
				}
				else if (filaPlan.actualizarApoderado()) {
					apoderadosActualizados.add(claveApoderado);
				}
			}
			huella.add(lineaHuella(filaPlan, alumnosPorDocumento, apoderadosPorDocumento, matriculaPorAlumno));
		}

		ResumenImportacion resumen = new ResumenImportacion(filas.size(),
				(int) plan.stream().filter(FilaPlan::conErrores).count(),
				contar(plan, Clasificacion.NUEVO), contar(plan, Clasificacion.ACTUALIZA),
				contar(plan, Clasificacion.SIN_CAMBIOS), apoderadosNuevos.size(), apoderadosActualizados.size(),
				apoderadosNuevos.size(), (int) plan.stream().filter(FilaPlan::matricular).count(),
				filas.stream().mapToInt(f -> f.advertencias().size()).sum());
		return new PlanImportacion(List.copyOf(plan), resumen, sha256(String.join("\n", huella)));
	}

	private static FilaPlan planificarFila(FilaImportacion fila, AnioEscolar anio, Map<String, Seccion> seccionesDelAnio,
			Map<String, Alumno> alumnosPorDocumento, Map<String, Apoderado> apoderadosPorDocumento,
			Map<Long, Matricula> matriculaPorAlumno, Set<String> apoderadosRevisados) {
		List<ErrorFila> errores = new ArrayList<>();
		List<CambioFila> cambios = new ArrayList<>();
		Seccion seccion = seccionesDelAnio.get(claveSeccion(fila.grado().name(), fila.seccion()));
		String etiquetaSeccion = fila.grado().etiqueta() + " " + fila.seccion();
		if (seccion == null) {
			errores.add(error(fila, ColumnaImportacion.SECCION, "La sección " + etiquetaSeccion + " no existe en "
					+ anio.getAnio() + ". Créala primero en Colegio."));
		}
		else if (!seccion.isActiva()) {
			errores.add(error(fila, ColumnaImportacion.SECCION, "La sección " + etiquetaSeccion + " está desactivada en "
					+ anio.getAnio() + "."));
		}

		String claveApoderado = clave(fila.apoderado().documento());
		Apoderado apoderado = apoderadosPorDocumento.get(claveApoderado);
		boolean actualizarApoderado = false;
		if (apoderado != null) {
			if (!apoderado.isActivo()) {
				errores.add(error(fila, ColumnaImportacion.APODERADO_NUMERO_DOCUMENTO, "El apoderado "
						+ apoderado.nombreCompleto() + " está desactivado. Reactívalo o elige otro responsable."));
			}
			else if (apoderadosRevisados.add(claveApoderado)) {
				List<CambioFila> deApoderado = cambiosApoderado(apoderado, fila.apoderado());
				actualizarApoderado = deApoderado.stream().anyMatch(c -> !c.requiereSolicitud());
				cambios.addAll(deApoderado);
			}
		}

		Alumno alumno = alumnosPorDocumento.get(clave(fila.alumno().documento()));
		boolean actualizarAlumno = false;
		boolean matricular = alumno == null;
		if (alumno != null) {
			if (!alumno.activo()) {
				errores.add(error(fila, ColumnaImportacion.ALUMNO_NUMERO_DOCUMENTO, alumno.nombreCompleto() + " está "
						+ alumno.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT)
						+ ": no se importa. Revisa su ficha."));
			}
			if (apoderado == null) {
				errores.add(error(fila, ColumnaImportacion.APODERADO_NUMERO_DOCUMENTO, alumno.nombreCompleto()
						+ " ya está registrado y este apoderado no. Agrégalo desde la ficha de su familia y, si "
						+ "corresponde, cambia ahí el responsable de pago."));
			}
			else if (!apoderado.getFamilia().getId().equals(alumno.getFamilia().getId())) {
				errores.add(error(fila, ColumnaImportacion.APODERADO_NUMERO_DOCUMENTO, alumno.nombreCompleto()
						+ " es de otra familia (" + alumno.getFamilia().getNombre() + "). Si cambió de responsable de "
						+ "pago, corrígelo desde su ficha."));
			}
			else if (!apoderado.getId().equals(alumno.getResponsablePago().getId())) {
				// Auditoría A4: la importación no cambia el responsable de pago; se pide desde la ficha.
				cambios.add(new CambioFila("Responsable de pago de " + alumno.nombreCompleto(),
						alumno.getResponsablePago().nombreCompleto(), apoderado.nombreCompleto(), true));
			}
			List<CambioFila> deAlumno = cambiosAlumno(alumno, fila.alumno());
			actualizarAlumno = !deAlumno.isEmpty();
			cambios.addAll(deAlumno);
			Matricula matricula = matriculaPorAlumno.get(alumno.getId());
			if (matricula == null) {
				matricular = true;
				cambios.add(new CambioFila("Matrícula " + anio.getAnio(), "Sin matrícula", etiquetaSeccion));
			}
			else if (seccion != null && !matricula.getSeccion().getId().equals(seccion.getId())) {
				errores.add(error(fila, ColumnaImportacion.SECCION, alumno.nombreCompleto() + " ya está matriculado en "
						+ matricula.getSeccion().etiqueta() + " (" + anio.getAnio() + "). Para moverlo usa «Cambiar "
						+ "sección» en su ficha."));
			}
		}

		if (!errores.isEmpty()) {
			return new FilaPlan(fila, Clasificacion.ERROR, List.of(), List.copyOf(errores), null, null, null, false,
					false, false);
		}
		boolean aplicaAlgo = cambios.stream().anyMatch(c -> !c.requiereSolicitud());
		Clasificacion clasificacion = alumno == null ? Clasificacion.NUEVO
				: aplicaAlgo ? Clasificacion.ACTUALIZA : Clasificacion.SIN_CAMBIOS;
		return new FilaPlan(fila, clasificacion, List.copyOf(cambios), List.of(), alumno == null ? null : alumno.getId(),
				apoderado == null ? null : apoderado.getId(), seccion.getId(), actualizarAlumno, actualizarApoderado,
				matricular);
	}

	private static List<CambioFila> cambiosAlumno(Alumno alumno, DatosAlumno datos) {
		List<CambioFila> cambios = new ArrayList<>();
		String antes = alumno.nombreCompleto();
		String despues = nombre(datos.nombres(), datos.apellidoPaterno(), datos.apellidoMaterno());
		if (!antes.equals(despues)) {
			cambios.add(new CambioFila("Nombre del alumno", antes, despues));
		}
		if (!alumno.getFechaNacimiento().equals(datos.fechaNacimiento())) {
			cambios.add(new CambioFila("Fecha de nacimiento de " + antes, Calendario.formatear(alumno.getFechaNacimiento()),
					Calendario.formatear(datos.fechaNacimiento())));
		}
		return cambios;
	}

	private static List<CambioFila> cambiosApoderado(Apoderado apoderado, DatosApoderado datos) {
		List<CambioFila> cambios = new ArrayList<>();
		String antes = apoderado.nombreCompleto();
		String despues = nombre(datos.nombres(), datos.apellidoPaterno(), datos.apellidoMaterno());
		if (!antes.equals(despues)) {
			cambios.add(new CambioFila("Nombre del apoderado", antes, despues));
		}
		if (apoderado.getParentesco() != datos.parentesco()) {
			cambios.add(new CambioFila("Parentesco de " + antes, apoderado.getParentesco().etiqueta(),
					datos.parentesco().etiqueta()));
		}
		if (!Objects.equals(apoderado.getTelefonoWhatsapp(), datos.telefonoWhatsapp())) {
			cambios.add(new CambioFila("Celular de " + antes, enmascararTelefono(apoderado.getTelefonoWhatsapp()),
					enmascararTelefono(datos.telefonoWhatsapp()), true));
		}
		if (!Objects.equals(apoderado.getCorreo(), datos.correo())) {
			cambios.add(new CambioFila("Correo de " + antes, enmascararCorreo(apoderado.getCorreo()),
					enmascararCorreo(datos.correo()), true));
		}
		return cambios;
	}

	/** Todo lo que se hará y la versión de cada registro que se toca: si algo cambia en la base, cambia la huella. */
	private static String lineaHuella(FilaPlan p, Map<String, Alumno> alumnosPorDocumento,
			Map<String, Apoderado> apoderadosPorDocumento, Map<Long, Matricula> matriculaPorAlumno) {
		FilaImportacion f = p.fila();
		Alumno alumno = f.alumno() == null ? null : alumnosPorDocumento.get(clave(f.alumno().documento()));
		Apoderado apoderado = f.apoderado() == null ? null : apoderadosPorDocumento.get(clave(f.apoderado().documento()));
		Matricula matricula = alumno == null ? null : matriculaPorAlumno.get(alumno.getId());
		return Stream.of(f.fila(), p.clasificacion(), f.alumno(), f.apoderado(), f.grado(), f.seccion(),
						alumno == null ? "-" : alumno.getId() + ":" + alumno.getVersion(),
						apoderado == null ? "-" : apoderado.getId() + ":" + apoderado.getVersion(),
						matricula == null ? "-" : matricula.getId() + ":" + matricula.getVersion(), p.seccionId(),
						p.cambios(), p.errores(), p.actualizarAlumno(), p.actualizarApoderado(), p.matricular())
				.map(String::valueOf).collect(Collectors.joining("|"));
	}

	private static int contar(List<FilaPlan> plan, Clasificacion clasificacion) {
		return (int) plan.stream().filter(p -> p.clasificacion() == clasificacion).count();
	}

	private static ErrorFila error(FilaImportacion fila, ColumnaImportacion columna, String mensaje) {
		return new ErrorFila(fila.fila(), columna.letra(), columna.encabezado(), mensaje);
	}

	static String clave(DocumentoIdentidad documento) {
		return documento.tipo() + ":" + documento.numero();
	}

	private static String claveSeccion(String grado, String nombre) {
		return grado + ":" + Normalizador.paraBusqueda(nombre);
	}

	private static String nombre(String nombres, String paterno, String materno) {
		return nombres + " " + paterno + (materno == null ? "" : " " + materno);
	}

	private static String enmascararTelefono(String telefono) {
		return telefono == null ? "(ninguno)" : Enmascarar.telefono(telefono);
	}

	private static String enmascararCorreo(String correo) {
		return correo == null ? "(ninguno)" : Enmascarar.correo(correo);
	}

	static String sha256(String texto) {
		return sha256(texto.getBytes(StandardCharsets.UTF_8));
	}

	static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}
}
