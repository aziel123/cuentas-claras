package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import java.util.List;

/**
 * Columna de una plantilla descargable.
 *
 * @param encabezado texto de la fila 1
 * @param texto      la columna tiene formato Texto (@): documentos y teléfonos no pierden el cero inicial
 * @param lista      valores permitidos (validación de lista), o vacía
 * @param ayuda      nota que aparece al seleccionar la celda (o null)
 * @param ancho      ancho en caracteres
 */
public record ColumnaPlantilla(String encabezado, boolean texto, List<String> lista, String ayuda, int ancho) {
}
