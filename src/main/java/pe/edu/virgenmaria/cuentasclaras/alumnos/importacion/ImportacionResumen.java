package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import pe.edu.virgenmaria.cuentasclaras.alumnos.model.ImportacionAlumnos;

import java.time.LocalDateTime;

/** Fila del historial de importaciones. */
public record ImportacionResumen(Long id, LocalDateTime creadoEn, String creadoPor, Integer anio,
		String archivoNombre, String sha256Corto, ImportacionAlumnos.Conteos conteos) {
}
