package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import java.util.List;

/** Filas leídas del Excel y avisos del archivo entero (por ejemplo, columnas extra que se ignoraron). */
public record LecturaImportacion(List<FilaImportacion> filas, List<String> avisos) {
}
