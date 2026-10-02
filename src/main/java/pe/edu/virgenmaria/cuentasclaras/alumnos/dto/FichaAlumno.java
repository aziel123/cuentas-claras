package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.dto.SeccionOpcion;

import java.time.LocalDate;
import java.util.List;

/**
 * Ficha completa del alumno (solo personal autorizado: Promotoría, Dirección y Administración).
 *
 * @param edad                 edad cumplida hoy
 * @param otrosApoderados      apoderados activos de la familia que podrían ser responsables de pago
 * @param seccionesParaMatricular secciones activas de los años abiertos en los que aún no está matriculado
 */
public record FichaAlumno(CabeceraAlumno cabecera, String tipoDocumento, String numeroDocumento,
		String apellidoPaterno, String apellidoMaterno, String nombres, LocalDate fechaNacimiento, int edad,
		ApoderadoVista responsable, List<ApoderadoVista> otrosApoderados, List<HermanoVista> hermanos,
		List<MatriculaVista> matriculas, LocalDate retiradoEn, String retiradoPor, String motivoRetiro,
		List<SeccionOpcion> seccionesParaMatricular) {
}
