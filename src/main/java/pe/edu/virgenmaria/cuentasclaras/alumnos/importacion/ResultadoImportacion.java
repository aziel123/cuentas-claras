package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

/** Importación confirmada. */
public record ResultadoImportacion(Long importacionId, int anio, String archivoNombre, ResumenImportacion resumen) {
}
